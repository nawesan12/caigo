import SwiftUI
import Nucleo
import AuthenticationServices

/// Botón negro como "Agregar a Wallet" del prototipo (base `#333333`).
let ingresoGelNegro = Gel(fondo: .liso(C.negro), sombras: [externa(4, 0, Color(hex: 0x333333))])

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

/// Ingresar con Google o Apple (por Clerk). No está en el handoff: se arma con la composición y el
/// fondo violeta de 01c, el CTA lima y el botón negro de Wallet.
struct Ingresar: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var ocupado: String?
    @State private var web = IngresoWeb()

    var body: some View {
        GeometryReader { g in
            ZStack {
                IngresoFondoVioleta()
                VStack(spacing: 0) {
                    IngresoZonaFogon()
                    VStack(alignment: .leading, spacing: 12) {
                        T("Hacé planes. Conocé gente.", 40, 800, C.blanco, alto: 1, espaciado: -1.4)
                            .padding(.horizontal, 2)
                        if !c.clerk.disponible {
                            T("El ingreso todavía no está disponible en esta versión.", 14, 600, C.blanco.opacity(0.8), alto: 1.4)
                                .padding(.horizontal, 2)
                        }
                        Spacer().frame(height: 14)
                        VStack(spacing: 14) {
                            Cta(texto: "Seguir con Google", gel: Gels.limaChico, alto: 62, radio: 22, tam: 18, color: C.carbon,
                                habilitado: c.clerk.disponible && ocupado == nil, cargando: ocupado == "google") { entrarCon("google") }
                            Cta(texto: "Seguir con Apple", gel: ingresoGelNegro, alto: 56, radio: 20, tam: 16, color: C.blanco,
                                habilitado: c.clerk.disponible && ocupado == nil, cargando: ocupado == "apple") { entrarConApple() }
                            #if DEBUG
                            Button { ir.debug() } label: { T("Entrar con token", 15, 700, C.blanco).padding(4) }
                                .buttonStyle(EstiloPlano())
                            #endif
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 34 + g.safeAreaInsets.bottom)
                }
            }
            .ignoresSafeArea()
        }
        .ocultarBarraNavegacion()
        .preferredColorScheme(.dark)
    }

    /// Google (y Apple si el nativo no anda): la Frontend API de Clerk en ASWebAuthenticationSession.
    private func entrarCon(_ proveedor: String) {
        ocupado = proveedor
        Task {
            defer { ocupado = nil }
            do {
                let url = try await c.clerk.iniciar(proveedor)
                let vuelta = try await web.abrir(url)
                let jwt = try await c.clerk.completar(nonce: Clerk.nonce(de: vuelta))
                try await canjear(jwt)
            } catch let e as Clerk.ErrorClerk {
                c.avisos.error(ingresoMensajeClerk(e.codigo))
            } catch let e as IngresoErrorClerk {
                c.avisos.error(ingresoMensajeClerk(e.codigo))
            } catch is CancellationError {
            } catch {
                c.avisos.error(ingresoMensaje(error))
            }
        }
    }

    /// Sign in with Apple nativo, canjeado en Clerk (`oauth_token_apple`). Si Clerk no lo acepta
    /// (falta registrar el bundle en la conexión de Apple), se cae al ingreso web.
    private func entrarConApple() {
        #if os(iOS)
        ocupado = "apple"
        Task {
            do {
                let token = try await IngresoApple().pedirToken()
                do {
                    let jwt = try await IngresoClerkApple(fapi: c.clerk.fapi).completar(tokenApple: token)
                    try await canjear(jwt)
                    ocupado = nil
                } catch {
                    ocupado = nil
                    entrarCon("apple")
                }
            } catch {
                // Canceló la hoja de Apple: no se avisa nada.
                ocupado = nil
            }
        }
        #else
        entrarCon("apple")
        #endif
    }

    private func canjear(_ jwt: String) async throws {
        let s = try await c.api.crearSesion(jwtClerk: jwt)
        c.entrar(token: s.token, usuarioId: s.usuarioId)
        ir.alta(try await ingresoRutaDeAlta(c))
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

/// Sólo en debug: entrar pegando un token `cs_…` creado a mano en la base de desarrollo.
struct DebugToken: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var token = ""
    @State private var cargando = false

    var body: some View {
        Pantalla {
            VStack(alignment: .leading, spacing: 0) {
                IngresoBarraArriba(volver: { ir.atras() }) { T("Debug", 14, 700, C.texto2) }
                Spacer().frame(height: 32)
                VStack(alignment: .leading, spacing: 10) {
                    T("Entrar con token", 34, 700, C.carbon, alto: 1.05, espaciado: -0.8)
                    T("Pegá un token cs_… de la base de desarrollo.", 15, 400, C.texto2, alto: 1.5)
                }
                Spacer().frame(height: 28)
                IngresoCampo(valor: Binding(get: { token }, set: { token = $0.trimmingCharacters(in: .whitespacesAndNewlines) }),
                             pista: "cs_…", tam: 15)
                Spacer()
                Cta(texto: "Seguir", habilitado: token.hasPrefix("cs_"), cargando: cargando) { entrar() }
            }
            .padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 36)
        }
        .environment(\.colorScheme, .light)
        .onAppear {
            #if !DEBUG
            ir.atras()
            #endif
        }
    }

    private func entrar() {
        cargando = true
        Task {
            defer { cargando = false }
            do {
                // Se valida el token pidiendo /v1/yo con él antes de guardarlo.
                let yo: Perfil = try JSON.leer(Perfil.self, try await c.api.crudo("GET", c.api.url("/v1/yo"), autorizacion: token))
                c.entrar(token: token, usuarioId: yo.id)
                ir.alta(try await ingresoRutaDeAlta(c))
            } catch {
                c.salirLocal()
                c.avisos.error(ingresoMensaje(error))
            }
        }
    }
}
