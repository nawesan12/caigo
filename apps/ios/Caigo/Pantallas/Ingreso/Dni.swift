import SwiftUI
import Nucleo

/// Pasos de la verificación con DNI (verify del prototipo).
private enum PasoDni: Int {
    case cargando = 0, frente, dorso, selfie, revisando, enviado, aprobado
}

/// Barras de progreso de los tres pasos (7): hechos violeta, el actual `#B3A3FF`.
private struct ProgresoDni: View {
    var paso: PasoDni
    var body: some View {
        HStack(spacing: 6) {
            ForEach(1...3, id: \.self) { i in
                Capsule()
                    .fill(i < paso.rawValue || paso.rawValue >= PasoDni.revisando.rawValue ? C.violeta : (i == paso.rawValue ? C.violeta1 : C.pista))
                    .frame(height: 7)
            }
        }
    }
}

/// Las cuatro esquinas lima del encuadre (34, 4 de trazo, radio 10).
private struct Esquinas: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let m: CGFloat = 24, l: CGFloat = 32, r: CGFloat = 10
        func esquina(_ x: CGFloat, _ y: CGFloat, _ dx: CGFloat, _ dy: CGFloat) {
            p.move(to: CGPoint(x: x, y: y + dy * l))
            p.addLine(to: CGPoint(x: x, y: y + dy * r))
            p.addQuadCurve(to: CGPoint(x: x + dx * r, y: y), control: CGPoint(x: x, y: y))
            p.addLine(to: CGPoint(x: x + dx * l, y: y))
        }
        esquina(rect.minX + m, rect.minY + m, 1, 1)
        esquina(rect.maxX - m, rect.minY + m, -1, 1)
        esquina(rect.minX + m, rect.maxY - m, 1, -1)
        esquina(rect.maxX - m, rect.maxY - m, -1, -1)
        return p
    }
}

/// Ícono de Caigo latiendo (tk-pop alternado) mientras se revisa.
private struct IconoLatiendo: View {
    @State private var k: CGFloat = 0
    var body: some View {
        Image("CaigoIcono").resizable().frame(width: 120, height: 120)
            .scaleEffect(CGFloat(0.9) + 0.18 * k)
            .rotationEffect(.degrees(Double(-6 + 10 * k)))
            .onAppear { withAnimation(.easeInOut(duration: 0.6).repeatForever(autoreverses: true)) { k = 1 } }
    }
}

/// Píldora lavanda de requisito ("Sin reflejos").
private struct PillLavanda: View {
    var texto: String
    var body: some View {
        T(texto, 13, 700, C.carbon)
            .padding(.horizontal, 12).padding(.vertical, 8)
            .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(C.lavanda))
    }
}

/// Verificación con DNI del prototipo: frente (con lectura del PDF417), dorso, selfie con prueba
/// de vida y el envío. La revisión la hace una persona del equipo: queda pendiente.
struct Dni: View {
    var plan: String?
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var paso: PasoDni = .cargando
    @State private var datos: DatosDni?
    @State private var frente: Data?
    @State private var dorso: Data?
    @State private var selfie: Data?
    @State private var disparo = false
    @State private var vida = IngresoVida()
    @State private var disparador = IngresoDisparador()
    @State private var permiso = IngresoPermisoCamara.concedido

    private var etiquetaFin: String { plan != nil ? "Seguir con el plan" : "Listo" }

    var body: some View {
        Pantalla {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    IngresoBarraArriba(icono: I.cruz, volver: { ir.atras() }) { IngresoEncriptado() }
                    switch paso {
                    case .cargando:
                        Puntitos().frame(maxWidth: .infinity).frame(height: 300)
                    case .frente, .dorso:
                        documento
                    case .selfie:
                        pasoSelfie
                    case .revisando, .enviado:
                        revisando
                    case .aprobado:
                        aprobado
                    }
                }
                .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 40)
            }
            .scrollDisabled(paso == .frente || paso == .dorso || paso == .selfie)
        }
        .environment(\.colorScheme, .light)
        .task { await arrancar() }
        // La selfie se saca sola al completar la prueba de vida.
        .onChange(of: vida.fase) { _, f in
            guard paso == .selfie, f == 3, selfie == nil else { return }
            Task {
                if let s = try? await disparador.foto() {
                    selfie = s
                    try? await Task.sleep(nanoseconds: 400_000_000)
                    await enviar()
                } else {
                    c.avisos.error("No pudimos sacar la foto. Probá de nuevo.")
                    vida.reiniciar()
                }
            }
        }
    }

    private func arrancar() async {
        guard paso == .cargando else { return }
        let v = try? await c.api.verificacion()
        switch v?.dni {
        case "pendiente": paso = .enviado
        case "aprobada": paso = .aprobado
        default: paso = .frente
        }
        if !permiso && (paso == .frente) { permiso = await IngresoPermisoCamara.pedir() }
    }

    @ViewBuilder private var documento: some View {
        ProgresoDni(paso: paso)
        VStack(alignment: .leading, spacing: 8) {
            T(paso == .frente ? "Frente del DNI" : "Dorso del DNI", 32, 800, C.carbon, alto: 1.02, espaciado: -1)
            T("Apoyalo en una superficie oscura y encuadralo.", 15, 400, C.texto2, alto: 1.5)
        }
        ZStack {
            C.oscuro6
            if permiso {
                IngresoCamaraDni(disparador: disparador) { crudo in
                    if datos == nil, let d = Pdf417Dni.leer(crudo) {
                        datos = d
                        Sistema.vibrar()
                    }
                }
                .id(paso)
            }
            Esquinas().stroke(C.lima, style: StrokeStyle(lineWidth: 4, lineCap: .butt))
            if disparo {
                ZStack {
                    rgba(32, 29, 43, 0.6)
                    Icono(I.check, 34, C.carbon, trazo: 3.2)
                        .frame(width: 72, height: 72)
                        .background(RoundedRectangle(cornerRadius: 24, style: .continuous).fill(C.lima))
                        .pop()
                }
                .transition(.opacity)
            }
        }
        .frame(height: 250)
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        HStack(spacing: 8) {
            PillLavanda(texto: "Sin reflejos")
            if datos != nil { IngresoChipRequisito(texto: "Que se lean los datos", ok: true) } else { PillLavanda(texto: "Que se lean los datos") }
        }
        .frame(maxWidth: .infinity)
        if !disparo {
            IngresoObturador { obturador() }
                .frame(maxWidth: .infinity)
                .padding(.top, 6)
        }
    }

    @ViewBuilder private var pasoSelfie: some View {
        ProgresoDni(paso: paso)
        VStack(alignment: .leading, spacing: 8) {
            T("Comparamos con tu cara", 32, 800, C.carbon, alto: 1.02, espaciado: -1)
            T("Así confirmamos que el DNI es tuyo.", 15, 400, C.texto2, alto: 1.5)
        }
        ZStack {
            Color.clear.frame(width: 260, height: 260).gel(130, nil, [externa(20, 40, rgba(80, 60, 160, 0.5), spread: -16)])
            IngresoAnillo(tam: 260, grosor: 9, progreso: vida.progreso, color: C.lima, pista: rgba(255, 255, 255, 0.18)) {
                if let selfie {
                    IngresoImagenDatos(datos: selfie)
                } else if permiso {
                    IngresoCamaraVida(vida: vida, disparador: disparador)
                }
            }
        }
        .frame(maxWidth: .infinity)
        VStack(spacing: 6) {
            T(vida.fase == 1 ? "Girá despacio a la izquierda" : (vida.fase >= 2 ? "Ahora a la derecha" : "Mirá a la cámara"), 14, 700, C.blancoCalido)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(C.carbon))
            T("\(Int(vida.progreso * 100))%", 22, 800, C.violetaTexto)
        }
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder private var revisando: some View {
        VStack(spacing: 16) {
            IconoLatiendo()
            T("Revisando tus datos…", 26, 700, C.carbon, alto: 1.1, alinear: .center)
            T(paso == .revisando ? "Comparamos tu selfie con la foto del DNI. Tarda unos segundos." : "Los revisa una persona del equipo, no un bot.",
              14, 400, C.texto2, alto: 1.5, alinear: .center)
            if paso == .enviado {
                Spacer().frame(height: 12)
                CtaGrande(etiquetaFin) { ir.atras() }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 120)
    }

    @ViewBuilder private var aprobado: some View {
        VStack(spacing: 16) {
            ZStack(alignment: .bottomTrailing) {
                Color.clear.frame(width: 150, height: 150).gel(75, nil, [externa(18, 30, rgba(80, 60, 160, 0.5), spread: -12)])
                Avatar(nombre: c.yo?.nombre ?? "", foto: c.yo?.fotos.first, tam: 150, borde: 5, colorBorde: C.blanco)
                Icono(I.check, 26, C.lima, trazo: 3.2)
                    .frame(width: 52, height: 52)
                    .gel(18, Gel(fondo: Gels.fondoVioleta, sombras: [interna(3, 2, rgba(255, 255, 255, 0.45)), externa(4, 0, C.violetaBase)]))
                    .pop()
                    .offset(x: 4, y: -4)
            }
            T("Tu DNI ya está verificado", 34, 800, C.carbon, alto: 1.02, espaciado: -1, alinear: .center)
            T("Tu perfil muestra el badge y podés sumarte a planes «solo verificados».", 15, 400, C.texto2, alto: 1.5, alinear: .center)
            Spacer().frame(height: 12)
            CtaGrande(etiquetaFin) { ir.atras() }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }

    private func obturador() {
        guard permiso else {
            Task { permiso = await IngresoPermisoCamara.pedir() }
            return
        }
        guard !disparo else { return }
        Task {
            guard let f = try? await disparador.foto() else {
                c.avisos.error("No pudimos sacar la foto. Probá de nuevo.")
                return
            }
            if paso == .frente { frente = f } else { dorso = f }
            withAnimation(.easeOut(duration: 0.2)) { disparo = true }
            Sistema.vibrar()
            try? await Task.sleep(nanoseconds: 650_000_000)
            withAnimation(.easeOut(duration: 0.2)) { disparo = false }
            if paso == .dorso && datos == nil {
                // El código está en el frente (DNI nuevo) o en el dorso (DNI viejo): sin leerlo no se puede seguir.
                c.avisos.error("No pudimos leer el número de DNI.")
                dorso = nil
                return
            }
            withAnimation(.easeOut(duration: 0.25)) { paso = PasoDni(rawValue: paso.rawValue + 1) ?? .selfie }
        }
    }

    private func enviar() async {
        paso = .revisando
        do {
            guard let d = datos, let frente, let dorso, let selfie else { throw CancellationError() }
            let f = try await c.api.subir(tipo: "dni", jpeg: frente)
            let r = try await c.api.subir(tipo: "dni", jpeg: dorso)
            let s = try await c.api.subir(tipo: "selfie", jpeg: selfie)
            try await c.api.dni(DatosDNI(frenteId: f.id, dorsoId: r.id, selfieId: s.id, numero: d.numero, apellido: d.apellido,
                                         nombres: d.nombres, sexo: d.sexo, nacimiento: d.nacimiento, tramite: d.tramite))
            paso = .enviado
        } catch let e as ErrorApi {
            if e.codigo == "pendiente" {
                paso = .enviado
            } else {
                c.avisos.error(ingresoMensaje(e))
                vida.reiniciar()
                selfie = nil; frente = nil; dorso = nil; datos = nil
                paso = .frente
            }
        } catch {
            c.avisos.error(ingresoMensaje(error))
            vida.reiniciar()
            selfie = nil
            paso = .selfie
        }
    }
}
