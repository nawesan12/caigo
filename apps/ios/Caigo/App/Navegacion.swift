import SafariServices
import SwiftUI
import Nucleo

/// La raíz: una sola pila con la base del momento y las pantallas apiladas encima.
struct CaigoRaiz: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var actualizar = false
    /// Un link de caigo.com.ar que la app no tiene: la web dentro de la app.
    @State private var web: URL?

    /// GET /v1/version: si este build es más viejo que `minIos`, no se deja seguir.
    func chequearVersion() async {
        guard let v = try? await c.api.version() else { return }
        let build = Int((Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String) ?? "") ?? 0
        if v.pideActualizar(build: build) { withAnimation { actualizar = true } }
    }

    var body: some View {
        @Bindable var ir = ir
        ZStack {
            NavigationStack(path: $ir.camino) {
                vistaBase(ir.base)
                    .navigationDestination(for: Ruta.self) { r in vista(r) }
            }
            CapaAvisos(avisos: c.avisos)
            if actualizar { ActualizarApp().transition(.opacity).zIndex(50) }
        }
        .task { await chequearVersion() }
        .sheet(item: Binding(get: { web.map(PaginaWeb.init) }, set: { web = $0?.url })) { p in NavegadorWeb(url: p.url).ignoresSafeArea() }
        .modifier(ConPaleta())
        .onChange(of: c.destino) { resolverDestino() }
        .onChange(of: ir.base) { resolverDestino() }
        .onChange(of: c.conSesion) { _, con in
            guard !con else { return }
            // Sin sesión (salió, borró la cuenta o la API respondió 401) en la app o en el alta: de vuelta a Ingresar,
            // con la pestaña de arranque (la próxima cuenta no hereda nada).
            ir.pestana = .ahora
            ir.modoAhora = nil
            switch ir.base {
            case .arranque, .intro, .ingresar, .debug: break
            default: ir.base = .ingresar; ir.camino = []
            }
        }
        // 403 `falta_dni` (red de seguridad: las pantallas lo chequean antes con `yo.verificada`): sin DNI se
        // mira, pero para hacer algo con otra gente se pide, con la pantalla amable de antes del DNI.
        .onChange(of: c.faltaDni) {
            guard c.conSesion, case .principal = ir.base else { return }
            if case .dni = ir.camino.last { return }
            ir.pedirDni("gente")
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
        case .dni: Dni(plan: nil, alta: true)
        case .intenciones: Intenciones(editar: false)
        case .completarPerfil: EditarPerfil(completar: true)
        case let .principal(p, m): Principal(inicial: p, modo: m)
        }
    }

    @ViewBuilder
    func vista(_ r: Ruta) -> some View {
        switch r {
        case .ingresar: Ingresar()
        case .debug: DebugToken()
        case .ingresoMail: IngresoMail()
        case .telefono: Telefono()
        case let .selfie(volver): Selfie(volver: volver)
        case let .intenciones(editar): Intenciones(editar: editar)
        case .verificacion: Verificacion()
        case let .dni(plan): Dni(plan: plan)
        case let .pedirDni(motivo, plan): PedirDni(motivo: motivo, plan: plan)
        case let .mazo(modo): VistaMazo(modo: modo)
        case .completarPerfil: EditarPerfil(completar: true, apilado: true)
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
        case let .editarPlan(id): Crear(editar: id)
        case .bloqueos: VistaBloqueos()
        case .ajustesAvisos: AjustesDeAvisos()
        case .semana: VistaSemana()
        case let .fotosPlan(id): FotosDelPlan(planId: id)
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
            case "detalle": if let plan { ir.plan(plan) }
            case "checkin": if let plan { ir.checkin(plan) }
            case "repetimos": if let plan { ir.repetimos(plan) }
            // La víspera («¿Seguís yendo?») y «¿Todo bien?»: el detalle, que muestra las dos cosas.
            case "vispera", "dia": if let plan { ir.plan(plan) }
            case "semana": ir.semana()
            // solicitud.rechazada / solicitud.sacada: de vuelta a Ahora a buscar otro plan.
            case "ahora": ir.principal(.ahora)
            case "recibidos": ir.recibidos(modo: modo ?? "gente", pestana: 0)
            case "invitaciones": ir.recibidos(modo: "gente", pestana: 1)
            case "pases": ir.pases()
            default:
                if let privado { ir.privado(privado, nombre: "") } else if let plan { ir.plan(plan) }
            }
        case let .seccion(p): ir.principal(p)
        case let .ruta(r): ir.ir(r)
        case let .web(u): web = u
        }
    }
}

/// La web de caigo.com.ar dentro de la app (un link que la app no tiene).
struct PaginaWeb: Identifiable { var url: URL; var id: String { url.absoluteString } }

struct NavegadorWeb: UIViewControllerRepresentable {
    var url: URL
    func makeUIViewController(context: Context) -> SFSafariViewController { SFSafariViewController(url: url) }
    func updateUIViewController(_ c: SFSafariViewController, context: Context) {}
}
