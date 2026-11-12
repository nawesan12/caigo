import SwiftUI
import Nucleo

/// El paso del alta que le falta a la persona: datos → teléfono → selfie → intenciones → la app.
/// Lee GET /v1/yo y GET /v1/verificacion y deja el perfil en `c.yo` (como `rutaDeAlta` de Android).
@MainActor
func ingresoRutaDeAlta(_ c: Contenedor) async throws -> Base {
    let yo = try await c.api.yo()
    c.yo = yo
    let v = try await c.api.verificacion()
    if yo.nombre.trimmingCharacters(in: .whitespaces).isEmpty || yo.edad == nil { return .datos }
    if v.telefono.isEmpty { return .telefono }
    if !v.selfie { return .selfie }
    if yo.intenciones.isEmpty { return .intenciones }
    // Sin perfiles vacíos: bio, fotos e intereses mínimos (personas.Faltantes en la API).
    if !yo.faltan.isEmpty { return .completarPerfil }
    return .principal(.ahora, modo: nil)
}

/// El error para el toast: el detalle del problema o el primer campo con problemas.
func ingresoMensaje(_ e: Error) -> String {
    if let a = e as? ErrorApi, let primero = a.problema.issues.first, !primero.message.isEmpty { return primero.message }
    return mensajeDe(e)
}

/// Título y bajada de los pasos del registro: `700 34px/1.05`, -.8px; `15px/1.5` gris.
struct IngresoTitulo: View {
    var titulo: String
    var bajada: String
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            T(titulo, 34, 700, C.carbon, alto: 1.05, espaciado: -0.8)
            T(bajada, 15, 400, C.texto2, alto: 1.5)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Campo de 60 del registro (01d): blanco, radio 18, borde lavanda o violeta con foco.
struct IngresoCampo: View {
    @Binding var valor: String
    var pista = ""
    var teclado: TipoTeclado = .texto
    var tam: CGFloat = 18
    var peso = 600
    var espaciado: CGFloat = 0.5
    var contenido: ContenidoCampo = .ninguno
    /// Contraseña: se tapa lo que se escribe (SecureField).
    var secreto = false
    @FocusState private var foco: Bool

    enum ContenidoCampo { case ninguno, nombre, telefono, email, clave, claveNueva }

    var body: some View {
        ZStack(alignment: .leading) {
            if valor.isEmpty && !pista.isEmpty { T(pista, tam, 500, C.apagado, unRenglon: true) }
            Group {
                if secreto { SecureField("", text: $valor) } else { TextField("", text: $valor) }
            }
            .font(Geist.fuente(tam, peso))
            .tracking(espaciado)
            .foregroundStyle(C.carbon)
            .tint(C.violeta)
            .focused($foco)
            .teclado(teclado)
            .autocorrectionDisabled(teclado != .texto)
            .modifier(IngresoTipoContenido(tipo: contenido))
        }
        .padding(.horizontal, 18)
        .frame(height: 60)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(C.blanco))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
            .strokeBorder(foco ? C.violeta : Color(hex: 0xE6E1F2), lineWidth: foco ? 2 : 1.5))
        .contentShape(Rectangle())
        .onTapGesture { foco = true }
    }
}

struct IngresoTipoContenido: ViewModifier {
    var tipo: IngresoCampo.ContenidoCampo
    func body(content: Content) -> some View {
        #if os(iOS)
        switch tipo {
        case .ninguno: content
        case .nombre: content.textContentType(.givenName)
        case .telefono: content.textContentType(.telephoneNumber)
        case .email: content.textContentType(.emailAddress)
        case .clave: content.textContentType(.password)
        case .claveNueva: content.textContentType(.newPassword)
        }
        #else
        content
        #endif
    }
}

/// Barra de arriba de los pasos: volver (44×44) y "Paso N de 2" o el candado "Encriptado".
struct IngresoBarraArriba<Derecha: View>: View {
    var icono: String = I.atras
    var volver: (() -> Void)?
    @ViewBuilder var derecha: () -> Derecha
    var body: some View {
        HStack {
            if let volver {
                BotonIcono(d: icono, iconoTam: icono == I.cruz ? 20 : 22, trazo: 2.4, gel: Gels.volver, accion: volver)
            } else {
                Color.clear.frame(width: 44, height: 44)
            }
            Spacer()
            derecha()
        }
    }
}

struct IngresoEncriptado: View {
    var body: some View {
        HStack(spacing: 6) {
            Icono(I.candado, 14, .clear, trazo: 0, relleno: C.texto2)
            T("Encriptado", 13, 700, C.texto2)
        }
    }
}

/// Puntitos del onboarding: el activo mide 26.
struct IngresoPuntos: View {
    var activo: Int
    var total: Int
    var on: Color
    var off: Color
    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<total, id: \.self) { i in
                Capsule().fill(i == activo ? on : off).frame(width: i == activo ? 26 : 8, height: 8)
            }
        }
        .animation(.easeOut(duration: 0.2), value: activo)
    }
}

/// Chip chico lima con check (los requisitos de la selfie) o apagado si no se cumple.
struct IngresoChipRequisito: View {
    var texto: String
    var ok: Bool
    var body: some View {
        HStack(spacing: 6) {
            if ok { Icono(I.check, 14, C.carbon, trazo: 3) }
            T(texto, 13, 700, ok ? C.carbon : C.texto2, alto: 1.25, unRenglon: true)
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .gel(12, ok ? Gels.limaChico : Gels.chipClaro)
        .animation(.easeOut(duration: 0.2), value: ok)
    }
}

/// Obturador del DNI: círculo blanco de 82 con anillo carbón de 6 y lima de 9.
struct IngresoObturador: View {
    var tam: CGFloat = 82
    var habilitado = true
    var accion: () -> Void
    var body: some View {
        Button(action: accion) {
            ZStack {
                Color.clear.frame(width: tam + 18, height: tam + 18)
                    .gel((tam + 18) / 2, .liso(C.lima), [externa(14, 24, rgba(0, 0, 0, 0.4), spread: -8)])
                Circle().fill(C.carbon).frame(width: tam + 12, height: tam + 12)
                Circle().fill(C.blanco).frame(width: tam, height: tam)
            }
        }
        .buttonStyle(EstiloToque())
        .disabled(!habilitado)
        .accessibilityLabel("Sacar foto")
    }
}

/// Píldora "Saltar" translúcida sobre la foto.
struct IngresoSaltar: View {
    var alfa: Double
    var accion: () -> Void
    var body: some View {
        Button(action: accion) {
            T("Saltar", 14, 700, C.blanco)
                .padding(.horizontal, 14).padding(.vertical, 8)
                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(rgba(255, 255, 255, alfa)))
        }
        .buttonStyle(EstiloPlano())
    }
}

/// Fondo del splash (00): grafito con un radial más claro en el centro, y grano.
struct IngresoFondoSplash: View {
    var body: some View {
        ZStack {
            Color(hex: 0x17151F)
            VistaFondo(fondo: .radial(cx: 0.5, cy: 0.42, rx: 0.7, ry: 0.45,
                                      Fondo.paradas((0, Color(hex: 0x3A3450)), (0.5, Color(hex: 0x262233)), (1, Color(hex: 0x17151F)))))
            Grano()
        }
        .ignoresSafeArea()
    }
}

/// Fondo violeta de 01c (y de Ingresar): radial lavanda arriba a la derecha + grano.
struct IngresoFondoVioleta: View {
    var body: some View {
        ZStack {
            Color(hex: 0x5A3DD8)
            VistaFondo(fondo: .radial(cx: 0.85, cy: 0, rx: 1.2, ry: 0.7,
                                      Fondo.paradas((0, Color(hex: 0xA38FFF)), (0.45, C.violeta), (1, Color(hex: 0x5A3DD8)))))
            Grano()
        }
        .ignoresSafeArea()
    }
}

/// Mancha de color difuminada (las del splash): radial que se apaga hacia el borde.
struct IngresoMancha: View {
    var tam: CGFloat
    var claro: Color
    var medio: Color
    var oscuro: Color
    var alfa: Double
    var body: some View {
        Circle()
            .fill(RadialGradient(stops: Fondo.paradas((0, claro.opacity(alfa)), (0.35, medio.opacity(alfa)),
                                                      (0.55, oscuro.opacity(alfa * 0.7)), (0.72, oscuro.opacity(0))),
                                 center: .center, startRadius: 0, endRadius: tam / 2 * 1.414))
            .frame(width: tam, height: tam)
            .allowsHitTesting(false)
    }
}
