import SwiftUI

/// Marcador de una pantalla que todavía no está armada.
struct Pendiente: View {
    var titulo: String
    @Environment(Ir.self) private var ir
    var body: some View {
        Pantalla {
            VStack(spacing: 20) {
                MensajeCentro(titulo: titulo, cuerpo: "Esta pantalla está en camino.")
                Cta(texto: "Volver", gel: Gels.violeta, alto: 52, radio: 18, tam: 16, color: C.blanco) { ir.atras() }.frame(width: 200)
            }
            .padding(32)
        }
    }
}
