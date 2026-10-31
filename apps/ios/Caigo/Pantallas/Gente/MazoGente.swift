import SwiftUI
import Nucleo

/// Modos Gente y Citas del prototipo: mazo de personas arrastrables. Derecha (más de 100) = me
/// prendo / me interesa; izquierda = paso. Tocar arriba a los costados cambia de foto; tocar abajo
/// abre el perfil. Va embebido en Ahora, debajo del encabezado.
struct MazoGente: View {
    var modo: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p

    @State private var mazo: [Tarjeta]?
    @State private var error: String?
    @State private var recibidos: Recibidos?
    @State private var estado = FisicaMazo.Estado()
    @State private var dx: CGFloat = 0
    @State private var dy: CGFloat = 0
    @State private var sumados = 0
    @State private var invitando: Tarjeta?
    @State private var ocupado = false

    private var esCitas: Bool { modo == "citas" }
    private var plus: EstadoPlus? { c.plus }
    private var arriba: Tarjeta? { mazo.flatMap { estado.indice < $0.count ? $0[estado.indice] : nil } }
    private var siguiente: Tarjeta? { mazo.flatMap { estado.indice + 1 < $0.count ? $0[estado.indice + 1] : nil } }

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                filaArriba.padding(.horizontal, 20).padding(.top, 54)
                GeometryReader { g in mazoVista(g.size) }
                    .padding(.horizontal, 14).padding(.top, 10).padding(.bottom, 6)
                if arriba != nil { botones.frame(height: 76) } else { Color.clear.frame(height: 76) }
            }
            .padding(.bottom, altoTabBar + 14)
            if let t = invitando {
                GenteHojaInvitar(personaId: t.id, nombre: t.nombre, cerrar: { invitando = nil }) { plan in
                    Task { await reaccionar("prendo", mensaje: "Invitaste a \(t.nombre) a \(plan.titulo). Le llega como solicitud.") }
                }
            }
        }
        .animation(.easeOut(duration: 0.25), value: invitando?.id)
        .ocultarTabBar(invitando != nil, ir)
        .task(id: modo) { await cargar() }
    }

    private func cargar() async {
        estado = FisicaMazo.Estado()
        dx = 0; dy = 0
        mazo = nil
        error = nil
        async let m = c.api.mazo(modo)
        async let r = try? c.api.recibidos(modo)
        async let pl = c.recargarPlus()
        do { mazo = try await m.personas } catch { self.error = mensajeDe(error) }
        recibidos = await r
        _ = await pl
    }

    // MARK: Fila de arriba: quién te sumó y cuántas sumadas te quedan.

    private var filaArriba: some View {
        HStack {
            Button { ir.recibidos(modo: modo, pestana: 0) } label: {
                HStack(spacing: 8) {
                    HStack(spacing: -8) {
                        ForEach(Array((recibidos?.personas ?? []).prefix(3).enumerated()), id: \.offset) { _, r in
                            Avatar(nombre: "·", foto: r.foto, tam: 26, fondo: (C.oscuro2, C.oscuro2))
                                .blur(radius: recibidos?.bloqueada == true ? 3 : 0)
                        }
                    }
                    .padding(.leading, 8)
                    let n = recibidos?.personas.count ?? 0
                    T(esCitas ? "\(n) les interesás" : "\(n) te sumaron", 13, 800, C.carbon, unRenglon: true)
                }
                .padding(.leading, 4).padding(.trailing, 12).padding(.vertical, 4)
                .frame(minHeight: 34)
                .gel(17, Gels.neutra)
            }
            .buttonStyle(EstiloToque(baja: 2))
            Spacer()
            Button {
                if plus?.activo == true { c.avisos.ok("Ya sos Caigo Plus. Disfrutalo.") } else { ir.plus(motivo: "perfil") }
            } label: {
                HStack(spacing: 6) {
                    let e = plus
                    let sumadas = max(0, (e?.limite ?? 3) - (e?.sumadasHoy ?? 0))
                    Icono(I.mas, 13, C.violetaTexto, trazo: 3.4)
                    if esCitas {
                        // En Citas la pill suma los «me encanta» que quedan hoy, con la estrella del botón.
                        T(e?.activo == true ? "Plus" : "\(sumadas)", 12, 800, C.violetaTexto, unRenglon: true)
                        T("·", 12, 800, C.violetaTexto)
                        Icono(I.estrella, 13, .clear, trazo: 0, relleno: C.violetaTexto)
                        T("\(GenteEncantas.quedan(e?.encantasHoy ?? 0, e?.limiteEncantas ?? 1))", 12, 800, C.violetaTexto, unRenglon: true)
                    } else {
                        T(e?.activo == true ? "Plus · sin límite" : "\(sumadas) sumadas hoy", 12, 800, C.violetaTexto, unRenglon: true)
                    }
                }
                .padding(.horizontal, 11).padding(.vertical, 8)
                .gel(13, Gels.neutra)
            }
            .buttonStyle(EstiloToque(baja: 2))
        }
    }

    // MARK: El mazo

    @ViewBuilder
    private func mazoVista(_ tam: CGSize) -> some View {
        if mazo == nil {
            ZStack {
                if let error { T(error, 15, 500, p.texto2, alinear: .center).padding(24) } else { Puntitos() }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if let t = arriba {
            let k = FisicaMazo.fuerza(dx)
            ZStack {
                if let s = siguiente {
                    ZStack {
                        C.oscuro2
                        if let f = s.fotos.first { ImagenRed(url: f).overlay(Color.black.opacity(0.2)) }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 34, style: .continuous))
                    .scaleEffect(FisicaMazo.escalaSiguiente(dx))
                    .offset(y: 16 * (1 - k))
                    .opacity(FisicaMazo.alfaSiguiente(dx))
                    .id("sig-" + s.id)
                }
                GenteCarta(t: t, esCitas: esCitas, foto: estado.foto, tinte: dx) { abrirPerfil(t) }
                    .offset(x: dx, y: dy)
                    .rotationEffect(.degrees(FisicaMazo.rotacion(dx)))
                    .id(t.id)
            }
            .contentShape(Rectangle())
            .gesture(arrastre(t, tam))
        } else {
            MazoVacio(sumados: sumados) { Task { await cargar() } }
        }
    }

    private func arrastre(_ t: Tarjeta, _ tam: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                guard !ocupado else { return }
                if !estado.arrastrando { estado = FisicaMazo.reducir(estado, .empieza) }
                dx = v.translation.width
                dy = v.translation.height
                estado = FisicaMazo.reducir(estado, .mueve(dx, dy))
            }
            .onEnded { v in
                guard !ocupado else { return }
                let tx = v.translation.width, ty = v.translation.height
                if abs(tx) < 6 && abs(ty) < 6 {
                    // Toque: abajo abre el perfil, arriba cambia de foto.
                    estado.arrastrando = false
                    if v.startLocation.y > tam.height * 0.58 {
                        abrirPerfil(t)
                    } else {
                        estado = FisicaMazo.reducir(estado, .foto(delta: v.startLocation.x < tam.width / 2 ? -1 : 1, cantidad: t.fotos.count))
                    }
                    withAnimation(.easeOut(duration: 0.15)) { dx = 0; dy = 0 }
                    return
                }
                let sale = FisicaMazo.reducir(estado, .suelta)
                if sale.vuela != 0 {
                    estado.arrastrando = false
                    Task { await reaccionar(sale.vuela < 0 ? "paso" : (esCitas ? "interesa" : "prendo")) }
                } else {
                    estado = sale
                    volver()
                }
            }
    }

    private func abrirPerfil(_ t: Tarjeta) { ir.persona(t.id, desde: esCitas ? "citas" : "gente") }

    private func volver() { withAnimation(.easeOut(duration: 0.26)) { dx = 0; dy = 0 } }

    /// Reacciona y hace volar la carta. accion: prendo | interesa | encanta | paso.
    private func reaccionar(_ accion: String, mensaje: String? = nil) async {
        guard let t = arriba, !ocupado else { return }
        let lado = accion == "paso" ? -1 : 1
        if lado > 0, let e = plus {
            if !e.activo && e.sumadasHoy >= e.limite { volver(); ir.plus(motivo: "limit"); return }
            if accion == "encanta" && GenteEncantas.quedan(e.encantasHoy, e.limiteEncantas) == 0 {
                volver()
                c.avisos.error(GenteEncantas.aviso(plus: e.activo, limite: e.limiteEncantas))
                if !e.activo { ir.plus(motivo: "perfil") }
                return
            }
        }
        ocupado = true
        defer { ocupado = false }
        // La carta vuela ya; la API responde mientras tanto.
        estado = FisicaMazo.reducir(estado, .vuela(lado))
        withAnimation(.easeOut(duration: 0.26)) {
            dx = CGFloat(lado) * FisicaMazo.salida
            dy -= 40
        }
        do {
            let r = try await c.api.reaccionar(t.id, modo: modo, accion: accion)
            c.plus = r.plus
            if lado > 0 {
                sumados += 1
                c.avisos.ok(mensaje ?? genteMensaje(nombre: t.nombre, accion: accion, modo: modo, mutuo: r.mutuo))
            }
            var sin = Transaction()
            sin.disablesAnimations = true
            withTransaction(sin) {
                estado = FisicaMazo.reducir(estado, .siguiente)
                dx = 0; dy = 0
            }
        } catch let e as ErrorApi {
            estado.vuela = 0
            volver()
            switch e.codigo {
            case "limite": ir.plus(motivo: "limit")
            case "limite_encanta":
                c.avisos.error(e.detalle)
                if var pl = c.plus { pl.encantasHoy = pl.limiteEncantas; c.plus = pl }
                if c.plus?.activo != true { ir.plus(motivo: "perfil") }
            default: c.avisos.error(e.detalle)
            }
        } catch {
            estado.vuela = 0
            volver()
            c.avisos.error(error)
        }
    }

    // MARK: Botones: pasar, sumar / me interesa y el tercero (invitar a un plan / me encanta).

    private var botones: some View {
        let k = FisicaMazo.fuerza(dx)
        return HStack(spacing: 22) {
            GenteBotonRedondo(tam: 60, gel: genteGelPaso, etiqueta: "Paso", accion: { Task { await reaccionar("paso") } }) {
                Icono(I.cruz, 24, C.coral2, trazo: 3.2)
            }
            .scaleEffect(1 + 0.18 * (dx < 0 ? k : 0))
            if esCitas {
                GenteBotonRedondo(tam: 76, gel: genteGelCorazon, etiqueta: "Me interesa", accion: { Task { await reaccionar("interesa") } }) {
                    Icono(I.corazon, 34, .clear, trazo: 0, relleno: C.blanco)
                }
                .scaleEffect(1 + 0.15 * (dx > 0 ? k : 0))
                GenteBotonRedondo(tam: 60, gel: genteGelVioletaRedondo, etiqueta: "Me encanta", accion: { Task { await reaccionar("encanta") } }) {
                    Icono(I.estrella, 26, .clear, trazo: 0, relleno: C.lima)
                }
            } else {
                GenteBotonRedondo(tam: 76, gel: Gels.limaChico, etiqueta: "Me prendo", accion: { Task { await reaccionar("prendo") } }) {
                    Icono(I.mas, 32, C.carbon, trazo: 3.4)
                }
                .scaleEffect(1 + 0.15 * (dx > 0 ? k : 0))
                GenteBotonRedondo(tam: 60, gel: genteGelVioletaRedondo, etiqueta: "Invitar a un plan", accion: { invitando = arriba }) {
                    Icono(I.calendarioSimple, 24, C.blanco, trazo: 2.4)
                }
            }
        }
        .animation(.easeOut(duration: 0.15), value: k)
    }
}

/// La carta de una persona (foto con barras, nombre y edad, zona, chips).
struct GenteCarta: View {
    var t: Tarjeta
    var esCitas: Bool
    var foto: Int
    var tinte: CGFloat
    var abrirPerfil: () -> Void

    var body: some View {
        let forma = RoundedRectangle(cornerRadius: 34, style: .continuous)
        ZStack {
            C.oscuro2
            if let f = (foto < t.fotos.count ? t.fotos[foto] : t.fotos.first) { ImagenRed(url: f) }
            LinearGradient(stops: Fondo.paradas((0, rgba(32, 29, 43, 0.4)), (0.16, rgba(32, 29, 43, 0)), (0.6, rgba(32, 29, 43, 0)),
                                                 (0.78, rgba(32, 29, 43, 0.65)), (1, rgba(32, 29, 43, 0.95))), startPoint: .top, endPoint: .bottom)
            // Tinte lima (me prendo) o coral (paso) según el lado del arrastre.
            let k = FisicaMazo.fuerza(tinte)
            if k > 0 {
                let col = tinte > 0 ? C.lima : C.coral
                LinearGradient(stops: Fondo.paradas((0.35, col.opacity(0)), (1, col.opacity(0.4 * k))), startPoint: .top, endPoint: .bottom)
            }
            VStack(spacing: 0) {
                // Barras de fotos.
                HStack(spacing: 5) {
                    ForEach(0..<t.fotos.count, id: \.self) { i in
                        Capsule().fill(i == foto ? C.blanco : rgba(255, 255, 255, 0.38)).frame(height: 4)
                    }
                }
                .padding(.horizontal, 14).padding(.top, 12)
                HStack(alignment: .top) {
                    Sello(texto: esCitas ? "ME INTERESA" : "ME PRENDO", color: C.lima, grados: -12, alfa: FisicaMazo.alfaMeGusta(tinte))
                    Spacer()
                    Sello(texto: "PASO", color: C.coral, grados: 12, alfa: FisicaMazo.alfaPaso(tinte))
                }
                .padding(.horizontal, 22).padding(.top, 28)
                Spacer()
                datos.padding(.leading, 20).padding(.trailing, 16).padding(.bottom, 22)
            }
        }
        .clipShape(forma)
        .background(forma.fill(C.oscuro2).shadow(color: rgba(36, 33, 45, 0.65), radius: 22, y: 28).padding(18))
    }

    private var datos: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .bottom, spacing: 10) {
                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        T(t.nombre, 32, 800, C.blanco, alto: 1, espaciado: -0.8, unRenglon: true)
                        if let e = t.edad { T("\(e)", 28, 500, Color(hex: 0xE4E0EE), alto: 1, unRenglon: true) }
                        if t.verificada { Verificado() }
                    }
                    HStack(spacing: 5) {
                        Icono(I.pin, 14, Color(hex: 0xE4E0EE), trazo: 2.4)
                        let meta = [t.zona, esCitas ? t.trabajo : asistencia(t.asistencia)].filter { !$0.isEmpty }.joined(separator: " · ")
                        T(meta, 13, 600, Color(hex: 0xE4E0EE), unRenglon: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Button(action: abrirPerfil) {
                    Icono(I.arriba, 18, C.blanco, trazo: 2.8)
                        .frame(width: 40, height: 40)
                        .gel(20, .liso(rgba(255, 255, 255, 0.22)), [interna(1, 0, rgba(255, 255, 255, 0.35))])
                }
                .buttonStyle(EstiloToque())
                .accessibilityLabel("Ver perfil")
            }
            if esCitas {
                if let r = t.prompts.first?.r, !r.isEmpty {
                    GentePildora {
                        Icono(I.corazonChico, 13, .clear, trazo: 0, relleno: C.coral)
                        T(r, 12, 700, C.blanco, unRenglon: true)
                    }
                }
            } else {
                if let pr = t.proximos.first {
                    HStack(spacing: 6) {
                        Icono(I.calendarioSimple, 12, C.carbon, trazo: 3)
                        T("Va a \(pr.titulo)", 12, 800, C.carbon, unRenglon: true)
                    }
                    .padding(.horizontal, 11).padding(.vertical, 7)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(C.lima))
                }
                let tags = Array(NSOrderedSet(array: t.enComun + t.intereses).array.compactMap { $0 as? String }.prefix(4))
                if !tags.isEmpty {
                    HStack(spacing: 6) {
                        ForEach(tags, id: \.self) { g in
                            T(g, 12, 700, C.blanco, unRenglon: true)
                                .padding(.horizontal, 11).padding(.vertical, 6)
                                .gel(12, .liso(rgba(255, 255, 255, 0.16)), [interna(1, 0, rgba(255, 255, 255, 0.18))])
                        }
                    }
                }
            }
        }
    }
}

private struct Sello: View {
    var texto: String
    var color: Color
    var grados: Double
    var alfa: Double
    var body: some View {
        T(texto, 26, 800, color, alto: 1, unRenglon: true)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(rgba(32, 29, 43, 0.35)))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(color, lineWidth: 3))
            .rotationEffect(.degrees(grados))
            .opacity(alfa)
    }
}

/// "Ya viste a todos por hoy".
private struct MazoVacio: View {
    var sumados: Int
    var otraVez: () -> Void
    var body: some View {
        VStack(spacing: 14) {
            Image("CaigoIcono").resizable().frame(width: 120, height: 120)
            T("Ya viste a todos por hoy", 26, 700, C.carbon, alto: 1.1, alinear: .center)
            T("Sumaste \(sumados) a tu gente. ¿Y si armás algo con ellos?", 14, 400, C.texto2, alto: 1.5, alinear: .center)
            Button(action: otraVez) {
                T("Ver de nuevo", 14, 800, C.carbon)
                    .padding(.horizontal, 18).padding(.vertical, 12)
                    .gel(16, Gel(fondo: .vertical(C.blanco, Color(hex: 0xF3F0FA)),
                                 sombras: [interna(2, 0, C.blanco), interna(-3, 0, rgba(116, 86, 241, 0.1)), externa(3, 0, C.borde3)]))
            }
            .buttonStyle(EstiloToque())
        }
        .padding(30)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .gel(34, .vertical(Color(hex: 0xF3EFFF), Color(hex: 0xE9E2FF)))
    }
}
