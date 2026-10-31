import Foundation

/// Ingreso con Google y Apple por Clerk, la identidad compartida de Grupo Vimo, sin el SDK: se habla
/// con la Frontend API en modo nativo (`_is_native=1`), con el token del cliente de Clerk en
/// `Authorization`. Es el mismo flujo que Android (datos/Clerk.kt) y la web de Tu Local Web:
///
/// 1. `iniciar` crea el intento (`oauth_google` / `oauth_apple`) con `redirect_url =
///    ar.com.caigo.app://callback` y devuelve la URL del proveedor, que se abre con
///    ASWebAuthenticationSession.
/// 2. El proveedor vuelve a Clerk y Clerk a `ar.com.caigo.app://callback?rotating_token_nonce=…`.
/// 3. `completar` recarga el intento con el nonce (o lo pasa a alta si la persona es nueva), pide el
///    JWT de la sesión de Clerk y cierra esa sesión: la API lo verifica y lo canjea por la de Caigo.
public final class Clerk: @unchecked Sendable {
    public struct ErrorClerk: Error, Sendable {
        public let codigo: String
        public var mensaje: String {
            switch codigo {
            case "cancelado": return "Se canceló el ingreso."
            case "sin_clave": return "Falta configurar el ingreso en esta versión."
            default: return "No pudimos completar el ingreso. Probá de nuevo."
            }
        }
    }

    public static let versionFapi = "2025-11-10"
    public static let vuelta = "ar.com.caigo.app://callback"
    public static let esquema = "ar.com.caigo.app"

    public let fapi: URL?
    public var disponible: Bool { fapi != nil }
    private let http: URLSession
    private let guardado: UserDefaults

    public init(publicable: String, http: URLSession = .shared, guardado: UserDefaults = .standard) {
        fapi = Clerk.fapiDe(publicable)
        self.http = http
        self.guardado = guardado
    }

    /// La Frontend API sale de la clave publicable: pk_<entorno>_<base64("host$")>.
    public static func fapiDe(_ publicable: String?) -> URL? {
        guard let p = publicable else { return nil }
        let partes = p.split(separator: "_", maxSplits: 2).map(String.init)
        guard partes.count == 3, partes[0] == "pk", partes[1] == "live" || partes[1] == "test" else { return nil }
        var b = partes[2].replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        while b.count % 4 != 0 { b += "=" }
        guard let datos = Data(base64Encoded: b), var host = String(data: datos, encoding: .utf8) else { return nil }
        if host.hasSuffix("$") { host.removeLast() }
        let valido = host.range(of: "^[a-z0-9.-]+\\.[a-z]{2,}$", options: [.regularExpression, .caseInsensitive]) != nil
        return valido ? URL(string: "https://\(host)") : nil
    }

    private struct Verificacion: Decodable {
        var status: String?
        var external_verification_redirect_url: String?
    }
    private struct Recurso: Decodable {
        var id: String?
        var status: String?
        var created_session_id: String?
        var first_factor_verification: Verificacion?
    }
    private struct ErrorFapi: Decodable { var code: String? }
    private struct Respuesta: Decodable {
        var response: Recurso?
        var jwt: String?
        var errors: [ErrorFapi]?
    }

    private func pedir(_ ruta: String, _ metodo: String = "GET", cuerpo: [String: String]? = nil, cliente: String? = nil, query: [URLQueryItem] = []) async throws -> (Respuesta, String) {
        guard let base = fapi else { throw ErrorClerk(codigo: "sin_clave") }
        var c = URLComponents(url: base.appendingPathComponent("v1" + ruta), resolvingAgainstBaseURL: false)!
        c.queryItems = query + [URLQueryItem(name: "_is_native", value: "1"), URLQueryItem(name: "__clerk_api_version", value: Clerk.versionFapi)]
        var r = URLRequest(url: c.url!)
        r.httpMethod = metodo
        if let cliente, !cliente.isEmpty { r.setValue(cliente, forHTTPHeaderField: "Authorization") }
        if metodo == "POST" {
            r.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            r.httpBody = Data(Clerk.formulario(cuerpo ?? [:]).utf8)
        }
        let (datos, resp) = try await http.data(for: r)
        let h = resp as? HTTPURLResponse
        let respuesta = (try? JSONDecoder().decode(Respuesta.self, from: datos)) ?? Respuesta()
        guard let h, (200..<300).contains(h.statusCode) else {
            throw ErrorClerk(codigo: respuesta.errors?.first?.code ?? "clerk_no_responde")
        }
        // Clerk rota el token del cliente: vale el último que mandó.
        let nuevo = h.value(forHTTPHeaderField: "authorization") ?? ""
        return (respuesta, nuevo.isEmpty ? (cliente ?? "") : nuevo)
    }

    static func formulario(_ d: [String: String]) -> String {
        var permitidos = CharacterSet.alphanumerics
        permitidos.insert(charactersIn: "-._~")
        return d.sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: permitidos) ?? "")" }
            .joined(separator: "&")
    }

    /// Arranca el ingreso y devuelve la URL del proveedor. proveedor: google | apple.
    public func iniciar(_ proveedor: String) async throws -> URL {
        let (r, cliente) = try await pedir("/client/sign_ins", "POST", cuerpo: ["strategy": "oauth_\(proveedor)", "redirect_url": Clerk.vuelta])
        guard let s = r.response?.first_factor_verification?.external_verification_redirect_url, let url = URL(string: s),
              !cliente.isEmpty, let intento = r.response?.id, !intento.isEmpty else {
            throw ErrorClerk(codigo: "clerk_no_responde")
        }
        guardado.set(cliente, forKey: "clerk.cliente")
        guardado.set(intento, forKey: "clerk.intento")
        return url
    }

    /// Termina el ingreso con el nonce de la vuelta y devuelve el JWT de sesión de Clerk.
    public func completar(nonce: String?) async throws -> String {
        let clienteGuardado = guardado.string(forKey: "clerk.cliente") ?? ""
        let intento = guardado.string(forKey: "clerk.intento") ?? ""
        guardado.removeObject(forKey: "clerk.cliente")
        guardado.removeObject(forKey: "clerk.intento")
        guard !clienteGuardado.isEmpty, intento.range(of: "^sia_[A-Za-z0-9]+$", options: .regularExpression) != nil else {
            throw ErrorClerk(codigo: "estado_invalido")
        }
        // Con cuenta en Clerk la vuelta trae el nonce; alguien nuevo vuelve sin él (va al alta).
        let q = (nonce?.isEmpty == false) ? [URLQueryItem(name: "rotating_token_nonce", value: nonce)] : []
        var resp: Respuesta
        var cliente: String
        do {
            (resp, cliente) = try await pedir("/client/sign_ins/\(intento)", cliente: clienteGuardado, query: q)
        } catch let e as ErrorClerk where e.codigo == "external_account_not_found" {
            resp = Respuesta(response: Recurso(first_factor_verification: Verificacion(status: "transferable")))
            cliente = clienteGuardado
        }
        var sesion = resp.response?.created_session_id ?? ""
        if sesion.isEmpty, resp.response?.first_factor_verification?.status == "transferable" {
            (resp, cliente) = try await pedir("/client/sign_ups", "POST", cuerpo: ["transfer": "true"], cliente: cliente)
            sesion = resp.response?.created_session_id ?? ""
        }
        guard sesion.range(of: "^sess_[A-Za-z0-9]+$", options: .regularExpression) != nil else { throw ErrorClerk(codigo: "cancelado") }
        let (t, clienteT) = try await pedir("/client/sessions/\(sesion)/tokens", "POST", cliente: cliente)
        guard let jwt = t.jwt, !jwt.isEmpty else { throw ErrorClerk(codigo: "clerk_no_responde") }
        // Caigo sigue con su propia sesión: la de Clerk se cierra. Si falla, vence sola.
        _ = try? await pedir("/client/sessions/\(sesion)/remove", "POST", cliente: clienteT)
        return jwt
    }

    /// El nonce de la URL de vuelta (`ar.com.caigo.app://callback?rotating_token_nonce=…`).
    public static func nonce(de url: URL) -> String? {
        URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first { $0.name == "rotating_token_nonce" }?.value
    }
}
