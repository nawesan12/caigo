import SwiftUI
import Nucleo

/// «Antes de sumarte»: aparece cuando la API responde 422 `confirmar_mujer` (plan «Solo mujeres») o
/// `confirmar_diversidad` (plan «Solo diversidades»). La persona lo confirma (el interruptor arranca
/// apagado) y se reintenta el pedido con esa confirmación. Caigo no guarda género, orientación ni
/// identidad de género: la confirmación vale sólo para ese pedido (AAIP 1.2).
/// `pide`: `mujer` | `diversidad` (Generos.pide); nil, cerrada.
/// No está en el handoff: se arma con la hoja de Mis planes y el interruptor del alta.
struct HojaAntesDeSumarte: View {
    var pide: String?
    var cerrar: () -> Void
    var confirmar: (Confirma) -> Void
    @Environment(\.paleta) private var p
    @State private var soy = false
    /// El último pedido: el texto no cambia mientras la hoja se va.
    @State private var ultimo = ""

    private var mujer: Bool { (pide ?? ultimo) == "mujer" }

    var body: some View {
        MiosHoja(abierta: pide != nil, titulo: "Antes de sumarte", cerrar: cerrar) {
            T(mujer ? "Este plan es sólo para mujeres." : "Este plan es sólo para la diversidad LGBTIQ+.", 15, 400, alto: 1.45)
            HStack(spacing: 12) {
                T(mujer ? "Soy mujer" : "Soy parte de la diversidad LGBTIQ+", 14, 600)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Interruptor(on: $soy)
            }
            T("No lo guardamos: sólo vale para sumarte a este plan.", 12, 400, p.texto2, alto: 1.4)
            Cta(texto: "Sumarme", habilitado: soy) {
                let c = Generos.confirma(pide ?? ultimo)
                cerrar()
                confirmar(c)
            }
        }
        .onChange(of: pide, initial: true) { _, x in
            soy = false
            if let x { ultimo = x }
        }
    }
}
