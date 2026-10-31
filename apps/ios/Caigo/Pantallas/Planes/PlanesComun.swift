import SwiftUI
import Nucleo

// Piezas que comparten las pantallas de planes (TarjetaPlan.kt y Detalle.kt de Android).

/// "Lugar · 1,2 km" (o la zona si no hay lugar).
func lugarYDistancia(_ p: Plan) -> String {
    let lugar = p.lugar?.nombre ?? (p.lugarNombre.isEmpty ? p.zona : p.lugarNombre)
    return [lugar, distancia(p.distanciaM)].filter { !$0.isEmpty }.joined(separator: " · ")
}

/// Plan de un lugar aliado que se paga (entrada o seña).
func esDeLugar(_ p: Plan) -> Bool { p.lugar != nil && p.cobro != "gratis" }

func textoCosto(_ p: Plan) -> String {
    if p.lugar != nil, let precio = p.precioCentavos {
        return Plata.pesos(precio) + " por persona" + (p.incluye.isEmpty ? "" : " · \(p.incluye)")
    }
    if let c = p.costoCentavos, c > 0 { return Plata.pesos(c) + " por persona" }
    return "Cada uno lo suyo"
}

func textoIngreso(_ p: Plan) -> String {
    if p.lugar != nil && p.cobro != "gratis" { return "Con entrada" }
    switch p.ingreso {
    case "libre": return "Libre"
    case "mi_gente": return "Solo mi gente"
    default: return "Con aprobación"
    }
}

/// Foto del plan (o la del lugar).
func fotoDe(_ p: Plan) -> String { p.foto.isEmpty ? (p.lugar?.foto ?? "") : p.foto }

/// El violeta con la base de 4 y la sombra difusa más corta (botones chicos del mockup).
let planesVioletaChico = Gel(fondo: Gels.fondoVioleta, sombras: [
    interna(3, 2, rgba(255, 255, 255, 0.45)), interna(-5, 0, rgba(40, 20, 140, 0.35)),
    externa(4, 0, C.violetaBase), externa(16, 26, rgba(116, 86, 241, 0.75), spread: -10),
])

/// Clave donde el detalle deja lo que eligió la persona para pagar: "entrada" (todo) o "sena".
/// La lee la pantalla de Checkout (Pagos) para mandar `modo` a POST /v1/planes/{id}/entradas.
func claveModoCheckout(_ planId: String) -> String { "checkout.modo.\(planId)" }

/// Chip chico sobre la foto (categoría clara, hora oscura con grano).
struct ChipFoto: View {
    var texto: String
    var oscuro: Bool
    var fondo: Gel?
    var color: Color?
    @Environment(\.paleta) private var p

    var body: some View {
        let invertido = oscuro == !p.oscuro
        T(texto, 12, 700, color ?? (invertido ? C.blancoCalido : C.carbon), unRenglon: true)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .gel(11, fondo ?? (invertido ? Gels.oscuroSel : Gel(fondo: .liso(C.blancoCalido))))
    }
}

/// Plan destacado con Caigo Plus: chip lima con la estrella del me encanta.
struct ChipDestacado: View {
    var soloEstrella = false
    var body: some View {
        HStack(spacing: 5) {
            Icono(I.estrella, soloEstrella ? 14 : 12, .clear, trazo: 0, relleno: C.carbon)
            if !soloEstrella { T("Destacado", 12, 700, C.carbon, unRenglon: true) }
        }
        .padding(.horizontal, soloEstrella ? 8 : 10).padding(.vertical, 6)
        .gel(11, .liso(C.lima))
    }
}

/// Chip «Plus» lima (filtros finos, invitaciones bloqueadas).
struct PlanesChipPlus: View {
    var texto = "Plus"
    var body: some View {
        T(texto, 11, 800, C.carbon, unRenglon: true)
            .padding(.horizontal, 10).padding(.vertical, 6)
            .gel(11, .liso(C.lima))
    }
}

/// Sumarse a un plan desde la tarjeta o el detalle. Los errores de verificación llevan a la selfie o
/// al DNI (y vuelven); el resto se muestra tal cual en el toast. Devuelve el estado nuevo.
@MainActor
/// Con `faltaGenero`, el 422 `falta_genero` no se muestra: se llama para preguntarlo y reintentar.
/// Sin él (la tarjeta de una lista), se abre el detalle, que tiene la hoja.
func planesSumarse(_ c: Contenedor, _ ir: Ir, _ plan: Plan, cantidad: Int, mensaje: String = "",
                   faltaGenero: (() -> Void)? = nil) async -> String? {
    do {
        let e = try await c.api.sumarme(plan.id, cantidad: cantidad, mensaje: mensaje).estado
        let quien = plan.organizador.nombre.isEmpty ? "quien organiza" : plan.organizador.nombre
        c.avisos.ok(e == "aceptada" ? "¡Estás adentro!" : "Le pediste a \(quien) sumarte.")
        Sistema.vibrar()
        return e
    } catch let e as ErrorApi {
        if e.codigo == "falta_genero" {
            if let faltaGenero { faltaGenero() } else { ir.plan(plan.id) }
            return nil
        }
        c.avisos.error(e.detalle)
        if e.codigo == "falta_selfie" { ir.selfie(volver: true) }
        if e.codigo == "falta_dni" { ir.dni(plan: plan.id) }
        return nil
    } catch {
        c.avisos.error(error)
        return nil
    }
}

/// Cantidad con la que se suma por defecto: la del 01b («Voy solo / Somos 2 / Somos 4»), acotada a
/// lo que acepta el plan.
func planesCantidadInicial(_ somos: Int, _ p: Plan) -> Int {
    guard p.aceptaGrupos, p.faltan > 1 else { return 1 }
    return max(1, min(somos, p.faltan, 10))
}

/// Política de cancelación de una entrada (contrato §2), dicha para quien compró.
func planesPolitica(_ cancelacion: String) -> String {
    switch cancelacion {
    case "mod": return "Hasta 72 h antes te devolvemos la entrada. Después podés cancelar igual para liberar el lugar, pero sin reembolso. El cargo por servicio no se devuelve."
    case "strict": return "El lugar no devuelve entradas de este plan. Si el plan se cancela, te devolvemos todo."
    default: return "Hasta 24 h antes te devolvemos la entrada. Después podés cancelar igual para liberar el lugar, pero sin reembolso. El cargo por servicio no se devuelve."
    }
}

/// Botón de acción de la tarjeta: Sumarme / Entrada · Esperando… · Adentro.
struct AccionTarjeta: View {
    var plan: Plan
    var alCambiar: (String) -> Void
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var enviando = false

    var body: some View {
        if plan.miEstado == "pendiente" {
            T("Esperando…", 14, 800, C.violetaTexto, unRenglon: true)
                .padding(.horizontal, 16).padding(.vertical, 11)
                .gel(16, .liso(C.tinte1), [interna(2, 4, rgba(116, 86, 241, 0.18))])
        } else if plan.adentro {
            HStack(spacing: 7) {
                Icono(I.check, 14, C.lima, trazo: 3.4)
                T(plan.esMio ? "Tu plan" : "Adentro", 14, 800, C.blanco, unRenglon: true)
            }
            .padding(.horizontal, 16).padding(.vertical, 11)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(C.carbon))
        } else if !Cupo.completo(plan.faltan) {
            Button {
                if esDeLugar(plan) { ir.plan(plan.id); return }
                Task {
                    enviando = true
                    if let e = await planesSumarse(c, ir, plan, cantidad: planesCantidadInicial(c.somos, plan)) { alCambiar(e) }
                    enviando = false
                }
            } label: {
                HStack(spacing: 6) {
                    if enviando { Puntitos(color: C.carbon, tam: 6) } else {
                        Icono(I.mas, 14, C.carbon, trazo: 4)
                        T(esDeLugar(plan) ? "Entrada" : "Sumarme", 14, 800, C.carbon, unRenglon: true)
                    }
                }
                .padding(.horizontal, 14).padding(.vertical, 9)
                .gel(14, Gels.limaChico)
            }
            .buttonStyle(EstiloToque())
            .disabled(enviando)
        }
    }
}

/// Tarjeta de plan (02 · Ahora): foto de 150 con categoría y hora arriba, badge "Faltan N" abajo a la
/// derecha; título, lugar · distancia, avatares apilados, "Somos N de M" y el botón lima.
struct TarjetaPlan: View {
    var plan: Plan
    var altoFoto: CGFloat = 150
    var alCambiar: (String) -> Void = { _ in }
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p

    var body: some View {
        let forma = RoundedRectangle(cornerRadius: 24, style: .continuous)
        VStack(alignment: .leading, spacing: 0) {
            ZStack {
                Foto(url: fotoDe(plan), tinte: colorDeCategoria(plan.categoria))
                VStack {
                    HStack(spacing: 6) {
                        ChipFoto(texto: Categorias.nombre(plan.categoria), oscuro: false)
                        ChipFoto(texto: Fecha.cuando(plan.empiezaEn), oscuro: true)
                        if plan.lugar != nil { ChipFoto(texto: "Lugar aliado", oscuro: false, fondo: Gel(fondo: .liso(C.lima)), color: C.carbon) }
                        Spacer(minLength: 0)
                    }
                    Spacer(minLength: 0)
                    HStack(alignment: .bottom) {
                        if plan.destacado { ChipDestacado() }
                        Spacer(minLength: 0)
                        BadgeFalta(faltan: plan.faltan)
                    }
                }
                .padding(12)
            }
            .frame(height: altoFoto)
            .clipped()

            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    T(plan.titulo, 20, 700, p.texto, alto: 1.15, espaciado: -0.3)
                    T(lugarYDistancia(plan), 13, 400, p.texto2, unRenglon: true)
                }
                HStack(spacing: 6) {
                    // Con "Adentro" / "Esperando…" el botón es más ancho: van sólo las cuentas.
                    let conAvatares = !plan.van.isEmpty && plan.miEstado == "ninguno"
                    if conAvatares { Avatares(personas: plan.van.map { ($0.nombre, $0.foto) }, total: plan.somos) }
                    T(Cupo.somos(plan.somos, plan.total), 13, 600, p.oscuro ? C.apagado2 : C.texto2, unRenglon: true)
                    Spacer(minLength: 4)
                    AccionTarjeta(plan: plan, alCambiar: alCambiar)
                }
            }
            .padding(EdgeInsets(top: 14, leading: 16, bottom: 16, trailing: 16))
        }
        .clipShape(forma)
        .gel(24, Gels.tarjeta(p))
        .contentShape(forma)
        .onTapGesture { ir.plan(plan.id) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }
}

/// Beneficio por check-in ("Café y medialuna de regalo").
struct PlanesBeneficio: View {
    var texto: String
    var desbloqueado: Bool
    var body: some View {
        HStack(spacing: 12) {
            Icono(I.regalo, 22, desbloqueado ? C.lima : C.violetaTexto, trazo: 2.2)
                .frame(width: 44, height: 44)
                .background(RoundedRectangle(cornerRadius: 15, style: .continuous).fill(desbloqueado ? C.carbon : C.blanco))
            VStack(alignment: .leading, spacing: 2) {
                T(texto, 14, 800, C.carbon)
                T(desbloqueado ? "Desbloqueado · mostralo en la caja" : "Se desbloquea cuando hacés check-in en el lugar", 12, 600,
                  desbloqueado ? C.limaTexto : C.texto2)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .gel(22, desbloqueado
             ? Gel(fondo: .vertical(C.lima0, C.lima), sombras: [interna(2, 0, rgba(255, 255, 255, 0.7)), externa(3, 0, C.limaBase)])
             : Gel(fondo: .liso(C.lavanda)))
    }
}

/// Filas que se acomodan solas y bajan de renglón (el `flex-wrap` del mockup).
struct PlanesFlujo: Layout {
    var espacio: CGFloat = 8
    var renglon: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let ancho = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, alto: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let t = s.sizeThatFits(.unspecified)
            if x > 0 && x + t.width > ancho { x = 0; y += alto + renglon; alto = 0 }
            x += t.width + espacio
            maxX = max(maxX, x - espacio)
            alto = max(alto, t.height)
        }
        return CGSize(width: proposal.width ?? maxX, height: y + alto)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, alto: CGFloat = 0
        for s in subviews {
            let t = s.sizeThatFits(.unspecified)
            if x > bounds.minX && x + t.width > bounds.maxX { x = bounds.minX; y += alto + renglon; alto = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(t))
            x += t.width + espacio
            alto = max(alto, t.height)
        }
    }
}

/// Campo de texto liso con pista (Geist), para buscadores y formularios.
struct PlanesCampo: View {
    @Binding var texto: String
    var pista: String
    var tam: CGFloat = 15
    var peso = 500
    var colorPista: Color = C.texto2
    var color: Color?
    var tipo: TipoTeclado = .texto
    @Environment(\.paleta) private var p

    var body: some View {
        ZStack(alignment: .leading) {
            if texto.isEmpty { T(pista, tam, peso == 500 ? 400 : 600, colorPista, unRenglon: true).allowsHitTesting(false) }
            TextField("", text: $texto)
                .textFieldStyle(.plain)
                .font(Geist.fuente(tam, peso))
                .foregroundStyle(color ?? p.texto)
                .tint(C.violeta)
                .teclado(tipo)
                .autocorrectionDisabled(tipo != .texto)
        }
    }
}
