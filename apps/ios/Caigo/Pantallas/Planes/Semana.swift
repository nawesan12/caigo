import SwiftUI
import Nucleo

/*
 * «Esta semana en <ciudad>» (informe del 18/11, B2): la tarjeta de los lunes arriba de Ahora y la
 * pantalla con lo que queda de la semana, día por día. Sin pantalla en el handoff: el contador oscuro
 * del detalle, los chips de días de Explorar y las tarjetas de Ahora. Igual que Semana.kt en Android.
 */

/// La tarjeta arriba de Ahora (de lunes a miércoles, con 5 planes o más).
struct TarjetaSemana: View {
    var s: Semana
    @Environment(Ir.self) private var ir

    var body: some View {
        Button { ir.semana() } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    T("Esta semana en \(s.ciudad)", 22, 800, C.lima, alto: 1.1, espaciado: -0.6)
                    T(s.resumen, 14, 600, C.blancoCalido)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Icono(I.adelante, 22, C.lima, trazo: 3)
            }
            .padding(16)
            .gel(24, miosOscura)
        }
        .buttonStyle(EstiloToque())
    }
}

/// La pantalla «Esta semana».
struct VistaSemana: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var semana: Semana?
    @State private var error: String?

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                Encabezado(titulo: "Esta semana", sub: semana?.ciudad) { ir.atras() }.padding(.horizontal, 16).padding(.top, 6)
                if let s = semana {
                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 16) {
                            numeros(s)
                            dias(s)
                            if s.planes.isEmpty {
                                MiosVacio(titulo: "Esta semana armá el tuyo", cuerpo: "Si publicás algo, sos lo primero que ven.",
                                          cta: "Armá un plan") { ir.crear() }
                            } else {
                                T("Lo mejor de la semana", 20, 700)
                                ForEach(s.planes) { pl in TarjetaPlan(plan: pl) }
                            }
                        }
                        .padding(.horizontal, 20).padding(.vertical, 18)
                    }
                } else if let error {
                    MiosVacio(titulo: "No pudimos traer la semana", cuerpo: error, cta: "Probar de nuevo") { Task { await cargar() } }
                        .padding(28)
                } else {
                    Puntitos(color: p.acento).frame(maxHeight: .infinity)
                }
            }
        }
        .task { await cargar() }
    }

    private func cargar() async {
        do { semana = try await c.api.semana(); error = nil } catch { self.error = mensajeDe(error) }
    }

    private func numeros(_ s: Semana) -> some View {
        HStack(alignment: .top, spacing: 8) {
            numero(s.total, "planes")
            numero(s.aliados, "en lugares aliados")
            numero(s.deTuGente, "de tu gente")
        }
        .padding(16)
        .gel(24, miosOscura)
    }

    private func numero(_ n: Int, _ texto: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            T("\(n)", 34, 800, C.lima, alto: 1, espaciado: -1)
            T(texto, 12, 600, C.blancoCalido)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func dias(_ s: Semana) -> some View {
        let maximo = max(1, s.dias.map(\.cantidad).max() ?? 1)
        return HStack(spacing: 6) {
            ForEach(s.dias, id: \.dia) { d in
                let fecha = Fecha.parse(d.dia + "T12:00:00-03:00") ?? Date()
                VStack(spacing: 4) {
                    T(Fecha.diaCorto(fecha), 11, 700, p.texto2)
                    T("\(Fecha.calendario.component(.day, from: fecha))", 16, 800)
                    ZStack(alignment: .bottom) {
                        Capsule().fill(C.lavanda)
                        Capsule().fill(C.violeta).frame(height: 36 * CGFloat(d.cantidad) / CGFloat(maximo))
                    }
                    .frame(width: 8, height: 36)
                    T("\(d.cantidad)", 12, 800)
                }
                .frame(maxWidth: .infinity).padding(.vertical, 8)
                .gel(14, Gels.chip(p))
                .opacity(d.cantidad == 0 ? 0.55 : 1)
            }
        }
    }
}
