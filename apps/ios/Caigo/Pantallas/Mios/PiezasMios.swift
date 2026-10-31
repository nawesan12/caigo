import SwiftUI
import Nucleo

/*
 * Piezas que comparten Mis planes, Perfil, Solicitudes, Chat y Check-in (PiezasMios.kt de Android).
 * Llevan el prefijo `mios` para no chocar con las de otras carpetas.
 */

/// Tarjeta blanca chica (`0 2px 0 #E8E3F4, 0 12px 24px -12px`), la de las filas de Mis planes y Solicitudes.
let miosTarjeta = Gel(fondo: .liso(C.blanco), sombras: [
    interna(1, 0, C.blanco), externa(2, 0, C.borde2), externa(12, 24, rgba(80, 60, 160, 0.22), spread: -12),
])

let miosTarjetaOscura = Gel(fondo: .vertical(C.oscuro3, C.oscuro4), sombras: [
    interna(1, 0, rgba(255, 255, 255, 0.08)), externa(12, 24, rgba(0, 0, 0, 0.5), spread: -12),
])

func miosGelTarjeta(_ p: Paleta) -> Gel { p.oscuro ? miosTarjetaOscura : miosTarjeta }

/// Tarjeta oscura con grano (contador, asistencia real).
let miosOscura = Gel(
    fondo: .radial(cx: 0, cy: 0, rx: 1.2, ry: 0.9, Fondo.paradas((0, C.oscuro1), (0.62, C.carbon))),
    sombras: [interna(1, 0, rgba(255, 255, 255, 0.08)), externa(12, 26, rgba(36, 33, 45, 0.55), spread: -10)],
    grano: true)

/// Tile violeta gel chico (ícono de "Verificación y seguridad").
let miosTileVioleta = Gel(fondo: Gels.fondoVioleta, sombras: [
    interna(3, 2, rgba(255, 255, 255, 0.45)), interna(-4, 0, rgba(40, 20, 140, 0.35)), externa(3, 0, C.violetaBase),
])

/// Botón violeta gel con base de 4 (enviar del chat).
let miosVioletaChico = Gel(fondo: Gels.fondoVioleta, sombras: [
    interna(3, 2, rgba(255, 255, 255, 0.45)), interna(-5, 0, rgba(40, 20, 140, 0.35)),
    externa(4, 0, C.violetaBase), externa(16, 26, rgba(116, 86, 241, 0.75), spread: -10),
])

/// "94% asistencia · 11 planes", o "Nuevo en Caigo · todavía sin planes".
func miosAsistencia(_ a: Asist, extra: String? = nil) -> String {
    if a.de <= 0 { return "Nuevo en Caigo · todavía sin planes" }
    let pct = a.fue * 100 / a.de
    if let extra { return "\(pct)% asistencia · \(extra)" }
    return "\(pct)% asistencia · \(a.de) \(a.de == 1 ? "plan" : "planes")"
}

/// Badge de verificado: el sello violeta con el check blanco del mockup 11.
struct MiosVerificado: View {
    var tam: CGFloat = 22
    var body: some View {
        ZStack {
            Icono(I.verificado, tam, .clear, trazo: 0, relleno: C.violeta)
            Icono(I.checkChico, tam, C.blanco, trazo: 2.6)
        }
        .frame(width: tam, height: tam)
        .accessibilityLabel("Verificada")
    }
}

/// Título de sección (`700 15px`).
struct MiosSeccion: View {
    var texto: String
    var body: some View { T(texto, 15, 700) }
}

/// Overline ("HOY", "ESTA SEMANA"): 13, 800, letter-spacing 1.
struct MiosOverline: View {
    var texto: String
    @Environment(\.paleta) private var p
    var body: some View { T(texto, 13, 800, p.texto2, espaciado: 1) }
}

/// Estado vacío como 13b: dos círculos punteados lavanda + el "+" lima, título `700 30px`, texto y CTA.
struct MiosVacio: View {
    var titulo: String
    var cuerpo: String
    var cta: String
    var accion: () -> Void
    @Environment(\.paleta) private var p

    var body: some View {
        VStack(spacing: 18) {
            HStack(spacing: 0) {
                ForEach(0..<2, id: \.self) { i in
                    Circle().fill(p.superficie)
                        .overlay(Circle().inset(by: 1.5).stroke(Color(hex: 0xB9ACF5), style: StrokeStyle(lineWidth: 3, dash: [6, 4])))
                        .frame(width: 76, height: 76)
                        .offset(x: CGFloat(-18 * i))
                }
                Icono(I.mas, 34, C.carbon, trazo: 3)
                    .frame(width: 84, height: 84)
                    .gel(42, .radial(cx: 0.32, cy: 0.26, rx: 0.9, ry: 0.9, Fondo.paradas((0, Color(hex: 0xF2FFC8)), (0.4, C.lima), (1, Color(hex: 0xA6D640)))),
                         [interna(-4, 8, rgba(110, 150, 20, 0.3)), externa(12, 22, rgba(140, 190, 40, 0.6), spread: -8)])
                    .overlay(Circle().strokeBorder(p.fondo, lineWidth: 4))
                    .offset(x: -36)
            }
            .padding(.leading, 54)
            T(titulo, 30, 700, alto: 1.08, espaciado: -0.6, alinear: .center)
            T(cuerpo, 15, 400, p.texto2, alto: 1.5, alinear: .center)
            Cta(texto: cta, alto: 58, tam: 16, accion: accion).padding(.top, 8)
        }
        .padding(.horizontal, 8)
        .frame(maxWidth: .infinity)
    }
}

/// Campo de texto con el estilo del registro (01d): 60 de alto, radio 18, borde 1,5 `#E6E1F2` que pasa a
/// 2 violeta con foco.
struct MiosCampo: View {
    @Binding var valor: String
    var placeholder = ""
    var renglones = 1
    var maximo = 200
    @FocusState private var foco: Bool
    @Environment(\.paleta) private var p

    var body: some View {
        ZStack(alignment: renglones == 1 ? .leading : .topLeading) {
            if valor.isEmpty { T(placeholder, 16, 500, C.inactivo, unRenglon: renglones == 1).allowsHitTesting(false) }
            Group {
                if renglones == 1 {
                    TextField("", text: $valor)
                } else {
                    TextField("", text: $valor, axis: .vertical).lineLimit(renglones...6)
                }
            }
            .font(Geist.fuente(16, 600))
            .foregroundStyle(p.texto)
            .tint(C.violeta)
            .focused($foco)
            .onChange(of: valor) { _, v in if v.count > maximo { valor = String(v.prefix(maximo)) } }
        }
        .padding(.horizontal, 18).padding(.vertical, renglones == 1 ? 0 : 18)
        .frame(minHeight: renglones > 1 ? 96 : 60, alignment: renglones == 1 ? .leading : .topLeading)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(p.oscuro ? p.superficie : C.blanco))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
            .strokeBorder(foco ? C.violeta : (p.oscuro ? C.oscuro1 : Color(hex: 0xE6E1F2)), lineWidth: foco ? 2 : 1.5))
        .contentShape(Rectangle())
        .onTapGesture { foco = true }
    }
}

/// Fila de una tarjeta de opciones (como "Herramientas para salir tranqui"): ícono, título y bajada.
struct MiosFila: View {
    var icono: String
    var titulo: String
    var sub: String?
    var colorIcono: Color = C.violeta
    var ultima = false
    var accion: () -> Void
    @Environment(\.paleta) private var p

    var body: some View {
        VStack(spacing: 0) {
            Button(action: accion) {
                HStack(spacing: 12) {
                    Icono(icono, 20, colorIcono, trazo: 2.2)
                    VStack(alignment: .leading, spacing: 1) {
                        T(titulo, 14, 800)
                        if let sub { T(sub, 12, 400, p.texto2) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Icono(I.adelante, 18, p.texto3, trazo: 2.2)
                }
                .padding(.horizontal, 16).padding(.vertical, 14)
                .contentShape(Rectangle())
            }
            .buttonStyle(EstiloPlano())
            if !ultima { Rectangle().fill(p.oscuro ? p.separador : C.lavanda).frame(height: 1) }
        }
    }
}

/// Tarjeta neutra que agrupa filas (radio 22, `0 3px 0 #E6E0F3`).
struct MiosGrupo<Contenido: View>: View {
    @ViewBuilder var contenido: () -> Contenido
    @Environment(\.paleta) private var p
    var body: some View {
        VStack(spacing: 0, content: contenido)
            .frame(maxWidth: .infinity)
            .gel(22, p.oscuro ? Gels.tarjetaOscura : Gels.neutra)
    }
}

/// Hoja que sube desde abajo sobre un velo oscuro (como los overlays del prototipo).
struct MiosHoja<Contenido: View>: View {
    var abierta: Bool
    var titulo: String
    var cerrar: () -> Void
    @ViewBuilder var contenido: () -> Contenido
    @Environment(\.paleta) private var p

    var body: some View {
        ZStack(alignment: .bottom) {
            if abierta {
                rgba(21, 19, 28, 0.45).ignoresSafeArea().onTapGesture(perform: cerrar).transition(.opacity)
                VStack(alignment: .leading, spacing: 14) {
                    Capsule().fill(C.borde6).frame(width: 40, height: 5).frame(maxWidth: .infinity)
                    T(titulo, 20, 700, espaciado: -0.3)
                    contenido()
                }
                .padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 24)
                .background(UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous)
                    .fill(p.fondo).ignoresSafeArea(edges: .bottom))
                .transition(.move(edge: .bottom))
            }
        }
        .animation(.easeOut(duration: 0.26), value: abierta)
        .zIndex(30)
    }
}

/// Pill chica (etiquetas "Adentro", "Esperando", "Completan").
struct MiosPill: View {
    var texto: String
    var fondo: Color
    var color: Color
    var gel: Gel?
    var tam: CGFloat = 13
    var body: some View {
        let t = T(texto, tam, 800, color, unRenglon: true).padding(.horizontal, 10).padding(.vertical, 5)
        if let gel { t.gel(10, gel) } else { t.background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(fondo)) }
    }
}

/// "Lu", "Lu y Nico", "Lu, Nico y Cami".
func miosNombres(_ n: [String]) -> String {
    switch n.count {
    case 0: return ""
    case 1: return n[0]
    default: return n.dropLast().joined(separator: ", ") + " y " + n.last!
    }
}

/// Primer nombre.
func miosPrimer(_ nombre: String) -> String {
    String(nombre.trimmingCharacters(in: .whitespaces).split(separator: " ").first ?? "")
}

/// Contenedor de pantalla con scroll y el aire de la tab bar abajo.
struct MiosScroll<Contenido: View>: View {
    var abajo: CGFloat = 24
    @ViewBuilder var contenido: () -> Contenido
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0, content: contenido)
                .padding(.horizontal, 20)
                .padding(.bottom, abajo)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}
