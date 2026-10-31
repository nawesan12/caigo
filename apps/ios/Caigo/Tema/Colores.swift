import SwiftUI

extension Color {
    /// `#RRGGBB` (y alfa aparte), como los tokens del handoff.
    init(hex: UInt32, alfa: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255, opacity: alfa)
    }
}

/// rgba(r,g,b,a) del CSS.
func rgba(_ r: Int, _ g: Int, _ b: Int, _ a: Double) -> Color {
    Color(.sRGB, red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255, opacity: a)
}

/// Los colores del handoff (README · Design Tokens), con los mismos nombres que Android (tema/Colores.kt).
enum C {
    // Marca
    static let coral = Color(hex: 0xFF7868)
    static let violeta = Color(hex: 0x7456F1)
    static let lima = Color(hex: 0xC8F36B)
    static let blancoCalido = Color(hex: 0xFAF9F6)
    static let grafito = Color(hex: 0x201D2B)
    static let carbon = Color(hex: 0x24212D)
    static let lavanda = Color(hex: 0xF0ECFF)
    static let blanco = Color.white
    static let negro = Color.black

    // Texto
    static let texto2 = Color(hex: 0x5C586B)
    static let texto3 = Color(hex: 0x6B6780)
    static let inactivo = Color(hex: 0x8A8699)
    static let apagado = Color(hex: 0xA8A3BD)
    static let apagado2 = Color(hex: 0xB9B4CC)

    // Lavandas de borde y base
    static let borde1 = Color(hex: 0xE6E0F3)
    static let borde2 = Color(hex: 0xE8E3F4)
    static let borde3 = Color(hex: 0xDCD5EE)
    static let borde4 = Color(hex: 0xE2DCF0)
    static let borde5 = Color(hex: 0xC9BEF5)
    static let borde6 = Color(hex: 0xCFC8E6)
    static let pista = Color(hex: 0xE4DEFA)

    // Violetas
    static let violeta1 = Color(hex: 0xB3A3FF)
    static let violeta2 = Color(hex: 0x8A70FF)
    static let violeta4 = Color(hex: 0x5F41E0)
    static let violetaTexto = Color(hex: 0x5B43C9)
    static let violetaBase = Color(hex: 0x4A2FC2)
    static let tinte1 = Color(hex: 0xEDE8FF)
    static let tinte2 = Color(hex: 0xF8F6FF)
    static let tinte3 = Color(hex: 0xECE5FF)
    static let tinte4 = Color(hex: 0xE3DAFF)
    static let tinte5 = Color(hex: 0xF6F3FF)

    // Limas
    static let lima0 = Color(hex: 0xF0FFC2)
    static let lima1 = Color(hex: 0xD6FA80)
    static let lima3 = Color(hex: 0xABDB4B)
    static let limaBase = Color(hex: 0x94C43A)
    static let limaTexto = Color(hex: 0x3F6A00)
    static let cupoLleno1 = Color(hex: 0xE2FF9C)
    static let cupoLleno2 = Color(hex: 0xB2E04F)

    // Corales
    static let coral0 = Color(hex: 0xFFB2A6)
    static let coral2 = Color(hex: 0xF05E4C)
    static let coralBase = Color(hex: 0xD04A3A)

    // Oscuros
    static let oscuro1 = Color(hex: 0x3D3756)
    static let oscuro2 = Color(hex: 0x3A3452)
    static let oscuro3 = Color(hex: 0x312C42)
    static let oscuro4 = Color(hex: 0x28243A)
    static let oscuro5 = Color(hex: 0x2B2738)
    static let oscuro6 = Color(hex: 0x15131C)
    static let oscuro7 = Color(hex: 0x0E0D14)
    static let oscuro8 = Color(hex: 0x0B0A10)

    static let lienzo = Color(hex: 0xE9E6DF)
}

/// Lo que cambia entre el modo claro y el oscuro (14a–14b). El resto de los colores no cambia.
struct Paleta {
    var oscuro: Bool
    var fondo: Color
    var fondoBrillo: Color
    var texto: Color
    var texto2: Color
    var texto3: Color
    var tarjeta: [Color]
    var tarjetaPlano: Color
    var bordeAvatar: Color
    var chipApagado: [Color]
    var segPista: Color
    var segOnTexto: Color
    var segOffTexto: Color
    var acento: Color
    var tabActivo: Color
    var tabInactivo: Color
    var superficie: Color
    var separador: Color

    static let clara = Paleta(
        oscuro: false, fondo: C.blancoCalido, fondoBrillo: C.tinte3, texto: C.carbon, texto2: C.texto2, texto3: C.texto3,
        tarjeta: [C.blanco, C.blanco], tarjetaPlano: C.blanco, bordeAvatar: C.blanco, chipApagado: [C.tinte2, C.tinte1],
        segPista: C.lavanda, segOnTexto: C.carbon, segOffTexto: C.texto2, acento: C.violeta,
        tabActivo: C.violeta, tabInactivo: C.inactivo, superficie: C.lavanda, separador: Color(hex: 0xF0ECF8))

    static let oscura = Paleta(
        oscuro: true, fondo: C.grafito, fondoBrillo: Color(hex: 0x352B6E), texto: C.blancoCalido, texto2: C.apagado, texto3: C.apagado2,
        tarjeta: [C.oscuro3, C.oscuro4], tarjetaPlano: C.oscuro3, bordeAvatar: Color(hex: 0x2E2A3F),
        chipApagado: [Color(hex: 0x36314A), C.oscuro5], segPista: Color(hex: 0x2E2A3D), segOnTexto: C.blancoCalido,
        segOffTexto: C.apagado, acento: C.lima, tabActivo: C.lima, tabInactivo: Color(hex: 0x9A95B0),
        superficie: Color(hex: 0x2E2A3D), separador: Color.white.opacity(0.08))
}

private struct ClavePaleta: EnvironmentKey {
    static let defaultValue = Paleta.clara
}

extension EnvironmentValues {
    var paleta: Paleta {
        get { self[ClavePaleta.self] }
        set { self[ClavePaleta.self] = newValue }
    }
}

/// Pone la paleta según el modo del sistema (claro u oscuro).
struct ConPaleta: ViewModifier {
    @Environment(\.colorScheme) private var esquema
    func body(content: Content) -> some View {
        content.environment(\.paleta, esquema == .dark ? .oscura : .clara)
    }
}
