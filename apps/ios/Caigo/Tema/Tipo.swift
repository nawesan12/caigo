import SwiftUI

/// Geist, la tipografía final del handoff (v5), embebida en la app (Recursos/Fuentes, OFL).
enum Geist {
    static func nombre(_ peso: Int) -> String {
        switch peso {
        case 800...: return "Geist-ExtraBold"
        case 700..<800: return "Geist-Bold"
        case 600..<700: return "Geist-SemiBold"
        case 500..<600: return "Geist-Medium"
        default: return "Geist-Regular"
        }
    }

    /// Tamaño fijo, como en el mockup (390 pt de ancho); escala con Dynamic Type relativo a .body.
    static func fuente(_ tam: CGFloat, _ peso: Int = 400) -> Font {
        .custom(nombre(peso), size: tam, relativeTo: .body)
    }
}

/// Sin renglones viudos (regla de Nahuel): el último espacio pasa a ser no separable, así la última
/// palabra nunca queda sola en el renglón de abajo. Sólo en textos de 3 palabras o más.
func sinViudas(_ s: String) -> String {
    guard s.count > 12, let r = s.range(of: " ", options: .backwards), s[r.upperBound...].count <= 14 else { return s }
    let palabras = s.split(separator: " ").count
    guard palabras >= 3, !s[r.upperBound...].contains("\n") else { return s }
    var t = s
    t.replaceSubrange(r, with: "\u{00A0}")
    return t
}

/// Texto con el estilo del handoff: `font: <peso> <tam>px/<alto>`, letter-spacing en pt.
/// Es el único texto que usan las pantallas.
struct T: View {
    var texto: String
    var tam: CGFloat
    var peso: Int = 400
    var color: Color?
    var alto: CGFloat?
    var espaciado: CGFloat = 0
    var alinear: TextAlignment = .leading
    var renglones: Int?
    @Environment(\.paleta) private var p

    init(_ texto: String, _ tam: CGFloat, _ peso: Int = 400, _ color: Color? = nil, alto: CGFloat? = nil,
         espaciado: CGFloat = 0, alinear: TextAlignment = .leading, renglones: Int? = nil, unRenglon: Bool = false) {
        self.texto = texto
        self.tam = tam
        self.peso = peso
        self.color = color
        self.alto = alto
        self.espaciado = espaciado
        self.alinear = alinear
        self.renglones = unRenglon ? 1 : renglones
    }

    var body: some View {
        // Geist trae ~1.2 de alto natural: el resto del line-height del CSS va como interlineado.
        let extra = max(0, tam * (alto ?? 1.35) - tam * 1.2)
        Text(renglones == 1 ? texto : sinViudas(texto))
            .font(Geist.fuente(tam, peso))
            .tracking(espaciado)
            .lineSpacing(extra)
            .foregroundStyle(color ?? p.texto)
            .multilineTextAlignment(alinear)
            .lineLimit(renglones)
            .truncationMode(.tail)
            .fixedSize(horizontal: false, vertical: renglones != 1)
    }
}

/// Títulos de pantalla: `700 30px/1`, -0.045em.
func espaciadoTitulo(_ tam: CGFloat) -> CGFloat { -0.045 * tam }
