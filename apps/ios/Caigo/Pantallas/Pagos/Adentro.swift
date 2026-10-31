import SwiftUI
import Nucleo

// La usan la app y el App Clip de Caigo Tap.

private let fondoAdentro = Fondo.radial(cx: 0.5, cy: 0.3, rx: 1, ry: 0.6, Fondo.paradas((0, C.lima0), (0.55, C.lima), (1, Color(hex: 0x9FD13B))))
private let verdeOscuro = Color(hex: 0x2E4A00)

/// "Adentro" (Pases NFC 1e / prototipo `tapdone`): check-in hecho, beneficio y visitas.
struct VistaAdentro: View {
    /// Qué hacer con "Volver al plan" / "Listo" (en el App Clip no hay pila: lo decide el Clip).
    var alTerminar: ((_ planId: String?) -> Void)?
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir

    var body: some View {
        Pantalla(fondo: AnyView(ZStack { C.lima; VistaFondo(fondo: fondoAdentro); Grano() })) {
            if let r = c.ultimoTap { contenido(r) } else { Color.clear.onAppear { if alTerminar == nil { ir.atras() } } }
        }
        .environment(\.paleta, .clara)
    }

    private func contenido(_ r: ResultadoTap) -> some View {
        let nombre = c.yo?.nombre.split(separator: " ").first.map(String.init) ?? ""
        let plan = r.plan
        let socio = r.socio
        let texto = plan?.repetido == true ? "Ya habías hecho check-in en \(r.lugar)."
            : nombre.isEmpty ? "Check-in hecho en \(r.lugar). Que lo disfrutes." : "Check-in hecho en \(r.lugar). Que lo disfrutes, \(nombre)."
        return VStack(spacing: 16) {
            ScrollView {
                VStack(spacing: 16) {
                    Icono(I.check, 60, C.lima, trazo: 3)
                        .frame(width: 120, height: 120)
                        .gel(40, Gel(fondo: .liso(C.carbon), sombras: [externa(8, 0, C.oscuro7), externa(30, 40, rgba(36, 33, 45, 0.6), spread: -16)]))
                        .pop()
                    T("Adentro", 58, 800, C.carbon, alto: 1, espaciado: -2.4, unRenglon: true)
                    T(texto, 16, 700, verdeOscuro, alto: 1.45, alinear: .center)
                    if let b = plan?.beneficio, !b.isEmpty { TarjetaBeneficio(titulo: "\(b) desbloqueado", sub: "Mostralo en la barra") }
                    if let s = socio, s.gano, !s.beneficio.isEmpty {
                        TarjetaBeneficio(titulo: s.beneficio, sub: "Lo ganaste con tu pase de socio · mostralo en la barra")
                    }
                    if let saldo = plan?.saldoCentavos, saldo > 0 {
                        T("Seña paga · en el lugar pagás \(Plata.pesos(saldo))", 14, 800, C.carbon)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 16).padding(.vertical, 14)
                            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(rgba(36, 33, 45, 0.08)))
                    }
                }
                .padding(.top, 50)
            }
            if let s = socio, s.sumo {
                T("+1 visita en tu pase de \(r.lugar) (\(s.visitas) de \(s.meta))", 13, 700, verdeOscuro, alinear: .center)
            }
            PagosBoton(texto: plan != nil ? "Volver al plan" : "Listo", estilo: .grafito, alto: 60, radio: 22, tam: 17, peso: 800) {
                let id = plan?.id ?? ""
                if let alTerminar { alTerminar(id.isEmpty ? nil : id) } else if !id.isEmpty { ir.plan(id) } else { ir.atras() }
            }
        }
        .padding(.horizontal, 24).padding(.bottom, 30)
    }
}

private struct TarjetaBeneficio: View {
    var titulo: String
    var sub: String
    var body: some View {
        HStack(spacing: 12) {
            Icono(I.regalo, 24, C.lima, trazo: 2.2)
                .frame(width: 46, height: 46)
                .background(RoundedRectangle(cornerRadius: 15, style: .continuous).fill(C.carbon))
            VStack(alignment: .leading, spacing: 2) {
                T(titulo, 15, 800, C.carbon)
                T(sub, 12, 600, C.texto2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(16)
        .gel(22, Gel(fondo: .liso(C.blanco), sombras: [externa(4, 0, C.limaBase)]))
    }
}
