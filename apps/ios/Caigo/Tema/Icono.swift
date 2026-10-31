import SwiftUI
import Nucleo

/// Forma de un ícono del handoff (path de SVG en un viewBox de 24×24), escalada a la caja.
struct FormaSVG: Shape {
    let d: String
    func path(in rect: CGRect) -> Path {
        let k = min(rect.width, rect.height) / 24
        return Path(TrazoSVG.cacheado(d)).applying(CGAffineTransform(scaleX: k, y: k).translatedBy(x: rect.minX / k, y: rect.minY / k))
    }
}

extension TrazoSVG {
    private static var cache: [String: CGPath] = [:]
    static func cacheado(_ d: String) -> CGPath {
        if let c = cache[d] { return c }
        let p = camino(d)
        cache[d] = p
        return p
    }
}

/// Ícono lineal del handoff. `trazo` es el stroke-width del SVG (en unidades del viewBox de 24);
/// con `relleno` se pinta lleno (corazón, estrella, puntos).
struct Icono: View {
    var d: String
    var tam: CGFloat = 24
    var color: Color?
    var trazo: CGFloat = 2
    var relleno: Color?
    @Environment(\.paleta) private var p

    init(_ d: String, _ tam: CGFloat = 24, _ color: Color? = nil, trazo: CGFloat = 2, relleno: Color? = nil) {
        self.d = d; self.tam = tam; self.color = color; self.trazo = trazo; self.relleno = relleno
    }

    var body: some View {
        let forma = FormaSVG(d: d)
        ZStack {
            if let relleno { forma.fill(relleno) }
            if trazo > 0 && color != .clear {
                forma.stroke(color ?? p.texto, style: StrokeStyle(lineWidth: trazo * tam / 24, lineCap: .round, lineJoin: .round))
            }
        }
        .frame(width: tam, height: tam)
        .accessibilityHidden(true)
    }
}
