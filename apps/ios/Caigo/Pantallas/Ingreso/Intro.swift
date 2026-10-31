import SwiftUI
import Nucleo

/// Las medidas del mockup (390×844) llevadas al tamaño real de la pantalla.
struct IngresoEscala {
    var w: CGFloat
    var h: CGFloat
    func x(_ v: CGFloat) -> CGFloat { w * v / 390 }
    func y(_ v: CGFloat) -> CGFloat { h * v / 844 }
}

/// 01a–01c: tres slides con swipe. Al terminar queda marcada la intro y se va a ingresar.
struct Intro: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var pagina = 0

    var body: some View {
        TabView(selection: $pagina) {
            IngresoSlidePlanes(saltar: terminar, avanzar: avanzar).tag(0)
            IngresoSlideAmigos(saltar: terminar, avanzar: avanzar).tag(1)
            IngresoSlideAfuera(terminar: terminar).tag(2)
        }
        .modifier(IngresoPaginado())
        .ignoresSafeArea()
        .background(C.grafito.ignoresSafeArea())
        .ocultarBarraNavegacion()
        .preferredColorScheme(pagina == 1 ? nil : .dark)
    }

    private func avanzar() { withAnimation(.easeInOut(duration: 0.3)) { pagina = min(pagina + 1, 2) } }

    private func terminar() {
        c.marcarIntro()
        ir.alta(.ingresar)
    }
}

/// TabView paginado (en la Mac no existe: ahí sólo se chequean tipos).
struct IngresoPaginado: ViewModifier {
    func body(content: Content) -> some View {
        #if os(iOS)
        content.tabViewStyle(.page(indexDisplayMode: .never))
        #else
        content
        #endif
    }
}

/// CTA "Siguiente →" de la intro.
private struct Siguiente: View {
    var alto: CGFloat
    var accion: () -> Void
    var body: some View {
        Button(action: accion) {
            HStack(spacing: 10) {
                T("Siguiente", 17, 800, C.grafito)
                Icono(I.flecha, 22, C.grafito, trazo: 2.6)
            }
            .padding(.leading, 26).padding(.trailing, 22)
            .frame(height: alto)
            .gel(22, Gels.limaChico)
        }
        .buttonStyle(EstiloToque())
    }
}

/// 01a · Planes a los que les falta gente.
struct IngresoSlidePlanes: View {
    var saltar: () -> Void
    var avanzar: () -> Void

    var body: some View {
        GeometryReader { g in
            let e = IngresoEscala(w: g.size.width, h: g.size.height + g.safeAreaInsets.top + g.safeAreaInsets.bottom)
            ZStack(alignment: .top) {
                C.grafito
                Image("IngresoPrevia").resizable().scaledToFill()
                    .frame(width: g.size.width, height: e.y(560))
                    .clipped()
                LinearGradient(stops: Fondo.paradas(
                    (0, rgba(32, 29, 43, 0.55)), (0.18, rgba(32, 29, 43, 0)), (0.46, rgba(32, 29, 43, 0)),
                    (0.60, rgba(32, 29, 43, 0.85)), (0.67, C.grafito), (1, C.grafito)), startPoint: .top, endPoint: .bottom)
                VStack(spacing: 0) {
                    HStack {
                        T("Caigo", 30, 700, C.blancoCalido, alto: 1, espaciado: espaciadoTitulo(30))
                        Spacer()
                        IngresoSaltar(alfa: 0.18, accion: saltar)
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, g.safeAreaInsets.top + 12)
                    Spacer(minLength: 0)
                    // Tarjeta flotante de ejemplo del handoff.
                    HStack(spacing: 10) {
                        ZStack(alignment: .leading) {
                            ForEach(Array([("N", C.violeta, C.blanco), ("L", C.coral, C.carbon), ("C", C.lima, C.carbon)].enumerated()), id: \.offset) { i, a in
                                Avatar(nombre: a.0, foto: nil, tam: 30, borde: 2.5, colorBorde: C.blanco, fondo: (a.1, a.2), tamTexto: 12)
                                    .offset(x: CGFloat(21 * i))
                            }
                        }
                        .frame(width: 30 + 21 * 2, alignment: .leading)
                        VStack(alignment: .leading, spacing: 0) {
                            T("Previa en La Perla", 13, 800, C.carbon, unRenglon: true)
                            HStack(spacing: 6) {
                                Circle().fill(C.coral).frame(width: 7, height: 7)
                                T("Ahora · 900 m", 12, 600, C.texto2, unRenglon: true)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        BadgeFalta(faltan: 1, tam: 15)
                    }
                    .padding(.leading, 16).padding(.vertical, 12).padding(.trailing, 12)
                    .gel(24, Gel(fondo: .vertical(rgba(255, 255, 255, 0.97), rgba(246, 243, 255, 0.94)),
                                 sombras: [interna(1.5, 0, C.blanco), externa(22, 40, rgba(0, 0, 0, 0.6), spread: -14)]))
                    .padding(.horizontal, 20)
                    Spacer().frame(height: e.y(64))
                    VStack(alignment: .leading, spacing: 12) {
                        T("Planes a los que les falta gente.", 40, 800, C.blancoCalido, alto: 1, espaciado: -1.4)
                        T("Una previa, un partido, una mesa de truco. Pasando ahora, cerca tuyo.", 15, 400, Color(hex: 0xC9C4DA), alto: 1.5)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 26)
                    Spacer().frame(height: e.y(42))
                    HStack {
                        IngresoPuntos(activo: 0, total: 3, on: C.lima, off: Color(hex: 0x4A4460))
                        Spacer()
                        Siguiente(alto: 58, accion: avanzar)
                    }
                    .padding(.horizontal, 26)
                    .padding(.bottom, 38 + g.safeAreaInsets.bottom)
                }
            }
            .ignoresSafeArea()
        }
    }
}

/// 01b · Sumate solo o con amigos.
struct IngresoSlideAmigos: View {
    var saltar: () -> Void
    var avanzar: () -> Void
    @Environment(Contenedor.self) private var c
    @State private var cuantos = 1
    /// Lo que se guarda en `c.somos` (con cuántos se suma por defecto) para cada opción.
    private static let personas = [1, 2, 4]

    private var gelVioleta: Gel {
        Gel(fondo: Gels.fondoVioleta, sombras: [
            interna(3, 2, rgba(255, 255, 255, 0.45)), interna(-5, 0, rgba(40, 20, 140, 0.35)), externa(4, 0, C.violetaBase),
            externa(16, 26, rgba(116, 86, 241, 0.75), spread: -10),
        ])
    }

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .top) {
                C.blancoCalido
                Grano()
                Image("IngresoAmigos").resizable().scaledToFill()
                    .frame(width: g.size.width, height: max(0, g.size.height + g.safeAreaInsets.top + g.safeAreaInsets.bottom - 300))
                    .clipped()
                LinearGradient(colors: [rgba(32, 29, 43, 0.5), rgba(32, 29, 43, 0)], startPoint: .top, endPoint: .bottom).frame(height: 140)
                VStack(spacing: 0) {
                    HStack {
                        Spacer()
                        IngresoSaltar(alfa: 0.22, accion: saltar)
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, g.safeAreaInsets.top + 12)
                    Spacer(minLength: 0)
                    // Píldora "Cita doble" sobre la foto, siempre arriba de la hoja.
                    HStack(spacing: 10) {
                        HStack(spacing: -8) {
                            Avatar(nombre: "C", foto: nil, tam: 38, colorBorde: C.blanco, fondo: (C.violeta, C.blanco), tamTexto: 15)
                            Avatar(nombre: "L", foto: nil, tam: 38, colorBorde: C.blanco, fondo: (C.coral, C.carbon), tamTexto: 15)
                        }
                        Icono(I.mas, 16, C.lima, trazo: 4)
                            .frame(width: 34, height: 34)
                            .gel(12, gelVioleta)
                            .padding(.leading, -8)
                        T("Cita doble", 17, 700, C.carbon).padding(.leading, -8)
                    }
                    .padding(.leading, 18).padding(.vertical, 10).padding(.trailing, 16)
                    .gel(22, Gel(fondo: .vertical(rgba(255, 255, 255, 0.95), rgba(248, 246, 255, 0.9)),
                                 sombras: [interna(1.5, 0, C.blanco), externa(20, 40, rgba(20, 10, 60, 0.55), spread: -14)]))
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, 24).padding(.bottom, 22)
                    // Hoja de abajo: alto natural, pegada abajo.
                    VStack(alignment: .leading, spacing: 18) {
                        HStack(spacing: 0) {
                            ForEach(Array(["Voy solo", "Somos 2", "Somos 4"].enumerated()), id: \.offset) { i, o in
                                let on = i == cuantos
                                T(o, 15, on ? 800 : 600, on ? C.blanco : C.texto2, unRenglon: true)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 46)
                                    .background { if on { Color.clear.gel(16, gelVioleta) } }
                                    .contentShape(Rectangle())
                                    .onTapGesture {
                                        withAnimation(.easeOut(duration: 0.18)) { cuantos = i }
                                        c.somos = Self.personas[i]
                                    }
                            }
                        }
                        .padding(5)
                        .gel(20, .vertical(C.tinte1, C.tinte5), [interna(2, 4, rgba(116, 86, 241, 0.15))])
                        T("Sumate solo o con amigos.", 40, 800, C.carbon, alto: 1, espaciado: -1.4)
                        T("Entrás con tu gente en un toque. Así salen las citas dobles y los partidos grupo contra grupo.", 16, 400, C.texto2, alto: 1.5)
                        HStack {
                            IngresoPuntos(activo: 1, total: 3, on: C.violeta, off: Color(hex: 0xD9D3EE))
                            Spacer()
                            Siguiente(alto: 60, accion: avanzar)
                        }
                        .padding(.top, 8)
                    }
                    .padding(.horizontal, 26).padding(.top, 30)
                    .padding(.bottom, 40 + g.safeAreaInsets.bottom)
                    .background {
                        ZStack { C.blancoCalido; Grano() }
                            .clipShape(UnevenRoundedRectangle(topLeadingRadius: 36, topTrailingRadius: 36, style: .continuous))
                    }
                }
            }
            .ignoresSafeArea()
        }
        .environment(\.colorScheme, .light)
        .onAppear {
            // Lo que ya eligió. Si nunca tocó nada se ve «Somos 2» (el del handoff), pero no se guarda.
            if UserDefaults.standard.object(forKey: "somos") != nil, let k = Self.personas.firstIndex(of: c.somos) { cuantos = k }
        }
    }
}

/// 01c · Lo mejor pasa afuera.
struct IngresoSlideAfuera: View {
    var terminar: () -> Void
    var body: some View {
        GeometryReader { g in
            ZStack {
                IngresoFondoVioleta()
                VStack(spacing: 0) {
                    IngresoZonaFogon()
                    VStack(alignment: .leading, spacing: 12) {
                        T("Lo mejor pasa afuera.", 46, 800, C.blanco, alto: 0.98, espaciado: -1.8)
                        T("Compartís un plan y conocés gente. Sin swipes, sin chats eternos.", 16, 400, C.blanco, alto: 1.5)
                        Spacer().frame(height: 14)
                        VStack(spacing: 14) {
                            Cta(texto: "Empezar", alto: 62, radio: 22, tam: 18, color: C.carbon, icono: I.mas, iconoTrazo: 4, iconoTam: 18, accion: terminar)
                            Button(action: terminar) { T("Ya tengo cuenta", 15, 700, C.blanco).padding(4) }.buttonStyle(EstiloPlano())
                        }
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.horizontal, 24)
                    .padding(.bottom, 34 + g.safeAreaInsets.bottom)
                }
            }
            .ignoresSafeArea()
        }
    }
}

/// La composición del fogón (y 110…530 del mockup) en el espacio que queda arriba del texto: se
/// escala a ese alto para que nunca se pise con el título en pantallas más bajas que el mockup.
struct IngresoZonaFogon: View {
    var body: some View {
        GeometryReader { g in
            let w = min(g.size.width, g.size.height * (390 / 520))
            let e = IngresoEscala(w: w, h: g.size.height * (844 / 560))
            IngresoComposicionFogon(e: e)
                .frame(width: w, height: g.size.height, alignment: .topLeading)
                .frame(maxWidth: .infinity)
        }
    }
}

/// La foto del fogón inclinada, el isotipo y el chip "Ya somos 6" de 01c.
struct IngresoComposicionFogon: View {
    var e: IngresoEscala
    var body: some View {
        ZStack(alignment: .topLeading) {
            Image("IngresoFogon").resizable().scaledToFill()
                .frame(width: e.x(300), height: e.y(360))
                .clipShape(RoundedRectangle(cornerRadius: 34, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: 34, style: .continuous).strokeBorder(C.blancoCalido, lineWidth: 6))
                .background(Color.clear.gel(34, nil, [externa(30, 60, rgba(20, 10, 80, 0.7), spread: -20)]))
                .rotationEffect(.degrees(-4))
                .offset(x: e.x(36), y: e.y(110))
            ZStack {
                Color.clear.frame(width: 104, height: 104).gel(30, nil, [externa(22, 22, rgba(20, 10, 80, 0.5))])
                Image("CaigoIcono").resizable().frame(width: 130, height: 130)
            }
            .frame(width: 130, height: 130)
            .rotationEffect(.degrees(12))
            .offset(x: e.w - e.x(22) - 130, y: e.y(400))
            T("Ya somos 6", 15, 700, C.carbon, alto: 1)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .gel(14, Gels.limaChico)
                .rotationEffect(.degrees(6))
                .fixedSize()
                .frame(width: e.w - e.x(30), alignment: .trailing)
                .offset(y: e.y(128))
        }
    }
}
