import SwiftUI
import Nucleo

/// 07 · Solicitudes de un plan propio (y el vacío 13c).
struct VistaSolicitudes: View {
    var planId: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var plan: Plan?
    @State private var sols: [Solicitud]?
    @State private var error: String?
    @State private var respondidas: Set<String> = []
    @State private var ocupado: String?
    /// Los que ya van (aceptados): el organizador puede sacarlos (contrato §2).
    @State private var van: [Solicitud] = []
    @State private var sacando: Solicitud?

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                Encabezado(titulo: "Solicitudes",
                           sub: plan.map { [$0.titulo, Fecha.cuando($0.empiezaEn)].filter { !$0.isEmpty }.joined(separator: " · ") }) { ir.atras() }
                    .padding(.horizontal, 16).padding(.top, 6)
                contenido.frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            HojaConfirmar(abierta: sacando != nil, titulo: "¿Sacar a \(miosPrimer(sacando?.persona.nombre ?? "")) del plan?",
                          cuerpo: "Le avisamos que ya no está en el plan y su lugar vuelve a quedar libre.",
                          accion: "Sacar del plan", cerrar: { sacando = nil }) {
                guard let s = sacando else { return }
                try await c.api.sacarPersona(plan: planId, usuario: s.persona.id)
                c.avisos.ok("Listo, \(miosPrimer(s.persona.nombre)) ya no está en el plan.")
                withAnimation { van.removeAll { $0.id == s.id } }
                if let pl = try? await c.api.plan(planId) { plan = pl }
            }
        }
        .task { await cargar() }
    }

    @ViewBuilder
    private var contenido: some View {
        if let pl = plan, let todas = sols {
            let lista = todas.filter { !respondidas.contains($0.id) }
            if lista.isEmpty && van.isEmpty {
                MiosVacioSolicitudes(pl: pl) { Sistema.compartir(miosLinkCompartir(pl)) }
            } else {
                // La primera solicitud que alcanza para llenar el cupo va destacada ("Completan").
                let completa = lista.first { pl.faltan > 0 && $0.cantidad >= pl.faltan }?.id
                ScrollView(showsIndicators: false) {
                    LazyVStack(spacing: 12) {
                        resumen(pl, lista.count)
                        ForEach(lista) { s in
                            TarjetaSolicitud(s: s, completan: s.id == completa, ocupado: ocupado == s.id,
                                             aceptar: { responder(s, true) }, rechazar: { responder(s, false) },
                                             verPersona: { ir.persona(s.persona.id, desde: "solicitud") })
                        }
                        if !van.isEmpty { yaVan(pl) }
                    }
                    .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 24)
                }
                .refreshable { await cargar() }
            }
        } else if let error {
            MiosVacio(titulo: "No pudimos cargar las solicitudes", cuerpo: error, cta: "Probar de nuevo") { Task { await cargar() } }
                .padding(28)
        } else {
            Puntitos(color: p.acento)
        }
    }

    private func cargar() async {
        do {
            async let a = c.api.plan(planId)
            async let b = c.api.solicitudes(planId)
            let (pl, s) = try await (a, b)
            plan = pl
            sols = s.solicitudes.filter { $0.estado == "pendiente" }
            van = s.solicitudes.filter { $0.estado == "aceptada" }
            error = nil
        } catch {
            self.error = mensajeDe(error)
        }
    }

    private func responder(_ s: Solicitud, _ aceptar: Bool) {
        ocupado = s.id
        Task {
            do {
                try await c.api.responder(solicitud: s.id, aceptar: aceptar)
                withAnimation {
                    _ = respondidas.insert(s.id)
                    if aceptar { var a = s; a.estado = "aceptada"; van.append(a) }
                }
                if aceptar { c.avisos.ok("\(miosPrimer(s.persona.nombre)) ya está adentro.") }
                if let pl = try? await c.api.plan(planId) { plan = pl }
            } catch {
                c.avisos.error(error)
            }
            ocupado = nil
        }
    }

    /// «Ya van»: los aceptados, con «Sacar» (antes de que empiece el plan).
    private func yaVan(_ pl: Plan) -> some View {
        let empezo = Fecha.parse(pl.empiezaEn).map { Date() > $0 } ?? false
        return VStack(alignment: .leading, spacing: 10) {
            MiosSeccion(texto: "Ya van · \(van.reduce(0) { $0 + $1.cantidad })").padding(.top, 10)
            ForEach(van) { s in
                let per = s.persona
                HStack(spacing: 12) {
                    Avatar(nombre: per.nombre, foto: per.foto, tam: 44, borde: 0, tamTexto: 17)
                    VStack(alignment: .leading, spacing: 1) {
                        HStack(spacing: 6) {
                            T(s.cantidad > 1 ? "\(miosPrimer(per.nombre)) · somos \(s.cantidad)" : miosPrimer(per.nombre), 15, 700, unRenglon: true)
                            if per.verificada { MiosVerificado(tam: 15) }
                        }
                        T(miosAsistencia(per.asistencia), 12, 400, p.texto2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .contentShape(Rectangle())
                    .onTapGesture { ir.persona(per.id, desde: "solicitud") }
                    if !empezo && !pl.cancelado {
                        Button { withAnimation { sacando = s } } label: {
                            T("Sacar", 14, 700, C.coral2, unRenglon: true).padding(.horizontal, 12).padding(.vertical, 8)
                        }
                        .buttonStyle(EstiloPlano())
                        .accessibilityLabel("Sacar a \(miosPrimer(per.nombre)) del plan")
                    }
                }
                .padding(12)
                .gel(20, miosGelTarjeta(p))
            }
        }
    }

    /// Contador oscuro: "Faltan 2 / Somos 8 de 10" y "3 pedidos".
    private func resumen(_ pl: Plan, _ pedidos: Int) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 0) {
                T(Cupo.badge(pl.faltan), 34, 800, Cupo.completo(pl.faltan) ? C.lima : C.coral, alto: 1, unRenglon: true)
                T(Cupo.somos(pl.somos, pl.total), 13, 600, C.blancoCalido)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            T(pedidos == 1 ? "1 pedido" : "\(pedidos) pedidos", 13, 700, C.blancoCalido)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color(hex: 0x3A3550)))
        }
        .padding(.horizontal, 16).padding(.vertical, 14)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(C.carbon))
    }
}

private struct TarjetaSolicitud: View {
    var s: Solicitud
    var completan: Bool
    var ocupado: Bool
    var aceptar: () -> Void
    var rechazar: () -> Void
    var verPersona: () -> Void
    @Environment(\.paleta) private var p

    var body: some View {
        let per = s.persona
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                HStack(spacing: -14) {
                    Avatar(nombre: per.nombre, foto: per.foto, tam: 44, borde: s.cantidad > 1 ? 2 : 0, tamTexto: 17)
                    if s.cantidad > 1 {
                        Avatar(nombre: "+", foto: nil, tam: 44, texto: "+\(s.cantidad - 1)", fondo: (C.lima, C.carbon), tamTexto: 15)
                    }
                }
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        let nombre = s.cantidad > 1 ? "\(miosPrimer(per.nombre)) · somos \(s.cantidad)"
                            : [miosPrimer(per.nombre), per.edad.map(String.init)].compactMap { $0 }.joined(separator: ", ")
                        T(nombre, 15, 700, unRenglon: true)
                        if per.verificada { MiosVerificado(tam: 15) }
                    }
                    T(per.enMiGente ? miosAsistencia(per.asistencia, extra: "está en tu gente") : miosAsistencia(per.asistencia), 12, 400, p.texto2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if completan {
                    T("Completan", 11, 800, C.carbon).padding(.horizontal, 9).padding(.vertical, 5).gel(10, Gels.limaChico)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture(perform: verPersona)
            if !s.mensaje.isEmpty {
                T(s.mensaje, 14, 400, alto: 1.4)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12).padding(.vertical, 10)
                    .gel(14, Gels.chip(p))
            }
            HStack(spacing: 8) {
                Button(action: rechazar) {
                    T("Ahora no", 15, 700, p.texto2)
                        .frame(maxWidth: .infinity).frame(height: 46)
                        .overlay(RoundedRectangle(cornerRadius: 15, style: .continuous).strokeBorder(p.oscuro ? C.oscuro1 : C.pista, lineWidth: 1.5))
                }
                .buttonStyle(EstiloToque())
                .disabled(ocupado)
                Cta(texto: s.cantidad > 1 ? "Aceptar a los \(s.cantidad)" : "Aceptar", alto: 46, radio: 15, tam: 15, color: C.carbon,
                    cargando: ocupado, accion: aceptar)
                    .layoutPriority(1.4)
                    .frame(minWidth: 150)
            }
        }
        .padding(14)
        .background {
            if completan {
                RoundedRectangle(cornerRadius: 24, style: .continuous).fill(p.tarjetaPlano)
                    .overlay(RoundedRectangle(cornerRadius: 24, style: .continuous).strokeBorder(C.lima, lineWidth: 2))
            } else {
                Color.clear.gel(24, miosGelTarjeta(p))
            }
        }
    }
}

/// 13c · Nadie pidió sumarse todavía.
struct MiosVacioSolicitudes: View {
    var pl: Plan
    var compartir: () -> Void
    @Environment(\.paleta) private var p

    var body: some View {
        VStack(spacing: 18) {
            Spacer()
            VStack(spacing: 0) {
                T(Cupo.badge(pl.faltan), 56, 800, Cupo.completo(pl.faltan) ? C.lima : C.coral, alto: 1, espaciado: -2, unRenglon: true)
                T(Cupo.somos(pl.somos, pl.total), 13, 600, C.blancoCalido)
            }
            .padding(.horizontal, 26).padding(.vertical, 20)
            .gel(28, .radial(cx: 0, cy: 0, rx: 1.2, ry: 0.9, Fondo.paradas((0, C.oscuro1), (0.62, C.carbon))),
                 [interna(1, 0, rgba(255, 255, 255, 0.08)), externa(22, 36, rgba(36, 33, 45, 0.6), spread: -14)], grano: true)
            .rotationEffect(.degrees(4))
            T("Nadie pidió sumarse todavía", 30, 700, alto: 1.08, espaciado: -0.6, alinear: .center)
            T("Pasá el link por el grupo. Siempre hay alguien que se prende.", 15, 400, p.texto2, alto: 1.5, alinear: .center)
            Cta(texto: "Compartir link del plan", alto: 58, tam: 16, icono: I.compartir, iconoTrazo: 2.4, iconoTam: 18, accion: compartir)
                .padding(.top, 8)
            Spacer()
        }
        .padding(.horizontal, 28)
    }
}
