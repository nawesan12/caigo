import SwiftUI
import Nucleo

/// Cuánto lugar deja abajo el contenido de una pestaña para no quedar tapado por la tab bar
/// (24 abajo + 70 de alto + 14 de aire; el área segura de abajo se suma sola).
let altoTabBar: CGFloat = 24 + 70 + 14

/// Las cuatro pestañas con la tab bar flotante encima.
struct Principal: View {
    var inicial: Pestana
    var modo: String?
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p

    var body: some View {
        ZStack(alignment: .bottom) {
            FondoCaigo(oscuro: p.oscuro)
            Group {
                switch ir.pestana {
                case .ahora: Ahora(modoInicial: ir.modoAhora ?? modo)
                case .explorar: Explorar()
                case .mis: MisPlanes()
                case .perfil: MiPerfil()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            if !ir.tabBarOculta {
                TabBar(activa: ir.pestana, alTocar: { ir.pestana = $0 }, alCrear: { ir.crear() })
                    .transition(.opacity)
            }
        }
        .animation(.easeOut(duration: 0.2), value: ir.tabBarOculta)
        // El permiso de avisos se pide al llegar a la app, no en medio del alta.
        .task { Push.pedirPermiso() }
        .ocultarBarraNavegacion()
    }
}

/// Tab bar flotante (README · Navegación): 18 a los costados, 24 abajo, 70 de alto, radio 35.
/// En el medio, el isotipo de Caigo de 68 que sobresale (Crear).
struct TabBar: View {
    var activa: Pestana
    var alTocar: (Pestana) -> Void
    var alCrear: () -> Void
    @Environment(\.paleta) private var p

    var body: some View {
        HStack(spacing: 0) {
            item("Ahora", I.reloj, .ahora)
            item("Explorar", I.lupa, .explorar)
            Button(action: alCrear) {
                ZStack {
                    // drop-shadow(0 10px 12px rgba(116,86,241,.45)) del isotipo
                    Color.clear.frame(width: 54, height: 54).gel(17, nil, [externa(10, 12, rgba(116, 86, 241, 0.45))])
                    Image("CaigoIcono").resizable().frame(width: 68, height: 68)
                }
                .frame(width: 68, height: 68)
            }
            .buttonStyle(EstiloToque())
            .offset(y: -22)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .accessibilityLabel("Crear un plan")
            item("Mis planes", I.calendario, .mis)
            item("Perfil", I.usuario, .perfil)
        }
        .padding(.horizontal, 6)
        .frame(height: 70)
        .gel(35, p.oscuro ? Gels.tabBarOscura : Gels.tabBar)
        .padding(.horizontal, 18)
        .padding(.bottom, 24)
        .ignoresSafeArea(.keyboard)
    }

    func item(_ l: String, _ d: String, _ t: Pestana) -> some View {
        let on = t == activa
        let color = on ? p.tabActivo : p.tabInactivo
        return Button { alTocar(t) } label: {
            VStack(spacing: 2) {
                Icono(d, 24, color, trazo: 2.2)
                T(l, 10.5, on ? 800 : 500, color, unRenglon: true)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background { if on { Color.clear.gel(28, p.oscuro ? Gels.tabPillOscura : Gels.tabPill) } }
            .padding(.horizontal, 3).padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(EstiloPlano())
        .frame(maxWidth: .infinity)
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

extension View {
    /// Esconde la tab bar mientras `oculta` sea true (una hoja abierta adentro de una pestaña).
    func ocultarTabBar(_ oculta: Bool, _ ir: Ir) -> some View {
        onChange(of: oculta, initial: true) { _, o in ir.tabBarOculta = o }
            .onDisappear { ir.tabBarOculta = false }
    }
}
