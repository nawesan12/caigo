import SwiftUI
import Nucleo

private func requisitos(_ p: Plan) -> [String] {
    var r: [String] = []
    if p.soloVerificados { r.append("Solo verificados") }
    r.append(textoIngreso(p))
    if p.genero == "mujeres" { r.append("Solo mujeres") }
    if p.genero == "diversidades" { r.append("Solo diversidades") }
    if p.edadMin != nil || p.edadMax != nil { r.append("\(p.edadMin ?? 18) a \(p.edadMax ?? 99)") }
    if p.visibilidad == "mi_gente" { r.append("Para mi gente") }
    return r
}

/// El check-in abre media hora antes y cierra cuando termina el plan.
private func enVentanaDeCheckin(_ p: Plan) -> Bool {
    guard let t = Fecha.parse(p.empiezaEn) else { return false }
    let ahora = Date()
    return ahora > t.addingTimeInterval(-30 * 60) && ahora < t.addingTimeInterval(Double(p.duracionMin) * 60)
}

private func empezo(_ p: Plan) -> Bool {
    guard let t = Fecha.parse(p.empiezaEn) else { return false }
    return Date() > t
}

private func yaPaso(_ p: Plan) -> Bool {
    guard let t = Fecha.parse(p.empiezaEn) else { return false }
    return Date() > t.addingTimeInterval(Double(p.duracionMin) * 60)
}

/// 05 · Detalle del plan (y 14b en oscuro), con los estados del prototipo: esperando, adentro, plan de
/// lugar con entrada (pagar todo o señar), beneficio y Caigo Tap.
struct DetallePlan: View {
    var id: String?
    var slug: String?
    init(id: String) { self.id = id }
    init(slug: String) { self.slug = slug }

    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var pal
    @Environment(\.scenePhase) private var fase
    @State private var planId: String?
    @State private var plan: Plan?
    @State private var error: String?
    @State private var vuelta = 0
    @State private var oyente: UUID?

    var body: some View {
        Group {
            if let p = plan {
                ContenidoDetalle(p: p, recargar: { vuelta += 1 })
            } else {
                Pantalla {
                    if let error {
                        VStack(spacing: 16) {
                            T(error, 16, 600, pal.texto, alinear: .center)
                            Cta(texto: "Volver", alto: 56, tam: 16) { ir.atras() }
                        }
                        .padding(32)
                    } else {
                        Puntitos(color: pal.acento)
                    }
                }
            }
        }
        .ocultarBarraNavegacion()
        .task(id: vuelta) {
            do {
                var pid = planId ?? id
                if pid == nil, let slug {
                    pid = try await c.api.publico(slug: slug).id
                    planId = pid
                }
                guard let pid else { return }
                let punto = c.ubicador.conocida
                plan = try await c.api.plan(pid, lat: punto?.0, lng: punto?.1)
                error = nil
            } catch is CancellationError {
            } catch {
                if plan == nil { self.error = mensajeDe(error) }
            }
        }
        // Al volver de Mercado Pago, de la verificación o del chat, se refresca.
        .onAppear {
            if plan != nil { vuelta += 1 }
            // En vivo: si cambia el plan o sus solicitudes (te aceptan, alguien se suma), se recarga.
            if oyente == nil {
                oyente = c.tiempoReal.escuchar { e in
                    if e.tipo == "plan", e.planId == (planId ?? id) { vuelta += 1 }
                }
            }
        }
        .onDisappear {
            if let o = oyente { c.tiempoReal.dejar(o) }
            oyente = nil
        }
        .onChange(of: fase) { _, f in if f == .active && plan != nil { vuelta += 1 } }
    }
}

private struct ContenidoDetalle: View {
    var p: Plan
    var recargar: () -> Void
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var pal
    @State private var grupo = 0
    @State private var cantidad = 2
    @State private var mensaje = ""
    @State private var enviando = false
    @State private var opciones = false
    @State private var reportando = false
    @State private var sena = false
    @State private var preguntarGenero = false
    @State private var preguntarDiversidad = false
    @State private var cancelandoPlan = false
    @State private var cancelandoEntrada = false

    private var deLugar: Bool { esDeLugar(p) }
    private var link: String { "https://caigo.com.ar/p/\(p.slug)" }

    var body: some View {
        ZStack(alignment: .top) {
            (pal.oscuro ? C.grafito : C.blancoCalido).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 0) {
                    ZStack {
                        Foto(url: fotoDe(p), tinte: colorDeCategoria(p.categoria))
                        LinearGradient(stops: Fondo.paradas((0, rgba(32, 29, 43, 0.45)), (0.35, rgba(32, 29, 43, 0))), startPoint: .top, endPoint: .bottom)
                    }
                    .frame(height: 300)
                    .clipped()
                    // La hoja de contenido sube 30 sobre la foto (top:270 en el mockup).
                    VStack(alignment: .leading, spacing: 16) {
                        chips
                        T(p.titulo, 30, 700, pal.texto, alto: 1.05, espaciado: -0.8)
                        PlanesContador(p: p)
                        organizador
                        info
                        if !p.descripcion.isEmpty { T(p.descripcion, 15, 400, pal.texto2, alto: 1.5) }
                        if p.lugar != nil { tarjetaEntrada }
                        if !p.beneficio.isEmpty { PlanesBeneficio(texto: p.beneficio, desbloqueado: false) }
                        if p.soloVerificados {
                            HStack(spacing: 10) {
                                Icono(I.escudoCheck, 20, pal.oscuro ? C.lima : C.violetaTexto, trazo: 2.2)
                                T("Solo gente verificada con DNI", 13, 700, pal.texto)
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 14).padding(.vertical, 12)
                            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(pal.superficie))
                        }
                        PlanesFlujo(espacio: 6, renglon: 6) {
                            T("Requisitos", 13, 700, pal.texto2).padding(.vertical, 6)
                            ForEach(requisitos(p), id: \.self) { r in
                                T(r, 12, 700, pal.texto, unRenglon: true)
                                    .padding(.horizontal, 10).padding(.vertical, 6)
                                    .overlay(RoundedRectangle(cornerRadius: 11, style: .continuous).strokeBorder(pal.oscuro ? Color(hex: 0x3A3550) : C.pista, lineWidth: 1.5))
                            }
                        }
                    }
                    .padding(EdgeInsets(top: 22, leading: 20, bottom: 30, trailing: 20))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background {
                        UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous)
                            .fill(pal.oscuro ? C.grafito : C.blancoCalido)
                    }
                    .offset(y: -30)
                    .padding(.bottom, -30)
                }
            }
            .ignoresSafeArea(edges: .top)
            .scrollDismissesKeyboard(.interactively)
            .safeAreaInset(edge: .bottom, spacing: 0) { barraAbajo }

            // Volver, compartir y más.
            HStack {
                let g = gelSobreFoto
                BotonIcono(d: I.atras, gel: g, color: pal.texto) { ir.atras() }
                    .accessibilityLabel("Volver")
                Spacer()
                HStack(spacing: 8) {
                    BotonIcono(d: I.compartir, iconoTam: 20, gel: g, color: pal.texto) {
                        Sistema.compartir("\(p.titulo) · \(Cupo.badge(p.faltan)). Sumate en Caigo: \(link)")
                    }
                    .accessibilityLabel("Compartir")
                    BotonIcono(d: I.puntos, iconoTam: 20, trazo: 0, gel: g, color: .clear, relleno: pal.texto) { withAnimation { opciones = true } }
                        .accessibilityLabel("Más opciones")
                }
            }
            .padding(.horizontal, 16).padding(.top, 8)

            if opciones { HojaOpciones(cerrar: { withAnimation { opciones = false } }, filas: filasOpciones) }
            if reportando {
                HojaReporte(cerrar: { withAnimation { reportando = false } }) { motivo in
                    Task {
                        do {
                            try await c.api.reportar(Reporte(planId: p.id, motivo: motivo))
                            c.avisos.ok("Gracias. Lo revisa una persona del equipo en menos de 24 h.")
                        } catch { c.avisos.error(error) }
                    }
                }
            }
            HojaConfirmar(abierta: cancelandoPlan, titulo: "¿Cancelar el plan?",
                          cuerpo: cuerpoCancelarPlan, accion: "Cancelar el plan", cerrar: { cancelandoPlan = false }) {
                try await c.api.cancelarPlan(p.id)
                c.avisos.ok("Cancelaste el plan. Le avisamos a la gente.")
                recargar()
            }
            HojaConfirmar(abierta: cancelandoEntrada, titulo: p.cancelacion == "strict" ? "Tu entrada no tiene reembolso" : "¿Cancelar tu entrada?",
                          cuerpo: planesPolitica(p.cancelacion), accion: p.cancelacion == "strict" ? nil : "Cancelar mi entrada", cerrar: { cancelandoEntrada = false }) {
                try await cancelarMiEntrada()
            }
            HojaGenero(abierta: preguntarGenero, cerrar: { preguntarGenero = false }) { sumarme() }
            HojaDiversidad(abierta: preguntarDiversidad, cerrar: { preguntarDiversidad = false }) { sumarme(diversidad: true) }
        }
        .onAppear {
            sena = UserDefaults.standard.string(forKey: claveModoCheckout(p.id)) == "sena" && p.cobro == "sena"
            // 01b: con cuántos se suma por defecto.
            let n = planesCantidadInicial(c.somos, p)
            if n > 1 { grupo = 1; cantidad = max(2, n) }
        }
    }

    private var cuerpoCancelarPlan: String {
        let base = "Les avisamos a quienes se sumaron o pidieron sumarse. No tiene vuelta."
        return p.lugar != nil ? base + " A quienes pagaron entrada les devolvemos todo, cargo incluido." : base
    }

    /// `diversidad`: lo confirmó en la HojaDiversidad (vale sólo para este pedido).
    private func sumarme(diversidad: Bool = false) {
        let n = grupo == 1 && p.aceptaGrupos && p.faltan > 1 ? cantidad : 1
        Task {
            enviando = true
            if await planesSumarse(c, ir, p, cantidad: n, mensaje: mensaje.trimmingCharacters(in: .whitespaces), diversidad: diversidad,
                                   faltaGenero: { preguntarGenero = true },
                                   confirmarDiversidad: { preguntarDiversidad = true }) != nil { recargar() }
            enviando = false
        }
    }

    /// DELETE /v1/entradas/{id}: devuelve según la política (flex, mod) o 409 `sin_reembolso`.
    private func cancelarMiEntrada() async throws {
        guard let e = try await c.api.pases().entradas.first(where: { $0.planId == p.id }) else {
            throw ErrorApi(estado: 0, problema: Problema(detail: "No encontramos tu entrada. Probá en un ratito."))
        }
        do {
            let r = try await c.api.cancelarEntrada(e.id)
            c.avisos.ok(r.reembolsoCentavos > 0 ? "Listo. Te devolvemos \(Plata.pesos(r.reembolsoCentavos)) por Mercado Pago."
                                                 : "Listo, cancelaste tu entrada.")
            recargar()
        } catch let x as ErrorApi where x.codigo == "sin_reembolso" {
            throw ErrorApi(estado: x.estado, problema: Problema(detail: x.problema.detail.isEmpty
                ? "El lugar no devuelve entradas de este plan." : x.problema.detail))
        }
    }

    /// Botón claro sobre la foto del detalle (#FAF9F6 en claro; gradiente grafito en oscuro).
    private var gelSobreFoto: Gel {
        pal.oscuro ? Gel(fondo: .vertical(Color(hex: 0x36314A), C.grafito), sombras: [interna(1, 0, rgba(255, 255, 255, 0.12))])
            : Gel(fondo: .liso(C.blancoCalido))
    }

    private var chips: some View {
        PlanesFlujo(espacio: 6, renglon: 6) {
            let claro = pal.oscuro ? Gel(fondo: .liso(Color(hex: 0x2E2A3D))) : Gels.chipClaro
            ChipFoto(texto: Categorias.nombre(p.categoria), oscuro: false, fondo: claro, color: pal.texto)
            if let pronto = Fecha.enCuanto(p.empiezaEn) {
                ChipFoto(texto: pronto, oscuro: false, fondo: Gels.coralBadge, color: C.carbon)
            } else {
                ChipFoto(texto: Fecha.cuando(p.empiezaEn), oscuro: true, fondo: pal.oscuro ? Gels.coralBadge : Gels.oscuroSel,
                         color: pal.oscuro ? C.carbon : C.blancoCalido)
            }
            if !p.nivel.isEmpty { ChipFoto(texto: p.nivel, oscuro: false, fondo: claro, color: pal.texto) }
            if p.destacado { ChipDestacado() }
        }
    }

    private var organizador: some View {
        let o = p.organizador
        let nombre = p.lugar?.nombre ?? o.nombre
        let iniciales = nombre.split(separator: " ").prefix(2).map { String($0.prefix(1)) }.joined().uppercased()
        return HStack(spacing: 12) {
            Avatar(nombre: nombre, foto: p.lugar?.foto ?? o.foto, tam: 44, borde: 0,
                   texto: String(iniciales.prefix(p.lugar != nil ? 2 : 1)), fondo: (C.violeta, C.blanco), tamTexto: 18)
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 6) {
                    T("Organiza \(nombre)", 15, 700, pal.texto, unRenglon: true)
                    if o.verificada { Verificado(tam: 16, color: pal.oscuro ? C.lima : C.violeta, tilde: pal.oscuro ? C.carbon : C.blanco) }
                }
                T(p.lugar != nil ? "Lugar aliado · verificado por Caigo"
                    : (o.asistencia.de > 0 ? "Fue a \(o.asistencia.fue) de \(o.asistencia.de) planes · \(asistencia(o.asistencia))" : "Nuevo en Caigo"),
                  13, 400, pal.texto2)
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
        .onTapGesture { if !o.id.isEmpty && !p.esMio && p.lugar == nil { ir.persona(o.id, desde: "plan") } }
    }

    private var info: some View {
        let acento = pal.oscuro ? C.lima : C.violeta
        let sep = pal.oscuro ? Color(hex: 0x3A3550) : C.lavanda
        let gel = pal.oscuro
            ? Gel(fondo: .vertical(Color(hex: 0x2F2A40), C.oscuro5), sombras: [interna(1, 0, rgba(255, 255, 255, 0.06))])
            : Gel(fondo: .liso(C.blanco), sombras: [interna(1, 0, C.blanco), externa(2, 0, C.borde2), externa(12, 24, rgba(80, 60, 160, 0.22), spread: -12)])
        let zonaBase = p.zona.isEmpty ? p.ciudad : p.zona
        let zona = [zonaBase.isEmpty ? "" : (zonaBase.hasPrefix("Zona") ? zonaBase : "Zona \(zonaBase)"), distancia(p.distanciaM)]
            .filter { !$0.isEmpty }.joined(separator: " · ")
        let dir = p.direccion?.texto ?? ""
        let detalleDir = !dir.isEmpty ? [p.lugarNombre, dir].filter { !$0.isEmpty }.joined(separator: " · ")
            : (p.ocultarDireccion ? "La dirección exacta te llega cuando te aceptan" : p.lugarNombre)
        return VStack(spacing: 0) {
            fila(I.relojCirculo, acento) { T(Fecha.rango(p.empiezaEn, duracionMin: p.duracionMin), 14, 600, pal.texto) }
            Rectangle().fill(sep).frame(height: 1)
            fila(I.pin, acento) {
                VStack(alignment: .leading, spacing: 0) {
                    T(zona, 14, 600, pal.texto)
                    if !detalleDir.isEmpty { T(detalleDir, 12, 400, pal.texto2) }
                }
            }
            Rectangle().fill(sep).frame(height: 1)
            HStack(spacing: 12) {
                T("$", 17, 800, acento).frame(width: 20, height: 20)
                T(textoCosto(p), 14, 600, pal.texto)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 14).padding(.vertical, 12)
        }
        .gel(20, gel)
    }

    private func fila<V: View>(_ icono: String, _ color: Color, @ViewBuilder _ contenido: () -> V) -> some View {
        HStack(spacing: 12) {
            Icono(icono, 20, color, trazo: 2)
            contenido()
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
    }

    /// Tarjeta "ENTRADA $8.000 + $600 de servicio" del plan de lugar aliado (prototipo).
    @ViewBuilder
    private var tarjetaEntrada: some View {
        if let precio = p.precioCentavos {
            let cargo = p.cargoCentavos > 0 ? p.cargoCentavos : Plata.cargo(precio)
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    T(p.cobro == "sena" ? "SEÑA \(p.senaPct ?? 0)%" : "ENTRADA", 11, 800, C.texto2, espaciado: 0.8)
                    T(Plata.pesos(precio), 26, 800, C.carbon, alto: 1)
                    T("+ \(Plata.pesos(cargo)) de servicio" + (p.incluye.isEmpty ? "" : " · \(p.incluye)"), 12, 600, C.texto2)
                }
                Spacer(minLength: 0)
                T("Lugar aliado", 12, 800, C.carbon, unRenglon: true)
                    .padding(.horizontal, 11).padding(.vertical, 7)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(C.lima))
            }
            .padding(16)
            .gel(22, Gels.neutra)
        }
    }

    // MARK: Barra de abajo: según el estado de quien mira.

    private var barraAbajo: some View {
        VStack(spacing: 10) {
            estadoAbajo
        }
        .padding(EdgeInsets(top: 12, leading: 20, bottom: 14, trailing: 20))
        .frame(maxWidth: .infinity)
        .background {
            (pal.oscuro ? rgba(26, 24, 35, 0.94) : C.blanco)
                .overlay(alignment: .top) { Rectangle().fill(pal.oscuro ? Color(hex: 0x332E45) : Color(hex: 0xECE8F2)).frame(height: 1) }
                .ignoresSafeArea(edges: .bottom)
        }
    }

    @ViewBuilder
    private var estadoAbajo: some View {
        if p.cancelado {
            T("Este plan se canceló.", 15, 700, C.coral2, alinear: .center)
        } else if p.esMio {
            if yaPaso(p) {
                Cta(texto: "¿Repetimos?", gel: Gels.violeta, alto: 58, color: C.blanco) { ir.repetimos(p.id) }
            } else {
                HStack(spacing: 10) {
                    Cta(texto: p.pendientes > 0 ? "Solicitudes · \(p.pendientes)" : "Solicitudes", gel: Gels.blanco, alto: 52, tam: 15) { ir.solicitudes(p.id) }
                    Cta(texto: "QR de check-in", gel: Gels.blanco, alto: 52, tam: 15, icono: I.qr, iconoTrazo: 2.2, iconoTam: 18) { ir.qrOrganizador(p.id) }
                }
                Cta(texto: "Ir al chat del plan", gel: Gels.violeta, alto: 58, tam: 18, color: C.blanco) { ir.chat(p.id) }
            }
        } else if p.miEstado == "pendiente" {
            T("Le avisamos a \(p.organizador.nombre). Esperá un toque…", 15, 800, C.violetaTexto, alinear: .center)
                .padding(.horizontal, 14)
                .frame(maxWidth: .infinity).frame(height: 62)
                .gel(22, .liso(C.tinte1), [interna(3, 6, rgba(116, 86, 241, 0.2))])
        } else if p.adentro {
            if yaPaso(p) {
                Cta(texto: "¿Repetimos?", gel: Gels.violeta, alto: 58, color: C.blanco) { ir.repetimos(p.id) }
            } else {
                T(deLugar ? "Tenés tu entrada. Cuando llegues, hacé check-in." : "Estás adentro. La dirección ya está en el chat.",
                  14, 800, pal.oscuro ? C.lima : C.limaTexto, alinear: .center)
                if deLugar {
                    Cta(texto: "Apoyar el celu en la placa", alto: 56, icono: I.nfc, iconoTrazo: 2.6, iconoTam: 20) { ir.tap(plan: p.id) }
                    Cta(texto: "Mostrar QR de entrada", gel: Gels.blanco, alto: 52, tam: 16, icono: I.qr, iconoTrazo: 2.2, iconoTam: 20) {
                        // Plan gratis de un lugar: no hay entrada; el QR personal sale de /v1/planes/{id}/qr.
                        if !p.conEntrada { ir.qrOrganizador(p.id); return }
                        Task {
                            do {
                                if let e = try await c.api.pases().entradas.first(where: { $0.planId == p.id }) {
                                    ir.qrEntrada(e.id)
                                } else {
                                    c.avisos.error("Todavía no está tu entrada. Probá en un ratito.")
                                }
                            } catch { c.avisos.error(error) }
                        }
                    }
                } else if enVentanaDeCheckin(p) {
                    Cta(texto: "Llegué", alto: 56, icono: I.check, iconoTrazo: 2.6, iconoTam: 20) { ir.checkin(p.id) }
                }
                Cta(texto: "Ir al chat del plan", gel: Gels.violeta, alto: 62, radio: 22, tam: 18, color: C.blanco) { ir.chat(p.id) }
            }
        } else if Cupo.completo(p.faltan) {
            T("Completo", 17, 800, C.carbon).frame(maxWidth: .infinity).frame(height: 58).gel(20, Gels.limaBadge)
        } else if yaPaso(p) {
            T("Este plan ya pasó.", 15, 700, pal.texto2, alinear: .center)
        } else {
            sumarse
        }
    }

    @ViewBuilder
    private var sumarse: some View {
        let permiteGrupo = p.aceptaGrupos && p.faltan > 1
        let n = grupo == 1 && permiteGrupo ? cantidad : 1
        if permiteGrupo {
            Segmentado(opciones: ["Voy solo", grupo == 1 ? "Somos \(cantidad)" : "Somos 2+"], seleccion: $grupo, radio: 15, radioOpcion: 12, alto: 44)
            if grupo == 1 {
                HStack(spacing: 10) {
                    BotonIcono(d: "M6 12h12", tam: 36, radio: 12, iconoTam: 16, trazo: 3) { cantidad = max(2, cantidad - 1) }
                        .accessibilityLabel("Uno menos")
                    T("Somos \(cantidad)", 16, 800, pal.texto)
                    BotonIcono(d: I.mas, tam: 36, radio: 12, iconoTam: 16, trazo: 3) { cantidad = min(min(10, p.faltan), cantidad + 1) }
                        .accessibilityLabel("Uno más")
                }
            }
        }
        if deLugar, p.cobro == "sena", let pct = p.senaPct, let precio = p.precioCentavos {
            // Prototipo: "Pagar todo" o "Señar N %"; el modo lo manda el checkout a la API.
            let d = Plata.desglose(precio, cobro: sena ? "sena" : "entrada", senaPct: pct, cantidad: n,
                                   cargoPorPersona: p.cargoCentavos > 0 ? p.cargoCentavos : nil)
            Segmentado(opciones: ["Pagar todo", "Señar \(pct)%"], seleccion: Binding(get: { sena ? 1 : 0 }, set: { sena = $0 == 1 }),
                       radio: 15, radioOpcion: 12, alto: 44)
            T(sena ? "Reservás con \(Plata.pesos(d.alLugar)); el resto en el lugar" : "Entrada completa ahora",
              13, 600, pal.texto2, alinear: .center)
        }
        if !deLugar && p.ingreso != "libre" { campoMensaje }
        Cta(texto: deLugar ? (n > 1 ? "Comprar \(n) entradas" : "Comprar entrada") : (n > 1 ? "Sumarnos" : "Sumarme"),
            alto: 58, color: C.carbon, icono: I.mas, iconoTrazo: 4, iconoTam: 18, cargando: enviando) {
            if deLugar {
                UserDefaults.standard.set(p.cobro == "sena" && sena ? "sena" : "entrada", forKey: claveModoCheckout(p.id))
                ir.checkout(p.id, cantidad: n)
                return
            }
            sumarme()
        }
    }

    /// Campo de mensaje opcional para el pedido (con las piezas del input del chat 08).
    private var campoMensaje: some View {
        PlanesCampo(texto: Binding(get: { mensaje }, set: { mensaje = String($0.prefix(200)) }),
                    pista: "Escribí algo para quien organiza (opcional)", colorPista: pal.texto3)
            .padding(.horizontal, 16)
            .frame(height: 48)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(pal.oscuro ? Color(hex: 0x2E2A3D) : Color(hex: 0xF2F0EC)))
    }

    private var filasOpciones: [Opcion] {
        var f: [Opcion] = [Opcion(texto: "Compartir link") { Sistema.compartir(link) }]
        let vivo = !p.cancelado && !yaPaso(p)
        if p.esMio && vivo && !p.destacado {
            f.append(Opcion(texto: "Destacar plan") {
                Task {
                    do {
                        try await c.api.destacar(p.id)
                        c.avisos.ok("Listo. Tu plan sale destacado en Ahora y Explorar.")
                        recargar()
                    } catch let e as ErrorApi {
                        // Sin Plus: el paywall. Ya usado esta semana: el detalle dice desde cuándo.
                        if e.codigo == "plus" { ir.plus(motivo: "destacar") } else { c.avisos.error(e.detalle) }
                    } catch { c.avisos.error(error) }
                }
            })
        }
        if p.esMio && vivo && !empezo(p) && p.lugar == nil {
            f.append(Opcion(texto: "Editar el plan") { ir.editarPlan(p.id) })
        }
        if p.esMio && vivo {
            f.append(Opcion(texto: p.somos > 1 ? "Gente del plan" : "Solicitudes") { ir.solicitudes(p.id) })
            f.append(Opcion(texto: "Cancelar el plan") { withAnimation { cancelandoPlan = true } })
        }
        if !p.esMio && p.adentro && deLugar && vivo {
            f.append(Opcion(texto: "Cancelar mi entrada") { withAnimation { cancelandoEntrada = true } })
        }
        if !p.esMio && (p.adentro || p.miEstado == "pendiente") && !deLugar {
            f.append(Opcion(texto: "Bajarme del plan") {
                Task {
                    do {
                        try await c.api.salir(p.id)
                        c.avisos.ok("Listo, te bajaste del plan.")
                        recargar()
                    } catch { c.avisos.error(error) }
                }
            })
        }
        if !p.esMio { f.append(Opcion(texto: "Reportar") { withAnimation { reportando = true } }) }
        return f
    }
}

/// Contador protagonista: "Faltan 2 / Somos 8 de 10" con avatares y barra segmentada.
struct PlanesContador: View {
    var p: Plan
    @Environment(\.paleta) private var pal

    var body: some View {
        let completo = Cupo.completo(p.faltan)
        let gel = pal.oscuro
            ? Gel(fondo: .radial(cx: 0, cy: 0, rx: 1.2, ry: 1, Fondo.paradas((0, Color(hex: 0x3F3860)), (0.65, C.oscuro5))),
                  sombras: [interna(1, 0, rgba(255, 255, 255, 0.08))], grano: true)
            : Gels.oscuroSel
        VStack(alignment: .leading, spacing: 14) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    T(Cupo.badge(p.faltan), 46, 800, completo ? C.lima : C.coral, alto: 1, espaciado: -1.5, unRenglon: true)
                    T(Cupo.somos(p.somos, p.total), 14, 600, C.blancoCalido)
                }
                Spacer(minLength: 8)
                if p.adentro || p.lugar != nil {
                    T(textoIngreso(p), 12, 800, C.blancoCalido, unRenglon: true)
                        .padding(.horizontal, 11).padding(.vertical, 7)
                        .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color(hex: 0x3A3550)))
                } else if !p.van.isEmpty {
                    Avatares(personas: p.van.map { ($0.nombre, $0.foto) }, total: p.somos, max: 4, tam: 32)
                }
            }
            BarraCupo(somos: p.somos, total: p.total, alto: 8)
        }
        .padding(16)
        .gel(24, gel)
        .overlay {
            if pal.oscuro { RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(Color(hex: 0x3A3550), lineWidth: 1) }
        }
    }
}
