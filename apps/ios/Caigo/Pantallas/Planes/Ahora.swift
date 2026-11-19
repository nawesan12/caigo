import SwiftUI
import CoreLocation
import Nucleo

/// Las categorías del filtro de Ahora (las del mockup 02).
private let filtrosAhora: [(String?, String)] = [
    (nil, "Todos"), ("deporte", "Deporte"), ("juego", "Juego"), ("previa", "Previa"),
    ("cita_doble", "Cita doble"), ("salida", "Salida"), ("taller", "Taller"),
]

/// Ahora (02 lista · 03 mapa · 13a vacío · 14a oscuro) con los modos del prototipo en la cabecera: Planes,
/// Gente y Citas (Citas sólo si la persona eligió esa intención). Volvieron el 19/11 (decisión de Nahuel:
/// deslizar gente es lo que le da sentido a Citas). Sin DNI se elige el modo igual y se ve una lámina que
/// pide verificarse (el mazo pide el DNI: son caras de otra gente).
struct Ahora: View {
    var modoInicial: String?
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var modo: String?
    @State private var mapa = false
    @State private var categoria: String?
    /// «Sólo chicas» (planes «Solo mujeres»): gratis, como toda la seguridad.
    @State private var chicas = false
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
    /// «Esta semana en <ciudad>»: de lunes a miércoles, con 5 planes o más.
    @State private var semana: Semana?
    @State private var vuelta = 0
    /// «No me interesa» y su «Deshacer».
    @State private var ocultos = PlanesOcultos()
    /// «Decidí rápido» abierto: los planes del mazo (los de la lista al abrirlo).
    @State private var decidiendo: [Plan]?
    /// El plan de hoy (fijo arriba, con «Llegué» y «¿Todo bien?») y su hoja.
    @State private var hoy: Plan?
    @State private var hojaHoy = false

    private var modoActual: String {
        let m = modo ?? modoInicial ?? "planes"
        return m == "citas" && !conCitas && c.yo != nil ? "planes" : m
    }
    private var conCitas: Bool { c.yo?.intenciones.contains("citas") == true }

    private struct Clave: Equatable {
        var lat: Double?; var lng: Double?; var sinUbicacion: Bool; var categoria: String?; var chicas: Bool; var q: String?
        var vuelta: Int
    }

    var body: some View {
        Group {
            if modoActual == "planes" && mapa {
                MapaAhora(planes: ocultos.visibles(planes ?? []), punto: punto, categoria: $categoria, busqueda: $busqueda, alLista: { mapa = false })
            } else {
                contenido
            }
        }
        .task {
            if c.yo == nil { await c.recargarYo() }
            await ubicar()
        }
        // Un aviso que abre Ahora en Gente o Citas con Ahora ya en pantalla.
        .onChange(of: modoInicial) { _, m in if let m { modo = m } }
        .task { invitaciones = try? await c.api.invitaciones() }
        .task(id: vuelta) { if c.yo?.verificada == true { hoy = await planDeHoy(c) } }
        .task {
            guard Dia.semanaVisible(), let s = try? await c.api.semana(), s.total >= 5 else { return }
            semana = s
        }
        .task(id: Clave(lat: punto?.0, lng: punto?.1, sinUbicacion: sinUbicacion, categoria: categoria, chicas: chicas,
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
            planes = try await c.api.ahora(lat: punto?.0, lng: punto?.1, categoria: categoria, filtros: FiltrosFinos(chicas: chicas),
                                           q: q, ciudad: ciudad).planes
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
                // Sin el renglón de ubicación y logo (ver encabezado): el mazo sube y gana lugar.
                Group {
                    if c.yo?.verificada == false {
                        MazoSinDni(modo: modoActual)
                    } else {
                        MazoGente(modo: modoActual)
                    }
                }
                .padding(.top, 24)
            }
            VStack(alignment: .leading, spacing: 14) {
                encabezado
                if modoActual == "planes" {
                    chips
                    lista
                }
            }
            .padding(.top, 6)
            AvisoDeshacer(ocultos: ocultos)
            if let hoy {
                HojaTodoBien(p: hoy, abierta: hojaHoy, cerrar: { withAnimation { hojaHoy = false } },
                             reportar: { ir.plan(hoy.id) }, recargar: { vuelta += 1 })
                    .zIndex(15)
            }
            if let d = decidiendo {
                DecidiRapido(planes: d, ocultos: ocultos) {
                    decidiendo = nil
                    vuelta += 1
                }
                .transition(.opacity)
                .zIndex(20)
            }
        }
        .animation(.easeOut(duration: 0.2), value: decidiendo == nil)
        .ocultarTabBar(decidiendo != nil, ir)
    }

    private var encabezado: some View {
        VStack(alignment: .leading, spacing: 14) {
            // En Gente y Citas la ubicación y el logo se sacan: le roban lugar a la tarjeta (Nahuel, 1/11).
            if modoActual == "planes" {
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
                        ir.recibidos(modo: "gente", pestana: hay ? 1 : 0)
                    }
                    .overlay(alignment: .topTrailing) {
                        if hay { Circle().fill(C.coral).frame(width: 9, height: 9).offset(x: -10, y: 9) }
                    }
                    .accessibilityLabel("Te sumaron y te invitan")
                }
            }
            }
            HStack {
                T(modoActual == "gente" ? "Gente" : (modoActual == "citas" ? "Citas" : "Ahora"), 40, 800, p.texto,
                  alto: 1, espaciado: -1.4, unRenglon: true)
                    .minimumScaleFactor(0.8)
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
                // Primero y separado: un interruptor (se suma a la categoría elegida), no otra categoría. La
                // seguridad a la vista, gratis (igual que la web).
                InterruptorChicas(on: $chicas)
                Rectangle().fill(p.oscuro ? Color(hex: 0x3A3550) : C.lavanda).frame(width: 1, height: 22)
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
        let planes = self.planes.map { ocultos.visibles($0) }
        if let planes, planes.isEmpty, !cargando {
            ScrollView { PlanesVacioAhora().padding(.bottom, altoTabBar) }
        } else {
            ScrollView {
                LazyVStack(spacing: 16) {
                    if let inv = invitaciones, !inv.invitaciones.isEmpty {
                        BannerInvitaciones(inv: inv)
                    }
                    if let hoy { HoyTarjeta(p: hoy) { withAnimation { hojaHoy = true } } }
                    if let semana { TarjetaSemana(s: semana) }
                    HStack {
                        VStack(alignment: .leading, spacing: 0) {
                            T("Próximas 3 horas", 15, 700, p.texto, unRenglon: true)
                            if let planes {
                                T("\(planes.count) \(planes.count == 1 ? "plan cerca" : "planes cerca")", 13, 400, p.texto2, unRenglon: true)
                                let mazo = Gestos.paraDecidir(planes, yo: c.usuarioId)
                                if mazo.count >= Gestos.minimoDecidir {
                                    Chip(texto: "Decidí rápido · \(mazo.count)", on: false, gelOff: Gels.chip(p), colorOff: p.texto,
                                         tam: 13, padH: 12, padV: 7, radio: 12) { decidiendo = mazo }
                                        .padding(.top, 8)
                                }
                            }
                        }
                        Spacer(minLength: 8)
                        Segmentado(opciones: ["Lista", "Mapa"], seleccion: Binding(get: { 0 }, set: { if $0 == 1 { mapa = true } }),
                                   radio: 14, radioOpcion: 11, alto: 40, tam: 14, llenar: false)
                            .fixedSize()
                    }
                    if let planes {
                        ForEach(planes) { pl in
                            TarjetaPlan(plan: pl, alCambiar: { _ in vuelta += 1 })
                                .modifier(DeslizarPlan(radio: 24, alCaigo: {
                                    Task { if await planesCaigo(c, ir, pl) != nil { vuelta += 1 } }
                                }, alNoMeInteresa: { withAnimation(.easeOut(duration: 0.2)) { ocultos.ocultar(c, pl) } },
                                pista: pl.id == planes.first?.id))
                        }
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
    @Environment(Contenedor.self) private var c
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
                Cta(texto: "Armá un plan", alto: 58, tam: 16) { if c.conDni(ir, "crear") { ir.crear() } }
                Button { ir.principal(.explorar) } label: { T("Ver lo que hay mañana", 14, 700, C.violetaTexto) }
                    .buttonStyle(EstiloPlano())
            }
            .padding(.top, 6)
        }
        .padding(.horizontal, 28)
    }
}

/// «Sólo chicas» como interruptor (no como otra categoría): borde y escudo apagado, lima con tilde prendido.
struct InterruptorChicas: View {
    @Binding var on: Bool
    @Environment(\.paleta) private var p

    var body: some View {
        Button { on.toggle() } label: {
            HStack(spacing: 6) {
                Icono(on ? I.check : I.escudo, 15, on ? C.carbon : (p.oscuro ? C.lima : C.violeta), trazo: on ? 3 : 2.2)
                T("Sólo chicas", 14, on ? 700 : 600, on ? C.carbon : (p.oscuro ? C.lima : C.violetaTexto), unRenglon: true)
            }
            .padding(.horizontal, 13).padding(.vertical, 8)
            .background {
                if on {
                    RoundedRectangle(cornerRadius: 14, style: .continuous).fill(C.lima)
                } else {
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder((p.oscuro ? C.lima : C.violeta).opacity(0.4), lineWidth: 1.5)
                }
            }
        }
        .buttonStyle(EstiloToque())
        .accessibilityLabel("Sólo chicas")
        .accessibilityValue(on ? "Activado" : "Desactivado")
        .accessibilityAddTraits(.isToggle)
    }
}

/// Gente o Citas sin el DNI: se puede elegir el modo, pero el mazo son caras de otra gente y pide verificarse.
/// Cartas borrosas (nunca fotos de nadie) y «Verificarme», que abre la pantalla amable de antes del DNI.
struct MazoSinDni: View {
    var modo: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p

    var body: some View {
        let citas = modo == "citas"
        VStack(spacing: 0) {
            ZStack {
                ForEach(0..<3, id: \.self) { i in
                    RoundedRectangle(cornerRadius: 34, style: .continuous)
                        .fill(LinearGradient(colors: citas ? [Color(hex: 0xF07A5F), Color(hex: 0x5B4BB0)] : [Color(hex: 0x8B74F5), Color(hex: 0x2E2850)],
                                             startPoint: .topLeading, endPoint: .bottomTrailing))
                        .overlay(Circle().fill(Color.white.opacity(0.28)).frame(width: 120).offset(y: -40).blur(radius: 18))
                        .blur(radius: 2)
                        .scaleEffect(1 - CGFloat(2 - i) * 0.05)
                        .offset(y: CGFloat(2 - i) * 14)
                        .opacity(i == 2 ? 1 : 0.55)
                }
                VStack(spacing: 14) {
                    Spacer()
                    Icono(I.escudoCheck, 34, C.lima, trazo: 2.4)
                        .frame(width: 64, height: 64)
                        .background(Circle().fill(rgba(255, 255, 255, 0.14)))
                    T(citas ? "Citas con gente verificada" : "Conocé gente que sale a lo mismo que\u{00A0}vos",
                      26, 800, C.blanco, alto: 1.1, espaciado: -0.6, alinear: .center)
                    T("Todos tienen el DNI verificado. Para ver y sumar gente, verificamos que seas\u{00A0}vos.",
                      15, 500, Color(hex: 0xE4E0EE), alto: 1.45, alinear: .center)
                    Spacer()
                }
                .padding(.horizontal, 28)
            }
            .padding(.horizontal, 14).padding(.top, 64)
            .accessibilityElement(children: .combine)
            CtaGrande("Verificarme") { _ = c.conDni(ir, "gente") }
                .padding(.horizontal, 20).padding(.top, 18)
        }
        .padding(.bottom, altoTabBar + 14)
    }
}
