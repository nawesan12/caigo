import SwiftUI
import Nucleo

/// Toca sin resaltado; mientras está apretado baja 3 pt (el `:active` de los botones gel).
struct EstiloToque: ButtonStyle {
    var baja: CGFloat = 3
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Rectangle())
            .offset(y: configuration.isPressed ? baja : 0)
            .animation(.easeOut(duration: 0.08), value: configuration.isPressed)
    }
}

/// Simple: tocable sin movimiento.
struct EstiloPlano: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.contentShape(Rectangle()).opacity(configuration.isPressed ? 0.85 : 1)
    }
}

extension View {
    /// Toque sin ripple ni movimiento (filas, tarjetas).
    func tocable(_ accion: @escaping () -> Void) -> some View {
        Button(action: accion) { self }.buttonStyle(EstiloPlano())
    }

    /// Toque gel: baja 3 pt mientras se aprieta.
    func toque(habilitado: Bool = true, _ accion: @escaping () -> Void) -> some View {
        Button(action: accion) { self }.buttonStyle(EstiloToque()).disabled(!habilitado)
    }
}

/// CTA gel (lima por defecto, 60 de alto, radio 20, `800 17px`): "Seguir", "Sumarme", "Publicar plan"…
struct Cta: View {
    var texto: String
    var gel: Gel = Gels.limaChico
    var alto: CGFloat = 60
    var radio: CGFloat = 20
    var tam: CGFloat = 17
    var peso = 800
    var color: Color = C.grafito
    var icono: String?
    var iconoTrazo: CGFloat = 3.4
    var iconoTam: CGFloat = 16
    var habilitado = true
    var cargando = false
    var accion: () -> Void

    var body: some View {
        Button(action: accion) {
            ZStack {
                if cargando {
                    Puntitos(color: color)
                } else {
                    HStack(spacing: 8) {
                        if let icono { Icono(icono, iconoTam, color, trazo: iconoTrazo) }
                        T(texto, tam, peso, color, unRenglon: true)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: alto)
            .gel(radio, gel)
        }
        .buttonStyle(EstiloToque())
        .disabled(!habilitado || cargando)
        .opacity(habilitado ? 1 : 0.5)
    }
}

/// CTA lima grande del prototipo (62, radio 22, base de 5).
func CtaGrande(_ texto: String, habilitado: Bool = true, cargando: Bool = false, icono: String? = nil, accion: @escaping () -> Void) -> Cta {
    Cta(texto: texto, gel: Gels.lima, alto: 62, radio: 22, tam: 18, color: C.carbon, icono: icono, iconoTrazo: 4, iconoTam: 18,
        habilitado: habilitado, cargando: cargando, accion: accion)
}

/// CTA violeta ("Siguiente").
func CtaVioleta(_ texto: String, habilitado: Bool = true, cargando: Bool = false, accion: @escaping () -> Void) -> Cta {
    Cta(texto: texto, gel: Gels.violeta, alto: 60, radio: 20, tam: 17, color: C.blanco, habilitado: habilitado, cargando: cargando, accion: accion)
}

/// Tres puntitos que laten: el "cargando" de los botones.
struct Puntitos: View {
    var color: Color = C.violeta
    var tam: CGFloat = 8
    @State private var fase = 0
    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<3, id: \.self) { i in
                Circle().fill(color).frame(width: tam, height: tam).opacity(i == fase ? 1 : 0.35)
            }
        }
        .animation(.easeInOut(duration: 0.2), value: fase)
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 220_000_000)
                fase = (fase + 1) % 3
            }
        }
    }
}

/// Botón ícono claro de 44×44 (volver, cerrar, compartir).
struct BotonIcono: View {
    var d: String
    var tam: CGFloat = 44
    var radio: CGFloat = 16
    var iconoTam: CGFloat = 22
    var trazo: CGFloat = 2.2
    var gel: Gel = Gels.icono
    var color: Color = C.carbon
    var relleno: Color?
    var accion: () -> Void

    var body: some View {
        Button(action: accion) {
            Icono(d, iconoTam, color, trazo: trazo, relleno: relleno)
                .frame(width: tam, height: tam)
                .gel(radio, gel)
        }
        .buttonStyle(EstiloToque())
    }
}

/// Chip de selección (SEL violeta / OFF lavanda), `600 14px`, padding 9×14, radio 14.
struct Chip: View {
    var texto: String
    var on: Bool
    var gelOn: Gel = Gels.sel
    var gelOff: Gel = Gels.off
    var colorOn: Color = C.blanco
    var colorOff: Color = C.carbon
    var tam: CGFloat = 14
    var padH: CGFloat = 14
    var padV: CGFloat = 9
    var radio: CGFloat = 14
    var accion: () -> Void

    var body: some View {
        Button(action: accion) {
            T(texto, tam, on ? 700 : 600, on ? colorOn : colorOff, unRenglon: true)
                .padding(.horizontal, padH).padding(.vertical, padV)
                .gel(radio, on ? gelOn : gelOff)
        }
        .buttonStyle(EstiloToque())
    }
}

/// Segmentado: pista lavanda con padding 4 y la opción activa blanca con sombra.
struct Segmentado: View {
    var opciones: [String]
    @Binding var seleccion: Int
    var radio: CGFloat = 16
    var radioOpcion: CGFloat = 12
    var alto: CGFloat = 44
    var tam: CGFloat = 14
    var llenar = true
    var padOpcion: CGFloat = 14
    var gelOn: (Int) -> Gel? = { _ in nil }
    var colorOn: (Int) -> Color? = { _ in nil }
    @Environment(\.paleta) private var p

    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(opciones.enumerated()), id: \.offset) { i, o in
                let on = i == seleccion
                T(o, tam, on ? 700 : 600, on ? (colorOn(i) ?? p.segOnTexto) : p.segOffTexto, unRenglon: true)
                    .padding(.horizontal, padOpcion)
                    .frame(maxWidth: llenar ? .infinity : nil)
                    .frame(height: alto - 8)
                    .background { if on { Color.clear.gel(radioOpcion, gelOn(i) ?? (p.oscuro ? Gels.segOnOscuro : Gels.segOn)) } }
                    .contentShape(Rectangle())
                    .onTapGesture { withAnimation(.easeOut(duration: 0.18)) { seleccion = i } }
            }
        }
        .padding(4)
        .gel(radio, .liso(p.segPista), p.oscuro ? [interna(2, 4, rgba(0, 0, 0, 0.3))] : [])
    }
}

/// Badge "Faltan N" (coral gel) o "Completo" (lima).
struct BadgeFalta: View {
    var faltan: Int
    var tam: CGFloat = 18
    var texto: String?
    var body: some View {
        T(texto ?? Cupo.badge(faltan), tam, 700, C.carbon, alto: 1, unRenglon: true)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .gel(13, Cupo.completo(faltan) ? Gels.limaBadge : Gels.coralBadge)
    }
}

/// Colores de los avatares con inicial (violeta, lima, coral, lavanda, grafito).
private let coloresAvatar: [(Color, Color)] = [
    (C.violeta, C.blanco), (C.lima, C.carbon), (C.coral, C.carbon), (C.lavanda, C.violetaTexto), (C.grafito, C.blanco),
]

func colorAvatar(_ semilla: String) -> (Color, Color) {
    // Hash estable (el de String cambia entre ejecuciones).
    var h: UInt32 = 0
    for u in semilla.unicodeScalars { h = h &* 31 &+ u.value }
    return coloresAvatar[Int(h % UInt32(coloresAvatar.count))]
}

/// Avatar redondo: foto si hay, si no la inicial sobre color con brillo arriba a la izquierda.
struct Avatar: View {
    var nombre: String
    var foto: String?
    var tam: CGFloat = 30
    var borde: CGFloat = 2
    var colorBorde: Color?
    var texto: String?
    var fondo: (Color, Color)?
    var tamTexto: CGFloat?
    @Environment(\.paleta) private var p

    var body: some View {
        let (bg, fg) = fondo ?? colorAvatar(nombre)
        ZStack {
            Circle().fill(bg)
            Circle().fill(RadialGradient(stops: Fondo.paradas((0, rgba(255, 255, 255, 0.6)), (0.42, rgba(255, 255, 255, 0))),
                                         center: UnitPoint(x: 0.3, y: 0.25), startRadius: 0, endRadius: tam * 0.9))
            if let foto, !foto.isEmpty {
                ImagenRed(url: foto).clipShape(Circle())
            } else {
                T(texto ?? String(nombre.prefix(1)).uppercased(), tamTexto ?? tam * 0.4, 800, fg, unRenglon: true)
            }
            if borde > 0 { Circle().strokeBorder(colorBorde ?? p.bordeAvatar, lineWidth: borde) }
        }
        .frame(width: tam, height: tam)
    }
}

/// Avatares apilados (margen -8) con "+N" lavanda al final.
struct Avatares: View {
    var personas: [(String, String?)]
    var total: Int?
    var max = 3
    var tam: CGFloat = 30
    var solape: CGFloat = 8

    var body: some View {
        let visibles = Array(personas.prefix(max))
        let resto = (total ?? personas.count) - visibles.count
        HStack(spacing: -solape) {
            ForEach(Array(visibles.enumerated()), id: \.offset) { i, p in
                Avatar(nombre: p.0, foto: p.1, tam: tam).zIndex(Double(max - i))
            }
            if resto > 0 {
                Avatar(nombre: "+", foto: nil, tam: tam, texto: "+\(resto)", fondo: (C.pista, C.violetaTexto))
            }
        }
    }
}

/// Foto de red con un tinte de fondo mientras carga.
struct Foto: View {
    var url: String?
    var tinte: Color = C.oscuro2
    var body: some View {
        ZStack {
            tinte
            if let url, !url.isEmpty { ImagenRed(url: url) }
        }
        .clipped()
    }
}

/// Interruptor del handoff: 50×30, violeta prendido, perilla blanca de 24.
struct Interruptor: View {
    @Binding var on: Bool
    var body: some View {
        ZStack(alignment: .leading) {
            Color.clear.gel(15, .liso(on ? C.violeta : Color(hex: 0xDDD8EA)), [interna(2, 4, rgba(0, 0, 0, 0.15))])
            Color.clear.frame(width: 24, height: 24)
                .gel(12, .liso(C.blanco), [externa(2, 4, rgba(0, 0, 0, 0.25))])
                .offset(x: on ? 23 : 3)
        }
        .frame(width: 50, height: 30)
        .contentShape(Rectangle())
        .onTapGesture { withAnimation(.easeOut(duration: 0.2)) { on.toggle() } }
        .accessibilityAddTraits(.isButton)
        .accessibilityValue(on ? "Sí" : "No")
    }
}

/// Barra de progreso de pasos (Crear plan, DNI): segmentos de 6, violeta / `#E4DEFA`.
struct BarraPasos: View {
    var paso: Int
    var total: Int
    var actual: Color = C.violeta
    var siguiente: Color?
    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<total, id: \.self) { i in
                Capsule().fill(i < paso ? actual : (i == paso && siguiente != nil ? siguiente! : C.pista)).frame(height: 6)
            }
        }
    }
}

/// Barra segmentada del cupo: ocupados lima, vacíos con borde coral punteado.
struct BarraCupo: View {
    var somos: Int
    var total: Int
    var alto: CGFloat = 10
    var body: some View {
        HStack(spacing: total > 14 ? 3 : 5) {
            ForEach(Array(Cupo.segmentos(somos: somos, total: total).enumerated()), id: \.offset) { _, lleno in
                if lleno {
                    Capsule().fill(LinearGradient(colors: [C.cupoLleno1, C.cupoLleno2], startPoint: .top, endPoint: .bottom)).frame(height: alto)
                } else {
                    Capsule().strokeBorder(C.coral, style: StrokeStyle(lineWidth: 1.5, dash: [3, 2])).frame(height: alto)
                }
            }
        }
    }
}

/// Encabezado "volver + título + subtítulo" (Solicitudes, Chat, Tu entrada).
struct Encabezado<Derecha: View>: View {
    var titulo: String
    var sub: String?
    var icono: String = I.atras
    var volver: () -> Void
    @ViewBuilder var derecha: () -> Derecha
    @Environment(\.paleta) private var p

    var body: some View {
        HStack(spacing: 12) {
            BotonIcono(d: icono, accion: volver)
            VStack(alignment: .leading, spacing: 0) {
                T(titulo, 22, 700, espaciado: -0.4, unRenglon: true)
                if let sub { T(sub, 13, 500, p.texto2, unRenglon: true) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            derecha()
        }
    }
}

extension Encabezado where Derecha == EmptyView {
    init(titulo: String, sub: String? = nil, icono: String = I.atras, volver: @escaping () -> Void) {
        self.init(titulo: titulo, sub: sub, icono: icono, volver: volver, derecha: { EmptyView() })
    }
}

/// Texto centrado de pantalla vacía / error, armado con las piezas de los vacíos 13a–13c.
struct MensajeCentro: View {
    var titulo: String
    var cuerpo: String
    @Environment(\.paleta) private var p
    var body: some View {
        VStack(spacing: 12) {
            T(titulo, 26, 700, alto: 1.1, espaciado: -0.6, alinear: .center)
            T(cuerpo, 15, 400, p.texto2, alto: 1.5, alinear: .center)
        }
    }
}

struct Separador: View {
    @Environment(\.paleta) private var p
    var body: some View { Rectangle().fill(p.separador).frame(height: 1) }
}

/// Pantalla completa con el fondo del handoff. Las medidas del mockup cuentan desde abajo de la barra
/// de estado (los 50 pt del "9:41"): `top:56px` → 6 pt desde el área segura.
struct Pantalla<Contenido: View>: View {
    var brillo: Fondo?
    var fondo: AnyView?
    @ViewBuilder var contenido: () -> Contenido
    @Environment(\.paleta) private var p

    var body: some View {
        ZStack {
            if let fondo { fondo.ignoresSafeArea() } else { FondoCaigo(oscuro: p.oscuro, brillo: brillo) }
            contenido()
        }
        .ocultarBarraNavegacion()
    }
}

extension View {
    /// Las pantallas dibujan su propio encabezado: se esconde la barra de navegación del sistema.
    func ocultarBarraNavegacion() -> some View {
        #if os(iOS)
        return self.toolbar(.hidden, for: .navigationBar)
        #else
        return self
        #endif
    }
}
