import SwiftUI
import AVFoundation
import CoreImage
import CoreImage.CIFilterBuiltins
import Nucleo

/// Tile violeta grande del check-in (120, radio 40). En el CSS la segunda box-shadow pisa a la primera.
private let tileCheckin = Gel(fondo: Gels.fondoVioleta, sombras: [externa(18, 40, rgba(116, 86, 241, 0.4))])

/// Brillo arriba a la izquierda de los círculos.
private func miosBrillo() -> some View {
    Circle().fill(RadialGradient(stops: Fondo.paradas((0, rgba(255, 255, 255, 0.6)), (0.42, rgba(255, 255, 255, 0))),
                                 center: UnitPoint(x: 0.3, y: 0.25), startRadius: 0, endRadius: 300))
}

/// 09a · Check-in: "Llegué" con la ubicación; si estás lejos, el QR del organizador.
struct Checkin: View {
    var planId: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var plan: Plan?
    @State private var ocupado = false
    @State private var lejos = false
    @State private var listo = false
    @State private var compartiendo = false

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                HStack {
                    BotonIcono(d: I.cruz, iconoTam: 20, trazo: 2.4, gel: Gels.chip(p), color: p.texto) { ir.atras() }
                    T([plan?.titulo, Fecha.hora(Date())].compactMap { $0 }.joined(separator: " · "), 14, 700, p.texto2, alinear: .center, unRenglon: true)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 8)
                    Color.clear.frame(width: 44, height: 44)
                }
                .padding(.horizontal, 16).padding(.top, 8)
                Spacer(minLength: 0).frame(maxHeight: .infinity).layoutPriority(-0.4)
                circulos
                let lugar = plan?.lugar?.nombre ?? (plan?.lugarNombre.isEmpty == false ? plan!.lugarNombre : (plan?.zona ?? ""))
                VStack(spacing: 10) {
                    T(listo ? "¡Llegaste!" : "Estás en \(lugar)", 32, 700, alto: 1.05, espaciado: -0.8, alinear: .center)
                    T("Hacé check-in para que cuente tu asistencia.", 15, 400, p.texto2, alto: 1.5, alinear: .center)
                }
                .padding(.horizontal, 24).padding(.top, 30)
                Spacer(minLength: 16)
                VStack(spacing: 14) {
                    if lejos && !listo {
                        Cta(texto: "Escanear el QR del organizador", gel: Gels.violeta, color: C.blanco, icono: I.qr, iconoTrazo: 2.2, iconoTam: 20) {
                            ir.escanearQr(planId)
                        }
                    }
                    Cta(texto: "Llegué", icono: I.check, iconoTrazo: 3, iconoTam: 20, habilitado: !listo && plan != nil, cargando: ocupado) { llegue() }
                    Button(action: compartirEnVivo) {
                        HStack(spacing: 6) {
                            let col = p.oscuro ? C.lima : C.violetaTexto
                            Icono(I.escudo, 16, col, trazo: 2.2)
                            T("Compartir mi plan en vivo", 14, 700, col)
                        }
                        .padding(4)
                    }
                    .buttonStyle(EstiloPlano())
                }
                .padding(.horizontal, 20).padding(.bottom, 34)
            }
        }
        .task { plan = try? await c.api.plan(planId) }
    }

    /// Círculos concéntricos con el tile violeta y el pin lima.
    private var circulos: some View {
        ZStack {
            Circle().fill(p.oscuro ? C.oscuro3 : C.lavanda).overlay(miosBrillo()).frame(width: 300, height: 300)
            Circle().fill(p.oscuro ? C.oscuro2 : C.pista).overlay(miosBrillo()).frame(width: 210, height: 210)
            Group {
                if listo {
                    Icono(I.check, 54, C.lima, trazo: 3).frame(width: 120, height: 120).gel(40, tileCheckin).pop()
                } else {
                    Icono(I.pin, 54, C.lima, trazo: 2.4).frame(width: 120, height: 120).gel(40, tileCheckin)
                }
            }
        }
    }

    private func llegue() {
        ocupado = true
        Task {
            defer { ocupado = false }
            guard let u = await c.ubicacion() else {
                c.avisos.error("No pudimos saber dónde estás. Escaneá el QR del organizador.")
                lejos = true
                return
            }
            do {
                try await c.api.checkin(planId, Api.Llegada(metodo: "geo", lat: u.0, lng: u.1))
                withAnimation { listo = true }
                Sistema.vibrar()
                c.avisos.ok("Listo, tu asistencia quedó registrada.")
                try? await Task.sleep(nanoseconds: 1_200_000_000)
                ir.atras()
            } catch {
                if let e = error as? ErrorApi, e.codigo == "lejos" { lejos = true }
                c.avisos.error(error)
            }
        }
    }

    private func compartirEnVivo() {
        guard !compartiendo else { return }
        compartiendo = true
        Task {
            do { Sistema.compartir(try await c.api.enVivo(planId).link) } catch { c.avisos.error(error) }
            compartiendo = false
        }
    }
}

private let motivosRepetimos: [(String, String)] = [
    ("acoso", "Acoso"), ("falso", "Perfil falso"), ("no_vino", "No vino"), ("inapropiado", "Algo inapropiado"),
    ("inseguro", "Me sentí insegura o inseguro"), ("otro", "Otro"),
]

/// 09b · ¿Repetimos? y "¿Volverías a salir con…?" (privado).
struct Repetimos: View {
    var planId: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var plan: Plan?
    @State private var error: String?
    @State private var volveria: [String: Bool] = [:]
    @State private var mandando: Bool?
    @State private var reportar = false

    /// `radial-gradient(120% 70% at 85% 0%, #A38FFF, #7456F1 45%, #5A3DD8)`.
    private var fondo: some View {
        ZStack {
            C.violeta
            VistaFondo(fondo: .radial(cx: 0.85, cy: 0, rx: 1.2, ry: 0.7, Fondo.paradas((0, Color(hex: 0xA38FFF)), (0.45, C.violeta), (1, Color(hex: 0x5A3DD8)))))
        }
    }

    var body: some View {
        Pantalla(fondo: AnyView(fondo)) {
            if let pl = plan {
                contenido(pl)
            } else if let error {
                T(error, 15, 600, C.blanco, alinear: .center).padding(28)
            } else {
                Puntitos(color: C.lima)
            }
            MiosHoja(abierta: reportar, titulo: "¿Qué pasó?", cerrar: { reportar = false }) {
                T("Lo revisa una persona del equipo, no un bot.", 13, 400, p.texto2)
                MiosGrupo {
                    ForEach(Array(motivosRepetimos.enumerated()), id: \.offset) { i, m in
                        MiosFila(icono: I.bloqueo, titulo: m.1, colorIcono: C.coral2, ultima: i == motivosRepetimos.count - 1) {
                            reportar = false
                            Task {
                                do {
                                    try await c.api.reportar(Reporte(planId: planId, motivo: m.0))
                                    c.avisos.ok("Gracias. Lo revisamos en menos de 24 h.")
                                } catch { c.avisos.error(error) }
                            }
                        }
                    }
                }
            }
        }
        .task {
            do { plan = try await c.api.plan(planId) } catch { self.error = mensajeDe(error) }
        }
    }

    private func cuando(_ t: Date) -> String {
        switch Fecha.diasEntre(t, Date()) {
        case 0: return "hoy"
        case 1: return Fecha.calendario.component(.hour, from: t) >= 19 ? "anoche" : "ayer"
        default: return "el \(Fecha.diaLargo(t).lowercased())"
        }
    }

    private func contenido(_ pl: Plan) -> some View {
        let t = Fecha.parse(pl.empiezaEn)
        let gente = ([pl.organizador] + pl.van).reduce(into: [PersonaCorta]()) { a, g in
            if !g.id.isEmpty && g.id != c.usuarioId && !a.contains(where: { $0.id == g.id }) { a.append(g) }
        }
        return VStack(spacing: 0) {
            VStack(alignment: .leading, spacing: 18) {
                HStack(spacing: 10) {
                    BotonIcono(d: I.cruz, tam: 32, radio: 11, iconoTam: 16, trazo: 2.6, gel: Gel(fondo: .liso(C.violetaTexto)), color: C.blanco) { ir.atras() }
                    T([pl.titulo, t.map(cuando)].compactMap { $0 }.joined(separator: " · "), 13, 700, C.blanco, unRenglon: true)
                        .padding(.horizontal, 11).padding(.vertical, 6)
                        .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(C.violetaTexto))
                }
                T("¿Repetimos?", 52, 800, C.blanco, alto: 0.95, espaciado: -2, unRenglon: true)
                HStack(spacing: 10) {
                    Cta(texto: t.map { "Sí, el \(Fecha.diaLargo($0).lowercased())" } ?? "Sí", alto: 54, radio: 18, tam: 16, color: C.carbon,
                        habilitado: mandando == nil || mandando == true, cargando: mandando == true) { responder(true) }
                        .layoutPriority(1.4)
                    Button { responder(false) } label: {
                        Group { if mandando == false { Puntitos(color: C.blanco) } else { T("Esta vez no", 16, 700, C.blanco, unRenglon: true) } }
                            .frame(maxWidth: .infinity).frame(height: 54)
                            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Color(hex: 0xA897F7), lineWidth: 2))
                    }
                    .buttonStyle(EstiloToque())
                    .disabled(mandando != nil)
                }
            }
            .padding(.horizontal, 24).padding(.top, 26).padding(.bottom, 26)

            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 14) {
                    if !gente.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            T("¿Volverías a salir con…?", 22, 700)
                            T("Los que sumes a tu gente ven primero tus próximos planes. Nadie ve tus respuestas.", 13, 400, p.texto2, alto: 1.45)
                        }
                        ForEach(gente) { g in filaPersona(g, pl) }
                    }
                    HStack(spacing: 0) {
                        T("¿Pasó algo? ", 13, 600, p.texto2)
                        T("Reportar", 13, 700, p.oscuro ? C.lima : C.violetaTexto)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 24)
                    .contentShape(Rectangle())
                    .onTapGesture { reportar = true }
                }
                .padding(.horizontal, 20).padding(.top, 24).padding(.bottom, 14)
            }
            .background(UnevenRoundedRectangle(topLeadingRadius: 32, topTrailingRadius: 32, style: .continuous).fill(p.fondo).ignoresSafeArea(edges: .bottom))
        }
    }

    private func filaPersona(_ g: PersonaCorta, _ pl: Plan) -> some View {
        let si = volveria[g.id] == true || g.enMiGente
        return HStack(spacing: 12) {
            Avatar(nombre: g.nombre, foto: g.foto, tam: 44, borde: 0, tamTexto: 17).onTapGesture { ir.persona(g.id, desde: "repetimos") }
            VStack(alignment: .leading, spacing: 1) {
                T(miosPrimer(g.nombre), 15, 700)
                if g.organizador || g.id == pl.organizador.id { T("Organizó el plan", 12, 400, p.texto2) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if si {
                Button { if !g.enMiGente { volveria[g.id] = false } } label: {
                    HStack(spacing: 6) {
                        Icono(I.check, 14, C.carbon, trazo: 3)
                        T("Tu gente", 13, 800, C.carbon)
                    }
                    .padding(.horizontal, 12).frame(height: 40).gel(14, Gels.limaChico)
                }
                .buttonStyle(EstiloToque())
            } else {
                BotonIcono(d: I.mas, tam: 40, radio: 14, iconoTam: 16, trazo: 3, gel: Gels.chip(p), color: C.violeta) { volveria[g.id] = true }
                    .accessibilityLabel("Sumar a tu gente")
            }
        }
        .padding(12)
        .gel(20, miosGelTarjeta(p))
    }

    private func responder(_ repetir: Bool) {
        mandando = repetir
        Task {
            do {
                let r = try await c.api.repetimos(planId, repetir: repetir,
                                                  volveria: volveria.filter { $0.value }.map { Opinion(usuarioId: $0.key, si: true) })
                if repetir, let nuevo = r.nuevo {
                    c.avisos.ok("Listo, ya está el plan de la semana que viene.")
                    ir.atras()
                    ir.plan(nuevo.id)
                } else {
                    c.avisos.ok("Gracias. Nadie ve tus respuestas.")
                    ir.atras()
                }
            } catch {
                c.avisos.error(error)
            }
            mandando = nil
        }
    }
}

/// Un QR dibujado con CoreImage (sin suavizar, para que se lea bien).
struct MiosCodigoQr: View {
    var texto: String
    var body: some View {
        if let cg = MiosCodigoQr.imagen(texto) {
            Image(decorative: cg, scale: 1).interpolation(.none).resizable().scaledToFit()
                .accessibilityLabel("Código QR")
        }
    }

    static func imagen(_ texto: String) -> CGImage? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(texto.utf8)
        f.correctionLevel = "M"
        guard let salida = f.outputImage else { return nil }
        return CIContext().createCGImage(salida, from: salida.extent)
    }
}

/// Recuadro blanco con anillo lima y base (el del QR de "Tu entrada" del prototipo).
private let gelQr = Gel(fondo: .liso(C.blanco), sombras: [
    interna(0, 0, C.lima, spread: -3), externa(5, 0, C.limaBase), externa(30, 50, rgba(80, 60, 160, 0.45), spread: -24),
])

/// QR del check-in que muestra quien organiza (GET /v1/planes/{id}/qr).
struct QrOrganizador: View {
    var planId: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var plan: Plan?
    @State private var codigo: String?
    @State private var error: String?

    var body: some View {
        Pantalla {
            VStack(spacing: 16) {
                HStack(spacing: 14) {
                    BotonIcono(d: I.cruz, iconoTam: 20, trazo: 2.6, gel: p.oscuro ? Gels.chipOscuro : Gels.volver, color: p.texto) { ir.atras() }
                    T("Check-in", 26, 800, alto: 1, espaciado: -0.8)
                    Spacer()
                }
                if let pl = plan {
                    VStack(alignment: .leading, spacing: 4) {
                        T(pl.titulo, 20, 700, alto: 1.15)
                        T([Fecha.cuando(pl.empiezaEn), pl.lugar?.nombre ?? (pl.lugarNombre.isEmpty ? pl.zona : pl.lugarNombre)]
                            .filter { !$0.isEmpty }.joined(separator: " · "), 13, 600, p.texto2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                ZStack {
                    if let codigo {
                        MiosCodigoQr(texto: codigo)
                    } else if let error {
                        T(error, 14, 600, C.texto2, alinear: .center)
                    } else {
                        Puntitos()
                    }
                }
                .padding(20)
                .frame(maxWidth: .infinity)
                .aspectRatio(1, contentMode: .fit)
                .gel(30, gelQr)
                .padding(.horizontal, 28)
                .padding(.top, 10)
                T("Mostralo cuando llegue la gente: lo escanean y cuenta su asistencia.", 14, 600, p.texto2, alto: 1.5, alinear: .center)
                Spacer()
                T("Subí el brillo si está oscuro.", 13, 700, p.oscuro ? C.lima : C.violetaTexto)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(p.superficie))
            }
            .padding(.horizontal, 22).padding(.top, 8).padding(.bottom, 36)
        }
        .task {
            async let a = try? c.api.plan(planId)
            do { codigo = try await c.api.qr(planId).codigo } catch { self.error = mensajeDe(error) }
            plan = await a
        }
    }
}

/// Escanear el QR del organizador para hacer check-in (método qr).
struct EscanearQr: View {
    var planId: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var leyendo = false
    @State private var permiso = AVCaptureDevice.authorizationStatus(for: .video) == .authorized

    var body: some View {
        Pantalla(fondo: AnyView(C.oscuro6)) {
            ZStack {
                if permiso {
                    MiosEscaner { codigo in leer(codigo) }.ignoresSafeArea()
                }
                MiosEsquinas().stroke(C.lima, style: StrokeStyle(lineWidth: 4, lineCap: .round)).frame(width: 250, height: 250)
                VStack {
                    HStack(spacing: 14) {
                        BotonIcono(d: I.cruz, iconoTam: 20, trazo: 2.4, gel: Gels.chipOscuro, color: C.blanco) { ir.atras() }
                        T("Escaneá el QR del plan", 22, 700, C.blanco)
                        Spacer()
                    }
                    .padding(.horizontal, 20).padding(.top, 8)
                    Spacer()
                    T(!permiso ? "Necesitamos la cámara para leer el QR." : (leyendo ? "Leyendo…" : "Pedile el QR a quien organiza y apuntalo con la cámara."),
                      13, 700, C.blanco, alinear: .center)
                        .padding(.horizontal, 14).padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(rgba(255, 255, 255, 0.12)))
                        .padding(.horizontal, 24).padding(.bottom, 40)
                }
            }
        }
        .task {
            if !permiso { permiso = await AVCaptureDevice.requestAccess(for: .video) }
        }
    }

    private func leer(_ codigo: String) {
        guard !leyendo else { return }
        leyendo = true
        Task {
            do {
                try await c.api.checkin(planId, Api.Llegada(metodo: "qr", codigo: codigo))
                Sistema.vibrar()
                c.avisos.ok("Listo, tu asistencia quedó registrada.")
                ir.atras()
            } catch {
                c.avisos.error(error)
                try? await Task.sleep(nanoseconds: 2_500_000_000)
                leyendo = false
            }
        }
    }
}

/// Las cuatro esquinas del marco del escáner (como el del frente del DNI).
struct MiosEsquinas: Shape {
    var largo: CGFloat = 34
    func path(in r: CGRect) -> Path {
        var p = Path()
        for (o, dx, dy) in [(CGPoint(x: r.minX, y: r.minY), 1.0, 1.0), (CGPoint(x: r.maxX, y: r.minY), -1.0, 1.0),
                            (CGPoint(x: r.minX, y: r.maxY), 1.0, -1.0), (CGPoint(x: r.maxX, y: r.maxY), -1.0, -1.0)] {
            p.move(to: CGPoint(x: o.x + dx * largo, y: o.y))
            p.addLine(to: o)
            p.addLine(to: CGPoint(x: o.x, y: o.y + dy * largo))
        }
        return p
    }
}

#if os(iOS)
/// Cámara trasera que lee códigos QR (AVCaptureMetadataOutput).
struct MiosEscaner: UIViewRepresentable {
    var alLeer: (String) -> Void

    func makeCoordinator() -> Coordinador { Coordinador(alLeer: alLeer) }

    func makeUIView(context: Context) -> VistaCamara {
        let v = VistaCamara()
        let s = AVCaptureSession()
        if let cam = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
           let entrada = try? AVCaptureDeviceInput(device: cam), s.canAddInput(entrada) {
            s.addInput(entrada)
            let salida = AVCaptureMetadataOutput()
            if s.canAddOutput(salida) {
                s.addOutput(salida)
                salida.setMetadataObjectsDelegate(context.coordinator, queue: .main)
                salida.metadataObjectTypes = [.qr]
            }
        }
        v.capa.session = s
        v.capa.videoGravity = .resizeAspectFill
        context.coordinator.sesion = s
        DispatchQueue.global(qos: .userInitiated).async { s.startRunning() }
        return v
    }

    func updateUIView(_ uiView: VistaCamara, context: Context) { context.coordinator.alLeer = alLeer }

    static func dismantleUIView(_ uiView: VistaCamara, coordinator: Coordinador) {
        let s = coordinator.sesion
        DispatchQueue.global(qos: .userInitiated).async { s?.stopRunning() }
    }

    final class VistaCamara: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var capa: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    final class Coordinador: NSObject, AVCaptureMetadataOutputObjectsDelegate {
        var alLeer: (String) -> Void
        var sesion: AVCaptureSession?
        init(alLeer: @escaping (String) -> Void) { self.alLeer = alLeer }

        func metadataOutput(_ output: AVCaptureMetadataOutput, didOutput metadataObjects: [AVMetadataObject], from connection: AVCaptureConnection) {
            guard let o = metadataObjects.first as? AVMetadataMachineReadableCodeObject, let s = o.stringValue, !s.isEmpty else { return }
            alLeer(s)
        }
    }
}
#else
/// En la Mac (sólo para el chequeo de tipos) no hay cámara de escaneo.
struct MiosEscaner: View {
    var alLeer: (String) -> Void
    var body: some View { Color.black }
}
#endif
