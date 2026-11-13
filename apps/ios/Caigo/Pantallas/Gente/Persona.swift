import SwiftUI
import Nucleo

/// Perfil de persona (prototipo · pp). `desde` dice qué botones van abajo:
/// "gente" / "citas" (vino del mazo), "recibido-gente" / "recibido-citas" (te sumó / le interesás),
/// "invitacion:<planId>" (te invita a un plan) o "perfil" (desde un plan o tu gente).
struct PerfilPersona: View {
    var id: String
    var desde: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir

    @State private var persona: Perfil?
    @State private var error: String?
    @State private var foto = 0
    @State private var opciones = false
    @State private var reportando = false
    @State private var invitando = false
    /// 422 `confirmar_mujer` / `confirmar_diversidad` al aceptar una invitación: lo que pide
    /// confirmar; se confirma y se reintenta.
    @State private var pide: String?

    private var esCitas: Bool { desde.hasSuffix("citas") }
    private var modo: String { esCitas ? "citas" : "gente" }
    private var planInvita: String? { desde.hasPrefix("invitacion:") ? String(desde.dropFirst("invitacion:".count)) : nil }

    var body: some View {
        Pantalla {
            if let p = persona {
                contenido(p)
            } else {
                ZStack {
                    if let error { T(error, 16, 600, C.carbon, alinear: .center).padding(30) } else { Puntitos() }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .overlay(alignment: .topLeading) {
                    BotonIcono(d: I.atras) { ir.atras() }.padding(.leading, 16).padding(.top, 8)
                }
            }
        }
        .overlay {
            if let p = persona, let pi = planInvita {
                HojaAntesDeSumarte(pide: pide, cerrar: { pide = nil }) { conf in
                    Task { do { try await aceptarInvitacion(pi, p, confirma: conf) } catch { c.avisos.error(error) } }
                }
            }
        }
        .environment(\.paleta, .clara)
        .animation(.easeOut(duration: 0.25), value: opciones)
        .animation(.easeOut(duration: 0.25), value: reportando)
        .animation(.easeOut(duration: 0.25), value: invitando)
        .task {
            do { persona = try await c.api.persona(id) } catch { self.error = mensajeDe(error) }
        }
    }

    private func accion(_ f: @escaping () async throws -> Void) {
        Task {
            do { try await f() } catch { c.avisos.error(error) }
        }
    }

    private func aceptarInvitacion(_ pi: String, _ p: Perfil, confirma: Confirma = Confirma()) async throws {
        do {
            _ = try await c.api.responderInvitacion(pi, aceptar: true, confirma: confirma)
            c.avisos.ok("Te sumaste al plan. \(p.nombre) ya sabe que vas.")
            ir.plan(pi)
        } catch let e as ErrorApi where Generos.pide(e.codigo) != nil {
            pide = Generos.pide(e.codigo)
        }
    }

    private func reaccionarYAvisar(_ p: Perfil, _ accion: String) async throws {
        let r = try await c.api.reaccionar(p.id, modo: modo, accion: accion)
        c.plus = r.plus
        c.avisos.ok(genteMensaje(nombre: p.nombre, accion: accion, modo: modo, mutuo: r.mutuo))
    }

    private func contenido(_ p: Perfil) -> some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(spacing: 0) {
                    cabecera(p)
                    cuerpo(p).padding(20)
                }
                .padding(.bottom, 150)
            }
            .ignoresSafeArea(edges: .top)
            LinearGradient(stops: Fondo.paradas((0, C.blancoCalido.opacity(0)), (0.6, C.blancoCalido)), startPoint: .top, endPoint: .bottom)
                .frame(height: 150).allowsHitTesting(false).ignoresSafeArea(edges: .bottom)
            botonesAbajo(p).padding(.horizontal, 20).padding(.bottom, 34)
            if invitando {
                GenteHojaInvitar(personaId: p.id, nombre: p.nombre, cerrar: { invitando = false }) { plan in
                    c.avisos.ok("Invitaste a \(p.nombre) a \(plan.titulo). Le llega como solicitud.")
                }
            }
            if opciones {
                HojaOpciones(cerrar: { opciones = false }, filas: [
                    Opcion(texto: "Reportar a \(p.nombre)") { reportando = true },
                    Opcion(texto: "Bloquear a \(p.nombre)") {
                        accion {
                            try await c.api.bloquear(p.id)
                            c.avisos.ok("Bloqueaste a \(p.nombre). No se van a ver más.")
                            ir.atras()
                        }
                    },
                ])
            }
            if reportando {
                HojaReporte(cerrar: { reportando = false }) { motivo in
                    accion {
                        try await c.api.reportar(Reporte(usuarioId: p.id, motivo: motivo))
                        c.avisos.ok("Gracias. Revisamos el perfil de \(p.nombre) en menos de 24 h.")
                    }
                }
            }
        }
    }

    private func cabecera(_ p: Perfil) -> some View {
        ZStack(alignment: .bottomLeading) {
            C.oscuro2
            if let f = (foto < p.fotos.count ? p.fotos[foto] : p.fotos.first) { ImagenRed(url: f) }
            LinearGradient(stops: Fondo.paradas((0, rgba(32, 29, 43, 0.5)), (0.2, rgba(32, 29, 43, 0)), (0.55, rgba(32, 29, 43, 0)),
                                                 (1, rgba(32, 29, 43, 0.9))), startPoint: .top, endPoint: .bottom)
            // Tocar a la izquierda o a la derecha cambia de foto.
            // 40 % izquierda (atrás) y 60 % derecha (adelante). Con layoutPriority y anchos infinitos la
            // derecha se quedaba con todo y no se podía volver.
            GeometryReader { g in
                HStack(spacing: 0) {
                    Color.clear.contentShape(Rectangle()).onTapGesture { foto = max(0, foto - 1) }
                        .frame(width: g.size.width * 0.4)
                    Color.clear.contentShape(Rectangle()).onTapGesture { foto = min(max(p.fotos.count - 1, 0), foto + 1) }
                        .frame(width: g.size.width * 0.6)
                }
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 8) {
                    T(p.nombre + (p.edad.map { ", \($0)" } ?? ""), 34, 800, C.blanco, alto: 1, espaciado: -0.8, unRenglon: true)
                    if p.verificada { Verificado() }
                }
                let sub = [p.trabajo, p.zona].filter { !$0.isEmpty }.joined(separator: " · ")
                if !sub.isEmpty { T(sub, 14, 600, Color(hex: 0xE4E0EE)) }
            }
            .padding(.horizontal, 20).padding(.bottom, 22)
            .allowsHitTesting(false)
        }
        .frame(height: 540)
        .clipped()
        .overlay(alignment: .top) {
            GeometryReader { g in
                let arriba = g.safeAreaInsets.top
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 5) {
                        ForEach(0..<p.fotos.count, id: \.self) { i in
                            Capsule().fill(i == foto ? C.blanco : rgba(255, 255, 255, 0.4)).frame(height: 4)
                        }
                    }
                    .padding(.horizontal, 16).padding(.top, 4)
                    HStack {
                        Button { ir.atras() } label: {
                            Icono(desde == "gente" || desde == "citas" ? I.abajo : I.atras, 20, C.blanco, trazo: 2.8)
                                .frame(width: 42, height: 42)
                                .background(RoundedRectangle(cornerRadius: 15, style: .continuous).fill(rgba(32, 29, 43, 0.45)))
                        }
                        .buttonStyle(EstiloToque())
                        .accessibilityLabel("Volver")
                        Spacer()
                        let badge = planInvita != nil ? "Te invita a un plan" : desde.hasPrefix("recibido") ? (esCitas ? "Le interesás" : "Te sumó") : nil
                        if let badge {
                            T(badge, 13, 800, C.carbon).padding(.horizontal, 12).padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 13, style: .continuous).fill(C.lima))
                        }
                    }
                    .padding(.horizontal, 16).padding(.top, 12)
                }
                .padding(.top, max(arriba, 50))
            }
        }
    }

    private func cuerpo(_ p: Perfil) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            if p.compartidos > 0 {
                T(p.compartidos == 1 ? "1 plan en común" : "\(p.compartidos) planes en común", 13, 800, C.carbon)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(RoundedRectangle(cornerRadius: 13, style: .continuous).fill(C.lima))
            }
            if !p.bio.isEmpty {
                tarjeta {
                    T("SOBRE MÍ", 12, 800, C.texto2, espaciado: 1.4)
                    T(p.bio, 15, 400, C.carbon, alto: 1.5)
                }
            }
            HStack(spacing: 10) {
                estadistica("\(p.planes)", "planes")
                estadistica(p.asistencia.de > 0 ? "\((p.asistencia.fue * 100 + p.asistencia.de / 2) / p.asistencia.de)%" : "—", "asistencia")
                estadistica(p.verificada ? "DNI" : "Selfie", "verificado")
            }
            ForEach(Array(p.prompts.filter { !$0.r.isEmpty }.enumerated()), id: \.offset) { _, pr in
                tarjeta {
                    T(pr.p, 13, 700, C.texto2)
                    T(pr.r, 22, 700, C.carbon, alto: 1.2, espaciado: -0.4)
                }
            }
            if !p.intereses.isEmpty {
                tarjeta {
                    T("LE COPA", 12, 800, C.texto2, espaciado: 1.4)
                    GenteFlujo(espacio: 8) {
                        ForEach(p.intereses, id: \.self) { g in
                            T(g, 13, 700, C.carbon, unRenglon: true).padding(.horizontal, 13).padding(.vertical, 8).gel(14, Gels.off)
                        }
                    }
                }
            }
            if !p.proximos.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    T("Sus próximos planes", 15, 800, C.carbon)
                    ForEach(p.proximos) { pr in proximo(pr) }
                }
            }
            T("Reportar o bloquear a \(p.nombre)", 13, 700, C.texto2, alinear: .center)
                .padding(8)
                .frame(maxWidth: .infinity)
                .tocable { opciones = true }
        }
    }

    @ViewBuilder
    private func botonesAbajo(_ p: Perfil) -> some View {
        HStack(spacing: 20) {
            if desde == "gente" || desde == "citas" {
                GenteBotonRedondo(tam: 60, gel: genteGelPaso, etiqueta: "Paso", accion: {
                    accion { _ = try await c.api.reaccionar(p.id, modo: modo, accion: "paso"); ir.atras() }
                }) { Icono(I.cruz, 24, C.coral2, trazo: 3.2) }
                if esCitas {
                    GenteBotonRedondo(tam: 76, gel: genteGelCorazon, etiqueta: "Me interesa", accion: {
                        accion { try await reaccionarYAvisar(p, "interesa"); ir.atras() }
                    }) { Icono(I.corazon, 34, .clear, trazo: 0, relleno: C.blanco) }
                    GenteBotonRedondo(tam: 60, gel: genteGelVioletaRedondo, etiqueta: "Me encanta", accion: {
                        accion {
                            do { try await reaccionarYAvisar(p, "encanta"); ir.atras() } catch let e as ErrorApi where e.codigo == "limite_encanta" {
                                c.avisos.error(e.detalle)
                                if c.plus?.activo != true { ir.plus(motivo: "perfil") }
                            }
                        }
                    }) { Icono(I.estrella, 26, .clear, trazo: 0, relleno: C.lima) }
                } else {
                    GenteBotonRedondo(tam: 76, gel: Gels.limaChico, etiqueta: "Me prendo", accion: {
                        accion {
                            do { try await reaccionarYAvisar(p, "prendo"); ir.atras() } catch let e as ErrorApi where e.codigo == "limite" {
                                ir.plus(motivo: "limit")
                            }
                        }
                    }) { Icono(I.mas, 32, C.carbon, trazo: 3.4) }
                    GenteBotonRedondo(tam: 60, gel: genteGelVioletaRedondo, etiqueta: "Invitar a un plan", accion: { invitando = true }) {
                        Icono(I.calendarioSimple, 24, C.blanco, trazo: 2.4)
                    }
                }
            } else if planInvita != nil || desde.hasPrefix("recibido") {
                GenteBotonRedondo(tam: 60, gel: genteGelPaso, etiqueta: "Ahora no", accion: {
                    accion {
                        if let pi = planInvita { _ = try await c.api.responderInvitacion(pi, aceptar: false) } else { try await c.api.descartarRecibido(p.id, modo: modo) }
                        ir.atras()
                    }
                }) { Icono(I.cruz, 24, C.coral2, trazo: 3.2) }
                Cta(texto: planInvita != nil ? "Me sumo al plan" : (esCitas ? "Me interesa también" : "Sumar de vuelta"),
                    alto: 62, radio: 22, icono: I.mas, iconoTrazo: 4, iconoTam: 18) {
                    accion {
                        if let pi = planInvita {
                            try await aceptarInvitacion(pi, p)
                        } else {
                            try await reaccionarYAvisar(p, esCitas ? "interesa" : "prendo")
                            ir.atras()
                        }
                    }
                }
            } else {
                Cta(texto: "Invitar a un plan", gel: Gels.violeta, alto: 58, color: C.blanco, icono: I.calendarioSimple,
                    iconoTrazo: 2.4, iconoTam: 20) { invitando = true }
            }
        }
        .frame(maxWidth: .infinity)
    }

    private func tarjeta<V: View>(@ViewBuilder _ v: () -> V) -> some View {
        VStack(alignment: .leading, spacing: 8) { v() }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(18)
            .gel(24, Gels.neutra)
    }

    /// Fila de «Sus próximos planes» (Prototipo): foto 56, título, cuándo y categoría.
    private func proximo(_ pr: ProximoPlan) -> some View {
        HStack(spacing: 12) {
            Foto(url: pr.foto, tinte: colorDeCategoria(pr.categoria))
                .frame(width: 56, height: 56)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                T(pr.titulo, 15, 800, C.carbon, unRenglon: true)
                T([Fecha.cuando(pr.empiezaEn), Categorias.nombre(pr.categoria)].filter { !$0.isEmpty }.joined(separator: " · "),
                  12, 600, C.texto2, unRenglon: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Icono(I.adelante, 18, Color(hex: 0x9A95AD), trazo: 2.6)
        }
        .padding(EdgeInsets(top: 10, leading: 10, bottom: 10, trailing: 14))
        .gel(22, Gels.neutra)
        .contentShape(Rectangle())
        .onTapGesture { ir.plan(pr.id) }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isButton)
    }

    private func estadistica(_ v: String, _ l: String) -> some View {
        VStack(spacing: 4) {
            T(v, 22, 800, C.violetaTexto, alto: 1, unRenglon: true)
            T(l, 12, 700, C.texto2, unRenglon: true)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14).padding(.horizontal, 6)
        .gel(20, Gels.neutra)
    }
}

/// Fila que se parte en renglones (chips de intereses).
struct GenteFlujo: Layout {
    var espacio: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let ancho = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, fila: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let t = s.sizeThatFits(.unspecified)
            if x > 0 && x + t.width > ancho { x = 0; y += fila + espacio; fila = 0 }
            x += t.width + espacio
            maxX = max(maxX, x - espacio)
            fila = max(fila, t.height)
        }
        return CGSize(width: proposal.width ?? maxX, height: y + fila)
    }

    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = b.minX, y = b.minY, fila: CGFloat = 0
        for s in subviews {
            let t = s.sizeThatFits(.unspecified)
            if x > b.minX && x + t.width > b.maxX { x = b.minX; y += fila + espacio; fila = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(t))
            x += t.width + espacio
            fila = max(fila, t.height)
        }
    }
}
