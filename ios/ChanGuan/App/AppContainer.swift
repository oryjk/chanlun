import Foundation

@MainActor
final class AppContainer {
    let model: AppModel
    let apple: AppleAuthorization
    init() {
        let store = KeychainSessionStore()
        let text = Bundle.main.object(forInfoDictionaryKey: "ChanlunAPIBaseURL") as? String ?? ""
        let url = URL(string: text) ?? URL(fileURLWithPath: "/invalid-api-configuration")
        let api = APIClient(baseURL: url, store: store)
        let market = MarketGateway(api: api)
        let auth = AuthGateway(api: api)
        apple = AppleAuthorization(store: store)
        model = AppModel(market: MarketUseCases(reader: market, writer: market),
                         auth: AuthUseCases(reader: auth, commands: auth, authorizer: apple, store: store),
                         routes: UserDefaultsRouteStore())
    }
    func reconcileSession() async {
        guard !model.accountBusy else { return }
        let epoch = model.authEpoch
        let revoked = await apple.credentialWasRevoked()
        guard epoch == model.authEpoch, !model.accountBusy else { return }
        if revoked { model.credentialsRevoked() }
        else { await model.restoreSession() }
    }
}
