import SwiftUI
import Nucleo

/// Botones redondos gel del mazo.
let genteGelPaso = Gel(fondo: .vertical(C.blanco, Color(hex: 0xF3F0FA)), sombras: [
    interna(2, 0, C.blanco), interna(-4, 0, rgba(255, 120, 104, 0.18)),
    externa(4, 0, Color(hex: 0xE4D6DA)), externa(14, 22, rgba(80, 60, 160, 0.35), spread: -10),
])
let genteGelCorazon = Gel(fondo: .vertical(Fondo.paradas((0, C.coral0), (0.35, Color(hex: 0xFF8A7B)), (0.6, C.coral), (1, Color(hex: 0xE85544)))), sombras: [
    interna(3, 2, rgba(255, 255, 255, 0.6)), interna(-5, 0, rgba(160, 40, 30, 0.3)),
    externa(4, 0, Color(hex: 0xC9432F)), externa(16, 26, rgba(255, 120, 104, 0.8), spread: -10),
])
let genteGelVioletaRedondo = Gel(fondo: Gels.fondoVioleta, sombras: [
    interna(3, 2, rgba(255, 255, 255, 0.45)), interna(-4, 0, rgba(40, 20, 140, 0.35)),
    externa(4, 0, C.violetaBase), externa(14, 22, rgba(116, 86, 241, 0.75), spread: -10),
])

/// Los «me encanta» de Citas: 1 por día sin Plus, 5 con Plus.
enum GenteEncantas {
    static func quedan(_ hoy: Int, _ limite: Int) -> Int { max(0, limite - hoy) }
    /// Lo que dice la API al pasarse (gente.Reaccionar), para no hacer volar la carta en vano.
    static func aviso(plus: Bool, limite: Int) -> String {
        plus ? "Ya usaste los \(limite) me encanta de hoy." : "Ya usaste tu me encanta de hoy. Con Plus tenés 5 por día."
    }
}

/// El toast después de reaccionar (prendo | interesa | encanta).
func genteMensaje(nombre: String, accion: String, modo: String, mutuo: Bool) -> String {
    if mutuo { return "¡Es mutuo con \(nombre)! Ya pueden hablar." }
    if accion == "encanta" { return "Le mandaste un «me encanta» a \(nombre). Si es mutuo, se abre el chat." }
    if modo == "citas" { return "Le avisamos a \(nombre) que te interesa. Si es mutuo, se abre el chat." }
    return "\(nombre) se suma a tu gente. Le van a llegar tus próximos planes."
}

/// Botón redondo gel del mazo (pasar, sumar, me encanta, invitar).
struct GenteBotonRedondo<Contenido: View>: View {
    var tam: CGFloat
    var gel: Gel
    var etiqueta: String
    var accion: () -> Void
    @ViewBuilder var contenido: () -> Contenido
    var body: some View {
        Button(action: accion) {
            contenido().frame(width: tam, height: tam).gel(tam / 2, gel)
        }
        .buttonStyle(EstiloToque())
        .accessibilityLabel(etiqueta)
    }
}

/// Elegir a cuál de mis planes invito a la persona (armado con las tarjetas del handoff).
struct GenteHojaInvitar: View {
    var personaId: String
    var nombre: String
    var cerrar: () -> Void
    var alInvitar: (Plan) -> Void
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var mios: [Plan]?

    var body: some View {
        ZStack(alignment: .bottom) {
            rgba(32, 29, 43, 0.45).ignoresSafeArea().onTapGesture(perform: cerrar)
            VStack(alignment: .leading, spacing: 14) {
                T("¿A qué plan invitás a \(nombre)?", 22, 700, C.carbon, alto: 1.1, espaciado: -0.4)
                if let ps = mios {
                    if ps.isEmpty {
                        T("Todavía no organizás nada. Armá un plan y después invitás.", 15, 400, C.texto2, alto: 1.5)
                        Cta(texto: "Armá un plan", alto: 56, tam: 16) { cerrar(); ir.crear() }
                    } else {
                        ScrollView {
                            VStack(spacing: 10) { ForEach(ps) { p in fila(p) } }.padding(.vertical, 4)
                        }
                        .frame(maxHeight: CGFloat(min(ps.count, 4) * 84))
                    }
                } else {
                    Puntitos().frame(maxWidth: .infinity).frame(height: 80)
                }
            }
            .padding(.horizontal, 20).padding(.top, 22).padding(.bottom, 34)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(UnevenRoundedRectangle(cornerRadii: .init(topLeading: 28, topTrailing: 28), style: .continuous)
                .fill(C.blancoCalido).ignoresSafeArea(edges: .bottom))
            .transition(.move(edge: .bottom))
        }
        .environment(\.paleta, .clara)
        .zIndex(30)
        .task { mios = (try? await c.api.mios("organizo").planes) ?? [] }
    }

    private func fila(_ p: Plan) -> some View {
        Button {
            Task {
                do {
                    try await c.api.invitar(plan: p.id, usuario: personaId)
                    cerrar()
                    alInvitar(p)
                } catch { c.avisos.error(error) }
            }
        } label: {
            HStack(spacing: 12) {
                Foto(url: p.foto, tinte: colorDeCategoria(p.categoria)).frame(width: 52, height: 52)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    T(p.titulo, 15, 700, C.carbon, unRenglon: true)
                    T(Fecha.cuando(p.empiezaEn) + " · " + Cupo.badge(p.faltan), 12, 500, C.texto2, unRenglon: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Icono(I.adelante, 18, C.violeta, trazo: 2.4)
            }
            .padding(12)
            .gel(20, Gels.neutra)
        }
        .buttonStyle(EstiloToque(baja: 2))
    }
}

/// Píldora de vidrio sobre foto (chips de la carta).
struct GentePildora<Contenido: View>: View {
    @ViewBuilder var contenido: () -> Contenido
    var body: some View {
        HStack(spacing: 7) { contenido() }
            .padding(.horizontal, 11).padding(.vertical, 6)
            .gel(12, .liso(rgba(255, 255, 255, 0.16)), [interna(1, 0, rgba(255, 255, 255, 0.2))])
    }
}
