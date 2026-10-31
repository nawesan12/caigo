import SwiftUI
import Nucleo

private let cuales = ["organizo", "me_sume", "historial"]

/// 10 · Mis planes: Organizo / Me sumé / Historial, agrupados en HOY, ESTA SEMANA y MÁS ADELANTE.
struct MisPlanes: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var tab = 0
    @State private var planes: [String: [Plan]] = [:]
    @State private var errores: [String: String] = [:]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 16) {
                T("Mis planes", 40, 800, alto: 1, espaciado: -1.4, unRenglon: true)
                Segmentado(opciones: [etiqueta("Organizo", planes["organizo"]?.count), etiqueta("Me sumé", planes["me_sume"]?.count), "Historial"],
                           seleccion: $tab, alto: 46, padOpcion: 4)
            }
            .padding(.horizontal, 20)
            .padding(.top, 8)
            contenido.frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .task { await cargarTodo() }
        .refreshable { await cargarTodo() }
    }

    private func etiqueta(_ l: String, _ n: Int?) -> String { if let n, n > 0 { return "\(l) · \(n)" }; return l }

    private func cargarTodo() async {
        await withTaskGroup(of: Void.self) { g in
            for k in cuales { g.addTask { await cargar(k) } }
        }
    }

    @MainActor
    private func cargar(_ k: String) async {
        do {
            planes[k] = try await c.api.mios(k).planes
            errores[k] = nil
        } catch {
            if planes[k] == nil { errores[k] = mensajeDe(error) }
        }
    }

    @ViewBuilder
    private var contenido: some View {
        let k = cuales[tab]
        if let lista = planes[k] {
            if lista.isEmpty {
                vacio.padding(.horizontal, 28).padding(.bottom, altoTabBar)
            } else {
                ScrollView(showsIndicators: false) {
                    LazyVStack(alignment: .leading, spacing: 14) {
                        ForEach(tab == 2 ? [("", lista)] : agrupar(lista), id: \.0) { titulo, grupo in
                            if !titulo.isEmpty { MiosOverline(texto: titulo) }
                            ForEach(grupo) { pl in TarjetaMio(pl: pl, cual: k) }
                        }
                    }
                    .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, altoTabBar)
                }
                .refreshable { await cargar(k) }
            }
        } else if let e = errores[k] {
            MiosVacio(titulo: "No pudimos cargar tus planes", cuerpo: e, cta: "Probar de nuevo") { Task { await cargar(k) } }
                .padding(28)
        } else {
            Puntitos(color: p.acento)
        }
    }

    @ViewBuilder
    private var vacio: some View {
        switch tab {
        case 0: MiosVacio(titulo: "Todavía no armaste ningún plan", cuerpo: "Armá uno en menos de 30 segundos. Siempre hay alguien que se prende.", cta: "Armá un plan") { ir.crear() }
        case 1: MiosVacio(titulo: "Todavía no te sumaste a nada", cuerpo: "Hay una mesa de truco a la que le falta uno. Y sabemos que te la sabés.", cta: "Ver planes cerca") { ir.pestana = .ahora }
        default: MiosVacio(titulo: "Todavía no fuiste a ningún plan", cuerpo: "Los planes que pasaron quedan acá, para repetirlos.", cta: "Ver planes cerca") { ir.pestana = .ahora }
        }
    }
}

/// HOY / ESTA SEMANA / MÁS ADELANTE según la fecha del plan.
private func agrupar(_ planes: [Plan]) -> [(String, [Plan])] {
    let ahora = Date()
    var g: [String: [Plan]] = [:]
    for pl in planes {
        let dias = Fecha.parse(pl.empiezaEn).map { Fecha.diasEntre(ahora, $0) } ?? 0
        let k = dias <= 0 ? "HOY" : (dias < 7 ? "ESTA SEMANA" : "MÁS ADELANTE")
        g[k, default: []].append(pl)
    }
    return ["HOY", "ESTA SEMANA", "MÁS ADELANTE"].compactMap { k in g[k].map { (k, $0) } }
}

/// "20:30 · Cancha Los Pinos" hoy; "Jue 21:30 · se repite cada semana" otro día.
func miosSubtitulo(_ pl: Plan) -> String {
    var cuando = ""
    if let t = Fecha.parse(pl.empiezaEn) {
        cuando = Fecha.diasEntre(Date(), t) == 0 ? Fecha.hora(t) : "\(Fecha.diaCorto(t)) \(Fecha.hora(t))"
    }
    let lugar = pl.lugar?.nombre ?? (pl.lugarNombre.isEmpty ? pl.zona : pl.lugarNombre)
    let donde = pl.recurrente ? "se repite cada semana" : lugar
    return [cuando, donde].filter { !$0.isEmpty }.joined(separator: " · ")
}

/// El link público del plan para compartir.
func miosLinkCompartir(_ pl: Plan) -> String { "\(pl.titulo) · https://\(linkPlan(pl.slug))" }

private struct TarjetaMio: View {
    var pl: Plan
    var cual: String
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p

    var body: some View {
        let franja = cual == "organizo" && pl.pendientes > 0
        let hoy = Fecha.parse(pl.empiezaEn).map { Fecha.diasEntre(Date(), $0) == 0 } ?? false
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                Foto(url: pl.foto).frame(width: 72, height: 72).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                VStack(alignment: .leading, spacing: 4) {
                    T(pl.titulo, 18, 700, alto: 1.15)
                    T(miosSubtitulo(pl), 12, 400, p.texto2, unRenglon: true)
                    HStack(spacing: 6) { pie(hoy: hoy) }.padding(.top, 2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(14)
            if franja {
                Button { ir.solicitudes(pl.id) } label: {
                    HStack {
                        T(pl.pendientes == 1 ? "1 persona quiere sumarse" : "\(pl.pendientes) personas quieren sumarse", 14, 700, C.blanco)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Icono(I.flecha, 18, C.lima, trazo: 2.6)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    .background(C.violeta)
                }
                .buttonStyle(EstiloPlano())
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .gel(24, franja ? Gels.tarjeta(p) : miosGelTarjeta(p))
        .contentShape(Rectangle())
        .onTapGesture { ir.plan(pl.id) }
    }

    @ViewBuilder
    private func pie(hoy: Bool) -> some View {
        let enlace = p.oscuro ? C.lima : C.violetaTexto
        switch cual {
        case "me_sume":
            if pl.miEstado == "aceptada" {
                MiosPill(texto: "Adentro", fondo: C.lima, color: C.carbon, tam: 15)
            } else {
                MiosPill(texto: "Esperando", fondo: C.tinte1, color: C.violetaTexto, tam: 15)
            }
        case "historial":
            T("¿Repetimos?", 12, 700, enlace).tocable { ir.repetimos(pl.id) }
        default:
            badge(hoy: hoy)
            if pl.destacado {
                Icono(I.estrella, 14, .clear, trazo: 0, relleno: C.carbon)
                    .padding(.horizontal, 8).padding(.vertical, 6)
                    .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(C.lima))
                    .accessibilityLabel("Destacado")
            }
            if !Cupo.completo(pl.faltan) && !hoy {
                T("Compartir link", 12, 700, enlace).tocable { Sistema.compartir(miosLinkCompartir(pl)) }
            }
        }
    }

    /// Coral si es hoy, lavanda si es más adelante, lima "Ya somos N" si está completo.
    private func badge(hoy: Bool) -> some View {
        let (gel, texto): (Gel, String) = Cupo.completo(pl.faltan) ? (Gels.limaChico, "Ya somos \(pl.somos)")
            : (hoy ? (Gels.coralBadge, Cupo.badge(pl.faltan)) : (Gels.chipClaro, Cupo.badge(pl.faltan)))
        return T(texto, 15, 700, C.carbon, alto: 1, unRenglon: true)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .gel(10, gel)
    }
}
