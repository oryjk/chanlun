import SwiftUI
import AuthenticationServices

@main
@MainActor
struct ChanGuanApp: App {
    private let container = AppContainer()
    @Environment(\.scenePhase) private var scenePhase
    var body: some Scene {
        WindowGroup {
            RootView(model: container.model)
                .preferredColorScheme(.light)
                .task {
                    await container.reconcileSession()
                    await container.model.start()
                }
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { Task { await container.reconcileSession() } }
                }
                .onReceive(NotificationCenter.default.publisher(for: ASAuthorizationAppleIDProvider.credentialRevokedNotification)) { _ in
                    container.model.credentialsRevoked()
                }
        }
    }
}
