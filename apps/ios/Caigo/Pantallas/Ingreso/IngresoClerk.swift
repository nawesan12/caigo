import SwiftUI
import Nucleo
import AuthenticationServices

// El ida y vuelta con Clerk (web y Sign in with Apple nativo). Lo usan la app y el App Clip.

/// Error del ida y vuelta con Clerk armado en la app (el de Nucleo no tiene init público).
struct IngresoErrorClerk: Error {
    var codigo: String
}

/// Mensajes para los códigos de error del ida y vuelta con Clerk.
func ingresoMensajeClerk(_ codigo: String) -> String {
    switch codigo {
    case "cancelado": return "Cancelaste el ingreso. Probá de nuevo cuando quieras."
    case "sin_clave": return "El ingreso todavía no está disponible en esta versión."
    default: return "No pudimos completar el ingreso. Probá de nuevo."
    }
}

/// La hoja web del sistema para el OAuth de Clerk. Devuelve la URL de vuelta
/// (`ar.com.caigo.app://callback?rotating_token_nonce=…`).
@MainActor
final class IngresoWeb: NSObject, ASWebAuthenticationPresentationContextProviding {
    private var sesion: ASWebAuthenticationSession?

    func abrir(_ url: URL) async throws -> URL {
        try await withCheckedThrowingContinuation { cont in
            let s = ASWebAuthenticationSession(url: url, callbackURLScheme: Clerk.esquema) { vuelta, error in
                if let vuelta {
                    cont.resume(returning: vuelta)
                } else if let e = error as? ASWebAuthenticationSessionError, e.code == .canceledLogin {
                    cont.resume(throwing: IngresoErrorClerk(codigo: "cancelado"))
                } else {
                    cont.resume(throwing: error ?? IngresoErrorClerk(codigo: "clerk_no_responde"))
                }
            }
            s.presentationContextProvider = self
            s.prefersEphemeralWebBrowserSession = false
            sesion = s
            if !s.start() { cont.resume(throwing: IngresoErrorClerk(codigo: "clerk_no_responde")) }
        }
    }

    nonisolated func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        MainActor.assumeIsolated {
            #if os(iOS)
            return Sistema.ventana() ?? ASPresentationAnchor()
            #else
            return NSApplication.shared.keyWindow ?? ASPresentationAnchor()
            #endif
        }
    }
}

#if os(iOS)
/// La hoja nativa de Sign in with Apple: devuelve el identity token (JWT de Apple).
@MainActor
final class IngresoApple: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    private var cont: CheckedContinuation<String, Error>?
    private var controlador: ASAuthorizationController?

    func pedirToken() async throws -> String {
        try await withCheckedThrowingContinuation { c in
            cont = c
            let pedido = ASAuthorizationAppleIDProvider().createRequest()
            pedido.requestedScopes = [.fullName, .email]
            let ctl = ASAuthorizationController(authorizationRequests: [pedido])
            ctl.delegate = self
            ctl.presentationContextProvider = self
            controlador = ctl
            ctl.performRequests()
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        let token = (authorization.credential as? ASAuthorizationAppleIDCredential)?.identityToken.flatMap { String(data: $0, encoding: .utf8) }
        MainActor.assumeIsolated {
            if let token { cont?.resume(returning: token) } else { cont?.resume(throwing: IngresoErrorClerk(codigo: "clerk_no_responde")) }
            cont = nil
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        MainActor.assumeIsolated {
            cont?.resume(throwing: error)
            cont = nil
        }
    }

    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated { Sistema.ventana() ?? ASPresentationAnchor() }
    }
}

/// Si la persona cerró la hoja de Apple (no se avisa nada). Cualquier otro error de la hoja pasa al
/// ingreso web con Apple.
func ingresoAppleCancelado(_ e: Error) -> Bool {
    (e as? ASAuthorizationError)?.code == .canceled
}
#endif

/// El canje del token de Apple en la Frontend API de Clerk (modo nativo), sin pasar por la web:
/// `POST /client/sign_ins strategy=oauth_token_apple&token=…`; si la persona es nueva, el intento
/// queda "transferable" y se pasa a `sign_ups` con `transfer=true`. Después se pide el JWT de la
/// sesión y se cierra la sesión de Clerk (Caigo sigue con la suya), igual que `Clerk.completar`.
struct IngresoClerkApple {
    var fapi: URL?

    private struct Verificacion: Decodable { var status: String? }
    private struct Recurso: Decodable {
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

    private func pedir(_ ruta: String, _ cuerpo: [String: String], cliente: String?) async throws -> (Respuesta, String) {
        guard let fapi else { throw IngresoErrorClerk(codigo: "sin_clave") }
        var c = URLComponents(url: fapi.appendingPathComponent("v1" + ruta), resolvingAgainstBaseURL: false)!
        c.queryItems = [URLQueryItem(name: "_is_native", value: "1"), URLQueryItem(name: "__clerk_api_version", value: Clerk.versionFapi)]
        var r = URLRequest(url: c.url!)
        r.httpMethod = "POST"
        r.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        if let cliente, !cliente.isEmpty { r.setValue(cliente, forHTTPHeaderField: "Authorization") }
        var permitidos = CharacterSet.alphanumerics
        permitidos.insert(charactersIn: "-._~")
        r.httpBody = Data(cuerpo.sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: permitidos) ?? "")" }
            .joined(separator: "&").utf8)
        let (datos, resp) = try await URLSession.shared.data(for: r)
        let h = resp as? HTTPURLResponse
        let res = (try? JSONDecoder().decode(Respuesta.self, from: datos)) ?? Respuesta()
        guard let h, (200..<300).contains(h.statusCode) else {
            throw IngresoErrorClerk(codigo: res.errors?.first?.code ?? "clerk_no_responde")
        }
        let nuevo = h.value(forHTTPHeaderField: "authorization") ?? ""
        return (res, nuevo.isEmpty ? (cliente ?? "") : nuevo)
    }

    func completar(tokenApple: String) async throws -> String {
        var (r, cliente) = try await pedir("/client/sign_ins", ["strategy": "oauth_token_apple", "token": tokenApple], cliente: nil)
        var sesion = r.response?.created_session_id ?? ""
        if sesion.isEmpty, r.response?.first_factor_verification?.status == "transferable" {
            (r, cliente) = try await pedir("/client/sign_ups", ["transfer": "true"], cliente: cliente)
            sesion = r.response?.created_session_id ?? ""
        }
        guard sesion.range(of: "^sess_[A-Za-z0-9]+$", options: .regularExpression) != nil else { throw IngresoErrorClerk(codigo: "cancelado") }
        let (t, clienteT) = try await pedir("/client/sessions/\(sesion)/tokens", [:], cliente: cliente)
        guard let jwt = t.jwt, !jwt.isEmpty else { throw IngresoErrorClerk(codigo: "clerk_no_responde") }
        _ = try? await pedir("/client/sessions/\(sesion)/remove", [:], cliente: clienteT)
        return jwt
    }
}
