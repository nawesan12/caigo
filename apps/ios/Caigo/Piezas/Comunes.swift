import SwiftUI
import Nucleo

/// "96% asistencia" o "Nuevo en Caigo".
func asistencia(_ fue: Int, _ de: Int) -> String { de > 0 ? "\((fue * 100 + de / 2) / de)% asistencia" : "Nuevo en Caigo" }
func asistencia(_ a: Asist) -> String { asistencia(a.fue, a.de) }

/// Sello de verificada: estrella lima con tilde carbón.
struct Verificado: View {
    var tam: CGFloat = 24
    var color: Color = C.lima
    var tilde: Color = C.carbon
    var body: some View {
        ZStack {
            Icono(I.verificado, tam, .clear, trazo: 0, relleno: color)
            Icono(I.checkChico, tam * 0.75, tilde, trazo: 2.6)
        }
        .accessibilityLabel("Verificada")
    }
}

/// El color de cada categoría (chips sobre foto, pines del mapa).
func colorDeCategoria(_ cat: String) -> Color {
    switch cat {
    case "deporte": return Color(hex: 0x3B6B57)
    case "juego": return C.violetaTexto
    case "previa", "fiesta", "musica": return C.oscuro2
    case "salida", "cita_doble": return Color(hex: 0x6B4A5E)
    default: return C.oscuro1
    }
}

/// Fondo de las píldoras de vidrio sobre fotos.
let vidrioSobreFoto = rgba(255, 255, 255, 0.16)

/// Una fila de una hoja de opciones.
struct Opcion: Identifiable {
    let id = UUID()
    var texto: String
    var accion: () -> Void
}

/// Hoja de opciones (armada con la tarjeta de filas de "Herramientas para salir tranqui"): se apoya
/// abajo sobre un velo; tocar afuera cierra. Las opciones que reportan, cancelan o bloquean van en coral.
struct HojaOpciones: View {
    var cerrar: () -> Void
    var filas: [Opcion]
    var body: some View {
        ZStack(alignment: .bottom) {
            rgba(32, 29, 43, 0.45).ignoresSafeArea().onTapGesture(perform: cerrar)
            VStack(spacing: 0) {
                ForEach(Array(filas.enumerated()), id: \.element.id) { i, f in
                    if i > 0 { Rectangle().fill(C.lavanda).frame(height: 1) }
                    Button {
                        cerrar()
                        f.accion()
                    } label: {
                        T(f.texto, 15, 700, ["Reportar", "Cancelar", "Bloquear", "Borrar"].contains { f.texto.hasPrefix($0) } ? C.coral2 : C.carbon)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 18).padding(.vertical, 16)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(EstiloPlano())
                }
            }
            .gel(24, Gels.neutra)
            .padding(.horizontal, 12).padding(.bottom, 12)
            .transition(.move(edge: .bottom))
        }
        .zIndex(30)
    }
}

/// Motivos de reporte (personas.go · motivos).
let motivosReporte: [(String, String)] = [
    ("acoso", "Acoso o mal trato"), ("falso", "Perfil o plan falso"), ("no_vino", "No vino"),
    ("inapropiado", "Contenido inapropiado"), ("inseguro", "Me sentí insegura/o"), ("otro", "Otro motivo"),
]

struct HojaReporte: View {
    var cerrar: () -> Void
    var alElegir: (String) -> Void
    var body: some View {
        HojaOpciones(cerrar: cerrar, filas: motivosReporte.map { m in Opcion(texto: m.1) { alElegir(m.0) } })
    }
}

/// tk-pop: escala .4 → 1.08 → 1 con rotación −20° → 6° → 0° (confirmaciones).
struct Pop: ViewModifier {
    @State private var fase = 0
    func body(content: Content) -> some View {
        content
            .scaleEffect(fase == 0 ? 0.4 : (fase == 1 ? 1.08 : 1))
            .rotationEffect(.degrees(fase == 0 ? -20 : (fase == 1 ? 6 : 0)))
            .task {
                withAnimation(.easeOut(duration: 0.27)) { fase = 1 }
                try? await Task.sleep(nanoseconds: 270_000_000)
                withAnimation(.easeInOut(duration: 0.18)) { fase = 2 }
            }
    }
}

/// tk-up: entra subiendo 20 y apareciendo.
struct Sube: ViewModifier {
    var retraso: Double = 0
    @State private var listo = false
    func body(content: Content) -> some View {
        content
            .opacity(listo ? 1 : 0)
            .offset(y: listo ? 0 : 20)
            .onAppear { withAnimation(.easeOut(duration: 0.25).delay(retraso)) { listo = true } }
    }
}

extension View {
    func pop() -> some View { modifier(Pop()) }
    func sube(_ retraso: Double = 0) -> some View { modifier(Sube(retraso: retraso)) }
}
