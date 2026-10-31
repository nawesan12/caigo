import SwiftUI
import Nucleo

/// Un renglón del chat, sea del plan o privado.
struct MiosLinea: Identifiable, Equatable {
    var id: Int64
    var texto: String
    var mio: Bool
    var sistema: Bool
    var autor: String
    var foto: String
}

private extension Mensaje {
    func linea(_ yo: String) -> MiosLinea {
        MiosLinea(id: id, texto: texto, mio: autor?.id == yo, sistema: autor == nil, autor: autor?.nombre ?? "", foto: autor?.foto ?? "")
    }
}

private extension Privado {
    func linea(_ yo: String, _ nombre: String) -> MiosLinea {
        MiosLinea(id: id, texto: texto, mio: deId == yo, sistema: false, autor: deId == yo ? "" : nombre, foto: "")
    }
}

/// Lo que vive mientras el chat está abierto: renglones, quién escribe y la suscripción al WebSocket.
@MainActor
@Observable
final class MiosHilo {
    var lineas: [MiosLinea] = []
    var cargado = false
    var error: String?
    var escribe: String?
    @ObservationIgnored var oyente: UUID?
    @ObservationIgnored private var apagarEscribe: Task<Void, Never>?

    func agregar(_ l: MiosLinea) {
        if !lineas.contains(where: { $0.id == l.id }) { lineas.append(l) }
    }

    /// "Lu está escribiendo…" se apaga a los 4 s.
    func marcarEscribe(_ nombre: String) {
        escribe = nombre
        apagarEscribe?.cancel()
        apagarEscribe = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 4_000_000_000)
            if !Task.isCancelled { self?.escribe = nil }
        }
    }
}

/// 08 · Chat del plan, en tiempo real por el WebSocket.
struct Chat: View {
    var planId: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var plan: Plan?
    @State private var hilo = MiosHilo()
    @State private var opciones = false
    @State private var reportando = false
    @State private var confirmando = false

    var body: some View {
        Pantalla {
            ZStack(alignment: .bottom) {
                chat
                hojas
            }
        }
        .task(id: planId) {
            plan = try? await c.api.plan(planId)
        }
        .task(id: planId) {
            do {
                hilo.lineas = try await c.api.mensajes(planId).mensajes.map { $0.linea(c.usuarioId) }
            } catch {
                hilo.error = mensajeDe(error)
            }
            hilo.cargado = true
        }
        .onAppear { escuchar() }
        .onDisappear {
            if let o = hilo.oyente { c.tiempoReal.dejar(o) }
            hilo.oyente = nil
        }
    }

    /// Menú del chat: reportar el plan y salir (el organizador no sale: cancela el plan).
    @ViewBuilder
    private var hojas: some View {
        let organizo = plan?.esMio == true
        if opciones {
            HojaOpciones(cerrar: { opciones = false }, filas: [
                Opcion(texto: "Reportar el plan") { reportando = true },
                Opcion(texto: organizo ? "Cancelar el plan" : "Salir del plan") { confirmando = true },
            ])
        }
        if reportando {
            HojaReporte(cerrar: { reportando = false }) { motivo in
                Task {
                    do {
                        try await c.api.reportar(Reporte(planId: planId, motivo: motivo))
                        c.avisos.ok("Gracias. Lo revisa una persona del equipo en menos de 24 h.")
                    } catch {
                        c.avisos.error(error)
                    }
                }
            }
        }
        if confirmando {
            HojaOpciones(cerrar: { confirmando = false }, filas: [
                Opcion(texto: organizo ? "Cancelar el plan y avisarles" : "Salir del plan") { salir(organizo) },
                Opcion(texto: organizo ? "No, lo mantengo" : "Me quedo") {},
            ])
        }
    }

    private func salir(_ organizo: Bool) {
        Task {
            do {
                if organizo {
                    try await c.api.cancelarPlan(planId)
                    c.avisos.ok("Cancelaste el plan. Les avisamos a todos.")
                } else {
                    try await c.api.salir(planId)
                    c.avisos.ok("Saliste del plan.")
                }
                ir.principal(.mis)
            } catch {
                c.avisos.error(error)
            }
        }
    }

    private var chat: some View {
            VStack(spacing: 0) {
                encabezado.padding(.horizontal, 16).padding(.top, 6)
                if let pl = plan { tarjetaFijada(pl).padding(.horizontal, 16).padding(.top, 12) }
                MiosVistaHilo(hilo: hilo)
                MiosEntrada(alTipear: { c.tiempoReal.escribiendo(planId: planId) }) { t in
                    do {
                        let m = try await c.api.escribir(planId, t)
                        hilo.agregar(m.linea(c.usuarioId))
                        return true
                    } catch {
                        c.avisos.error(error)
                        return false
                    }
                }
            }
    }

    private func escuchar() {
        c.tiempoReal.encender()
        let yo = c.usuarioId
        let h = hilo
        let id = planId
        hilo.oyente = c.tiempoReal.escuchar { ev in
            MainActor.assumeIsolated {
                if ev.tipo == "mensaje", let m = ev.mensaje, m.planId == id {
                    h.agregar(m.linea(yo))
                    if let n = m.autor?.nombre, miosPrimer(n) == h.escribe { h.escribe = nil }
                } else if ev.tipo == "escribiendo", ev.planId == id, let q = ev.quien, q.id != yo {
                    h.marcarEscribe(miosPrimer(q.nombre))
                }
            }
        }
    }

    private var encabezado: some View {
        HStack(spacing: 12) {
            BotonIcono(d: I.atras, gel: Gels.chip(p), color: p.texto) { ir.atras() }
            VStack(alignment: .leading, spacing: 0) {
                T(plan?.titulo ?? "", 19, 700, alto: 1.15, unRenglon: true)
                if let pl = plan {
                    HStack(spacing: 6) {
                        Circle().fill(Color(hex: 0x8DC63F)).frame(width: 8, height: 8)
                        T("\(pl.somos) de \(pl.total) · " + (Cupo.completo(pl.faltan) ? "plan completo" : Cupo.badge(pl.faltan).lowercased()),
                          12, 600, p.texto2, unRenglon: true)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let pl = plan {
                let gente = ([pl.organizador] + pl.van).reduce(into: [PersonaCorta]()) { a, g in
                    if !a.contains(where: { $0.id == g.id }) && g.id != c.usuarioId { a.append(g) }
                }
                HStack(spacing: -8) {
                    ForEach(Array(gente.prefix(3).enumerated()), id: \.offset) { i, g in
                        Avatar(nombre: g.nombre, foto: g.foto, tam: 30, colorBorde: p.fondo, tamTexto: 11).zIndex(Double(5 - i))
                    }
                }
                .padding(.leading, 8)
            }
            BotonIcono(d: I.puntos, iconoTam: 20, trazo: 0, gel: Gels.chip(p), color: .clear, relleno: p.texto) { opciones = true }
                .accessibilityLabel("Más opciones")
        }
    }

    private func tarjetaFijada(_ pl: Plan) -> some View {
        let lugar = pl.lugar?.nombre ?? (pl.lugarNombre.isEmpty ? pl.zona : pl.lugarNombre)
        let desde = Fecha.parse(pl.empiezaEn).map { "check-in desde \(Fecha.hora($0.addingTimeInterval(-30 * 60)))" }
        let dir = pl.direccion?.texto.isEmpty == false ? pl.direccion?.texto : (pl.zona.isEmpty ? nil : pl.zona)
        return HStack(spacing: 12) {
            Foto(url: pl.foto).frame(width: 52, height: 52).clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                T([Fecha.cuando(pl.empiezaEn), lugar].filter { !$0.isEmpty }.joined(separator: " · "), 14, 700, unRenglon: true)
                T([dir, desde].compactMap { $0 }.joined(separator: " · "), 12, 400, p.texto2, unRenglon: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            T("Ver plan", 13, 700, p.oscuro ? C.lima : C.violetaTexto).tocable { ir.plan(planId) }
        }
        .padding(12)
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(p.superficie))
    }
}

/// Los mensajes (abajo de todo el último) y "X está escribiendo…".
struct MiosVistaHilo: View {
    var hilo: MiosHilo
    var foto: String?
    @Environment(\.paleta) private var p

    var body: some View {
        ZStack {
            if !hilo.cargado {
                Puntitos(color: p.acento)
            } else if let e = hilo.error, hilo.lineas.isEmpty {
                T(e, 15, 500, p.texto2, alto: 1.5, alinear: .center).padding(28)
            } else {
                ScrollViewReader { lector in
                    ScrollView(showsIndicators: false) {
                        LazyVStack(spacing: 10) {
                            ForEach(hilo.lineas) { l in
                                MiosBurbuja(l: foto.map { f in var x = l; x.foto = f; return x } ?? l).id(l.id)
                            }
                            if let e = hilo.escribe {
                                T("\(e) está escribiendo…", 12, 400, p.texto2)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.leading, 36)
                                    .id(Int64(-1))
                            }
                        }
                        .padding(16)
                    }
                    .defaultScrollAnchor(.bottom)
                    .scrollDismissesKeyboard(.interactively)
                    .onChange(of: hilo.lineas.count) {
                        withAnimation { lector.scrollTo(hilo.lineas.last?.id, anchor: .bottom) }
                    }
                    .onChange(of: hilo.escribe) {
                        if hilo.escribe != nil { withAnimation { lector.scrollTo(Int64(-1), anchor: .bottom) } }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct MiosBurbuja: View {
    var l: MiosLinea
    @Environment(\.paleta) private var p

    var body: some View {
        if l.sistema {
            T(l.texto, 13, 700, C.carbon, alinear: .center)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .gel(13, Gels.limaChico)
                .frame(maxWidth: .infinity)
        } else if l.mio {
            T(l.texto, 15, 400, C.blanco, alto: 1.4)
                .padding(.horizontal, 14).padding(.vertical, 10)
                .background(UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 18, bottomTrailingRadius: 6, topTrailingRadius: 18, style: .continuous)
                    .fill(C.violeta))
                .frame(maxWidth: 250, alignment: .trailing)
                .frame(maxWidth: .infinity, alignment: .trailing)
        } else {
            HStack(alignment: .bottom, spacing: 8) {
                Avatar(nombre: l.autor, foto: l.foto, tam: 28, borde: 0, tamTexto: 11)
                VStack(alignment: .leading, spacing: 3) {
                    if !l.autor.isEmpty { T(miosPrimer(l.autor), 11, 700, p.texto2) }
                    T(l.texto, 15, 400, alto: 1.4)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background {
                            // Sombra de tarjeta y fondo con la punta de abajo a la izquierda (radio 6).
                            ZStack {
                                Color.clear.gel(18, nil, miosGelTarjeta(p).sombras.filter { !$0.inset })
                                UnevenRoundedRectangle(topLeadingRadius: 18, bottomLeadingRadius: 6, bottomTrailingRadius: 18, topTrailingRadius: 18, style: .continuous)
                                    .fill(p.tarjetaPlano)
                            }
                        }
                }
                .frame(maxWidth: 250, alignment: .leading)
                Spacer(minLength: 0)
            }
        }
    }
}

/// Barra de escribir: "Escribí algo…" + botón violeta. Enter envía.
struct MiosEntrada: View {
    var alTipear: () -> Void
    var enviar: (String) async -> Bool
    @State private var texto = ""
    @State private var mandando = false
    @Environment(\.paleta) private var p

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(p.oscuro ? p.separador : Color(hex: 0xECE8F2)).frame(height: 1)
            HStack(spacing: 10) {
                ZStack(alignment: .leading) {
                    if texto.isEmpty { T("Escribí algo…", 15, 400, C.inactivo).allowsHitTesting(false) }
                    TextField("", text: $texto)
                        .font(Geist.fuente(15))
                        .foregroundStyle(p.texto)
                        .tint(C.violeta)
                        .submitLabel(.send)
                        .onSubmit(mandar)
                        .onChange(of: texto) { viejo, nuevo in
                            if nuevo.count > 2000 { texto = String(nuevo.prefix(2000)) }
                            if nuevo.count > viejo.count { alTipear() }
                        }
                }
                .padding(.horizontal, 14)
                .frame(height: 44)
                .background(RoundedRectangle(cornerRadius: 15, style: .continuous).fill(p.oscuro ? p.superficie : Color(hex: 0xF6F4F0)))
                Button(action: mandar) {
                    Icono(I.flecha, 18, C.blanco, trazo: 2.4).frame(width: 44, height: 44).gel(15, miosVioletaChico)
                }
                .buttonStyle(EstiloToque())
                .disabled(mandando)
                .accessibilityLabel("Enviar")
            }
            .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 14)
        }
        .background((p.oscuro ? C.oscuro4 : C.blanco).ignoresSafeArea(edges: .bottom))
    }

    private func mandar() {
        let t = texto.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty, !mandando else { return }
        mandando = true
        Task {
            if await enviar(t) { texto = "" }
            mandando = false
        }
    }
}

/// Mensajes privados: las conversaciones con la gente que compartió un plan con vos.
struct VistaPrivados: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var lista: [Conversacion]?
    @State private var error: String?

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                Encabezado(titulo: "Mensajes") { ir.atras() }.padding(.horizontal, 16).padding(.top, 6)
                Group {
                    if let lista {
                        if lista.isEmpty {
                            MiosVacio(titulo: "Todavía no hay mensajes", cuerpo: "Podés escribirle a la gente con la que compartiste un plan.", cta: "Ver mi gente") { ir.gente() }
                                .padding(28)
                        } else {
                            ScrollView(showsIndicators: false) {
                                LazyVStack(spacing: 12) { ForEach(lista) { fila($0) } }
                                    .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 24)
                            }
                            .refreshable { await cargar() }
                        }
                    } else if let error {
                        MiosVacio(titulo: "No pudimos cargar tus mensajes", cuerpo: error, cta: "Probar de nuevo") { Task { await cargar() } }.padding(28)
                    } else {
                        Puntitos(color: p.acento)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task { await cargar() }
    }

    private func cargar() async {
        do { lista = try await c.api.conversaciones().conversaciones; error = nil } catch { self.error = mensajeDe(error) }
    }

    private func fila(_ cv: Conversacion) -> some View {
        HStack(spacing: 12) {
            Avatar(nombre: cv.con.nombre, foto: cv.con.foto, tam: 44, borde: 0, tamTexto: 17)
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    T(cv.con.nombre, 15, 700, unRenglon: true).frame(maxWidth: .infinity, alignment: .leading)
                    T(Fecha.hace(cv.ultimo.creadoEn), 12, 400, p.texto2, unRenglon: true)
                }
                T((cv.ultimo.deId == c.usuarioId ? "Vos: " : "") + cv.ultimo.texto, 13, cv.sinLeer > 0 ? 700 : 400,
                  cv.sinLeer > 0 ? p.texto : p.texto2, unRenglon: true)
            }
            if cv.sinLeer > 0 {
                T("\(cv.sinLeer)", 11, 800, C.carbon).frame(width: 22, height: 22).background(Circle().fill(C.coral))
            }
        }
        .padding(12)
        .gel(20, miosGelTarjeta(p))
        .contentShape(Rectangle())
        .onTapGesture { ir.privado(cv.con.id, nombre: cv.con.nombre) }
    }
}

/// Chat 1 a 1 (GET/POST /v1/privados/{id}), con las piezas del chat del plan.
struct VistaPrivado: View {
    var conId: String
    var nombre: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var persona: Perfil?
    @State private var hilo = MiosHilo()
    @State private var opciones = false
    @State private var reportando = false
    /// Último aviso de «escribiendo» que se mandó (como mucho uno cada 2 s).
    @State private var ultimoEscribiendo = Date.distantPast
    @Environment(\.paleta) private var p

    private var quien: String { nombre.isEmpty ? (persona?.nombre ?? "") : nombre }

    var body: some View {
        Pantalla {
            ZStack(alignment: .bottom) {
                conversacion
                hojas
            }
        }
        .task(id: conId) {
            // Si llega desde un aviso no trae el nombre (ni la foto): salen del perfil.
            persona = try? await c.api.persona(conId)
            do {
                hilo.lineas = try await c.api.privados(conId).privados.map { $0.linea(c.usuarioId, quien) }
            } catch {
                hilo.error = mensajeDe(error)
            }
            hilo.cargado = true
        }
        .onAppear {
            c.tiempoReal.encender()
            let yo = c.usuarioId, h = hilo, id = conId
            let nombreFijo = quien
            let persona = persona
            hilo.oyente = c.tiempoReal.escuchar { ev in
                MainActor.assumeIsolated {
                    if ev.tipo == "privado", let m = ev.privado, m.deId == id || m.aId == id {
                        h.agregar(m.linea(yo, nombreFijo))
                        if m.deId == id { h.escribe = nil }
                    } else if ev.tipo == "escribiendo", ev.privadoDe == id {
                        let n = nombreFijo.isEmpty ? (persona?.nombre ?? "") : nombreFijo
                        h.marcarEscribe(n.isEmpty ? "Escribiendo" : miosPrimer(n))
                    }
                }
            }
        }
        .onDisappear {
            if let o = hilo.oyente { c.tiempoReal.dejar(o) }
            hilo.oyente = nil
        }
    }

    /// Menú: reportar o bloquear a la otra persona.
    @ViewBuilder
    private var hojas: some View {
        let n = miosPrimer(quien)
        if opciones {
            HojaOpciones(cerrar: { opciones = false }, filas: [
                Opcion(texto: "Reportar a \(n)") { reportando = true },
                Opcion(texto: "Bloquear a \(n)") {
                    Task {
                        do {
                            try await c.api.bloquear(conId)
                            c.avisos.ok("Bloqueaste a \(n). No se van a ver más.")
                            ir.atras()
                        } catch {
                            c.avisos.error(error)
                        }
                    }
                },
            ])
        }
        if reportando {
            HojaReporte(cerrar: { reportando = false }) { motivo in
                Task {
                    do {
                        try await c.api.reportar(Reporte(usuarioId: conId, motivo: motivo))
                        c.avisos.ok("Gracias. Revisamos el perfil de \(n) en menos de 24 h.")
                    } catch {
                        c.avisos.error(error)
                    }
                }
            }
        }
    }

    private var conversacion: some View {
            VStack(spacing: 0) {
                Encabezado(titulo: quien, volver: { ir.atras() }) {
                    HStack(spacing: 8) {
                        Avatar(nombre: quien, foto: persona?.fotos.first, tam: 36, tamTexto: 14)
                            .onTapGesture { ir.persona(conId, desde: "privado") }
                        BotonIcono(d: I.puntos, iconoTam: 20, trazo: 0, gel: Gels.chip(p), color: .clear, relleno: p.texto) { opciones = true }
                            .accessibilityLabel("Más opciones")
                    }
                }
                .padding(.horizontal, 16).padding(.top, 6)
                MiosVistaHilo(hilo: hilo, foto: persona?.fotos.first ?? "")
                MiosEntrada(alTipear: {
                    guard Date().timeIntervalSince(ultimoEscribiendo) >= 2 else { return }
                    ultimoEscribiendo = Date()
                    c.tiempoReal.escribiendo(privado: conId)
                }) { t in
                    do {
                        let m = try await c.api.escribirPrivado(conId, t)
                        hilo.agregar(m.linea(c.usuarioId, quien))
                        return true
                    } catch {
                        c.avisos.error(error)
                        return false
                    }
                }
            }
    }
}
