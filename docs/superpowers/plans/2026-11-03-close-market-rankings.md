# 全市场收盘榜单实施计划

> 执行方式：superpowers:executing-plans，在当前 main 工作区实现；按用户要求不提交、不推送。

**Goal:** 沪深 A 股及境内 ETF 每个收盘日分析一次，页面只读取持久化结果。

**Architecture:** 新建完整证券清单读取端口、榜单 Reader/Writer、收盘构建 Command；保持 domain 纯算法。SQLite 保存原子快照、任务租约与逐证券检查点，app 生命周期管理日更任务。

**Tech Stack:** Python、标准库 SQLite/线程池、FastAPI、React。

**Spec:** ../specs/2026-11-03-full-market-rankings-design.md，顶部日更修订优先。

## 约束与重点

- 北京时间默认 16:00 后运行，依据实际指数交易日期避开未收盘日及假期；完成的交易日不重算。
- 每只合格候选均参与，列表缺页不发布；仅输出各榜前 6 不限制扫描范围。
- GET 无写入、无扫描；多进程租约隔离，退出取消，旧结果在错误时继续可读。
- 只消费本批成功结果，不将旧价格冒充新数据；首次准备、覆盖数和收盘日期明确。
- 不改动其他会话正在编辑的视觉样式；保持现有架构与价格格式规则。

## Task 1：全量清单与收盘边界

- [x] 先增加分页超过 40、缺页/重复与数值市场 0 的失败测试；实现 StockUniverseReader.list_all()。
- [x] 先验证 GetMarketSnapshotQuery 的 through_date 截止规则，保留普通行情接口默认行为。
- [x] 实现指数日 K 对应的 ClosedTradingDayReader.latest_on_or_before(date)。
- [x] 运行完整后端测试。

## Task 2：持久化与只读榜单

- [x] 写入失败测试：空状态、重启恢复、只读 GET、租约互斥、过期接管及失败保留快照。
- [x] 实现 domain/rankings.py、独立 Reader/Writer 端口和 infrastructure/persistence/sqlite_ranking_store.py。
- [x] 将现有排序抽成纯领域服务，GetRankingsQuery 仅读取快照与状态。
- [x] 运行完整后端测试。

## Task 3：日更构建与生命周期

- [x] 测试全候选处理、同交易日跳过、重试、检查点恢复、未收盘 K 线过滤、全失败保留旧结果。
- [x] 实现 BuildDailyRankingsCommand.execute(trade_date, cancelled) 的 4 线程有界并发。
- [x] 实现 app/ranking_worker.py 的日期选择与不重叠调度，在 app/container.py 装配并由 lifespan 管理。
- [x] 配置运行目录、时刻、并发、租约与重试；更新 API DTO 和 AGENTS.md。
- [x] 运行完整后端测试。

## Task 4：页面与全量验证

- [x] 页面展示收盘日期、成功/总数、后台状态，首次准备与真正空榜区分；请求经过 Gateway。
- [x] 完成前后端测试、生产构建、真实 HTTP 与浏览器验证，确认 GET 不重复扫描。
- [ ] 实际枚举全范围并启动全量收盘分析，记录真实覆盖数、失败数和耗时；未完成前如实报告进度。
- [x] 更新 FEATURES.md、CHANGELOG.md；完成独立审查并修复重要问题。

## 执行记录

- Task 1–3：已实现，后端 81 项测试通过；包含完整分页、截止日期、同日去重、只读 HTTP、失败保留、恢复和租约接管。
- Task 4：前端 17 项测试、生产构建和 390px/1440px 浏览器状态检查通过；真实 HTTP 验证初始 pending 与持久化榜单，连续读取不再次计算或写入。
- 独立审查：2 个重要问题已以 RED→GREEN 回归修复：强制绕过盘中缓存、拒绝损坏目录行。无人值守任务的异常原因日志也补充失败测试后修复。
- 实现取舍：日更只持久化榜单与逐证券检查点，不引入盘中增量和全历史库；未来若恢复盘中更新，需要另补该能力。
- 未完成验收：真实完整清单请求当前失败；HTTP/HTTPS 及替代节点均未取得成功响应。首次全范围统计与耗时待服务恢复，不能宣称全市场数据已生成。

- 运行验证：开发后端已重启，健康检查和 12 项真实 HTTP 检查通过；当前收盘榜单 state=error、generated_at=null，正确反映目录失败且没有伪造已完成数据。后台将自动重试。
