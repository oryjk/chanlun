package cn.oryjk.changuan.domain

object Glossary {
    val entries: List<TermDefinition> = listOf(
        TermDefinition(
            key = "guidance",
            term = "结构观察结论",
            meaning = "把价格位置、已确认笔和已确认线段按固定规则汇总成偏多、偏空或等待的研究标签。",
            usage = "帮助你快速了解当前结构偏强、偏弱还是证据不足，避免只看一根K线或一段反弹就做决定。它不是自动买卖建议。",
        ),
        TermDefinition(
            key = "invalidation",
            term = "失效参考价",
            meaning = "当前买卖点或结构判断所依据的固定价格边界。收盘越过它后，原来的主要依据就不再成立。",
            usage = "提醒你什么时候应该重新评估结构，而不是机械沿用旧结论。它不是止损价、目标价或买卖指令。",
        ),
        TermDefinition(
            key = "buySellPoint",
            term = "三类买卖点",
            meaning = "一类：趋势背驰的转折点；二类：一类点后首次回抽不破一类点价格；三类：离开中枢后回抽不再回到中枢核心区间。B 为买点、S 为卖点，虚化表示候选。图上日期为结构端点，首次识别和确认通常更晚，尚未追溯。",
            usage = "按缠论规则标记结构上的关键转折位置，帮助你聚焦需要重点观察的价位。它由程序按固定规则识别，不是买卖指令。",
        ),
        TermDefinition(
            key = "confirmed",
            term = "已确认与候选",
            meaning = "已确认表示后续结构已经支持当前划分；候选表示新行情仍可能让它延伸或改变端点。",
            usage = "帮助你区分哪些结构相对稳定、哪些仍可能变化，避免把尚未确定的候选当成最终结论。",
        ),
        TermDefinition(
            key = "zhongshu",
            term = "笔中枢",
            meaning = "几段笔反复重叠形成的价格区域，可以理解为多空反复争夺、价格反复接受的密集区。",
            usage = "观察价格是在密集区内部、上方还是下方，并辅助判断当前结构偏强还是偏弱。这里由笔计算，不等同于严格级别的走势中枢。",
        ),
        TermDefinition(
            key = "confluence",
            term = "多级别联立",
            meaning = "比对日线、1 小时、30 分钟、5 分钟各自最近的买卖点：两级别均已确认，次级别与大级别同向且结构端点不早于大级别，视为同向印证（✓）。候选点不算已确认印证；端点顺序不代表实际识别时间顺序。",
            usage = "辅助观察不同级别的已确认同向结构，对应缠论的区间套思路；不代表实际识别时间顺序或已验证的收益结论。",
        ),
        TermDefinition(
            key = "pen",
            term = "笔与线段",
            meaning = "笔连接相邻局部高点和低点；线段由至少三段有重叠的笔构成，并在反向特征分型确认后成立。",
            usage = "过滤细小K线波动，识别中枢与走势骨架。单独一笔或一线段不直接给出买卖建议。",
        ),
        TermDefinition(
            key = "divergence",
            term = "走势类型与背驰",
            meaning = "最近两个笔中枢核心区间互不重叠且依次抬高或降低为趋势，否则为盘整。离开段创新高/新低且同号 MACD 柱面积小于进入段为背驰。",
            usage = "只有趋势背驰产生一类买卖点，盘整背驰仅作力度衰减提示。",
        ),
    )

    fun find(key: String): TermDefinition? =
        entries.firstOrNull { it.key == key }
}
