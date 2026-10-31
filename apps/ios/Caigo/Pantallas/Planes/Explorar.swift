import SwiftUI
import Nucleo

/// Las categorías de Explorar: «Todos» y las 8 de un plan (06a), cada una con su foto local CC0
/// (Recursos/Assets.xcassets/Explorar*, fuentes en apps/ios/FUENTES.md).
private let tilesExplorar: [(String?, String, String?)] =
    [(nil, "Todos", "ExplorarTodos")] + Categorias.paraCrear.map { ($0.0, $0.1, fotoTile($0.0)) }

private func fotoTile(_ clave: String) -> String? {
    switch clave {
    case "deporte": return "ExplorarDeporte"
    case "juego": return "ExplorarJuego"
    case "previa": return "ExplorarPrevia"
    case "salida": return "ExplorarSalida"
    case "gastronomia": return "ExplorarGastronomia"
    case "cita_doble": return "ExplorarCitaDoble"
    case "cultura": return "ExplorarCultura"
    case "otro": return "ExplorarOtro"
    default: return nil
    }
}

/// Edad de quien organiza (filtro fino): etiqueta y rango para la API.
private let rangosEdad: [(String, Int?, Int?)] = [("Todas", nil, nil), ("18–30", 18, 30), ("31–45", 31, 45), ("46+", 46, nil)]

private let gelTarjetaFila = Gel(fondo: .liso(C.blanco), sombras: [
    interna(1, 0, C.blanco), externa(2, 0, C.borde2), externa(12, 24, rgba(80, 60, 160, 0.22), spread: -12),
])

/// 04 · Explorar: "¿Qué tenés ganas de hacer?", categorías con foto, días, recurrentes y agenda.
struct Explorar: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var categoria: String?
    @State private var dia = 0
    @State private var busqueda = ""
    @State private var verificados = false
    @State private var faltan: Int?
    @State private var edad = 0
    @State private var gratis = false
    @State private var ciudad: String?
    @State private var zona: String?
    @State private var zonas: [String] = []
    @State private var punto: (Double, Double)?
    @State private var filtros = false
    @State private var todos = false
    @State private var semana: [Plan]?
    @State private var error: String?
    @State private var vuelta = 0

    private var conPlus: Bool { c.plus?.activo == true }

    /// Los filtros finos son de Caigo Plus: sin Plus no se mandan (la API respondería 403 `plus`).
    private var finos: FiltrosFinos {
        guard conPlus else { return FiltrosFinos() }
        let r = rangosEdad[edad]
        return FiltrosFinos(verificados: verificados, faltan: faltan, edadDesde: r.1, edadHasta: r.2, gratis: gratis)
    }

    private struct Clave: Equatable {
        var categoria: String?; var ciudad: String?; var zona: String?; var lat: Double?; var finos: FiltrosFinos; var vuelta: Int
        var q: String?
    }

    private func fechaDia(_ i: Int) -> Date { Fecha.calendario.date(byAdding: .day, value: i, to: Fecha.calendario.startOfDay(for: Date()))! }

    var body: some View {
        // La búsqueda la hace la API con `q` (título, zona y lugar).
        let planes = semana ?? []
        let delDia = planes.filter { pl in Fecha.parse(pl.empiezaEn).map { Fecha.diasEntre(Date(), $0) == dia } ?? false }
        let recurrente = planes.first { $0.recurrente }

        ScrollView {
            LazyVStack(alignment: .leading, spacing: 22) {
                VStack(alignment: .leading, spacing: 14) {
                    T("Explorar", 40, 800, p.texto, alto: 1, espaciado: -1.4)
                    buscador
                    if filtros { panelFiltros }
                }
                .padding(.horizontal, 20)
                tiles
                dias(planes)
                    .padding(.horizontal, 20)
                if let r = recurrente {
                    TarjetaRecurrente(plan: r).padding(.horizontal, 20)
                }
                HStack {
                    let d = fechaDia(dia)
                    T(dia == 0 ? "Hoy" : "\(Fecha.diaLargo(d)) \(Fecha.calendario.component(.day, from: d))", 22, 700, p.texto)
                    Spacer()
                    if delDia.count > 4 && !todos {
                        Button { todos = true } label: { T("Ver los \(delDia.count)", 13, 700, p.oscuro ? C.lima : C.violeta) }
                            .buttonStyle(EstiloPlano())
                    }
                }
                .padding(.horizontal, 20)
                Group {
                    if semana == nil {
                        Group {
                            if let error { T(error, 15, 500, p.texto2, alinear: .center) } else { Puntitos(color: p.acento) }
                        }
                        .frame(maxWidth: .infinity).padding(.top, 30)
                    } else if delDia.isEmpty {
                        VStack(spacing: 12) {
                            T("Todavía no hay planes para este día. ¿Armás uno?", 15, 500, p.texto2, alinear: .center)
                            Chip(texto: "Armá un plan", on: true) { ir.crear() }
                        }
                        .frame(maxWidth: .infinity).padding(.vertical, 10)
                    } else {
                        ForEach(todos ? delDia : Array(delDia.prefix(4))) { pl in FilaAgenda(plan: pl) }
                    }
                }
                .padding(.horizontal, 20)
            }
            .padding(.top, 8)
            .padding(.bottom, altoTabBar)
        }
        .scrollDismissesKeyboard(.interactively)
        .refreshable { vuelta += 1 }
        .task {
            if c.plus == nil { await c.recargarPlus() }
            // La ubicación sólo sirve para la distancia: si no hay permiso, no se pide de nuevo acá.
            if c.ubicador.permitido { punto = await c.ubicacion() }
        }
        .task(id: ciudad ?? c.yo?.ciudad ?? "") {
            let cdad = ciudad ?? c.yo?.ciudad ?? ""
            zonas = (try? await c.api.zonas(ciudad: cdad.isEmpty ? "Mar del Plata" : cdad).zonas) ?? []
            if let z = zona, !zonas.contains(z) { zona = nil }
        }
        .task(id: Clave(categoria: categoria, ciudad: ciudad, zona: zona, lat: punto?.0, finos: finos, vuelta: vuelta, q: Api.busqueda(busqueda))) {
            let q = Api.busqueda(busqueda)
            // Mientras se escribe, se espera un toque antes de buscar (si cambia, esta tarea se cancela).
            if q != nil { try? await Task.sleep(nanoseconds: 350_000_000); if Task.isCancelled { return } }
            let cal = Fecha.calendario
            let hasta = cal.date(byAdding: .day, value: 7, to: cal.startOfDay(for: Date()))!
            do {
                semana = try await c.api.explorar(desde: Fecha.iso(Date()), hasta: Fecha.iso(hasta), categoria: categoria, ciudad: ciudad,
                                                  filtros: finos, lat: punto?.0, lng: punto?.1, q: q, zona: zona).planes
                error = nil
            } catch is CancellationError {
            } catch let e as ErrorApi where e.codigo == "plus" {
                ir.plus(motivo: "filtros")
            } catch {
                self.error = mensajeDe(error)
            }
        }
    }

    private var buscador: some View {
        HStack(spacing: 12) {
            Icono(I.lupaChica, 22, p.oscuro ? C.lima : C.violeta, trazo: 2.4)
            PlanesCampo(texto: $busqueda, pista: "¿Qué tenés ganas de hacer?", tam: 16, colorPista: C.inactivo)
            Button { withAnimation(.easeOut(duration: 0.2)) { filtros.toggle() } } label: {
                Icono(I.filtros, 20, C.blanco, trazo: 2.4).frame(width: 46, height: 46).gel(16, planesVioletaChico)
            }
            .buttonStyle(EstiloToque())
            .accessibilityLabel("Filtros")
        }
        .padding(.leading, 18).padding(.trailing, 8)
        .frame(height: 60)
        .gel(22, p.oscuro ? Gels.tarjetaOscura : Gel(fondo: .liso(C.blanco), sombras: [
            interna(1.5, 0, C.blanco), externa(2, 0, C.borde2), externa(16, 30, rgba(80, 60, 160, 0.3), spread: -12),
        ]))
    }

    private var tiles: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 10) {
                ForEach(tilesExplorar, id: \.1) { k, l, foto in
                    let on = k == categoria
                    let radio: CGFloat = on ? 20 : 24
                    ZStack(alignment: .bottomLeading) {
                        colorDeCategoria(k ?? "")
                        if let foto {
                            Image(foto).resizable().scaledToFill().frame(width: on ? 86 : 96, height: on ? 102 : 112).clipped()
                        }
                        LinearGradient(stops: Fondo.paradas((0.4, rgba(32, 29, 43, 0)), (1, rgba(32, 29, 43, 0.85))), startPoint: .top, endPoint: .bottom)
                        T(l, 15, 700, C.blanco).padding(10)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: radio, style: .continuous))
                    .gel(radio, nil, [externa(12, 22, rgba(36, 33, 45, 0.45), spread: -10)])
                    .padding(on ? 5 : 0)
                    .overlay { if on { RoundedRectangle(cornerRadius: 27, style: .continuous).strokeBorder(C.violeta, lineWidth: 3) } }
                    .frame(width: 96, height: 112)
                    .contentShape(Rectangle())
                    .onTapGesture { categoria = k }
                    .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
                }
            }
            .padding(EdgeInsets(top: 6, leading: 24, bottom: 14, trailing: 20))
        }
    }

    private func dias(_ planes: [Plan]) -> some View {
        HStack(spacing: 6) {
            ForEach(0..<6, id: \.self) { i in
                let d = fechaDia(i)
                let on = i == dia
                let hay = planes.contains { pl in Fecha.parse(pl.empiezaEn).map { Fecha.diasEntre(Date(), $0) == i } ?? false }
                let fg = on ? C.blanco : p.texto
                VStack(spacing: 3) {
                    T(i == 0 ? "Hoy" : Fecha.diaCorto(d), 12, 700, fg)
                    T("\(Fecha.calendario.component(.day, from: d))", 22, 700, fg, alto: 1.1)
                    Circle().fill(on ? C.lima : (hay ? C.coral : .clear)).frame(width: 5, height: 5)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .gel(18, on
                     ? Gel(fondo: .vertical(Fondo.paradas((0, Color(hex: 0x9A84FF)), (0.55, C.violeta), (1, Color(hex: 0x6243E6)))), sombras: [
                         interna(2, 1, rgba(255, 255, 255, 0.4)), interna(-3, 0, rgba(40, 20, 140, 0.3)), externa(10, 18, rgba(116, 86, 241, 0.6), spread: -6)])
                     : (p.oscuro ? Gels.chipOscuro : Gel(fondo: .vertical(C.blanco, C.tinte5), sombras: [interna(1, 0, C.blanco), externa(2, 0, C.borde2)])))
                .contentShape(Rectangle())
                .onTapGesture { dia = i; todos = false }
                .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
            }
        }
    }

    /// Panel de filtros: ciudad para todos; los finos con el chip «Plus». Sin Plus, tocarlos abre el paywall.
    private var panelFiltros: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                ForEach([(String?.none, "Todas"), ("Mar del Plata", "Mar del Plata"), ("Villa Gesell", "Villa Gesell")], id: \.1) { k, l in
                    Chip(texto: l, on: ciudad == k, tam: 13, padH: 12, padV: 8, radio: 13) { ciudad = k }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            if !zonas.isEmpty {
                separador
                VStack(alignment: .leading, spacing: 10) {
                    T("Zona", 14, 700, C.carbon, unRenglon: true)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            Chip(texto: "Todas", on: zona == nil, tam: 13, padH: 12, padV: 8, radio: 13) { zona = nil }
                            ForEach(zonas, id: \.self) { z in
                                Chip(texto: z, on: zona == z, tam: 13, padH: 12, padV: 8, radio: 13) { zona = zona == z ? nil : z }
                            }
                        }
                        .padding(.horizontal, 16).padding(.bottom, 4)
                    }
                    .padding(.horizontal, -16)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16).padding(.vertical, 12)
            }
            separador
            filaInterruptor("Solo verificados", $verificados)
            separador
            filaChips("Que falten al menos", ["Cualquiera", "1", "2", "3"], faltan ?? 0) { faltan = $0 > 0 ? $0 : nil }
            separador
            filaChips("Edad de quien organiza", rangosEdad.map(\.0), edad) { edad = $0 }
            separador
            filaInterruptor("Solo gratis", $gratis)
        }
        .gel(22, Gels.neutra)
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    private var separador: some View { Rectangle().fill(C.lavanda).frame(height: 1) }

    private func titulo(_ t: String) -> some View {
        HStack(spacing: 8) {
            T(t, 14, 700, C.carbon, unRenglon: true)
            if !conPlus { PlanesChipPlus() }
        }
    }

    private func filaInterruptor(_ t: String, _ valor: Binding<Bool>) -> some View {
        HStack {
            titulo(t)
            Spacer()
            if conPlus {
                Interruptor(on: valor)
            } else {
                Interruptor(on: .constant(false)).allowsHitTesting(false)
            }
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .contentShape(Rectangle())
        .onTapGesture { if conPlus { valor.wrappedValue.toggle() } else { ir.plus(motivo: "filtros") } }
    }

    private func filaChips(_ t: String, _ opciones: [String], _ elegida: Int, _ elegir: @escaping (Int) -> Void) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            titulo(t)
            HStack(spacing: 8) {
                ForEach(Array(opciones.enumerated()), id: \.offset) { i, l in
                    Chip(texto: l, on: conPlus && i == elegida, tam: 13, padH: 12, padV: 8, radio: 13) {
                        if conPlus { elegir(i) } else { ir.plus(motivo: "filtros") }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16).padding(.vertical, 12)
    }
}

/// Recurrente: "Todos los sábados · 10:00 · Pádel mixto en Gesell".
private struct TarjetaRecurrente: View {
    var plan: Plan
    @Environment(Ir.self) private var ir

    var body: some View {
        let dias = ["domingos", "lunes", "martes", "miércoles", "jueves", "viernes", "sábados"]
        let t = Fecha.parse(plan.empiezaEn)
        let etiqueta = t.map { "Todos los \(dias[Fecha.calendario.component(.weekday, from: $0) - 1]) · \(Fecha.hora($0))" } ?? "Todas las semanas"
        ZStack {
            Foto(url: fotoDe(plan), tinte: colorDeCategoria(plan.categoria))
            LinearGradient(stops: Fondo.paradas((0, rgba(32, 29, 43, 0.88)), (0.75, rgba(32, 29, 43, 0.2))), startPoint: .leading, endPoint: .trailing)
            VStack(alignment: .leading) {
                HStack(spacing: 6) {
                    T(etiqueta, 12, 800, C.blanco, unRenglon: true)
                        .padding(.horizontal, 10).padding(.vertical, 6)
                        .gel(11, .liso(rgba(255, 255, 255, 0.18)))
                    if plan.destacado { ChipDestacado() }
                    Spacer(minLength: 0)
                }
                Spacer(minLength: 0)
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 4) {
                        T(plan.titulo, 24, 700, C.blanco, alto: 1.05, renglones: 2)
                        T([plan.lugar?.nombre ?? plan.lugarNombre, plan.soloVerificados ? "verificados" : ""].filter { !$0.isEmpty }.joined(separator: " · "),
                          13, 400, Color(hex: 0xDAD6E6), unRenglon: true)
                    }
                    Spacer(minLength: 8)
                    BadgeFalta(faltan: plan.faltan, tam: 17)
                }
            }
            .padding(.horizontal, 18).padding(.vertical, 16)
        }
        .frame(height: 168)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .gel(28, nil, [externa(18, 32, rgba(36, 33, 45, 0.5), spread: -14)])
        .contentShape(Rectangle())
        .onTapGesture { ir.plan(plan.id) }
        .accessibilityAddTraits(.isButton)
    }
}

/// Fila de la agenda: foto 64, "18:00 · PÁDEL", título, lugar y badge.
private struct FilaAgenda: View {
    var plan: Plan
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p

    var body: some View {
        let f = plan.faltan
        let acento = p.oscuro ? C.lima : C.violeta
        HStack(spacing: 14) {
            Foto(url: fotoDe(plan), tinte: colorDeCategoria(plan.categoria))
                .frame(width: 64, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 5) {
                    if plan.destacado { Icono(I.estrella, 12, .clear, trazo: 0, relleno: acento) }
                    T("\(Fecha.hora(plan.empiezaEn)) · \(Categorias.nombre(plan.categoria).uppercased())", 12, 800, acento, unRenglon: true)
                }
                T(plan.titulo, 15, 700, p.texto, alto: 1.25, renglones: 2)
                T(lugarYDistancia(plan) + (plan.soloVerificados ? " · Verificados" : ""), 12, 400, p.texto2, unRenglon: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            T(Cupo.badge(f), 15, 700, C.carbon, alto: 1, unRenglon: true)
                .padding(.horizontal, 10).padding(.vertical, 7)
                .gel(12, Gel(fondo: Cupo.completo(f) ? .vertical(C.cupoLleno1, C.cupoLleno2)
                    : f == 1 ? .vertical(Fondo.paradas((0, Color(hex: 0xFFA597)), (0.5, C.coral), (1, Color(hex: 0xF2604F))))
                    : .vertical(C.tinte2, Color(hex: 0xEAE4FF))))
        }
        .padding(EdgeInsets(top: 10, leading: 10, bottom: 10, trailing: 14))
        .gel(24, p.oscuro ? Gels.tarjetaOscura : gelTarjetaFila)
        .contentShape(Rectangle())
        .onTapGesture { ir.plan(plan.id) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}
