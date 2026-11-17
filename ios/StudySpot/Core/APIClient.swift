import Foundation

struct APIError: LocalizedError, Sendable {
    var status: Int
    var message: String
    var notConnected: Bool = false

    var errorDescription: String? { message }
    var isAuthError: Bool { status == 401 || status == 403 }
}

/// Talks to the Study Spot Express/PostgreSQL backend (`server/`). Mirrors the
/// web client's surface: auth.*, entities.*, functions.invoke, connectors.*.
final class APIClient: @unchecked Sendable {
    static let shared = APIClient()

    private let session: URLSession
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 60
        session = URLSession(configuration: config)
    }

    var token: String? { Keychain.get(Keychain.tokenKey) }

    func setToken(_ token: String?) {
        if let token { Keychain.set(token, for: Keychain.tokenKey) }
        else { Keychain.delete(Keychain.tokenKey) }
    }

    // MARK: Core request

    func requestData(_ path: String, method: String = "GET", body: JSONValue? = nil, bearer: String? = nil) async throws -> Data {
        guard let url = URL(string: AppConfig.apiBaseURL + path) else {
            throw APIError(status: 0, message: "Invalid server URL. Check the server address in Settings.")
        }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        if let body {
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
            req.httpBody = try encoder.encode(body)
        }
        if let auth = bearer ?? token { req.setValue("Bearer \(auth)", forHTTPHeaderField: "Authorization") }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: req)
        } catch {
            throw APIError(status: 0, message: "Couldn't reach the server. \(error.localizedDescription)")
        }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(status) else {
            let json = try? decoder.decode(JSONValue.self, from: data)
            let message = json?["error"]?.stringValue ?? "Request failed (\(status))"
            throw APIError(status: status, message: message, notConnected: json?["notConnected"]?.boolValue == true)
        }
        return data
    }

    func request<T: Decodable>(_ path: String, method: String = "GET", body: JSONValue? = nil, as type: T.Type = T.self) async throws -> T {
        let data = try await requestData(path, method: method, body: body)
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIError(status: 0, message: "Unexpected response from server.")
        }
    }

    @discardableResult
    /// `bearer` overrides the stored session (e.g. to unregister while signing out).
    func send(_ path: String, method: String = "POST", body: JSONValue? = nil, bearer: String? = nil) async throws -> JSONValue {
        let data = try await requestData(path, method: method, body: body, bearer: bearer)
        if data.isEmpty { return .null }
        return (try? decoder.decode(JSONValue.self, from: data)) ?? .null
    }

    // MARK: Auth

    func me() async throws -> User { try await request("/auth/me") }

    @discardableResult
    func updateMe(_ patch: [String: JSONValue]) async throws -> User {
        try await request("/auth/me", method: "PATCH", body: .object(patch))
    }

    func forgotPassword(email: String) async throws {
        try await send("/auth/forgot-password", body: ["email": .string(email)])
    }

    // MARK: Entities

    func list<T: Decodable>(_ entity: String, sort: String? = nil, limit: Int? = nil) async throws -> [T] {
        var items: [URLQueryItem] = []
        if let sort { items.append(URLQueryItem(name: "sort", value: sort)) }
        if let limit { items.append(URLQueryItem(name: "limit", value: String(limit))) }
        var comps = URLComponents()
        comps.queryItems = items.isEmpty ? nil : items
        return try await request("/entities/\(entity)\(comps.percentEncodedQuery.map { "?\($0)" } ?? "")")
    }

    @discardableResult
    func create<T: Decodable>(_ entity: String, _ data: [String: JSONValue]) async throws -> T {
        try await request("/entities/\(entity)", method: "POST", body: .object(data))
    }

    @discardableResult
    func update<T: Decodable>(_ entity: String, id: String, _ data: [String: JSONValue]) async throws -> T {
        try await request("/entities/\(entity)/\(id)", method: "PATCH", body: .object(data))
    }

    func delete(_ entity: String, id: String) async throws {
        try await send("/entities/\(entity)/\(id)", method: "DELETE")
    }

    // Typed helpers
    func assignments(sort: String = "-due_date", limit: Int = 500) async throws -> [Assignment] {
        try await list("Assignment", sort: sort, limit: limit)
    }
    func classes() async throws -> [SchoolClass] { try await list("Class") }
    func templates() async throws -> [AssignmentTemplate] { try await list("AssignmentTemplate", sort: "-updated_date", limit: 100) }
    func notifications() async throws -> [AppNotification] { try await list("Notification", sort: "-created_date", limit: 30) }

    @discardableResult
    func updateAssignment(_ id: String, _ data: [String: JSONValue]) async throws -> Assignment {
        try await update("Assignment", id: id, data)
    }

    // MARK: Functions

    @discardableResult
    func invoke(_ name: String, _ payload: [String: JSONValue] = [:]) async throws -> JSONValue {
        try await send("/functions/\(name)", body: .object(payload))
    }

    func invoke<T: Decodable>(_ name: String, _ payload: [String: JSONValue] = [:], as type: T.Type) async throws -> T {
        try await request("/functions/\(name)", method: "POST", body: .object(payload))
    }

    func connections() async throws -> Connections {
        try await invoke("checkGoogleConnections", as: Connections.self)
    }

    // MARK: Connectors

    func googleConnectorURL(_ connector: String) async throws -> URL {
        let res = try await send("/connectors/google/start?connector=\(connector)&client=ios", method: "GET")
        guard let s = res["url"]?.stringValue, let url = URL(string: s) else {
            throw APIError(status: 0, message: "Couldn't start the Google connection.")
        }
        return url
    }

    func disconnectGoogle(_ connector: String) async throws {
        try await send("/connectors/google/\(connector)", method: "DELETE")
    }
}
