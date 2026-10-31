import SwiftUI
import Nucleo
import AuthenticationServices

/// Botón negro como "Agregar a Wallet" del prototipo (base `#333333`).
let ingresoGelNegro = Gel(fondo: .liso(C.negro), sombras: [externa(4, 0, Color(hex: 0x333333))])

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
