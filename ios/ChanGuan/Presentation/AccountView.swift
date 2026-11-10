import SwiftUI
import AuthenticationServices

struct AccountView: View {
    @ObservedObject var model: AppModel
    @State private var deleting = false
    var body: some View {
        List {
            if let user = model.user {
                Section("当前账号") { Label(user.nickname, systemImage: "person.crop.circle"); Text("通过 Apple 登录").font(.footnote).foregroundStyle(.secondary) }
                Section {
                    Button("退出登录") { Task { await model.logout() } }.disabled(model.accountBusy)
                    Button("删除账号", role: .destructive) { deleting = true }.disabled(model.accountBusy)
                }
            } else {
                Section {
                    Text("游客可使用全部行情分析功能").font(.subheadline)
                    AppleSignInControl { Task { await model.login() } }.frame(height: 44).disabled(model.accountBusy)
                } header: { Text("登录缠观") } footer: { Text("使用 Apple 账号登录，无需填写手机号。") }
            }
            if model.accountBusy { ProgressView("正在处理，请稍候") }
            if !model.accountError.isEmpty { Text(model.accountError).font(.footnote).foregroundStyle(.secondary) }
            Section("关于缠观") {
                Text("股票与 ETF 行情、量价及缠论结构分析")
                Text("买卖点为规则化结构识别，不构成买卖建议").font(.footnote).foregroundStyle(.secondary)
                NavigationLink("术语说明") { TermInfoView() }
            }
        }.listStyle(.plain)
        .confirmationDialog("删除缠观账号？", isPresented: $deleting, titleVisibility: .visible) {
            Button("删除账号与登录信息", role: .destructive) { Task { await model.deleteAccount() } }
            Button("取消", role: .cancel) { }
        } message: { Text("账号及登录信息将被删除，并撤销缠观的 Apple 授权。之后仍可作为游客查看行情。") }
    }
}

private struct AppleSignInControl: UIViewRepresentable {
    let action: () -> Void
    func makeCoordinator() -> Coordinator { Coordinator(action: action) }
    func makeUIView(context: Context) -> ASAuthorizationAppleIDButton {
        let button = ASAuthorizationAppleIDButton(type: .signIn, style: .black)
        button.cornerRadius = 4
        button.addTarget(context.coordinator, action: #selector(Coordinator.pressed), for: .touchUpInside)
        return button
    }
    func updateUIView(_ view: ASAuthorizationAppleIDButton, context: Context) { context.coordinator.action = action }
    final class Coordinator: NSObject {
        var action: () -> Void
        init(action: @escaping () -> Void) { self.action = action }
        @objc func pressed() { action() }
    }
}
