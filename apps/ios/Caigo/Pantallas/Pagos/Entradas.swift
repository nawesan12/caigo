import SwiftUI
import Nucleo
#if os(iOS)
import UIKit
#endif

/// Tarjeta de la entrada: radial grafito con grano.
private let gelTicket = Gel(fondo: .radial(cx: 0, cy: 0, rx: 1.2, ry: 0.9, Fondo.paradas((0, C.oscuro1), (0.62, C.carbon))),
                            sombras: [externa(30, 50, rgba(36, 33, 45, 0.7), spread: -24)], grano: true)
/// Opciones de cobro del checkout (coModes): elegida lavanda con anillo violeta, la otra blanca.
private let gelModoOn = Gel(fondo: .liso(C.lavanda), sombras: [interna(0, 0, C.violeta, spread: -2.5), externa(3, 0, C.borde5)])
private let gelModoOff = Gel(fondo: .liso(C.blanco), sombras: [interna(1, 0, C.blanco), externa(3, 0, C.borde1)])

/// "Tu entrada" (checkout del prototipo): cantidad, cómo se cobra (entrada entera o seña, lo define
/// el lugar), desglose con el cargo por servicio y pago con Mercado Pago.
struct Checkout: View {
    var planId: String
    var cantidad: Int
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @Environment(\.scenePhase) private var fase

    @State private var plan: Plan?
    @State private var error: String?
    @State private var q = 1
    @State private var modo = "entrada"
    @State private var pagando = false
    @State private var compra: Compra?
    @State private var revisando = false
    /// «Antes de sumarte» abierta: lo que pide confirmar (`mujer` | `diversidad`).
    @State private var pide: String?

    var body: some View {
        Pantalla {
            if let plan {
                if let compra { esperando(compra) } else { formulario(plan) }
            } else {
                VStack {
                    PagosEncabezado(titulo: "Tu entrada") { ir.atras() }
                    Spacer()
                    if let error { MensajeCentro(titulo: "No pudimos cargar el plan", cuerpo: error) } else { Puntitos() }
                    Spacer()
                }
                .padding(.horizontal, 20).padding(.top, 8)
            }
            // Plan «Solo mujeres» / «Solo diversidades»: se confirma y se reintenta (no se guarda).
            HojaAntesDeSumarte(pide: pide, cerrar: { pide = nil }) { conf in
                if let plan { Task { await pagar(plan, confirma: conf) } }
            }
        }
        .environment(\.paleta, .clara)
        .task {
            q = min(max(cantidad, 1), 10)
            // El detalle deja elegido "Pagar todo" o "Señar" (como el Prototipo).
            if let m = UserDefaults.standard.string(forKey: "checkout.modo.\(planId)"), m == "sena" || m == "entrada" { modo = m }
            do { plan = try await c.api.plan(planId) } catch { self.error = mensajeDe(error) }
        }
        // Al volver de Mercado Pago, se fija si ya se acreditó.
        .onChange(of: fase) { _, f in if f == .active, compra != nil { Task { await revisar() } } }
        .task(id: compra?.entradaId) {
            // Mientras espera, vuelve a mirar cada 4 s (el aviso de Mercado Pago puede tardar).
            guard compra != nil else { return }
            for _ in 0..<30 {
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                if Task.isCancelled { return }
                if await revisar() { return }
            }
        }
    }

    /// Busca la entrada ya pagada en "Mis pases" (el webhook de Mercado Pago la marca como pagada).
    @discardableResult
    private func revisar() async -> Bool {
        guard let id = compra?.entradaId, !id.isEmpty else { return false }
        revisando = true
        let ok = ((try? await c.api.pases().entradas.contains { $0.id == id }) ?? false)
        revisando = false
        if ok { ir.entrada(id, plan: planId) }
        return ok
    }

    /// `confirma`: lo que confirmó en «Antes de sumarte» (vale sólo para este pedido).
    private func pagar(_ plan: Plan, confirma: Confirma = Confirma()) async {
        pagando = true
        defer { pagando = false }
        do {
            let m = plan.cobro == "sena" && plan.senaPct != nil ? modo : "entrada"
            let r = try await c.api.comprarEntradas(plan.id, cantidad: q, modo: m, confirma: confirma)
            if !r.link.isEmpty {
                compra = r
                Sistema.abrir(r.link)
            } else if !r.entradaId.isEmpty {
                ir.entrada(r.entradaId, plan: plan.id)
            } else {
                c.avisos.ok("Ya tenés tu lugar en este plan.")
                ir.atras()
            }
        } catch let e as ErrorApi {
            if let x = Generos.pide(e.codigo) { pide = x; return }
            c.avisos.error(e.detalle)
            if e.codigo == "falta_selfie" { ir.selfie(volver: true) }
            if e.codigo == "falta_dni" { ir.dni(plan: plan.id) }
        } catch {
            c.avisos.error(error)
        }
    }

    private func esperando(_ compra: Compra) -> some View {
        VStack(spacing: 18) {
            PagosEncabezado(titulo: "Tu entrada") { self.compra = nil }
            Spacer()
            Puntitos()
            MensajeCentro(titulo: "Esperando la confirmación de Mercado Pago…",
                          cuerpo: "Cuando se acredite el pago, tu entrada aparece acá y en Mis pases.")
            Spacer()
            Cta(texto: "Ya pagué, revisar", cargando: revisando) {
                Task {
                    if !(await revisar()) { c.avisos.error("Todavía no nos llegó el pago. Esperá un ratito y probá de nuevo.") }
                }
            }
            T("Volver a abrir Mercado Pago", 15, 700, C.violetaTexto).padding(8).tocable { Sistema.abrir(compra.link) }
        }
        .padding(.horizontal, 24).padding(.top, 8).padding(.bottom, 30)
    }

    private func formulario(_ plan: Plan) -> some View {
        let conSena = plan.cobro == "sena" && plan.senaPct != nil
        let sena = conSena && modo == "sena"
        let precio = plan.precioCentavos ?? 0
        let cargo = plan.cargoCentavos > 0 ? plan.cargoCentavos : nil
        let porPersona = Plata.desglose(precio, cobro: "sena", senaPct: plan.senaPct, cantidad: 1, cargoPorPersona: cargo)
        let d = Plata.desglose(precio, cobro: sena ? "sena" : "entrada", senaPct: plan.senaPct, cantidad: q, cargoPorPersona: cargo)
        let maximo = plan.aceptaGrupos ? min(10, max(plan.faltan, 1)) : 1

        return ZStack(alignment: .bottom) {
            ScrollView {
                VStack(spacing: 16) {
                    PagosEncabezado(titulo: "Tu entrada") { ir.atras() }
                    HStack(spacing: 14) {
                        Foto(url: plan.foto).frame(width: 76, height: 76).clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                        VStack(alignment: .leading, spacing: 4) {
                            T(plan.titulo, 17, 700, C.carbon, alto: 1.15)
                            T(pagosCuandoDonde(plan.empiezaEn, pagosLugarDe(plan)), 13, 600, C.texto2)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(12).gel(24, Gels.neutra)

                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            T("Entradas", 15, 800, C.carbon)
                            T(q == 1 ? "Vas solo" : "Van \(q) juntos", 12, 600, C.texto2)
                        }
                        Spacer()
                        HStack(spacing: 14) {
                            BotonCantidad(signo: "−", habilitado: q > 1) { q -= 1 }
                            T("\(q)", 24, 800, C.carbon, alto: 1, alinear: .center).frame(minWidth: 20).monospacedDigit()
                            BotonCantidad(signo: "+", habilitado: q < maximo) { q += 1 }
                        }
                    }
                    .padding(.horizontal, 16).padding(.vertical, 14).gel(22, Gels.neutra)

                    // Plan con seña: «Pagar todo» o «Señar N%», las dos opciones del prototipo (coModes).
                    if conSena {
                        HStack(spacing: 10) {
                            opcionModo("entrada", "Pagar todo", "Entrada completa ahora")
                            opcionModo("sena", "Señar \(plan.senaPct ?? 0)%", "Reservás con \(Plata.pesos(porPersona.alLugar)); el resto en el lugar")
                        }
                        .fixedSize(horizontal: false, vertical: true)
                    }

                    VStack(spacing: 10) {
                        fila((sena ? "Seña \(plan.senaPct ?? 0)%" : "Entrada") + " × \(q)", Plata.pesos(d.alLugar))
                        fila("Cargo por servicio", Plata.pesos(d.cargo))
                        Rectangle().fill(C.lavanda).frame(height: 1)
                        HStack(alignment: .lastTextBaseline) {
                            T(sena ? "Pagás hoy" : "Total", 15, 800, C.carbon)
                            Spacer()
                            T(Plata.pesos(d.total), 28, 800, C.carbon, alto: 1, unRenglon: true)
                        }
                    }
                    .padding(16).gel(22, Gels.neutra)

                    HStack(spacing: 12) {
                        T("MP", 15, 800, C.blanco, alto: 1).frame(width: 44, height: 44)
                            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color(hex: 0x009EE3)))
                        VStack(alignment: .leading, spacing: 2) {
                            T("Mercado Pago", 15, 800, C.carbon)
                            T("Dinero en cuenta, débito o crédito", 12, 600, C.texto2)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        Circle().fill(C.blanco).overlay(Circle().strokeBorder(C.violeta, lineWidth: 7)).frame(width: 24, height: 24)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 14)
                    .gel(22, Gel(fondo: .liso(C.lavanda), sombras: [interna(0, 0, C.violeta, spread: -2)]))

                    if sena {
                        HStack {
                            T("En el lugar pagás", 14, 700, Color(hex: 0x7A2416))
                            Spacer()
                            T(Plata.pesos(d.saldo), 20, 800, Color(hex: 0x7A2416), alto: 1, unRenglon: true)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 14)
                        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Color(hex: 0xFFE9E4)))
                    }
                    T("Si el plan se cancela, te devolvemos todo. Para el lugar, vender en Caigo es gratis.", 12, 600, p.texto2, alto: 1.5)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 150)
            }
            Cta(texto: (sena ? "Señar " : "Pagar ") + Plata.pesos(d.total), gel: Gels.lima, alto: 62, radio: 22, tam: 18,
                color: C.carbon, cargando: pagando) { Task { await pagar(plan) } }
                .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 30)
                .background(LinearGradient(stops: Fondo.paradas((0, p.fondo.opacity(0)), (0.25, p.fondo)), startPoint: .top, endPoint: .bottom).ignoresSafeArea())
        }
    }

    private func opcionModo(_ k: String, _ l: String, _ sub: String) -> some View {
        let on = modo == k
        return Button { modo = k } label: {
            VStack(alignment: .leading, spacing: 3) {
                T(l, 15, 800, C.carbon)
                T(sub, 12, 600, C.texto2, alto: 1.35)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .padding(14)
            .gel(20, on ? gelModoOn : gelModoOff)
        }
        .buttonStyle(EstiloToque())
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private func fila(_ izq: String, _ der: String) -> some View {
        HStack {
            T(izq, 14, 600, C.texto2)
            Spacer()
            T(der, 14, 800, C.carbon, unRenglon: true)
        }
    }
}

private struct BotonCantidad: View {
    var signo: String
    var habilitado: Bool
    var accion: () -> Void
    var body: some View {
        Button(action: accion) {
            T(signo, 22, 800, C.violetaTexto, alto: 1)
                .frame(width: 40, height: 40)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(C.lavanda))
        }
        .buttonStyle(EstiloToque())
        .disabled(!habilitado)
        .opacity(habilitado ? 1 : 0.45)
        .accessibilityLabel(signo == "+" ? "Una entrada más" : "Una entrada menos")
    }
}

/// "Tenés tu entrada" (prototipo checkout_done + Pases NFC 1c): la tarjeta con el código, el
/// beneficio del check-in, agregar a Apple Wallet y volver al plan.
struct VistaEntrada: View {
    var entradaId: String
    var planId: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var entrada: EntradaMia?
    @State private var foto: String?
    @State private var cancelacion = ""
    @State private var cancelando = false
    @State private var cargando = true
    @State private var error: String?

    var body: some View {
        Pantalla {
            if let e = entrada {
                ScrollView {
                    VStack(spacing: 18) {
                        T("Tenés tu entrada", 36, 800, alto: 1, espaciado: -1.2, alinear: .center).sube()
                        T("También la tenés siempre en Mis pases.", 15, 600, p.texto2, alinear: .center)
                        ticket(e).pop()
                        if !e.beneficio.isEmpty {
                            HStack(spacing: 12) {
                                Icono(I.regalo, 24, C.violetaTexto, trazo: 2.2)
                                T("Hacé check-in al llegar y desbloqueás: \(e.beneficio)", 13, 700, C.carbon)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(C.lavanda))
                        }
                        PagosBoton(texto: "Mostrar QR de entrada", estilo: .blanco, icono: I.qr, radio: 20, peso: 800) { ir.qrEntrada(e.id) }
                        if PagosWallet.disponible {
                            PagosBotonWallet { try await c.api.pkpassEntrada(e.id) }
                        }
                        Cta(texto: "Ver el plan", gel: Gels.violeta, radio: 22, color: C.blanco) { ir.plan(e.planId) }
                        if !e.validada {
                            Button { cancelando = true } label: {
                                T("Cancelar mi entrada", 15, 700, C.coral2).padding(.horizontal, 18).padding(.vertical, 12)
                            }
                            .buttonStyle(EstiloPlano())
                        }
                    }
                    .padding(.horizontal, 24).padding(.top, 20).padding(.bottom, 40)
                }
            } else {
                VStack {
                    PagosEncabezado(titulo: "Tu entrada") { ir.atras() }
                    Spacer()
                    if cargando { Puntitos() } else {
                        MensajeCentro(titulo: "Tu entrada todavía no está",
                                      cuerpo: error ?? "Cuando Mercado Pago confirme el pago, aparece acá y en Mis pases.")
                    }
                    Spacer()
                }
                .padding(.horizontal, 20).padding(.top, 8)
            }
            // Cancelar según la política del plan (contrato §2): flex, mod o strict.
            HojaConfirmar(abierta: cancelando, titulo: cancelacion == "strict" ? "Tu entrada no tiene reembolso" : "¿Cancelar tu entrada?", cuerpo: planesPolitica(cancelacion),
                          accion: cancelacion == "strict" ? nil : "Cancelar mi entrada", cerrar: { cancelando = false }) {
                do {
                    let r = try await c.api.cancelarEntrada(entradaId)
                    c.avisos.ok(r.reembolsoCentavos > 0 ? "Listo. Te devolvemos \(Plata.pesos(r.reembolsoCentavos)) por Mercado Pago."
                                                         : "Listo, cancelaste tu entrada.")
                    ir.atras()
                } catch let x as ErrorApi where x.codigo == "sin_reembolso" && x.problema.detail.isEmpty {
                    throw ErrorApi(estado: x.estado, problema: Problema(detail: "El lugar no devuelve entradas de este plan."))
                }
            }
        }
        .environment(\.paleta, .clara)
        .task {
            do { entrada = try await c.api.pases().entradas.first { $0.id == entradaId } } catch { self.error = mensajeDe(error) }
            cargando = false
            let id = planId.isEmpty ? (entrada?.planId ?? "") : planId
            if !id.isEmpty, let pl = try? await c.api.plan(id) { foto = pl.foto; cancelacion = pl.cancelacion }
        }
    }

    private func ticket(_ e: EntradaMia) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Foto(url: foto).frame(height: 150).frame(maxWidth: .infinity)
            VStack(alignment: .leading, spacing: 8) {
                T(e.titulo, 22, 700, C.blancoCalido, alto: 1.1)
                T(pagosCuandoDonde(e.empiezaEn, e.lugar), 13, 600, Color(hex: 0xD3CEE2))
            }
            .padding(.horizontal, 20).padding(.vertical, 18)
            Line().stroke(rgba(255, 255, 255, 0.2), style: StrokeStyle(lineWidth: 2, dash: [6, 4])).frame(height: 2).padding(.horizontal, 20)
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    T("CÓDIGO", 11, 800, Color(hex: 0xC9C4DA), espaciado: 0.8)
                    T(e.codigo, 24, 800, C.lima, alto: 1, espaciado: 1, unRenglon: true)
                }
                Spacer()
                T((e.cantidad == 1 ? "1 entrada" : "\(e.cantidad) entradas") + (e.modo == "sena" ? " · señada" : ""), 13, 800, C.blancoCalido, unRenglon: true)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(rgba(255, 255, 255, 0.12)))
            }
            .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 20)
        }
        .gel(30, gelTicket)
        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
        .tocable { ir.qrEntrada(e.id) }
    }
}

private struct Line: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: r.midY))
        p.addLine(to: CGPoint(x: r.maxX, y: r.midY))
        return p
    }
}

/// "Tu entrada" con el QR para mostrar en la puerta (con el brillo al máximo).
struct QrEntrada: View {
    var entradaId: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var entrada: EntradaMia?
    @State private var cargando = true
    @State private var error: String?
    @State private var brilloAntes: CGFloat?

    var body: some View {
        Pantalla {
            VStack(spacing: 16) {
                PagosEncabezado(titulo: "Tu entrada", icono: I.cruz, tam: 26) { ir.atras() }
                if let e = entrada {
                    VStack(alignment: .leading, spacing: 4) {
                        T(e.titulo, 20, 700, alto: 1.15)
                        T(pagosCuandoDonde(e.empiezaEn, e.lugar), 13, 600, p.texto2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    PagosCodigoQr(texto: e.codigo)
                        .frame(width: 225, height: 225)
                        .padding(20)
                        .gel(30, Gel(fondo: .liso(C.blanco), sombras: [interna(0, 0, C.lima, spread: -3), externa(5, 0, C.limaBase),
                                                                      externa(30, 50, rgba(80, 60, 160, 0.45), spread: -24)]))
                        .padding(.top, 10)
                    T(e.codigo, 28, 800, alto: 1, espaciado: 1.5, unRenglon: true)
                    T("Mostralo en la puerta. Subí el brillo si está oscuro.", 14, 600, p.texto2, alto: 1.5, alinear: .center)
                    Spacer()
                } else {
                    Spacer()
                    if cargando { Puntitos() } else { MensajeCentro(titulo: "No encontramos tu entrada", cuerpo: error ?? "Fijate en Mis pases.") }
                    Spacer()
                }
            }
            .padding(.horizontal, 22).padding(.top, 8).padding(.bottom, 36)
        }
        .environment(\.paleta, .clara)
        .task {
            do { entrada = try await c.api.pases().entradas.first { $0.id == entradaId } } catch { self.error = mensajeDe(error) }
            cargando = false
        }
        .onAppear {
            #if os(iOS)
            brilloAntes = UIScreen.main.brightness
            UIScreen.main.brightness = 1
            #endif
        }
        .onDisappear {
            #if os(iOS)
            if let b = brilloAntes { UIScreen.main.brightness = b }
            #endif
        }
    }
}
