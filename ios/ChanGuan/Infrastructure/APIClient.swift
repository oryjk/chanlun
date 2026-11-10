import Foundation

enum APIError: LocalizedError {
    case configuration, response(Int, String), invalidData
    var errorDescription: String? {
        switch self {
        case .configuration: return "服务地址配置不正确，请检查 API 配置"
        case let .response(_, message): return message
        case .invalidData: return "服务返回的数据无法读取，请稍后重试"
        }
    }
}

extension JSONDecoder {
    static var market: JSONDecoder {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        decoder.dateDecodingStrategy = .custom { decoder in
            let text = try decoder.singleValueContainer().decode(String.self)
            let formatter = ISO8601DateFormatter()
            formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
            if let date = formatter.date(from: text) { return date }
            formatter.formatOptions = [.withInternetDateTime]
            if let date = formatter.date(from: text) { return date }
            throw DecodingError.dataCorruptedError(in: try decoder.singleValueContainer(), debugDescription: "Invalid ISO8601 date")
        }
        return decoder
    }
}

final class APIClient {
    let baseURL: URL
    let session: URLSession
    let store: any SessionStore
    init(baseURL: URL, store: any SessionStore, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.store = store
        self.session = session
    }

    func send<T: Decodable>(_ path: String, query: [URLQueryItem] = [], body: Data? = nil,
                            authenticated: Bool = false) async throws -> T {
        guard baseURL.host != nil, ["https", "http"].contains(baseURL.scheme ?? "") else { throw APIError.configuration }
        #if !DEBUG
        guard baseURL.scheme == "https" else { throw APIError.configuration }
        #endif
        var components = URLComponents(url: baseURL.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        if !query.isEmpty { components.queryItems = query }
        guard let url = components.url else { throw APIError.configuration }
        var request = URLRequest(url: url, timeoutInterval: 30)
        request.httpMethod = body == nil ? "GET" : "POST"
        request.httpBody = body
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if body != nil { request.setValue("application/json", forHTTPHeaderField: "Content-Type") }
        if authenticated, let token = try store.token() { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        let (data, response) = try await session.data(for: request)
        try Task.checkCancellation()
        guard let http = response as? HTTPURLResponse else { throw APIError.invalidData }
        guard (200..<300).contains(http.statusCode) else {
            struct ErrorBody: Decodable { let detail: String? }
            let detail = (try? JSONDecoder().decode(ErrorBody.self, from: data))?.detail
            throw APIError.response(http.statusCode, detail ?? "请求失败（\(http.statusCode)），请稍后重试")
        }
        do { return try JSONDecoder.market.decode(T.self, from: data) }
        catch { throw APIError.invalidData }
    }

    static func encode<T: Encodable>(_ value: T) throws -> Data {
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        encoder.dateEncodingStrategy = .iso8601
        return try encoder.encode(value)
    }
}
