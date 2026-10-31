import SwiftUI
import Nucleo

/// Pestañas de la tab bar flotante.
enum Pestana: String, Hashable { case ahora, explorar, mis, perfil }

/// Lo que va en la base de la pila (lo que en Android limpia la pila con popUpTo(0)).
enum Base: Hashable {
    case arranque
    case intro
    case ingresar
    case debug
    case datos
    case telefono
    case selfie
    case intenciones
    case principal(Pestana, modo: String?)
}

/// Las pantallas que se apilan (las mismas rutas que Navegacion.kt de Android).
enum Ruta: Hashable {
    case ingresar, debug
    case telefono
    case selfie(volver: Bool)
    case intenciones(editar: Bool)
    case verificacion
    case dni(plan: String?)
    case plan(String)
    case planSlug(String)
    case crear
    case publicado(String)
    case persona(String, desde: String)
    case recibidos(modo: String, pestana: Int)
    case solicitudes(String)
    case chat(String)
    case privados
    case privado(String, nombre: String)
    case checkin(String)
    case repetimos(String)
    case qrOrganizador(String)
    case escanearQr(String)
    case plus(motivo: String)
    case checkout(String, cantidad: Int)
    case entrada(String, plan: String)
    case qrEntrada(String)
    case pases
    case tap(plan: String?)
    case adentro
    case editarPerfil
    case gente
}

/// Las acciones de navegación que usan las pantallas. Ninguna pantalla toca la pila: así cada una se
/// puede armar y probar sola.
@MainActor
@Observable
final class Ir {
    var base: Base = .arranque
    var camino: [Ruta] = []
    /// Pestaña de Principal (se puede cambiar desde cualquier pantalla).
    var pestana: Pestana = .ahora
    var modoAhora: String?
    /// Mientras una hoja está abierta adentro de una pestaña, la tab bar se esconde.
    var tabBarOculta = false

    func atras() {
        // Sin nada apilado no se hace nada: desde el alta no se saltean pasos.
        if !camino.isEmpty { camino.removeLast() }
    }

    func ir(_ r: Ruta) {
        if camino.last == r { return }
        camino.append(r)
    }

    func principal(_ p: Pestana = .ahora, modo: String? = nil) {
        pestana = p
        modoAhora = modo
        tabBarOculta = false
        base = .principal(p, modo: modo)
        camino = []
    }

    func intro() { base = .intro; camino = [] }
    func ingresar() { ir(.ingresar) }
    func debug() { ir(.debug) }
    /// Siguiente paso del alta (datos → teléfono → selfie → intenciones) o la app.
    func alta(_ b: Base) {
        if case let .principal(p, m) = b { principal(p, modo: m) } else { base = b; camino = [] }
    }
    func telefono() { ir(.telefono) }
    func selfie(volver: Bool = false) { ir(.selfie(volver: volver)) }
    func intenciones(editar: Bool = false) { ir(.intenciones(editar: editar)) }

    func plan(_ id: String) { ir(.plan(id)) }
    func planSlug(_ slug: String) { ir(.planSlug(slug)) }
    func crear() { ir(.crear) }
    /// Después de publicar: reemplaza Crear por la confirmación.
    func publicado(_ id: String) {
        if camino.last == .crear { camino.removeLast() }
        camino.append(.publicado(id))
    }
    func solicitudes(_ planId: String) { ir(.solicitudes(planId)) }
    func chat(_ planId: String) { ir(.chat(planId)) }
    func privados() { ir(.privados) }
    func privado(_ conId: String, nombre: String) { ir(.privado(conId, nombre: nombre)) }
    func checkin(_ planId: String) { ir(.checkin(planId)) }
    func repetimos(_ planId: String) { ir(.repetimos(planId)) }
    func qrOrganizador(_ planId: String) { ir(.qrOrganizador(planId)) }
    func escanearQr(_ planId: String) { ir(.escanearQr(planId)) }
    func persona(_ id: String, desde: String = "perfil") { ir(.persona(id, desde: desde)) }
    func recibidos(modo: String, pestana: Int = 0) { ir(.recibidos(modo: modo, pestana: pestana)) }
    func plus(motivo: String = "perfil") { ir(.plus(motivo: motivo)) }
    func verificacion() { ir(.verificacion) }
    func dni(plan: String? = nil) { ir(.dni(plan: plan)) }
    func checkout(_ planId: String, cantidad: Int) { ir(.checkout(planId, cantidad: cantidad)) }
    func entrada(_ entradaId: String, plan: String) { ir(.entrada(entradaId, plan: plan)) }
    func qrEntrada(_ entradaId: String) { ir(.qrEntrada(entradaId)) }
    func pases() { ir(.pases) }
    func tap(plan: String? = nil) { ir(.tap(plan: plan)) }
    /// Después de un tap: reemplaza la pantalla de la placa por "Adentro".
    func adentro() {
        if case .tap = camino.last { camino.removeLast() }
        camino.append(.adentro)
    }
    func editarPerfil() { ir(.editarPerfil) }
    func gente() { ir(.gente) }
}

/// La raíz: una sola pila con la base del momento y las pantallas apiladas encima.
struct CaigoRaiz: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir

    var body: some View {
        @Bindable var ir = ir
        ZStack {
            NavigationStack(path: $ir.camino) {
                vistaBase(ir.base)
                    .navigationDestination(for: Ruta.self) { r in vista(r) }
            }
            CapaAvisos(avisos: c.avisos)
        }
        .modifier(ConPaleta())
        .onChange(of: c.destino) { resolverDestino() }
        .onChange(of: ir.base) { resolverDestino() }
        .onChange(of: c.conSesion) { _, con in
            if !con, case .principal = ir.base { ir.base = .ingresar; ir.camino = [] }
        }
    }

    @ViewBuilder
    func vistaBase(_ b: Base) -> some View {
        switch b {
        case .arranque: Arranque()
        case .intro: Intro()
        case .ingresar: Ingresar()
        case .debug: DebugToken()
        case .datos: Datos()
        case .telefono: Telefono()
        case .selfie: Selfie(volver: false)
        case .intenciones: Intenciones(editar: false)
        case let .principal(p, m): Principal(inicial: p, modo: m)
        }
    }

    @ViewBuilder
    func vista(_ r: Ruta) -> some View {
        switch r {
        case .ingresar: Ingresar()
        case .debug: DebugToken()
        case .telefono: Telefono()
        case let .selfie(volver): Selfie(volver: volver)
        case let .intenciones(editar): Intenciones(editar: editar)
        case .verificacion: Verificacion()
        case let .dni(plan): Dni(plan: plan)
        case let .plan(id): DetallePlan(id: id)
        case let .planSlug(slug): DetallePlan(slug: slug)
        case .crear: Crear()
        case let .publicado(id): Publicado(id: id)
        case let .persona(id, desde): PerfilPersona(id: id, desde: desde)
        case let .recibidos(modo, pestana): VistaRecibidos(modo: modo, pestana: pestana)
        case let .solicitudes(id): VistaSolicitudes(planId: id)
        case let .chat(id): Chat(planId: id)
        case .privados: VistaPrivados()
        case let .privado(id, nombre): VistaPrivado(conId: id, nombre: nombre)
        case let .checkin(id): Checkin(planId: id)
        case let .repetimos(id): Repetimos(planId: id)
        case let .qrOrganizador(id): QrOrganizador(planId: id)
        case let .escanearQr(id): EscanearQr(planId: id)
        case let .plus(motivo): VistaPlus(motivo: motivo)
        case let .checkout(id, cantidad): Checkout(planId: id, cantidad: cantidad)
        case let .entrada(id, plan): VistaEntrada(entradaId: id, planId: plan)
        case let .qrEntrada(id): QrEntrada(entradaId: id)
        case .pases: VistaPases()
        case let .tap(plan): Tap(planId: plan)
        case .adentro: VistaAdentro()
        case .editarPerfil: EditarPerfil()
        case .gente: MiGente()
        }
    }

    /// Links, avisos y placas: se resuelven cuando hay sesión y terminó el arranque y el alta.
    func resolverDestino() {
        guard let d = c.destino, c.conSesion, case .principal = ir.base else { return }
        c.destino = nil
        switch d {
        case let .plan(id): ir.plan(id)
        case let .planSlug(s): ir.planSlug(s)
        case .tap:
            c.tapPendiente = d
            ir.tap()
        case let .pantalla(pantalla, plan, privado, modo):
            switch pantalla {
            case "solicitudes": if let plan { ir.solicitudes(plan) }
            case "chat": if let plan { ir.chat(plan) }
            case "recibidos": ir.recibidos(modo: modo ?? "gente", pestana: 0)
            case "invitaciones": ir.recibidos(modo: "gente", pestana: 1)
            case "pases": ir.pases()
            default:
                if let privado { ir.privado(privado, nombre: "") } else if let plan { ir.plan(plan) }
            }
        }
    }
}
