import SwiftUI
import Nucleo

/// DDMMAAAA (sólo dígitos) → "AAAA-MM-DD" si es una fecha válida.
func ingresoFecha(_ digitos: String) -> Date? {
    guard digitos.count == 8, let d = Int(digitos.prefix(2)), let m = Int(digitos.dropFirst(2).prefix(2)), let a = Int(digitos.suffix(4)) else { return nil }
    var c = DateComponents()
    c.year = a; c.month = m; c.day = d
    let cal = Fecha.calendario
    guard let f = cal.date(from: c), cal.component(.day, from: f) == d, cal.component(.month, from: f) == m, a > 1900 else { return nil }
    return f
}

/// Muestra las barras de DD/MM/AAAA: el estado guarda sólo los dígitos.
func ingresoConBarras(_ d: String) -> String {
    var s = ""
    for (i, ch) in d.enumerated() {
        if i == 2 || i == 4 { s.append("/") }
        s.append(ch)
    }
    return s
}

/// Datos básicos (nombre, nacimiento, ciudad y zona). No está diseñada: se arma con las piezas de
/// 01d (título de 34, campos de 60, CTA lima).
struct Datos: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var nombre = ""
    @State private var nacimiento = ""
    @State private var ciudad = "Mar del Plata"
    @State private var zona = ""
    @State private var cargando = false
    @State private var listoInicial = false

    private var fecha: Date? { ingresoFecha(nacimiento) }
    private var listo: Bool { !nombre.trimmingCharacters(in: .whitespaces).isEmpty && fecha != nil && !zona.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 28) {
                        IngresoTitulo(titulo: "¿Cómo te llamás?", bajada: "Así te ven en los planes. Tu fecha de nacimiento no aparece en tu perfil.")
                        VStack(alignment: .leading, spacing: 12) {
                            T("Nombre", 14, 700, C.carbon)
                            IngresoCampo(valor: Binding(get: { nombre }, set: { nombre = String($0.prefix(40)) }), pista: "Tu nombre", contenido: .nombre)
                        }
                        VStack(alignment: .leading, spacing: 12) {
                            T("Fecha de nacimiento", 14, 700, C.carbon)
                            IngresoCampo(valor: Binding(get: { ingresoConBarras(nacimiento) },
                                                        set: { nacimiento = String($0.filter(\.isNumber).prefix(8)) }),
                                         pista: "DD/MM/AAAA", teclado: .numeros)
                        }
                        VStack(alignment: .leading, spacing: 12) {
                            T("¿Dónde estás?", 14, 700, C.carbon)
                            HStack(spacing: 8) {
                                ForEach(["Mar del Plata", "Villa Gesell"], id: \.self) { cdad in
                                    Chip(texto: cdad, on: ciudad == cdad) { ciudad = cdad }
                                }
                            }
                            IngresoCampo(valor: Binding(get: { zona }, set: { zona = String($0.prefix(60)) }),
                                         pista: ciudad == "Villa Gesell" ? "Zona" : "Güemes")
                        }
                    }
                    .padding(.horizontal, 24).padding(.top, 88)
                }
                .scrollDismissesKeyboard(.interactively)
                VStack(spacing: 16) {
                    T("Caigo es solo para mayores de 18.", 13, 400, C.texto2)
                    Cta(texto: "Seguir", habilitado: listo, cargando: cargando) { seguir() }
                }
                .padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 36)
            }
        }
        .environment(\.colorScheme, .light)
        .onAppear {
            guard !listoInicial, let yo = c.yo else { return }
            listoInicial = true
            nombre = yo.nombre
            if !yo.ciudad.isEmpty { ciudad = yo.ciudad }
            zona = yo.zona
        }
    }

    private func seguir() {
        guard let f = fecha else { return }
        let edad = Fecha.calendario.dateComponents([.year], from: f, to: Date()).year ?? 0
        if edad < 18 {
            c.avisos.error("Caigo es sólo para mayores de 18.")
            return
        }
        let d = Fecha.calendario.dateComponents([.year, .month, .day], from: f)
        var cambios = CambiosPerfil()
        cambios.nombre = nombre.trimmingCharacters(in: .whitespaces)
        cambios.nacimiento = String(format: "%04d-%02d-%02d", d.year ?? 0, d.month ?? 0, d.day ?? 0)
        cambios.ciudad = ciudad
        cambios.zona = zona.trimmingCharacters(in: .whitespaces)
        cargando = true
        Task {
            defer { cargando = false }
            do {
                c.yo = try await c.api.actualizarYo(cambios)
                ir.alta(try await ingresoRutaDeAlta(c))
            } catch {
                c.avisos.error(ingresoMensaje(error))
            }
        }
    }
}

/// 01d · Teléfono (Paso 1 de 2): número, código de 6 dígitos por mail y "Reenviar en 0:24".
struct Telefono: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var numero = ""
    @State private var codigo = ""
    @State private var pedido = false
    @State private var destino = ""
    @State private var espera = 0
    @State private var cargando = false
    @FocusState private var focoCodigo: Bool

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                IngresoBarraArriba(volver: { ir.alta(.datos) }) { T("Paso 1 de 2", 14, 700, C.texto2) }
                    .padding(.leading, 20).padding(.trailing, 24).padding(.top, 12)
                ScrollView {
                    VStack(alignment: .leading, spacing: 28) {
                        IngresoTitulo(titulo: "¿Cuál es tu número?", bajada: destino.isEmpty
                            ? "Te mandamos un código por mail. Tu número no aparece en tu perfil."
                            : "Te mandamos un código por mail a \(destino). Tu número no aparece en tu perfil.")
                        HStack(spacing: 10) {
                            T("AR +54", 17, 700, C.carbon)
                                .padding(.horizontal, 16)
                                .frame(height: 60)
                                .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(C.blanco))
                                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Color(hex: 0xE6E1F2), lineWidth: 1.5))
                            IngresoCampo(valor: Binding(get: { numero }, set: { numero = String($0.filter { $0.isNumber || $0 == " " }.prefix(16)) }),
                                         pista: "223 555 0142", teclado: .telefono, contenido: .telefono)
                        }
                        if pedido {
                            VStack(alignment: .leading, spacing: 12) {
                                HStack {
                                    T("Código", 14, 700, C.carbon)
                                    Spacer()
                                    if espera > 0 {
                                        T(String(format: "Reenviar en 0:%02d", espera), 14, 700, C.violeta)
                                    } else {
                                        Button { if !cargando { pedir() } } label: { T("Reenviar", 14, 700, C.violeta) }.buttonStyle(EstiloPlano())
                                    }
                                }
                                IngresoCasillas(codigo: $codigo, foco: $focoCodigo)
                            }
                            .sube()
                        }
                    }
                    .padding(.horizontal, 24).padding(.top, 32)
                }
                .scrollDismissesKeyboard(.interactively)
                VStack(spacing: 16) {
                    T("Caigo es solo para mayores de 18.", 13, 400, C.texto2)
                    Cta(texto: "Seguir", habilitado: pedido ? codigo.count == 6 : numero.filter(\.isNumber).count >= 8, cargando: cargando) {
                        if pedido { confirmar() } else { pedir() }
                    }
                }
                .padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 36)
            }
        }
        .environment(\.colorScheme, .light)
        .task(id: espera > 0) {
            while espera > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { return }
                espera -= 1
            }
        }
    }

    private func pedir() {
        cargando = true
        Task {
            defer { cargando = false }
            do {
                let r = try await c.api.pedirCodigo(numero.filter(\.isNumber))
                withAnimation { pedido = true }
                espera = 30
                codigo = ""
                destino = r.destino
                // Sin correo en la API (desarrollo), viene el código de prueba y se completa solo.
                if !r.codigoDePrueba.isEmpty { codigo = r.codigoDePrueba } else { focoCodigo = true }
            } catch {
                c.avisos.error(ingresoMensaje(error))
            }
        }
    }

    private func confirmar() {
        cargando = true
        Task {
            defer { cargando = false }
            do {
                try await c.api.confirmarCodigo(codigo)
                ir.alta(try await ingresoRutaDeAlta(c))
            } catch {
                c.avisos.error(ingresoMensaje(error))
            }
        }
    }
}

/// Las 6 casillas del código: las completas en gel lavanda, la actual con borde violeta. Encima,
/// un campo invisible que recibe el teclado (y el autocompletado del código que llega por mail).
struct IngresoCasillas: View {
    @Binding var codigo: String
    var foco: FocusState<Bool>.Binding

    var body: some View {
        ZStack {
            TextField("", text: Binding(get: { codigo }, set: { codigo = String($0.filter(\.isNumber).prefix(6)) }))
                .focused(foco)
                .teclado(.numeros)
                .modifier(IngresoCodigoUnaVez())
                .foregroundStyle(.clear)
                .tint(.clear)
                .frame(width: 1, height: 1)
                .opacity(0.01)
            HStack(spacing: 8) {
                ForEach(0..<6, id: \.self) { i in
                    let digitos = Array(codigo)
                    Group {
                        if i < digitos.count {
                            T(String(digitos[i]), 26, 700, C.carbon)
                                .frame(maxWidth: .infinity).frame(height: 58)
                                .gel(16, Gels.chipClaro)
                        } else {
                            RoundedRectangle(cornerRadius: 16, style: .continuous).fill(C.blanco)
                                .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .strokeBorder(i == digitos.count ? C.violeta : Color(hex: 0xE6E1F2), lineWidth: i == digitos.count ? 2 : 1.5))
                                .frame(height: 58)
                        }
                    }
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { foco.wrappedValue = true }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Código de 6 dígitos")
        .accessibilityValue(codigo)
    }
}

struct IngresoCodigoUnaVez: ViewModifier {
    func body(content: Content) -> some View {
        #if os(iOS)
        content.textContentType(.oneTimeCode)
        #else
        content
        #endif
    }
}
