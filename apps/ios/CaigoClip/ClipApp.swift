import SwiftUI
import Nucleo
#if os(iOS)
import StoreKit
#endif

/// App Clip de Caigo Tap: se abre al apoyar el celu en una placa (NFC) o al escanear su QR, que
/// apuntan a `https://caigo.com.ar/t/<placa>` (las NTAG 424 suman `?picc_data=…&cmac=…`, que se
/// mandan tal cual). Hace el check-in sin instalar la app: ingreso por Clerk si hace falta →
/// `POST /v1/sesion?cliente=ios` → `POST /v1/tap` → "Adentro". La sesión queda compartida con la app
/// completa (llavero del grupo + App Group), así al instalarla ya se entra.
@main
struct ClipApp: App {
    @State private var c = Contenedor()
    @State private var ir = Ir()
    @State private var clip = EstadoClip()

    var body: some Scene {
        WindowGroup {
            ClipRaiz()
                .environment(c)
                .environment(ir)
                .environment(clip)
                .modifier(ConPaleta())
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { a in
                    if let url = a.webpageURL { clip.recibir(url, c) }
                }
                .onOpenURL { url in clip.recibir(url, c) }
        }
    }
}

/// En qué anda el Clip.
@MainActor
@Observable
final class EstadoClip {
    enum Paso: Equatable {
        /// Sin placa todavía (abierto sin URL, p. ej. desde el sistema).
        case esperando
        /// Hay placa y falta la sesión.
        case ingresar
        /// Mandando el tap a la API.
        case mandando
        case adentro
        /// La API dijo que no (placa inactiva, sin plan, falta verificarse…): se muestra tal cual.
        case aviso(titulo: String, detalle: String)
    }

    var paso: Paso = .esperando
    var lectura: Lectura?
    /// Mostrar el SKOverlay para bajar la app completa.
    var ofrecerApp = false

    func recibir(_ url: URL, _ c: Contenedor) {
        guard case let .tap(placa, picc, cmac)? = c.destinoDe(url) else {
            paso = .aviso(titulo: "Esta no es una placa de Caigo", detalle: "Apoyá el celu en la placa Caigo Tap del lugar o escaneá su QR.")
            return
        }
        lectura = Lectura(placa: placa, picc: picc, cmac: cmac)
        seguir(c)
    }

    /// Con placa: si hay sesión, manda el tap; si no, pide ingresar.
    func seguir(_ c: Contenedor) {
        guard let l = lectura else { paso = .esperando; return }
        guard c.conSesion else { paso = .ingresar; return }
        paso = .mandando
        Task {
            do {
                async let yo = c.recargarYo()
                let r = try await c.api.tap(l)
                _ = await yo
                c.ultimoTap = r
                // Cada lectura de NTAG 424 vale una sola vez: no se reintenta con la misma.
                lectura = nil
                Sistema.vibrar()
                paso = .adentro
            } catch let e as ErrorApi where e.estado == 401 {
                paso = .ingresar
            } catch let e as ErrorApi {
                paso = .aviso(titulo: Self.titulo(e), detalle: e.detalle)
                ofrecerApp = true
            } catch {
                paso = .aviso(titulo: "No hay conexión", detalle: mensajeDe(error))
            }
        }
    }

    static func titulo(_ e: ErrorApi) -> String {
        switch e.codigo {
        case "tap_invalido": return "No pudimos leer la placa"
        case "falta_selfie", "falta_dni", "falta_telefono": return "Te falta verificarte"
        default: return e.estado == 404 ? "Nada para hacer acá" : "No se pudo hacer el check-in"
        }
    }

    /// Después de entrar: la sesión queda también en el App Group para la app completa.
    func entro(_ c: Contenedor, token: String, usuarioId: String) {
        c.entrar(token: token, usuarioId: usuarioId)
        Traspaso.dejar(token: token, usuarioId: usuarioId)
        seguir(c)
    }
}

struct ClipRaiz: View {
    @Environment(Contenedor.self) private var c
    @Environment(EstadoClip.self) private var clip

    var body: some View {
        @Bindable var clip = clip
        ZStack {
            switch clip.paso {
            case .esperando:
                ClipMensaje(titulo: "Apoyá el celu en la placa Caigo Tap",
                            detalle: "Acercá la parte de arriba del celu a la placa del lugar, o escaneá su QR con la cámara.",
                            icono: I.nfc)
            case .ingresar:
                ClipIngresar()
            case .mandando:
                ClipMensaje(titulo: "Haciendo check-in…", detalle: "Un segundo.", icono: I.nfc, cargando: true)
            case .adentro:
                VistaAdentro { _ in clip.ofrecerApp = true }
            case let .aviso(titulo, detalle):
                ClipMensaje(titulo: titulo, detalle: detalle, icono: I.cruz, reintentar: clip.lectura != nil ? { clip.seguir(c) } : nil)
            }
            CapaAvisos(avisos: c.avisos)
        }
        .animation(.easeOut(duration: 0.2), value: clip.paso)
        #if os(iOS)
        .appStoreOverlay(isPresented: $clip.ofrecerApp) { SKOverlay.AppClipConfiguration(position: .bottom) }
        #endif
    }
}

/// Fondo de la pantalla de la placa (Pases NFC 1d): violeta profundo sobre casi negro.
let fondoClip = Fondo.radial(cx: 0.5, cy: 0.6, rx: 0.9, ry: 0.55, Fondo.paradas((0, Color(hex: 0x3B2F78)), (0.75, C.oscuro6)))
let textoTenueClip = Color(hex: 0xC9C4DA)

/// Mensaje centrado sobre el fondo de la placa, con la acción de bajar la app.
struct ClipMensaje: View {
    var titulo: String
    var detalle: String
    var icono: String
    var cargando = false
    var reintentar: (() -> Void)?
    @Environment(EstadoClip.self) private var clip

    var body: some View {
        Pantalla(fondo: AnyView(ZStack { C.oscuro6; VistaFondo(fondo: fondoClip); Grano() })) {
            VStack(spacing: 18) {
                Spacer()
                ZStack {
                    if cargando {
                        Puntitos(color: C.lima, tam: 10)
                    } else {
                        Icono(icono, 64, C.lima, trazo: 2.4)
                    }
                }
                .frame(width: 150, height: 150)
                .gel(48, Gel(fondo: .vertical(C.oscuro1, C.carbon), sombras: [
                    interna(1, 0, rgba(255, 255, 255, 0.12)), externa(0, 60, rgba(116, 86, 241, 0.45)),
                ]))
                T(titulo, 30, 800, C.blancoCalido, alto: 1.05, espaciado: -1, alinear: .center)
                T(detalle, 15, 600, textoTenueClip, alto: 1.5, alinear: .center)
                Spacer()
                if let reintentar {
                    CtaGrande("Probar de nuevo", accion: reintentar)
                }
                if !cargando {
                    T("Bajate Caigo", 15, 800, C.lima)
                        .padding(.horizontal, 18).padding(.vertical, 12)
                        .tocable { clip.ofrecerApp = true }
                }
            }
            .padding(.horizontal, 24).padding(.bottom, 30)
        }
        .environment(\.paleta, .oscura)
    }
}
