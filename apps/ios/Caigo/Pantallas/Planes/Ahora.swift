import SwiftUI
import CoreLocation
import Nucleo

/// Las categorías del filtro de Ahora (las del mockup 02).
private let filtrosAhora: [(String?, String)] = [
    (nil, "Todos"), ("deporte", "Deporte"), ("juego", "Juego"), ("previa", "Previa"),
    ("cita_doble", "Cita doble"), ("salida", "Salida"), ("taller", "Taller"),
]

/// Ahora (02 lista · 03 mapa · 13a vacío · 14a oscuro) con los modos del prototipo: Planes, Gente y
/// Citas (Citas sólo si la persona eligió esa intención).
struct Ahora: View {
    var modoInicial: String?
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var modo: String?
    @State private var mapa = false
    @State private var categoria: String?
    @State private var punto: (Double, Double)?
    /// Sin permiso de ubicación (o sin señal): se consulta por la ciudad del perfil.
    @State private var sinUbicacion = false
    /// Barrio y ciudad de la ubicación (geocodificación inversa) para el header.
    @State private var lugarActual: String?
    /// Lo que se busca en el mapa (va a la API como `q`).
    @State private var busqueda = ""
    @State private var planes: [Plan]?
    @State private var error: String?
    @State private var cargando = true
    @State private var invitaciones: Invitaciones?
    @State private var vuelta = 0

    private var modoActual: String {
        let m = modo ?? modoInicial ?? "planes"
        return m == "citas" && !conCitas && c.yo != nil ? "planes" : m
    }
    private var conCitas: Bool { c.yo?.intenciones.contains("citas") == true }

    private struct Clave: Equatable {
        var lat: Double?; var lng: Double?; var sinUbicacion: Bool; var categoria: String?; var q: String?; var vuelta: Int
    }

    var body: some View {
        Group {
            if modoActual == "planes" && mapa {
                MapaAhora(planes: planes ?? [], punto: punto, categoria: $categoria, busqueda: $busqueda, alLista: { mapa = false })
            } else {
                contenido
            }
        }
        .task {
            if c.yo == nil { await c.recargarYo() }
            await ubicar()
        }
        .task { invitaciones = try? await c.api.invitaciones() }
        .task(id: Clave(lat: punto?.0, lng: punto?.1, sinUbicacion: sinUbicacion, categoria: categoria,
                        q: mapa ? Api.busqueda(busqueda) : nil, vuelta: vuelta)) {
            await cargar()
        }
        .task(id: punto.map { "\($0.0),\($0.1)" }) {
            guard let (la, ln) = punto else { return }
            lugarActual = await Ahora.barrio(la, ln)
        }
    }

    /// Pide el permiso si hace falta. Sin ubicación se consulta por ciudad, y si después llega el permiso
    /// (la persona lo acepta en el cartel del sistema) se pasa a la ubicación.
    private func ubicar() async {
        c.ubicador.pedirPermiso()
        if let u = await c.ubicacion() { punto = u; sinUbicacion = false; return }
        sinUbicacion = true
        for _ in 0..<30 {
            try? await Task.sleep(nanoseconds: 1_000_000_000)
            if Task.isCancelled { return }
            if c.ubicador.permitido, let u = await c.ubicacion() { punto = u; sinUbicacion = false; return }
        }
    }

    private func cargar() async {
        guard punto != nil || sinUbicacion else { return }
        let q = mapa ? Api.busqueda(busqueda) : nil
        // Mientras se escribe en el buscador, se espera a que la persona pare (y la tarea anterior se cancela).
        if q != nil {
            try? await Task.sleep(nanoseconds: 350_000_000)
            if Task.isCancelled { return }
        }
        cargando = true
        do {
            let ciudad = punto == nil ? ((c.yo?.ciudad).flatMap { $0.isEmpty ? nil : $0 } ?? "Mar del Plata") : nil
            planes = try await c.api.ahora(lat: punto?.0, lng: punto?.1, categoria: categoria, q: q, ciudad: ciudad).planes
            error = nil
        } catch is CancellationError {
        } catch let e as URLError where e.code == .cancelled {
        } catch {
            self.error = mensajeDe(error)
        }
        cargando = false
    }

    /// "Güemes, Mar del Plata" de unas coordenadas (CLGeocoder).
    static func barrio(_ la: Double, _ ln: Double) async -> String? {
        let marcas = try? await CLGeocoder().reverseGeocodeLocation(CLLocation(latitude: la, longitude: ln),
                                                                     preferredLocale: Locale(identifier: "es_AR"))
        guard let m = marcas?.first else { return nil }
        let partes = [m.subLocality, m.locality].compactMap { $0 }.filter { !$0.isEmpty }
        var vistas: [String] = []
        for x in partes where !vistas.contains(x) { vistas.append(x) }
        return vistas.isEmpty ? nil : vistas.joined(separator: ", ")
    }

    private var contenido: some View {
        ZStack(alignment: .top) {
            if modoActual != "planes" {
                MazoGente(modo: modoActual)
                    .padding(.top, 68)
            }
            VStack(alignment: .leading, spacing: 14) {
                encabezado
                if modoActual == "planes" { chips }
                if modoActual == "planes" { lista }
            }
            .padding(.top, 6)
        }
    }

    private var encabezado: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 4) {
                Icono(I.pin, 16, p.oscuro ? C.lima : C.violeta, trazo: 2.2)
                let delPerfil = [c.yo?.zona ?? "", c.yo?.ciudad ?? ""].filter { !$0.isEmpty }.joined(separator: ", ")
                let lugar = lugarActual ?? delPerfil
                T(lugar.isEmpty ? "Mar del Plata" : lugar, 14, 600, p.oscuro ? C.apagado2 : C.texto2, unRenglon: true)
                Spacer(minLength: 8)
                if p.oscuro {
                    T("Caigo", 25, 700, C.blancoCalido, espaciado: espaciadoTitulo(25))
                } else {
                    let hay = invitaciones?.invitaciones.isEmpty == false
                    BotonIcono(d: I.campana, tam: 42, radio: 15, iconoTam: 20, trazo: 2) {
                        ir.recibidos(modo: modoActual == "citas" ? "citas" : "gente", pestana: hay ? 1 : 0)
                    }
                    .overlay(alignment: .topTrailing) {
                        if hay { Circle().fill(C.coral).frame(width: 9, height: 9).offset(x: -10, y: 9) }
                    }
                    .accessibilityLabel("Te sumaron y te invitan")
                }
            }
            HStack {
                T(modoActual == "gente" ? "Gente" : (modoActual == "citas" ? "Citas" : "Ahora"), 40, 800, p.texto,
                  alto: 1, espaciado: -1.4, unRenglon: true)
                Spacer(minLength: 8)
                let modos: [(String, String)] = [("planes", "Planes"), ("gente", "Gente")] + (conCitas ? [("citas", "Citas")] : [])
                Segmentado(
                    opciones: modos.map(\.1),
                    seleccion: Binding(get: { max(0, modos.firstIndex { $0.0 == modoActual } ?? 0) }, set: { modo = modos[$0].0 }),
                    radio: 14, radioOpcion: 11, alto: 42, llenar: false, padOpcion: modos.count > 2 ? 10 : 13,
                    gelOn: { modos[$0].0 == "citas" ? Gels.coralBadge : nil },
                    colorOn: { modos[$0].0 == "citas" ? C.carbon : nil })
                    .fixedSize()
            }
        }
        .padding(.horizontal, 20)
    }

    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(filtrosAhora, id: \.1) { k, l in
                    Chip(texto: l, on: k == categoria,
                         gelOn: p.oscuro ? Gels.limaChico : Gels.oscuroSel,
                         gelOff: Gels.chip(p),
                         colorOn: p.oscuro ? C.carbon : C.blancoCalido,
                         colorOff: p.texto) { categoria = k }
                }
            }
            .padding(.horizontal, 20).padding(.top, 2).padding(.bottom, 6)
        }
    }

    @ViewBuilder
    private var lista: some View {
        if let planes, planes.isEmpty, !cargando {
            ScrollView { PlanesVacioAhora().padding(.bottom, altoTabBar) }
        } else {
            ScrollView {
                LazyVStack(spacing: 16) {
                    if let inv = invitaciones, !inv.invitaciones.isEmpty {
                        BannerInvitaciones(inv: inv)
                    }
                    HStack {
                        VStack(alignment: .leading, spacing: 0) {
                            T("Próximas 3 horas", 15, 700, p.texto, unRenglon: true)
                            if let planes {
                                T("\(planes.count) \(planes.count == 1 ? "plan cerca" : "planes cerca")", 13, 400, p.texto2, unRenglon: true)
                            }
                        }
                        Spacer(minLength: 8)
                        Segmentado(opciones: ["Lista", "Mapa"], seleccion: Binding(get: { 0 }, set: { if $0 == 1 { mapa = true } }),
                                   radio: 14, radioOpcion: 11, alto: 40, tam: 14, llenar: false)
                            .fixedSize()
                    }
                    if let planes {
                        ForEach(planes) { pl in TarjetaPlan(plan: pl, alCambiar: { _ in vuelta += 1 }) }
                    } else if let error {
                        VStack(spacing: 14) {
                            T(error, 15, 500, p.texto2, alinear: .center)
                            Chip(texto: "Probar de nuevo", on: false) { vuelta += 1 }
                        }
                        .padding(.top, 60)
                    } else {
                        Puntitos(color: p.acento).padding(.top, 60)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 4).padding(.bottom, altoTabBar)
            }
            .refreshable { vuelta += 1 }
        }
    }
}

/// El banner oscuro del prototipo: "3 personas te invitan a sus planes" (borroso sin Plus).
private struct BannerInvitaciones: View {
    var inv: Invitaciones
    @Environment(Ir.self) private var ir

    var body: some View {
        let n = inv.invitaciones.count
        let titulos = inv.invitaciones.map(\.titulo).filter { !$0.isEmpty }
        HStack(spacing: 12) {
            HStack(spacing: -8) {
                ForEach(Array(inv.invitaciones.prefix(3).enumerated()), id: \.offset) { _, i in
                    Avatar(nombre: "·", foto: i.de?.foto ?? i.foto, tam: 36, colorBorde: C.carbon, fondo: (C.oscuro2, C.oscuro2))
                        .blur(radius: inv.bloqueada ? 3 : 0)
                }
            }
            .padding(.leading, 8)
            VStack(alignment: .leading, spacing: 2) {
                T(n == 1 ? "1 persona te invita a su plan" : "\(n) personas te invitan a sus planes", 14, 800, C.blancoCalido)
                if !titulos.isEmpty {
                    T(titulos.map { $0.components(separatedBy: " en ").first ?? $0 }.joined(separator: ", "), 12, 600, Color(hex: 0xC9C4DA), unRenglon: true)
                }
            }
            Spacer(minLength: 0)
            if inv.bloqueada { PlanesChipPlus() }
            Icono(I.adelante, 18, Color(hex: 0xC9C4DA), trazo: 2.6)
        }
        .padding(EdgeInsets(top: 12, leading: 10, bottom: 12, trailing: 14))
        .gel(22, Gel(fondo: Gels.oscuroSel.fondo, sombras: [interna(1, 0, rgba(255, 255, 255, 0.08)), externa(14, 24, rgba(36, 33, 45, 0.6), spread: -14)], grano: true))
        .contentShape(Rectangle())
        .onTapGesture { ir.recibidos(modo: "gente", pestana: 1) }
        .accessibilityAddTraits(.isButton)
    }
}

/// 13a · No hay planes cerca.
struct PlanesVacioAhora: View {
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                Color.clear.frame(width: 130, height: 130).gel(36, nil, [externa(22, 24, rgba(116, 86, 241, 0.35))])
                Image("CaigoIcono").resizable().frame(width: 170, height: 170)
            }
            .rotationEffect(.degrees(-10))
            .frame(height: 180)
            T(VacioAhora.titulo, 30, 700, p.texto, alto: 1.08, espaciado: -0.6, alinear: .center)
            T(VacioAhora.cuerpo(Date()), 15, 400, p.texto2, alto: 1.5, alinear: .center)
            VStack(spacing: 12) {
                Cta(texto: "Armá un plan", alto: 58, tam: 16) { ir.crear() }
                Button { ir.principal(.explorar) } label: { T("Ver lo que hay mañana", 14, 700, C.violetaTexto) }
                    .buttonStyle(EstiloPlano())
            }
            .padding(.top, 6)
        }
        .padding(.horizontal, 28)
    }
}
