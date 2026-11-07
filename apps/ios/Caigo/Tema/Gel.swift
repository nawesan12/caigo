import SwiftUI

/// Una sombra de CSS (`box-shadow`): desplazamiento, desenfoque, extensión, color y si es interna.
/// Los valores van en px del handoff (= pt).
struct Sombra {
    var x: CGFloat = 0
    var y: CGFloat
    var blur: CGFloat = 0
    var spread: CGFloat = 0
    var color: Color
    var inset = false
}

func interna(_ y: CGFloat, _ blur: CGFloat, _ color: Color, spread: CGFloat = 0, x: CGFloat = 0) -> Sombra {
    Sombra(x: x, y: y, blur: blur, spread: spread, color: color, inset: true)
}

func externa(_ y: CGFloat, _ blur: CGFloat, _ color: Color, spread: CGFloat = 0, x: CGFloat = 0) -> Sombra {
    Sombra(x: x, y: y, blur: blur, spread: spread, color: color, inset: false)
}

/// Un fondo del CSS: color liso, `linear-gradient(180deg, …)` o `radial-gradient(rx% ry% at cx% cy%, …)`.
enum Fondo {
    case liso(Color)
    case vertical([Gradient.Stop])
    case radial(cx: CGFloat, cy: CGFloat, rx: CGFloat, ry: CGFloat, [Gradient.Stop])

    static func vertical(_ a: Color, _ b: Color) -> Fondo { .vertical([.init(color: a, location: 0), .init(color: b, location: 1)]) }
    static func paradas(_ p: (CGFloat, Color)...) -> [Gradient.Stop] { p.map { Gradient.Stop(color: $0.1, location: $0.0) } }
}

/// Fondo + sombras de un elemento "gel / 3D soft".
struct Gel {
    var fondo: Fondo?
    var sombras: [Sombra] = []
    var grano = false
}

/// Pinta un `Fondo` llenando la caja (el radial elíptico se arma escalando uno circular).
struct VistaFondo: View {
    var fondo: Fondo
    var body: some View {
        switch fondo {
        case let .liso(c): Rectangle().fill(c)
        case let .vertical(p): Rectangle().fill(LinearGradient(stops: p, startPoint: .top, endPoint: .bottom))
        case let .radial(cx, cy, rx, ry, p):
            GeometryReader { g in
                let w = max(g.size.width, 1), h = max(g.size.height, 1)
                let radioX = max(rx * w, 0.5), radioY = max(ry * h, 0.5)
                let k = radioY / radioX
                Rectangle()
                    .fill(RadialGradient(stops: p, center: UnitPoint(x: cx, y: cy * 1), startRadius: 0, endRadius: radioX))
                    .frame(width: w, height: h / k)
                    .scaleEffect(x: 1, y: k, anchor: UnitPoint(x: cx, y: cy))
                    .frame(width: w, height: h, alignment: .top)
                    .offset(y: 0)
            }
        }
    }
}

/// La textura de ruido del handoff (`assets/grain.png`), en mosaico de 160×160.
struct Grano: View {
    var body: some View {
        Image("Grano").resizable(resizingMode: .tile).allowsHitTesting(false)
    }
}

/// Sombra interna: el color por fuera de la forma desplazada, desenfocado y recortado a la forma.
private struct SombraInterna: View {
    var s: Sombra
    var radio: CGFloat
    var body: some View {
        let forma = RoundedRectangle(cornerRadius: radio, style: .continuous)
        if s.blur == 0 && s.x == 0 && s.y == 0 && s.spread < 0 {
            // `inset 0 0 0 Npx color`: un borde por dentro.
            forma.strokeBorder(s.color, lineWidth: -s.spread)
        } else {
            let hueco = RoundedRectangle(cornerRadius: max(0, radio - s.spread), style: .continuous)
            Rectangle().fill(s.color)
                .padding(-(s.blur * 2 + 20))
                .mask {
                    Rectangle().padding(-(s.blur * 2 + 20))
                        .overlay { hueco.padding(s.spread).offset(x: s.x, y: s.y).blendMode(.destinationOut) }
                        .compositingGroup()
                }
                .blur(radius: s.blur / 2)
                .clipShape(forma)
        }
    }
}

/// Dibuja el elemento como en el CSS: sombras externas, fondo, grano y sombras internas. El
/// contenido va encima.
struct EstiloGel: ViewModifier {
    var radio: CGFloat
    var gel: Gel

    func body(content: Content) -> some View {
        let forma = RoundedRectangle(cornerRadius: radio, style: .continuous)
        content.background {
            ZStack {
                ForEach(Array(gel.sombras.enumerated().reversed()), id: \.offset) { _, s in
                    if !s.inset {
                        RoundedRectangle(cornerRadius: max(0, radio + s.spread), style: .continuous)
                            .fill(s.color)
                            .padding(-s.spread)
                            .offset(x: s.x, y: s.y)
                            .blur(radius: s.blur / 2)
                    }
                }
                if let f = gel.fondo { VistaFondo(fondo: f).clipShape(forma) }
                if gel.grano { Grano().clipShape(forma) }
                ForEach(Array(gel.sombras.enumerated().reversed()), id: \.offset) { _, s in
                    if s.inset { SombraInterna(s: s, radio: radio) }
                }
            }
            .allowsHitTesting(false)
        }
    }
}

extension View {
    func gel(_ radio: CGFloat, _ gel: Gel) -> some View { modifier(EstiloGel(radio: radio, gel: gel)) }
    func gel(_ radio: CGFloat, _ fondo: Fondo?, _ sombras: [Sombra] = [], grano: Bool = false) -> some View {
        modifier(EstiloGel(radio: radio, gel: Gel(fondo: fondo, sombras: sombras, grano: grano)))
    }
}

/// Los fondos y sombras que se repiten en todo el handoff (los mismos que tema/Gel.kt de Android).
enum Gels {
    static let fondoLima = Fondo.vertical(Fondo.paradas((0, C.lima0), (0.32, C.lima1), (0.62, C.lima), (1, C.lima3)))
    static let fondoVioleta = Fondo.vertical(Fondo.paradas((0, C.violeta1), (0.30, C.violeta2), (0.62, C.violeta), (1, C.violeta4)))

    /// CTA lima grande (5 de base).
    static let lima = Gel(fondo: fondoLima, sombras: [
        interna(3, 2, rgba(255, 255, 255, 0.85)), interna(-5, 0, rgba(96, 140, 10, 0.32)),
        externa(5, 0, C.limaBase), externa(18, 28, rgba(120, 170, 30, 0.7), spread: -10),
    ])

    /// Lima chico (botón "Sumarme" de la tarjeta).
    static let limaChico = Gel(fondo: fondoLima, sombras: [
        interna(3, 2, rgba(255, 255, 255, 0.85)), interna(-5, 0, rgba(96, 140, 10, 0.32)),
        externa(4, 0, C.limaBase), externa(16, 26, rgba(120, 170, 30, 0.7), spread: -10),
    ])

    static let violeta = Gel(fondo: fondoVioleta, sombras: [
        interna(3, 2, rgba(255, 255, 255, 0.45)), interna(-5, 0, rgba(40, 20, 140, 0.35)),
        externa(5, 0, C.violetaBase), externa(18, 28, rgba(116, 86, 241, 0.75), spread: -10),
    ])

    static let coralBadge = Gel(fondo: .vertical(Fondo.paradas((0, C.coral0), (0.55, C.coral), (1, C.coral2))), sombras: [
        interna(2, 1, rgba(255, 255, 255, 0.6)), interna(-3, 0, rgba(160, 40, 30, 0.25)), externa(3, 0, C.coralBase),
    ])

    /// Badge "Completo" (lima).
    static let limaBadge = Gel(fondo: .vertical(Fondo.paradas((0, C.cupoLleno1), (0.5, C.lima), (1, C.cupoLleno2))), sombras: [
        interna(2, 1, rgba(255, 255, 255, 0.6)), interna(-3, 0, rgba(96, 140, 10, 0.25)), externa(3, 0, C.limaBase),
    ])

    /// Chip seleccionado (SEL).
    static let sel = Gel(fondo: fondoVioleta, sombras: [
        interna(2, 1, rgba(255, 255, 255, 0.45)), interna(-3, 0, rgba(40, 20, 140, 0.35)), externa(3, 0, C.violetaBase),
    ])

    /// Chip apagado (OFF).
    static let off = Gel(fondo: .vertical(C.tinte2, C.tinte1), sombras: [interna(1.5, 0, C.blanco), externa(2, 0, C.borde3)])

    /// Chip oscuro seleccionado (filtro de categoría).
    static let oscuroSel = Gel(
        fondo: .radial(cx: 0, cy: 0, rx: 1.2, ry: 0.9, Fondo.paradas((0, C.oscuro1), (0.62, C.carbon))),
        sombras: [interna(1, 0, rgba(255, 255, 255, 0.08)), externa(12, 26, rgba(36, 33, 45, 0.55), spread: -10)],
        grano: true)

    /// Chip oscuro del prototipo (DARKSEL).
    static let darkSel = Gel(fondo: .vertical(C.oscuro1, C.carbon), sombras: [interna(1, 0, rgba(255, 255, 255, 0.15)), externa(3, 0, C.oscuro7)])

    /// Chip de categoría apagado del mockup.
    static let chipClaro = Gel(fondo: .vertical(C.tinte2, C.tinte1), sombras: [interna(1.5, 0, C.blanco), interna(-2, 0, rgba(116, 86, 241, 0.10))])
    static let chipOscuro = Gel(fondo: .vertical(Color(hex: 0x36314A), C.oscuro5), sombras: [interna(1, 0, rgba(255, 255, 255, 0.08))])

    /// Botón ícono claro (44×44).
    static let icono = chipClaro

    static let segOn = Gel(fondo: .liso(C.blanco), sombras: [interna(1, 0, C.blanco), externa(3, 8, rgba(80, 60, 160, 0.3), spread: -2)])
    static let segOnOscuro = Gel(fondo: .vertical(Color(hex: 0x56506C), Color(hex: 0x454058)), sombras: [interna(1, 0, rgba(255, 255, 255, 0.15))])

    /// Tarjeta de plan.
    static let tarjeta = Gel(fondo: .liso(C.blanco), sombras: [
        interna(1, 0, C.blanco), externa(2, 0, C.borde2), externa(16, 30, rgba(80, 60, 160, 0.22), spread: -10),
    ])
    static let tarjetaOscura = Gel(fondo: .vertical(C.oscuro3, C.oscuro4), sombras: [
        interna(1, 0, rgba(255, 255, 255, 0.08)), externa(18, 30, rgba(0, 0, 0, 0.6), spread: -14),
    ])

    /// Tarjeta neutra del prototipo / panel.
    static let neutra = Gel(fondo: .liso(C.blanco), sombras: [interna(1, 0, C.blanco), externa(3, 0, C.borde1)])
    static let neutraOscura = Gel(fondo: .vertical(C.oscuro3, C.oscuro4), sombras: [interna(1, 0, rgba(255, 255, 255, 0.08)), externa(3, 0, C.oscuro7)])

    /// Opción seleccionada (radio-card).
    static let opcionSel = Gel(fondo: .liso(C.lavanda), sombras: [interna(0, 0, C.violeta, spread: -2), externa(3, 0, C.borde5)])

    /// Tarjeta oscura de contador ("Faltan 2 / Somos 8 de 10").
    static let contador = Gel(
        fondo: .radial(cx: 0, cy: 0, rx: 1.2, ry: 1.2, Fondo.paradas((0, C.oscuro2), (0.7, C.carbon))),
        sombras: [interna(1, 0, rgba(255, 255, 255, 0.1)), externa(18, 30, rgba(36, 33, 45, 0.45), spread: -12)],
        grano: true)

    /// Tab bar flotante.
    static let tabBar = Gel(fondo: .vertical(Color.white, Color(hex: 0xF4F0FF)), sombras: [
        interna(1.5, 0, C.blanco), interna(-2, 0, rgba(116, 86, 241, 0.08)),
        externa(22, 40, rgba(60, 40, 140, 0.45), spread: -14), externa(0, 0, rgba(116, 86, 241, 0.07), spread: 1),
    ])
    static let tabBarOscura = Gel(fondo: .vertical(rgba(58, 52, 80, 1), rgba(38, 35, 52, 1)), sombras: [
        interna(1, 0, rgba(255, 255, 255, 0.12)), externa(18, 36, rgba(0, 0, 0, 0.7), spread: -10),
    ])
    static let tabPill = Gel(fondo: .vertical(C.tinte5, Color(hex: 0xE6DEFF)), sombras: [
        interna(1.5, 0, C.blanco), interna(-2, 0, rgba(116, 86, 241, 0.14)), externa(4, 10, rgba(116, 86, 241, 0.35), spread: -4),
    ])
    static let tabPillOscura = Gel(fondo: .vertical(Color(hex: 0x4A4362), Color(hex: 0x332E45)), sombras: [
        interna(1, 0, rgba(255, 255, 255, 0.14)), externa(4, 10, rgba(0, 0, 0, 0.6), spread: -4),
    ])

    /// Botón blanco con borde ("Ahora no", "Mostrar QR de entrada").
    static let blanco = Gel(fondo: .liso(C.blanco), sombras: [interna(0, 0, C.borde4, spread: -1.5), externa(4, 0, C.borde4)])

    /// Botón ícono sobre foto: vidrio claro translúcido.
    static let vidrio = Gel(fondo: .vertical(rgba(255, 255, 255, 0.92), rgba(240, 236, 255, 0.92)), sombras: [
        interna(1.5, 0, C.blanco), interna(-2, 0, rgba(116, 86, 241, 0.1)),
    ])

    /// Botón volver / cerrar del prototipo: blanco a lavanda con base `#DCD5EE`.
    static let volver = Gel(fondo: .vertical(C.blanco, Color(hex: 0xF3F0FA)), sombras: [interna(2, 0, C.blanco), externa(3, 0, C.borde3)])

    static func tarjeta(_ p: Paleta) -> Gel { p.oscuro ? tarjetaOscura : tarjeta }
    static func neutra(_ p: Paleta) -> Gel { p.oscuro ? neutraOscura : neutra }
    static func chip(_ p: Paleta) -> Gel { p.oscuro ? chipOscuro : chipClaro }
}

/// Fondo de pantalla: color + brillo lavanda arriba a la derecha (violeta en oscuro) + grano.
struct FondoCaigo: View {
    var oscuro: Bool
    var brillo: Fondo?
    var body: some View {
        ZStack {
            (oscuro ? C.grafito : C.blancoCalido)
            VistaFondo(fondo: brillo ?? (oscuro
                ? .radial(cx: 1, cy: 0, rx: 1, ry: 0.4, Fondo.paradas((0, Color(hex: 0x352B6E)), (0.65, C.grafito.opacity(0))))
                : .radial(cx: 1, cy: 0, rx: 0.9, ry: 0.38, Fondo.paradas((0, C.tinte3), (0.7, C.tinte3.opacity(0))))))
            Grano()
        }
        .ignoresSafeArea()
    }
}
