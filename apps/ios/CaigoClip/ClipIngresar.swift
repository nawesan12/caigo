import SwiftUI
import Nucleo

/// Ingreso del Clip: la misma cuenta de Caigo, con Apple o Google por Clerk (como Ingresar de la
/// app). El alta completa (teléfono, selfie, DNI) se hace en la app: si falta, la API lo dice al
/// hacer el tap y se ofrece bajarla.
struct ClipIngresar: View {
    @Environment(Contenedor.self) private var c
    @Environment(EstadoClip.self) private var clip
    @State private var ocupado: String?
    @State private var web = IngresoWeb()

    var body: some View {
        Pantalla(fondo: AnyView(ZStack { C.oscuro6; VistaFondo(fondo: fondoClip); Grano() })) {
            VStack(spacing: 16) {
                Spacer()
                Image("CaigoIcono").resizable().frame(width: 120, height: 120)
                    .background(Color.clear.frame(width: 96, height: 96).gel(30, nil, [externa(20, 30, rgba(0, 0, 0, 0.5))]))
                T("Entrá para hacer check-in", 30, 800, C.blancoCalido, alto: 1.05, espaciado: -1, alinear: .center)
                T("Es tu cuenta de Caigo. No hace falta bajar la app.", 15, 600, textoTenueClip, alto: 1.5, alinear: .center)
                Spacer()
                PagosBoton(texto: "Continuar con Apple", estilo: .negro, icono: nil, alto: 60, radio: 20, tam: 17, peso: 800,
                           cargando: ocupado == "apple") { entrarConApple() }
                    .disabled(!c.clerk.disponible || ocupado != nil)
                CtaGrande("Continuar con Google", habilitado: c.clerk.disponible && ocupado == nil, cargando: ocupado == "google") {
                    entrarCon("google")
                }
                if !c.clerk.disponible {
                    T("El ingreso todavía no está disponible en esta versión.", 13, 600, textoTenueClip, alinear: .center)
                }
            }
            .padding(.horizontal, 24).padding(.bottom, 30)
        }
        .environment(\.paleta, .oscura)
    }

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
                c.avisos.error(error)
            }
        }
    }

    /// Sign in with Apple nativo canjeado en Clerk; si Clerk no lo acepta, el ingreso web.
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
                ocupado = nil
                if !ingresoAppleCancelado(error) { entrarCon("apple") }
            }
        }
        #else
        entrarCon("apple")
        #endif
    }

    private func canjear(_ jwt: String) async throws {
        let s = try await c.api.crearSesion(jwtClerk: jwt)
        clip.entro(c, token: s.token, usuarioId: s.usuarioId)
    }
}
