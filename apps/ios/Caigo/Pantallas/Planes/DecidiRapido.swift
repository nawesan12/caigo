import SwiftUI
import Nucleo

/// «Decidí rápido» (Ahora, con 8 o más planes que se suman con un toque): a pantalla completa, los planes de a
/// uno, con la misma tarjeta más grande (sin su botón: los del mazo van abajo) y los mismos gestos. «Paso» sólo
/// pasa (ocultar por hoy, con «Deshacer», es el gesto de la lista), «Ver más» abre el detalle y «Sumarme» se suma
/// directo, sin festejo en el medio: la carta muestra «Adentro» y el resumen va al final («Listo: te sumaste a N.»).
struct DecidiRapido: View {
    var planes: [Plan]
    var ocultos: PlanesOcultos
    var cerrar: () -> Void
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @Environment(\.dynamicTypeSize) private var tipo
    @State private var indice = 0
    @State private var sumados = 0
    @State private var ocupado = false
    @State private var adentro = false

    private var actual: Plan? { indice < planes.count ? planes[indice] : nil }

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                Encabezado(titulo: "Decidí rápido", sub: actual.map { _ in "\(indice + 1) de \(planes.count)" }, icono: I.cruz, volver: cerrar)
                    .padding(.horizontal, 16).padding(.top, 6)
                if let pl = actual {
                    ScrollView(showsIndicators: false) {
                        TarjetaPlan(plan: pl, altoFoto: 300, conAccion: false)
                            .modifier(DeslizarPlan(radio: 24, alCaigo: { sumarme(pl) }, alNoMeInteresa: { paso() }))
                            .overlay { if adentro { marcaAdentro } }
                            .id(pl.id)
                            .transition(.opacity)
                            .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 12)
                    }
                    botones(pl)
                } else {
                    fin
                }
            }
        }
        .animation(.easeOut(duration: 0.2), value: indice)
    }

    private var marcaAdentro: some View {
        HStack(spacing: 8) {
            Icono(I.check, 22, C.carbon, trazo: 3.4)
            T("Adentro", 20, 800, C.carbon, unRenglon: true)
        }
        .padding(.horizontal, 22).padding(.vertical, 14)
        .gel(20, Gels.lima)
        .transition(.scale(scale: 0.9).combined(with: .opacity))
        .accessibilityLabel("Adentro")
    }

    /// Con letra de accesibilidad, los tres botones van uno abajo del otro (si no, quedaban con «…»).
    private func botones(_ pl: Plan) -> some View {
        let paso = Cta(texto: "Paso", gel: Gels.blanco, alto: 56, tam: 16, color: C.carbon, icono: I.cruz, iconoTrazo: 3, iconoTam: 14) { self.paso() }
        let verMas = Cta(texto: "Ver más", gel: Gels.blanco, alto: 56, tam: 16, color: C.carbon) { ir.plan(pl.id) }
        let sumar = Cta(texto: "Sumarme", gel: Gels.lima, alto: 56, tam: 16, color: C.carbon, icono: I.mas, iconoTrazo: 4, iconoTam: 14,
                        cargando: ocupado) { sumarme(pl) }
        return Group {
            if tipo.isAccessibilitySize {
                VStack(spacing: 10) { sumar; verMas; paso }
            } else {
                HStack(spacing: 10) { paso; verMas; sumar }
            }
        }
        .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 16)
    }

    private var fin: some View {
        VStack(spacing: 18) {
            Spacer()
            T(sumados > 0 ? "Listo: te sumaste a\u{00A0}\(sumados)." : "Listo.", 30, 700, p.texto, alto: 1.08, espaciado: -0.6, alinear: .center)
            Cta(texto: "Volver a la lista", alto: 58, tam: 16, accion: cerrar).padding(.top, 6)
            Spacer()
        }
        .padding(.horizontal, 28)
        .accessibilityElement(children: .contain)
    }

    private func siguiente() { indice += 1 }

    /// «Paso»: sólo pasa a la siguiente (el plan sigue en la lista).
    private func paso() {
        guard !ocupado else { return }
        siguiente()
    }

    /// «Sumarme»: directo (el mazo sólo trae planes así), sin festejo; «Adentro» en la carta y a la siguiente.
    private func sumarme(_ pl: Plan) {
        guard !ocupado else { return }
        // Sin DNI se mira: pide verificarse y la tarjeta se queda (no pasa a la siguiente).
        guard c.conDni(ir, "sumarte", plan: pl.id) else { return }
        ocupado = true
        Task {
            let e = await planesCaigo(c, ir, pl, festejar: false)
            if e == "aceptada" || e == "pendiente" {
                sumados += 1
                withAnimation(.easeOut(duration: 0.18)) { adentro = true }
                try? await Task.sleep(nanoseconds: 550_000_000)
                adentro = false
            }
            ocupado = false
            if e != nil { siguiente() }
        }
    }
}
