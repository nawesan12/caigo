import SwiftUI
import StoreKit
import Nucleo

/// Fondo del paywall: violeta profundo arriba sobre grafito.
private let fondoPlus = Fondo.radial(cx: 0.5, cy: 0.12, rx: 0.9, ry: 0.45, Fondo.paradas((0, Color(hex: 0x3B2F78)), (0.7, C.grafito)))
private let textoSuave = Color(hex: 0xD3CEE2)
private let textoTenue = Color(hex: 0xC9C4DA)

/// Los productos de suscripción de App Store Connect.
enum PagosTienda {
    static let productos = ["plus_semana", "plus_mes", "plus_temporada"]

    /// Días de prueba gratis de la oferta introductoria (0 si no hay o si ya la usó).
    static func diasGratis(_ p: Product?) async -> Int {
        guard let s = p?.subscription, let o = s.introductoryOffer, o.paymentMode == .freeTrial else { return 0 }
        guard await s.isEligibleForIntroOffer else { return 0 }
        let n = o.period.value
        switch o.period.unit {
        case .day: return n
        case .week: return n * 7
        case .month: return n * 30
        case .year: return n * 365
        @unknown default: return 0
        }
    }
}

/// "20:03:24" hasta `hasta` (o medianoche de Argentina si no vino).
private func reloj(_ hasta: Date?, _ ahora: Date) -> String {
    let fin = hasta ?? Fecha.calendario.startOfDay(for: ahora).addingTimeInterval(86400)
    let t = max(0, Int(fin.timeIntervalSince(ahora)))
    return String(format: "%02d:%02d:%02d", t / 3600, (t / 60) % 60, t % 60)
}

private func redondearPeso(_ centavos: Double) -> Int { Int((centavos / 100).rounded()) * 100 }

/// Caigo Plus (paywall del prototipo). `motivo`: `limit` (se quedó sin sumadas), `likes` (quiere
/// ver quién lo sumó) o `perfil`. Se paga con StoreKit 2; la API verifica la transacción firmada.
struct VistaPlus: View {
    var motivo: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir

    @State private var plus: Nucleo.Plus?
    @State private var cargando = true
    @State private var recibidos: Recibidos?
    @State private var productos: [String: Product] = [:]
    @State private var diasGratis = 0
    @State private var elegido = "mes"
    @State private var comprando = false
    @State private var listo = false
    @State private var ahora = Date()

    var body: some View {
        Pantalla(fondo: AnyView(ZStack { C.grafito; VistaFondo(fondo: fondoPlus); Grano() })) {
            if listo {
                PlusListo(fotos: fotosTeaser) {
                    Task { await c.recargarYo(); await c.recargarPlus() }
                    ir.atras()
                }
            } else {
                paywall
            }
        }
        .environment(\.paleta, .oscura)
        .task { await cargar() }
        .task { await escucharActualizaciones() }
        .task {
            // Contador en vivo hasta que se renuevan las sumadas.
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                ahora = Date()
            }
        }
    }

    private var fotosTeaser: [String] { (recibidos?.personas ?? []).map(\.foto).filter { !$0.isEmpty }.prefix(4).map { $0 } }
    private var planes: [PlanPlus] { plus?.planes ?? [] }
    private func plan(_ k: String) -> PlanPlus? { planes.first { $0.plan == k } }

    /// Precio a mostrar: el de la tienda (en la moneda de la cuenta) o el de la API.
    private func precio(_ p: PlanPlus) -> String {
        if let prod = productos[p.producto] { return prod.displayPrice }
        return Plata.pesos(p.centavos)
    }

    private var paywall: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    HStack {
                        BotonIcono(d: I.cruz, iconoTam: 20, trazo: 2.6, gel: Gel(fondo: .liso(rgba(255, 255, 255, 0.1)), sombras: [interna(1, 0, rgba(255, 255, 255, 0.15))]),
                                   color: C.blanco) { ir.atras() }
                        Spacer()
                        T("Restaurar compra", 13, 700, textoTenue).tocable { Task { await restaurar() } }
                    }
                    // "Caigo" grande con la pill PLUS
                    ZStack(alignment: .topTrailing) {
                        T("Caigo", 80, 700, C.blancoCalido, alto: 1, espaciado: espaciadoTitulo(80), alinear: .center, unRenglon: true)
                            .frame(maxWidth: .infinity).padding(.top, 56)
                        PagosPillPlus().pop().padding(.top, 44).padding(.trailing, 26)
                    }
                    .frame(height: 150)
                    encabezado
                    if let n = recibidos?.personas.count, n > 0 { TeaserRecibidos(fotos: fotosTeaser, n: n) }
                    TablaPlus()
                    T("Elegí tu plan", 20, 700, C.blanco).padding(.top, 4)
                    if cargando && planes.isEmpty {
                        Puntitos(color: C.lima).frame(maxWidth: .infinity).padding(20)
                    }
                    ForEach(planes) { p in opcion(p) }
                    if motivo == "limit" {
                        T("Prefiero esperar a mañana", 14, 700, textoTenue)
                            .padding(.horizontal, 14).padding(.vertical, 8)
                            .tocable { ir.atras() }
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 190)
            }
            ctaFija
        }
    }

    @ViewBuilder
    private var encabezado: some View {
        VStack(spacing: 10) {
            let titulo = switch motivo {
            case "limit": "Te quedaste sin sumadas por hoy"
            case "likes": "Mirá quién te quiere conocer"
            default: "Más planes. Más gente. Sin límites."
            }
            T(titulo, 32, 800, C.blanco, alto: 1.02, espaciado: -1, alinear: .center)
            if motivo == "limit" {
                HStack(spacing: 8) {
                    T("Se renuevan en", 13, 700, Color(hex: 0xE4E0EE))
                    T(reloj(plus.flatMap { Fecha.parse($0.estado.reiniciaEn) }, ahora), 15, 800, C.carbon, alto: 1, espaciado: 0.5, unRenglon: true)
                        .monospacedDigit()
                        .padding(.horizontal, 9).padding(.vertical, 5)
                        .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(C.coral))
                }
                .padding(.leading, 14).padding(.trailing, 8).padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(rgba(255, 255, 255, 0.08)))
            } else {
                T(motivo == "likes"
                  ? "Con Plus ves a todos los que te sumaron o te invitaron a sus planes, y los sumás de vuelta."
                  : "Sumá a quien quieras, mirá quién te sumó y destacá tus planes.",
                  15, 400, textoSuave, alto: 1.5, alinear: .center)
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func opcion(_ p: PlanPlus) -> some View {
        let mesC = plan("mes")?.centavos
        var desc = "", por = "", etiqueta = "", ahorro = ""
        switch p.plan {
        case "semana": desc = "Para probar un finde largo"; por = "por semana"
        case "mes":
            desc = diasGratis > 0 ? "\(diasGratis) días gratis para arrancar" : ""
            por = "\(Plata.pesos(redondearPeso(Double(p.centavos) / 4))) por semana"
            etiqueta = "Más elegido"
        case "temporada":
            desc = "Todo el verano, 3 meses"
            por = "\(Plata.pesos(redondearPeso(Double(p.centavos) / 13))) por semana"
            if let m = mesC, m > 0 { ahorro = "−\(Int(((1 - Double(p.centavos) / Double(m * 3)) * 100).rounded()))%" }
        default: break
        }
        // Si la tienda cobra en otra moneda, el "por semana" en pesos no aplica.
        if productos[p.producto] != nil, productos[p.producto]?.priceFormatStyle.currencyCode != "ARS", p.plan != "semana" { por = "" }
        return OpcionPlan(nombre: p.nombre.isEmpty ? p.plan : p.nombre, precio: precio(p), desc: desc, por: por,
                          etiqueta: etiqueta, ahorro: ahorro, on: elegido == p.plan) { elegido = p.plan }
    }

    private var ctaFija: some View {
        let p = plan(elegido)
        let cta: String = {
            guard let p else { return "Elegí tu plan" }
            if p.plan == "mes" && diasGratis > 0 { return "Probar \(diasGratis) días gratis" }
            return "Pagar \(precio(p))"
        }()
        return VStack(spacing: 10) {
            Button { Task { await comprar() } } label: {
                ZStack {
                    if comprando {
                        Puntitos(color: C.carbon)
                    } else {
                        HStack(spacing: 10) {
                            Image("CaigoIcono").resizable().frame(width: 34, height: 34).offset(x: -6)
                            T(cta, 18, 800, C.carbon, unRenglon: true)
                        }
                    }
                }
                .frame(maxWidth: .infinity).frame(height: 64)
                .gel(24, pagosGelLimaNfc)
            }
            .buttonStyle(EstiloToque(baja: 4))
            .disabled(comprando || p == nil)
            if let p {
                let fino = p.plan == "mes" && diasGratis > 0 ? "Gratis \(diasGratis) días, después \(precio(p)) por mes."
                    : p.plan == "temporada" ? "Pagás \(precio(p)) hoy por 3 meses." : "Pagás \(precio(p)) hoy."
                T("\(fino) Cancelás cuando quieras · App Store", 12, 600, textoTenue, alto: 1.45, alinear: .center)
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 30)
        .background(LinearGradient(stops: Fondo.paradas((0, C.grafito.opacity(0)), (0.3, C.grafito)), startPoint: .top, endPoint: .bottom)
            .ignoresSafeArea())
    }

    // MARK: - StoreKit 2

    private func cargar() async {
        async let p = try? c.api.plus()
        async let r = try? c.api.recibidos("gente")
        async let prods = try? Product.products(for: PagosTienda.productos)
        let (pl, rs, ps) = await (p, r, prods)
        plus = pl
        recibidos = rs
        cargando = false
        if let pl {
            c.plus = pl.estado
            if pl.estado.activo { listo = true }
        }
        productos = Dictionary((ps ?? []).map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        let mes = plan("mes").flatMap { productos[$0.producto] } ?? productos["plus_mes"]
        diasGratis = await PagosTienda.diasGratis(mes)
    }

    private func comprar() async {
        guard let p = plan(elegido) else { return }
        guard let prod = productos[p.producto] else {
            c.avisos.error("Caigo Plus todavía no está disponible en el App Store.")
            return
        }
        comprando = true
        defer { comprando = false }
        var opciones: Set<Product.PurchaseOption> = []
        // appAccountToken = id de la persona en Caigo: así la API ata la compra a la cuenta.
        if let u = UUID(uuidString: c.usuarioId) { opciones.insert(.appAccountToken(u)) }
        do {
            switch try await prod.purchase(options: opciones) {
            case let .success(v): await registrar(v)
            case .pending: c.avisos.ok("Tu compra quedó pendiente de aprobación. Cuando se apruebe, se activa Plus.")
            case .userCancelled: break
            @unknown default: break
            }
        } catch {
            c.avisos.error("No se pudo completar la compra. Probá de nuevo.")
        }
    }

    /// Manda la transacción firmada (JWS) a la API para que la verifique y active Plus.
    @discardableResult
    private func registrar(_ v: VerificationResult<StoreKit.Transaction>) async -> Bool {
        do {
            let e = try await c.api.plusApple(jws: v.jwsRepresentation)
            if case let .verified(t) = v { await t.finish() }
            c.plus = e
            if e.activo { listo = true; return true }
            c.avisos.error("El App Store todavía no confirmó la compra. Probá de nuevo en un rato.")
        } catch {
            c.avisos.error(error)
        }
        return false
    }

    private func restaurar() async {
        try? await AppStore.sync()
        var encontrada = false
        for await v in StoreKit.Transaction.currentEntitlements {
            if case let .verified(t) = v, PagosTienda.productos.contains(t.productID) {
                encontrada = true
                if await registrar(v) { return }
            }
        }
        if !encontrada { c.avisos.error("No encontramos compras de Caigo Plus en esta cuenta del App Store.") }
    }

    /// Renovaciones, compras aprobadas después (Ask to Buy) y las hechas en otro dispositivo.
    private func escucharActualizaciones() async {
        for await v in StoreKit.Transaction.updates {
            if case let .verified(t) = v, PagosTienda.productos.contains(t.productID) {
                await registrar(v)
            }
        }
    }
}

/// "4 personas te sumaron · Con Plus ves quiénes son", con las caras borrosas.
private struct TeaserRecibidos: View {
    var fotos: [String]
    var n: Int
    var body: some View {
        HStack(spacing: 14) {
            HStack(spacing: -12) {
                ForEach(Array(fotos.enumerated()), id: \.offset) { _, f in
                    ImagenRed(url: f).scaleEffect(1.2).blur(radius: 6)
                        .frame(width: 44, height: 44).background(C.oscuro2).clipShape(Circle())
                        .overlay(Circle().strokeBorder(Color(hex: 0x2E2850), lineWidth: 2.5))
                }
            }
            .padding(.leading, 10)
            VStack(alignment: .leading, spacing: 2) {
                T(n == 1 ? "1 persona te sumó" : "\(n) personas te sumaron", 15, 800, C.blanco)
                T(n == 1 ? "Con Plus ves quién es" : "Con Plus ves quiénes son", 12, 600, textoSuave)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Icono(I.candado, 16, .clear, trazo: 0, relleno: C.blanco)
                .frame(width: 34, height: 34)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(rgba(255, 255, 255, 0.12)))
        }
        .padding(14)
        .gel(24, Gel(fondo: .vertical(rgba(116, 86, 241, 0.35), rgba(116, 86, 241, 0.15)),
                     sombras: [interna(1, 0, rgba(255, 255, 255, 0.15)), interna(0, 0, rgba(163, 143, 255, 0.35), spread: -1)]))
    }
}

/// Comparación Gratis / Plus (la tabla del prototipo).
private struct TablaPlus: View {
    let filas: [(String, String, String)] = [
        ("Sumadas en Gente", "3 por día", "Sin límite"),
        ("Ver quién te sumó", "—", "✓"),
        ("Me encanta en Citas", "1 por día", "5 por día"),
        ("Tu plan destacado", "—", "1 por semana"),
        ("Filtros finos", "Básicos", "Todos"),
    ]

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Color.clear.frame(maxWidth: .infinity).layoutPriority(1.5)
                columna("GRATIS", C.apagado)
                columna("PLUS", C.lima)
            }
            .padding(.top, 12).padding(.bottom, 4)
            ForEach(Array(filas.enumerated()), id: \.offset) { i, f in
                if i > 0 { Rectangle().fill(rgba(255, 255, 255, 0.08)).frame(height: 1) }
                HStack(spacing: 0) {
                    T(f.0, 13, 700, C.blancoCalido).padding(.leading, 14).padding(.vertical, 12)
                        .frame(width: nil, alignment: .leading).frame(maxWidth: .infinity, alignment: .leading)
                        .layoutPriority(1.5)
                    T(f.1, 12, 600, C.apagado, alinear: .center).frame(maxWidth: .infinity).padding(.horizontal, 4)
                    Group {
                        if f.2 == "✓" { Icono(I.check, 16, C.lima, trazo: 3.4) } else { T(f.2, 12, 800, C.lima, alinear: .center) }
                    }
                    .frame(maxWidth: .infinity).padding(.horizontal, 4)
                }
            }
        }
        .background(alignment: .trailing) {
            // Columna Plus resaltada (29 % a la derecha)
            GeometryReader { g in
                UnevenRoundedRectangle(cornerRadii: .init(topLeading: 0, bottomLeading: 0, bottomTrailing: 24, topTrailing: 24), style: .continuous)
                    .fill(LinearGradient(colors: [rgba(116, 86, 241, 0.45), rgba(116, 86, 241, 0.2)], startPoint: .top, endPoint: .bottom))
                    .overlay(alignment: .leading) { Rectangle().fill(rgba(163, 143, 255, 0.4)).frame(width: 1) }
                    .frame(width: g.size.width * 0.29)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .gel(24, Gel(fondo: .liso(rgba(255, 255, 255, 0.05)), sombras: [interna(0, 0, rgba(255, 255, 255, 0.08), spread: -1)]))
    }

    private func columna(_ t: String, _ c: Color) -> some View {
        T(t, 11, 800, c, espaciado: 0.8, alinear: .center, unRenglon: true).frame(maxWidth: .infinity)
    }
}

private struct OpcionPlan: View {
    var nombre: String
    var precio: String
    var desc: String
    var por: String
    var etiqueta: String
    var ahorro: String
    var on: Bool
    var accion: () -> Void

    var body: some View {
        let gel = on
            ? Gel(fondo: .liso(C.blanco), sombras: [interna(0, 0, C.lima, spread: -3), externa(4, 0, C.limaBase), externa(18, 30, rgba(200, 243, 107, 0.35), spread: -12)])
            : Gel(fondo: .liso(rgba(255, 255, 255, 0.06)), sombras: [interna(0, 0, rgba(255, 255, 255, 0.14), spread: -1.5)])
        let fg = on ? C.carbon : C.blanco
        let sub = on ? C.texto2 : textoTenue
        return Button(action: accion) {
            HStack(spacing: 14) {
                Circle().fill(C.blanco)
                    .overlay(Circle().strokeBorder(on ? C.violeta : rgba(255, 255, 255, 0.35), lineWidth: on ? 7 : 2))
                    .frame(width: 24, height: 24)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 8) {
                        T(nombre, 16, 800, fg, unRenglon: true)
                        if !ahorro.isEmpty {
                            T(ahorro, 11, 800, C.carbon, unRenglon: true).padding(.horizontal, 7).padding(.vertical, 3)
                                .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(C.lima))
                        }
                    }
                    if !desc.isEmpty { T(desc, 12, 600, sub) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .trailing, spacing: 2) {
                    T(precio, 22, 800, fg, alto: 1, unRenglon: true)
                    if !por.isEmpty { T(por, 11, 700, sub, unRenglon: true) }
                }
            }
            .padding(16)
            .gel(22, gel)
            .overlay(alignment: .topLeading) {
                if !etiqueta.isEmpty {
                    T(etiqueta, 11, 800, C.carbon, unRenglon: true)
                        .padding(.horizontal, 10).padding(.vertical, 4)
                        .gel(9, Gel(fondo: .vertical(Fondo.paradas((0, C.coral0), (0.6, C.coral), (1, C.coral2))), sombras: [externa(2, 0, Color(hex: 0xC9432F))]))
                        .offset(x: 18, y: -10)
                }
            }
        }
        .buttonStyle(EstiloToque(baja: 2))
        .padding(.top, etiqueta.isEmpty ? 0 : 10)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// "Ya sos Plus" (paywall_done).
private struct PlusListo: View {
    var fotos: [String]
    var seguir: () -> Void
    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            ZStack(alignment: .bottomTrailing) {
                ZStack {
                    Color.clear.frame(width: 150, height: 150).gel(46, nil, [externa(26, 30, rgba(116, 86, 241, 0.6))])
                    Image("CaigoIcono").resizable().frame(width: 190, height: 190)
                }
                .pop()
                PagosPillPlus(tam: 18).pop().offset(x: 14, y: -20)
            }
            T("Ya sos Plus", 42, 800, C.blanco, alto: 1, espaciado: -1.4, alinear: .center)
            T("Sumá a toda la gente que quieras. Ya podés ver quién te sumó y tu próximo plan sale destacado.",
              16, 400, textoSuave, alto: 1.5, alinear: .center)
            if !fotos.isEmpty {
                HStack(spacing: -12) {
                    ForEach(Array(fotos.enumerated()), id: \.offset) { _, f in
                        ImagenRed(url: f).frame(width: 56, height: 56).background(C.oscuro2).clipShape(Circle())
                            .overlay(Circle().strokeBorder(C.grafito, lineWidth: 3))
                    }
                }
                .padding(.top, 12)
            }
            Spacer()
            Button(action: seguir) {
                T("Seguir", 18, 800, C.carbon).frame(maxWidth: .infinity).frame(height: 62).gel(22, pagosGelLimaNfc)
            }
            .buttonStyle(EstiloToque(baja: 4))
            .padding(.top, 10)
        }
        .padding(30)
    }
}
