import SwiftUI

struct TermInfoView: View {
    @Environment(\.dismiss) private var dismiss
    private let terms = [
        ("分型", "相邻三根去包含 K 线的局部顶底，是构建笔的基础。"),
        ("笔", "连接相邻、符合独立 K 线间隔与极值规则的顶底分型。末笔仍可能变化。"),
        ("线段", "在笔之上，根据特征序列及分型确认形成的更高一级结构。"),
        ("笔中枢", "至少三笔价格区间的重叠核心。图中紫色区域为核心区间，延伸不改变原核心。"),
        ("MACD", "参数为 12/26/9，柱值为 2 ×（DIF − DEA），用于描述动能。"),
        ("背驰", "离开中枢的同向笔创新高或新低，但同号 MACD 柱面积减弱。趋势与盘整背驰分开描述。"),
        ("一类买卖点", "趋势背驰所对应的结构极值；盘整背驰仅作提示。"),
        ("二类买卖点", "一类点后首次同向回抽，且不破一类点价格的结构。"),
        ("三类买卖点", "离开中枢后第一笔回抽不再回到中枢核心区间。"),
        ("候选与确认", "末端笔可能修正，候选不算已确认。结构确认不代表已经通过交易效果验证。"),
        ("失效参考", "按信号自身结构固定参考。现有端点起已收盘 K 线严格越界即失效，价格恢复也不复用原信号。盘中触及另列事实。"),
        ("结构端点日期", "结构所定位的历史 K 线日期，不等于实际首次识别或确认日期；未追溯时显示未知。"),
        ("多级别联立", "两个级别最近信号已确认、同向，且次级别端点不早于大级别时，视为结构印证。"),
        ("量价证据", "MACD 面积比和前段、回抽量比尚未回测，不构成硬过滤条件或成功概率。")
    ]
    var body: some View {
        NavigationStack {
            List(terms, id: \.0) { title, detail in
                VStack(alignment: .leading, spacing: 8) { Text(title).font(.headline); Text(detail).font(.subheadline).foregroundStyle(.secondary) }.padding(.vertical, 6)
            }.listStyle(.plain).navigationTitle("术语说明").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .confirmationAction) { Button("完成") { dismiss() } } }
        }
    }
}
