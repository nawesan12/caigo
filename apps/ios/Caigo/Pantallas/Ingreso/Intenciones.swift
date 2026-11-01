import SwiftUI
import Nucleo

/// Una intención del onboarding del prototipo, con la clave que entiende la API.
private struct Intencion {
    var api: String
    var titulo: String
    var bajada: String
    var icono: String
    var fondo: Fondo
    var trazo: Color
}

private let intencionesTodas: [Intencion] = [
    Intencion(api: "planes", titulo: "Sumarme a planes", bajada: "Partidos, previas y mesas a las que les falta uno", icono: I.mas,
              fondo: .vertical(Fondo.paradas((0, C.lima0), (0.6, C.lima), (1, C.lima3))), trazo: C.carbon),
    Intencion(api: "gente", titulo: "Conocer gente nueva", bajada: "Personas de a una con ganas de salir", icono: I.gente,
              fondo: .vertical(Fondo.paradas((0, C.violeta1), (0.6, C.violeta), (1, C.violeta4))), trazo: C.blanco),
    Intencion(api: "completar", titulo: "Completar mis planes", bajada: "Ya tengo el plan, me falta gente", icono: I.calendario,
              fondo: .vertical(C.oscuro1, C.carbon), trazo: C.lima),
    Intencion(api: "citas", titulo: "Algo más romántico", bajada: "Citas, citas dobles y ver qué onda", icono: I.corazonChico,
              fondo: .vertical(Fondo.paradas((0, C.coral0), (0.6, C.coral), (1, C.coral2))), trazo: C.carbon),
]

/// Los intereses del prototipo (LIKES).
private let interesesTodos = [
    "Fútbol 5", "Pádel", "Truco", "Previas", "After beach", "Surf", "Cine", "Asado", "Running",
    "Juegos de mesa", "Música en vivo", "Mate en la playa",
]

private let gelIntencionOn = Gel(fondo: .liso(C.lavanda), sombras: [
    interna(0, 0, C.violeta, spread: -2.5), externa(4, 0, C.borde5), externa(14, 24, rgba(116, 86, 241, 0.5), spread: -12),
])
private let gelIntencionOff = Gel(fondo: .liso(C.blanco), sombras: [
    interna(1, 0, C.blanco), externa(3, 0, C.borde1), externa(12, 22, rgba(80, 60, 160, 0.3), spread: -14),
])

/// Onboarding de intenciones del prototipo: "¿Qué buscás en Caigo?" (al menos una) y "¿Qué te copa?".
/// Al terminar arranca en Planes, Gente o Citas según lo elegido.
struct Intenciones: View {
    var editar: Bool
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var paso = 0
    @State private var elegidas: Set<String> = []
    @State private var copan: Set<String> = []
    @State private var cargando = false
    @State private var cargado = false

    var body: some View {
        Pantalla {
            ZStack(alignment: .top) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        if paso == 0 { pasoIntenciones } else { pasoIntereses }
                    }
                    .padding(.horizontal, 20).padding(.top, 62).padding(.bottom, 160)
                    .id(paso)
                    .transition(.opacity)
                }
                if paso == 1 || editar {
                    HStack {
                        BotonIcono(d: I.atras, trazo: 2.4, gel: Gels.volver) {
                            if paso == 1 { withAnimation(.easeOut(duration: 0.2)) { paso = 0 } } else { ir.atras() }
                        }
                        Spacer()
                    }
                    .padding(.leading, 20).padding(.top, 8)
                }
                VStack(spacing: 16) {
                    Spacer()
                    IngresoPuntos(activo: paso, total: 2, on: C.violeta, off: Color(hex: 0xD9D3EE))
                    CtaGrande(paso == 0 ? "Seguir" : (editar ? "Listo" : "Listo, mostrame Caigo"), cargando: cargando) {
                        if paso == 0 {
                            if elegidas.isEmpty { c.avisos.error("Elegí al menos una, después la cambiás.") }
                            else { withAnimation(.easeOut(duration: 0.22)) { paso = 1 } }
                        } else {
                            terminar()
                        }
                    }
                }
                .padding(.horizontal, 24).padding(.bottom, 34)
            }
        }
        .environment(\.colorScheme, .light)
        .onAppear {
            guard !cargado else { return }
            cargado = true
            elegidas = Set(c.yo?.intenciones ?? [])
            copan = Set(c.yo?.intereses ?? [])
        }
    }

    @ViewBuilder private var pasoIntenciones: some View {
        T("¿Qué buscás en Caigo?", 34, 800, C.carbon, alto: 1.02, espaciado: -1)
        T("Elegí todas las que quieras. Armamos la app según esto y lo cambiás cuando quieras.", 15, 400, C.texto2, alto: 1.5)
            .padding(.bottom, 6)
        ForEach(intencionesTodas, id: \.api) { it in
            let on = elegidas.contains(it.api)
            Button {
                if on { elegidas.remove(it.api) } else { elegidas.insert(it.api) }
            } label: {
                HStack(spacing: 14) {
                    Icono(it.icono, 24, it.trazo, trazo: 2.6)
                        .frame(width: 52, height: 52)
                        .gel(18, it.fondo, [interna(2, 1, rgba(255, 255, 255, 0.45)), interna(-3, 0, rgba(0, 0, 0, 0.15))])
                    VStack(alignment: .leading, spacing: 2) {
                        T(it.titulo, 16, 800, C.carbon)
                        T(it.bajada, 13, 500, C.texto2, alto: 1.35)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if on {
                        Icono(I.check, 14, C.lima, trazo: 3.6)
                            .frame(width: 28, height: 28)
                            .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(C.violeta))
                    } else {
                        RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Color(hex: 0xD9D3EE), lineWidth: 2)
                            .frame(width: 28, height: 28)
                    }
                }
                .padding(14)
                .gel(24, on ? gelIntencionOn : gelIntencionOff)
            }
            .buttonStyle(EstiloToque(baja: 2))
            .accessibilityAddTraits(on ? .isSelected : [])
        }
    }

    @ViewBuilder private var pasoIntereses: some View {
        T("¿Qué te copa?", 34, 800, C.carbon, alto: 1.02, espaciado: -1)
        T("Te mostramos primero planes y gente con lo mismo.", 15, 400, C.texto2, alto: 1.5).padding(.bottom, 6)
        IngresoFilas(espacio: 9) {
            ForEach(interesesTodos, id: \.self) { l in
                let on = copan.contains(l)
                Chip(texto: l, on: on, tam: 15, padH: 16, padV: 12, radio: 17) {
                    if on { copan.remove(l) } else { copan.insert(l) }
                }
            }
        }
    }

    private func terminar() {
        var cambios = CambiosPerfil()
        cambios.intenciones = intencionesTodas.map(\.api).filter { elegidas.contains($0) }
        cambios.intereses = interesesTodos.filter { copan.contains($0) }
        cargando = true
        Task {
            defer { cargando = false }
            do {
                c.yo = try await c.api.actualizarYo(cambios)
                if editar {
                    ir.atras()
                } else {
                    // La regla del prototipo: si sólo busca gente o citas, arranca mostrando personas.
                    let soloGente = (elegidas.contains("gente") || elegidas.contains("citas")) && !elegidas.contains("planes") && !elegidas.contains("completar")
                    let modo = !soloGente ? "planes" : (elegidas.contains("citas") && !elegidas.contains("gente") ? "citas" : "gente")
                    c.avisos.ok(soloGente ? "Listo. Arrancamos mostrándote gente cerca." : "Listo. Arrancamos con los planes de las próximas horas.")
                    if c.yo?.faltan.isEmpty == false { ir.alta(.completarPerfil) } else { ir.principal(.ahora, modo: modo) }
                }
            } catch {
                c.avisos.error(ingresoMensaje(error))
            }
        }
    }
}

/// Filas que bajan enteras al renglón de abajo (FlowRow): los chips nunca se parten.
struct IngresoFilas: Layout {
    var espacio: CGFloat = 8
    var alineacion: HorizontalAlignment = .leading

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let filas = armar(ancho: proposal.width ?? .infinity, subviews)
        let alto = filas.map(\.alto).reduce(0, +) + espacio * CGFloat(max(filas.count - 1, 0))
        let ancho = filas.map(\.ancho).max() ?? 0
        return CGSize(width: proposal.width ?? ancho, height: alto)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for f in armar(ancho: bounds.width, subviews) {
            var x = bounds.minX + (alineacion == .center ? (bounds.width - f.ancho) / 2 : 0)
            for i in f.indices {
                let t = subviews[i].sizeThatFits(.unspecified)
                subviews[i].place(at: CGPoint(x: x, y: y + (f.alto - t.height) / 2), proposal: .unspecified)
                x += t.width + espacio
            }
            y += f.alto + espacio
        }
    }

    private struct Fila { var indices: [Int] = []; var ancho: CGFloat = 0; var alto: CGFloat = 0 }

    private func armar(ancho: CGFloat, _ subviews: Subviews) -> [Fila] {
        var filas: [Fila] = []
        var f = Fila()
        for (i, s) in subviews.enumerated() {
            let t = s.sizeThatFits(.unspecified)
            let nuevo = f.indices.isEmpty ? t.width : f.ancho + espacio + t.width
            if !f.indices.isEmpty && nuevo > ancho {
                filas.append(f)
                f = Fila()
            }
            f.ancho = f.indices.isEmpty ? t.width : f.ancho + espacio + t.width
            f.alto = max(f.alto, t.height)
            f.indices.append(i)
        }
        if !f.indices.isEmpty { filas.append(f) }
        return filas
    }
}
