import SwiftUI
import Nucleo
#if canImport(EventKitUI)
import EventKit
import EventKitUI
#endif

/// «¡Estás adentro!»: el festejo corto al entrar a un plan (sumarse quedó `aceptada`, desde el detalle, la
/// tarjeta, el gesto o «Decidí rápido»). Gel lima a pantalla completa, las caras de quienes van con la tuya
/// entrando al hueco «+ Vos», «Somos N de M» y tres salidas: el chat, la agenda y compartir. Se cierra con la X o
/// tocando afuera de los botones. Las caras entran en menos de 0,6 s; con «reducir movimiento», sin animación.
struct Festejo: View {
    var f: PlanFestejado
    var cerrar: () -> Void
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.accessibilityReduceMotion) private var sinMovimiento
    /// 0: nada; 1: entran las caras; 2: entra la tuya al hueco.
    @State private var fase = 0

    private var plan: Plan { f.plan }
    private var somos: Int { min(plan.total, plan.somos + f.cantidad) }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            ZStack {
                VistaFondo(fondo: Gels.fondoLima)
                Grano()
            }
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .onTapGesture(perform: cerrar)
            .accessibilityHidden(true)

            VStack(spacing: 0) {
                Spacer(minLength: 20)
                VStack(spacing: 22) {
                    caras
                    VStack(spacing: 8) {
                        T("¡Estás adentro!", 36, 800, C.carbon, alto: 1, espaciado: -1.2, alinear: .center)
                        T(plan.titulo, 16, 600, C.carbon, alto: 1.3, alinear: .center, renglones: 2)
                    }
                    T(Cupo.somos(somos, plan.total), 17, 800, C.limaTexto, unRenglon: true)
                }
                .padding(.horizontal, 28)
                .allowsHitTesting(false)
                .accessibilityElement(children: .combine)
                Spacer(minLength: 20)
                botones.padding(.horizontal, 20).padding(.bottom, 14)
            }

            BotonIcono(d: I.cruz, iconoTam: 18, trazo: 2.8, accion: cerrar)
                .accessibilityLabel("Cerrar")
                .padding(.top, 8).padding(.trailing, 16)
        }
        // Una capa encima de todo: para VoiceOver es modal (no se recorre lo de abajo), se anuncia y se cierra
        // con el gesto de escape.
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isModal)
        .accessibilityAction(.escape, cerrar)
        .task {
            AccessibilityNotification.ScreenChanged("¡Estás adentro!").post()
            if sinMovimiento { fase = 2; return }
            withAnimation(.easeOut(duration: 0.25)) { fase = 1 }
            try? await Task.sleep(nanoseconds: 220_000_000)
            withAnimation(.spring(response: 0.32, dampingFraction: 0.72)) { fase = 2 }
        }
    }

    /// Hasta 5 caras de los que van (Avatar, como en la tarjeta) y el hueco «+ Vos» que se llena con la tuya.
    private var caras: some View {
        let van = Array(plan.van.filter { $0.id != c.usuarioId }.prefix(5))
        let yo = c.yo
        return HStack(spacing: -12) {
            ForEach(Array(van.enumerated()), id: \.offset) { i, p in
                Avatar(nombre: p.nombre, foto: p.foto, tam: 56, borde: 3, colorBorde: C.lima1)
                    .zIndex(Double(van.count - i))
                    .opacity(fase >= 1 ? 1 : 0)
                    .offset(y: fase >= 1 ? 0 : 14)
            }
            ZStack {
                Circle().strokeBorder(C.carbon.opacity(0.35), style: StrokeStyle(lineWidth: 2.5, dash: [5, 4]))
                T("+ Vos", 13, 800, C.carbon, unRenglon: true).opacity(fase >= 2 ? 0 : 1)
                Avatar(nombre: yo?.nombre ?? "Vos", foto: yo?.fotos.first, tam: 64, borde: 3, colorBorde: C.carbon)
                    .scaleEffect(fase >= 2 ? 1 : 0.3)
                    .opacity(fase >= 2 ? 1 : 0)
            }
            .frame(width: 64, height: 64)
            .padding(.leading, van.isEmpty ? 0 : 20)
        }
        .accessibilityLabel(van.isEmpty ? "Vas vos" : "Van \(miosNombres(van.map { miosPrimer($0.nombre) })) y vos")
    }

    private var botones: some View {
        VStack(spacing: 10) {
            Cta(texto: "Saludar en el chat", gel: Gels.violeta, alto: 58, tam: 17, color: C.blanco, icono: I.enviar, iconoTrazo: 2.4, iconoTam: 18) {
                cerrar()
                ir.chat(plan.id)
            }
            HStack(spacing: 10) {
                Cta(texto: "Agendar", gel: Gels.blanco, alto: 52, tam: 14, color: C.carbon, icono: I.calendarioSimple, iconoTrazo: 2.4, iconoTam: 16) {
                    Agenda.agregar(plan, link: planesLink(plan))
                }
                Cta(texto: "Avisarle a alguien", gel: Gels.blanco, alto: 52, tam: 14, color: C.carbon) {
                    Sistema.compartir("\(plan.titulo) · \(Cupo.badge(max(0, plan.faltan - f.cantidad))). Sumate en Caigo: \(planesLink(plan))")
                }
            }
        }
    }
}

/// «Agendar»: la hoja del sistema para agregar el plan al calendario (EventKitUI; en iOS 17 no pide permiso).
@MainActor
enum Agenda {
    #if canImport(EventKitUI)
    private static let almacen = EKEventStore()
    /// El delegado tiene que vivir mientras la hoja está abierta.
    private static var delegado: Delegado?

    private final class Delegado: NSObject, EKEventEditViewDelegate {
        func eventEditViewController(_ controller: EKEventEditViewController, didCompleteWith action: EKEventEditViewAction) {
            controller.dismiss(animated: true)
            Task { @MainActor in Agenda.delegado = nil }
        }
    }
    #endif

    static func agregar(_ p: Plan, link: String) {
        #if canImport(EventKitUI)
        let e = EKEvent(eventStore: almacen)
        e.title = p.titulo
        let inicio = Fecha.parse(p.empiezaEn) ?? Date()
        e.startDate = inicio
        e.endDate = inicio.addingTimeInterval(Double(max(p.duracionMin, 30)) * 60)
        let lugar = p.lugar?.nombre ?? p.lugarNombre
        let dir = p.direccion?.texto ?? ""
        e.location = [lugar, dir.isEmpty ? (p.zona.isEmpty ? p.ciudad : p.zona) : dir].filter { !$0.isEmpty }.joined(separator: ", ")
        e.url = URL(string: link)
        let vc = EKEventEditViewController()
        vc.eventStore = almacen
        vc.event = e
        let d = Delegado()
        delegado = d
        vc.editViewDelegate = d
        Sistema.arriba()?.present(vc, animated: true)
        #endif
    }
}
