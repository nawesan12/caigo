import SwiftUI
import Nucleo

/// 00 · Splash. Mientras se ve, decide a dónde ir: sin sesión, a la intro (la primera vez) o al
/// ingreso; con sesión, al paso del alta que falte o a la app.
struct Arranque: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var progreso: CGFloat = 0.15

    var body: some View {
        ZStack {
            IngresoFondoSplash()
            GeometryReader { g in
                IngresoMancha(tam: 240, claro: Color(hex: 0xF2FFC8), medio: C.lima, oscuro: Color(hex: 0x9CCB3A), alfa: 0.5)
                    .position(x: -90 + 120, y: 90 + 120)
                IngresoMancha(tam: 210, claro: Color(hex: 0xFFC4BA), medio: C.coral, oscuro: Color(hex: 0xE0503F), alfa: 0.45)
                    .position(x: g.size.width + 80 - 105, y: g.size.height - 120 - 105)
            }
            .ignoresSafeArea()
            VStack(spacing: 26) {
                ZStack {
                    // drop-shadow(0 30px 34px rgba(0,0,0,.5)) del isotipo
                    Color.clear.frame(width: 150, height: 150).gel(42, nil, [externa(30, 34, rgba(0, 0, 0, 0.5))])
                    Image("CaigoIcono").resizable().frame(width: 188, height: 188)
                }
                T("Caigo", 46, 700, C.blancoCalido, alto: 1, espaciado: espaciadoTitulo(46))
            }
            .padding(.bottom, 40)
            VStack(spacing: 16) {
                Spacer()
                T("Hacé planes. Conocé gente.", 20, 700, C.blanco, espaciado: -0.2)
                ZStack(alignment: .leading) {
                    Capsule().fill(rgba(255, 255, 255, 0.22))
                    Capsule().fill(C.lima).frame(width: 120 * progreso)
                }
                .frame(width: 120, height: 5)
            }
            .padding(.bottom, 64)
        }
        .ocultarBarraNavegacion()
        .environment(\.colorScheme, .dark)
        .task { await decidir() }
    }

    private func decidir() async {
        withAnimation(.linear(duration: 0.9)) { progreso = 0.55 }
        let inicio = Date()
        let destino: Base
        if !c.conSesion {
            destino = c.vioIntro ? .ingresar : .intro
        } else {
            do {
                destino = try await ingresoRutaDeAlta(c)
            } catch let e as ErrorApi where e.estado == 401 {
                destino = .ingresar
            } catch {
                // Sin conexión: se entra igual y cada pantalla avisa.
                destino = .principal(.ahora, modo: nil)
            }
        }
        let falta = 0.9 - Date().timeIntervalSince(inicio)
        if falta > 0 { try? await Task.sleep(nanoseconds: UInt64(falta * 1_000_000_000)) }
        withAnimation(.easeOut(duration: 0.25)) { progreso = 1 }
        try? await Task.sleep(nanoseconds: 330_000_000)
        ir.alta(destino)
    }
}
