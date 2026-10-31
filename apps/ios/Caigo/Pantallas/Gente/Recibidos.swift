import SwiftUI
import Nucleo

/// "Te quieren conocer" (prototipo · likes): te sumaron / les interesás y te invitan, borroso sin Plus.
struct VistaRecibidos: View {
    var modo: String
    var pestana: Int
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir

    @State private var tab = 0
    @State private var recibidos: Recibidos?
    @State private var invitaciones: Invitaciones?
    @State private var listo = false

    private var esCitas: Bool { modo == "citas" }
    private var nR: Int { recibidos?.personas.count ?? 0 }
    private var nI: Int { invitaciones?.invitaciones.count ?? 0 }
    private var bloqueada: Bool { tab == 0 ? recibidos?.bloqueada == true : invitaciones?.bloqueada == true }

    /// Una tarjeta de la grilla: foto, nombre, sub, el plan (si es una invitación) y qué abre.
    private struct Item: Identifiable {
        var id: String
        var foto: String
        var nombre: String
        var sub: String
        var plan: String?
        var abrir: () -> Void
    }

    private var items: [Item]? {
        if tab == 0 {
            return recibidos?.personas.enumerated().map { i, r in
                Item(id: "r\(i)-\(r.id)", foto: r.foto,
                     nombre: bloqueada || r.nombre.isEmpty ? "Alguien cerca" : r.nombre + (r.edad.map { ", \($0)" } ?? ""),
                     sub: (esCitas ? "Le interesás · " : "Te sumó ") + Fecha.hace(r.cuando), plan: nil) {
                    if bloqueada || r.id.isEmpty { ir.plus(motivo: "likes") } else { ir.persona(r.id, desde: "recibido-\(modo)") }
                }
            }
        }
        return invitaciones?.invitaciones.enumerated().map { i, v in
            let de = v.de
            let plan = v.titulo.isEmpty ? nil : (v.titulo.components(separatedBy: " en ").first ?? v.titulo) + " · " + Fecha.cuando(v.empiezaEn)
            return Item(id: "i\(i)-\(v.planId)", foto: (de?.foto).flatMap { $0.isEmpty ? nil : $0 } ?? v.foto,
                        nombre: bloqueada ? "Alguien cerca" : (de?.nombre ?? v.titulo),
                        sub: "Te invitó " + Fecha.hace(v.cuando), plan: plan) {
                if bloqueada || (de?.id ?? "").isEmpty { ir.plus(motivo: "likes") } else { ir.persona(de!.id, desde: "invitacion:\(v.planId)") }
            }
        }
    }

    var body: some View {
        Pantalla {
            ZStack(alignment: .bottom) {
                VStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 16) {
                        HStack(spacing: 12) {
                            BotonIcono(d: I.atras, iconoTam: 20, trazo: 2.6, gel: Gels.neutra) { ir.atras() }
                            T("Te quieren conocer", 28, 800, C.carbon, alto: 1, espaciado: -0.8, unRenglon: true)
                        }
                        Segmentado(opciones: ["\(esCitas ? "Les interesás" : "Te sumaron") · \(nR)", "Te invitan · \(nI)"],
                                   seleccion: $tab, radio: 20, radioOpcion: 16, alto: 50)
                        T(explicacion, 14, 400, C.texto2, alto: 1.45)
                    }
                    .padding(.horizontal, 20).padding(.top, 10)
                    grilla
                }
                if bloqueada && (tab == 0 ? nR : nI) > 0 {
                    VStack(spacing: 10) {
                        Cta(texto: "Ver quiénes son", alto: 60, radio: 22) { ir.plus(motivo: "likes") }
                        T("Con Caigo Plus · cancelás cuando quieras", 12, 600, C.texto2)
                    }
                    .padding(.horizontal, 20).padding(.top, 40).padding(.bottom, 30)
                    .background(LinearGradient(stops: Fondo.paradas((0, C.blancoCalido.opacity(0)), (0.4, C.blancoCalido)), startPoint: .top, endPoint: .bottom)
                        .ignoresSafeArea(edges: .bottom))
                }
            }
        }
        .environment(\.paleta, .clara)
        .task {
            tab = pestana
            async let r = try? c.api.recibidos(modo)
            async let i = try? c.api.invitaciones()
            recibidos = await r
            invitaciones = await i
            listo = true
        }
    }

    private var explicacion: String {
        if tab == 0 {
            return bloqueada ? "\(nR) personas ya te sumaron. Con Plus ves quiénes son y las sumás de vuelta."
                : "Tocá un perfil para verlo completo. Si sumás de vuelta, es mutuo y se abre el chat."
        }
        return bloqueada ? "\(nI) personas te quieren en sus planes. Con Plus ves quién organiza antes de sumarte."
            : "Planes a los que te invitaron. Mirá quién organiza y sumate si te copa."
    }

    @ViewBuilder
    private var grilla: some View {
        if let items {
            if items.isEmpty {
                VStack(spacing: 12) {
                    Image("CaigoIcono").resizable().frame(width: 96, height: 96)
                    T("Ya respondiste a todos", 22, 700, C.carbon, alto: 1.15, alinear: .center)
                    T("Cuando alguien te sume o te invite, aparece acá.", 14, 400, C.texto2, alto: 1.5, alinear: .center)
                    Spacer()
                }
                .padding(.horizontal, 20).padding(.vertical, 50)
            } else {
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(items) { it in tarjeta(it) }
                    }
                    .padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 160)
                }
            }
        } else {
            Puntitos().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func tarjeta(_ it: Item) -> some View {
        Button(action: it.abrir) {
            ZStack(alignment: .bottomLeading) {
                C.oscuro2
                if !it.foto.isEmpty { ImagenRed(url: it.foto).blur(radius: bloqueada ? 14 : 0) }
                LinearGradient(stops: Fondo.paradas((0, rgba(32, 29, 43, 0.25)), (0.35, rgba(32, 29, 43, 0)), (1, rgba(32, 29, 43, 0.85))),
                               startPoint: .top, endPoint: .bottom)
                if let pt = it.plan {
                    VStack {
                        T(pt, 12, 800, C.carbon, alto: 1.25)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 10).padding(.vertical, 7)
                            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(rgba(250, 249, 246, 0.95)))
                        Spacer()
                    }
                    .padding(10)
                }
                if bloqueada {
                    Icono(I.candadoAbierto, 20, C.blanco, trazo: 2.4)
                        .frame(width: 46, height: 46)
                        .gel(23, .liso(rgba(255, 255, 255, 0.2)), [interna(1, 0, rgba(255, 255, 255, 0.35))])
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                VStack(alignment: .leading, spacing: 3) {
                    T(it.nombre, 16, 800, C.blanco, unRenglon: true)
                    T(it.sub, 12, 600, Color(hex: 0xE4E0EE), unRenglon: true)
                }
                .padding(14)
            }
            .frame(height: 236)
            .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
            .gel(26, nil, [externa(3, 0, C.borde1), externa(18, 28, rgba(60, 40, 140, 0.5), spread: -16)])
        }
        .buttonStyle(EstiloPlano())
    }
}
