import SwiftUI
import Nucleo

/// «Decidí rápido» (Ahora, con 8 o más planes para decidir): a pantalla completa, los planes de la lista de a uno,
/// con la misma tarjeta más grande y los mismos gestos. «Paso» lo oculta (como «No me interesa»), «Ver más» abre
/// el detalle y «Caigo» se suma directo o abre el detalle (planesCaigo). Al final: «Listo: te sumaste a N.».
struct DecidiRapido: View {
    var planes: [Plan]
    var ocultos: PlanesOcultos
    var cerrar: () -> Void
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var indice = 0
    @State private var sumados = 0
    @State private var ocupado = false

    private var actual: Plan? { indice < planes.count ? planes[indice] : nil }

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                Encabezado(titulo: "Decidí rápido", sub: actual.map { _ in "\(indice + 1) de \(planes.count)" }, icono: I.cruz, volver: cerrar)
                    .padding(.horizontal, 16).padding(.top, 6)
                if let pl = actual {
                    ScrollView(showsIndicators: false) {
                        TarjetaPlan(plan: pl, altoFoto: 300)
                            .modifier(DeslizarPlan(radio: 24, alCaigo: { caigo(pl) }, alNoMeInteresa: { paso(pl) }))
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

    private func botones(_ pl: Plan) -> some View {
        HStack(spacing: 10) {
            Cta(texto: "Paso", gel: Gels.blanco, alto: 56, tam: 16, color: C.carbon, icono: I.cruz, iconoTrazo: 3, iconoTam: 14) { paso(pl) }
            Cta(texto: "Ver más", gel: Gels.blanco, alto: 56, tam: 16, color: C.carbon) { ir.plan(pl.id) }
            Cta(texto: "Caigo", gel: Gels.lima, alto: 56, tam: 16, color: C.carbon, icono: I.mas, iconoTrazo: 4, iconoTam: 14,
                cargando: ocupado) { caigo(pl) }
        }
        .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 16)
    }

    private var fin: some View {
        VStack(spacing: 18) {
            Spacer()
            T(sumados > 0 ? "Listo: te sumaste a \(sumados)." : "Listo.", 30, 700, p.texto, alto: 1.08, espaciado: -0.6, alinear: .center)
            Cta(texto: "Volver a la lista", alto: 58, tam: 16, accion: cerrar).padding(.top, 6)
            Spacer()
        }
        .padding(.horizontal, 28)
    }

    private func siguiente() { indice += 1 }

    /// «Paso»: POST /v1/planes/{id}/ocultar (sin el aviso de «Deshacer»: el mazo sigue).
    private func paso(_ pl: Plan) {
        ocultos.ocultar(c, pl, avisar: false)
        siguiente()
    }

    /// «Caigo»: directo (con el festejo si queda adentro) o el detalle; en los dos casos el mazo sigue.
    private func caigo(_ pl: Plan) {
        guard !ocupado else { return }
        if !Gestos.directo(pl) || cerroParaSumarse(pl) {
            ir.plan(pl.id)
            siguiente()
            return
        }
        // Sin DNI se mira: «Caigo» pide verificarse y la tarjeta se queda (no pasa a la siguiente).
        guard c.conDni(ir, "sumarte", plan: pl.id) else { return }
        ocupado = true
        Task {
            let e = await planesCaigo(c, ir, pl)
            if e == "aceptada" { sumados += 1 }
            ocupado = false
            siguiente()
        }
    }
}
