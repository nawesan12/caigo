import SwiftUI
import Nucleo

/// «Entrar con mail» (como Tengo Libre, Corredor y la web): según la cuenta, la contraseña o un código de 6
/// números; si es la primera vez se crea la cuenta (con código, y la contraseña si la pide). «¿No podés
/// entrar?» pasa a recuperar el acceso en la misma pantalla. No está en el handoff: se arma con los pasos
/// del registro (01d). Termina igual que Google y Apple: el JWT de Clerk va a POST /v1/sesion.
struct IngresoMail: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var email = ""
    @State private var clave = ""
    @State private var codigo = ""
    @State private var paso: ClerkMail.Paso?
    @State private var flujo: ClerkMail?
    @State private var ocupado = false
    @State private var error = ""
    /// Recuperar el acceso: nil (no), "mail", "codigo", "confianza" (teléfono nuevo para Clerk) o "nueva".
    @State private var recuperar: String?
    @State private var enviado = false
    @FocusState private var focoCodigo: Bool

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                IngresoBarraArriba(volver: { volver() }) { T("Con mail", 14, 700, C.texto2) }
                    .padding(.leading, 20).padding(.trailing, 24).padding(.top, 12)
                ScrollView {
                    VStack(alignment: .leading, spacing: 28) {
                        if recuperar != nil { pasoRecuperar } else { pasoIngreso }
                        if !error.isEmpty { T(error, 14, 600, C.coral2, alto: 1.4) }
                        if flujo == nil {
                            T("El ingreso con mail no está disponible en este momento. Probá de nuevo en un rato.", 14, 600, C.coral2, alto: 1.4)
                        }
                    }
                    .padding(.horizontal, 24).padding(.top, 32)
                }
                .scrollDismissesKeyboard(.interactively)
                VStack(spacing: 16) {
                    Cta(texto: textoCta, habilitado: habilitado, cargando: ocupado) { seguir() }
                    if recuperar == nil, case .clave = paso {
                        Button { empezarRecuperar() } label: { T("¿No podés entrar?", 15, 700, C.violeta).padding(4) }
                            .buttonStyle(EstiloPlano())
                    }
                }
                .padding(.horizontal, 24).padding(.top, 12).padding(.bottom, 36)
            }
        }
        .environment(\.colorScheme, .light)
        .onAppear { if flujo == nil { flujo = ClerkMail(clerk: c.clerk) } }
    }

    // MARK: Ingreso

    @ViewBuilder
    private var pasoIngreso: some View {
        switch paso {
        case nil:
            IngresoTitulo(titulo: "Tu mail", bajada: "Si es tu primera vez, creamos tu cuenta con un código que te llega por mail.")
            IngresoCampo(valor: $email, pista: "tu@mail.com", teclado: .email, tam: 17, contenido: .email)
                .onSubmit { seguir() }
        case .clave:
            IngresoTitulo(titulo: "Tu contraseña", bajada: email)
            IngresoCampo(valor: $clave, tam: 17, contenido: .clave, secreto: true)
                .onSubmit { seguir() }
        case .codigo(let alta):
            IngresoTitulo(titulo: "Revisá tu mail",
                          bajada: sinViudas("Te mandamos un código a \(email). \(alta ? "Con él creamos tu cuenta." : "Escribilo para entrar.")"))
            IngresoCasillas(codigo: $codigo, foco: $focoCodigo)
                .onChange(of: codigo) { _, n in if n.count == 6 && !ocupado { seguir() } }
                .onAppear { focoCodigo = true }
        case .claveNueva:
            IngresoTitulo(titulo: "Elegí una contraseña", bajada: "La vas a usar para entrar con tu mail.")
            IngresoCampo(valor: $clave, tam: 17, contenido: .claveNueva, secreto: true)
            IngresoRequisitosClave(clave: clave)
        case .listo:
            EmptyView()
        }
    }

    // MARK: Recuperar

    @ViewBuilder
    private var pasoRecuperar: some View {
        if recuperar == "nueva" {
            IngresoTitulo(titulo: "Elegí una contraseña nueva", bajada: "Cerramos las otras sesiones abiertas por seguridad.")
            IngresoCampo(valor: $clave, tam: 17, contenido: .claveNueva, secreto: true)
            IngresoRequisitosClave(clave: clave)
        } else {
            IngresoTitulo(titulo: "¿No podés entrar?", bajada: sinViudas(
                "Si entrás con Google o Apple, usá ese botón: no necesitás contraseña. Si usás mail, te mandamos un código para crear una nueva."))
            if recuperar == "mail" {
                IngresoCampo(valor: $email, pista: "tu@mail.com", teclado: .email, tam: 17, contenido: .email)
            } else {
                VStack(alignment: .leading, spacing: 12) {
                    T("Código que te llegó a \(email)", 14, 700, C.carbon)
                    IngresoCasillas(codigo: $codigo, foco: $focoCodigo)
                        .onChange(of: codigo) { _, n in if n.count == 6 && !ocupado { seguir() } }
                        .onAppear { focoCodigo = true }
                }
            }
            if enviado {
                HStack(alignment: .top, spacing: 10) {
                    Icono(I.check, 16, C.carbon, trazo: 3)
                    T(sinViudas("Listo: si ese mail tiene cuenta, te llega un código en un minuto. Revisá también spam."), 14, 600, C.carbon, alto: 1.4)
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .gel(16, Gels.chipClaro)
            }
        }
    }

    // MARK: Acciones

    private var textoCta: String {
        if let recuperar {
            switch recuperar {
            case "mail": return "Mandarme el código"
            case "nueva": return "Guardar y entrar"
            default: return "Seguir"
            }
        }
        switch paso {
        case nil: return "Seguir"
        case .clave: return "Entrar"
        case .codigo(let alta): return alta ? "Crear mi cuenta" : "Entrar"
        case .claveNueva, .listo: return "Guardar y entrar"
        }
    }

    private var habilitado: Bool {
        guard flujo != nil else { return false }
        if let recuperar {
            switch recuperar {
            case "mail": return email.contains("@")
            case "nueva": return !clave.isEmpty
            default: return codigo.count == 6
            }
        }
        switch paso {
        case nil: return email.contains("@")
        case .clave: return !clave.isEmpty
        case .codigo: return codigo.count == 6
        case .claveNueva: return !clave.isEmpty
        case .listo: return false
        }
    }

    private var mail: String { email.trimmingCharacters(in: .whitespaces).lowercased() }

    /// Atrás: de recuperar al ingreso, de un paso al mail, y del mail a la pantalla anterior.
    private func volver() {
        guard !ocupado else { return }
        if recuperar != nil || paso != nil {
            recuperar = nil
            paso = nil
            clave = ""; codigo = ""; error = ""; enviado = false
            flujo = ClerkMail(clerk: c.clerk)
        } else {
            ir.atras()
        }
    }

    private func empezarRecuperar() {
        clave = ""; codigo = ""; error = ""; enviado = false
        recuperar = "mail"
    }

    private func seguir() {
        guard let flujo, !ocupado, habilitado else { return }
        if (recuperar == nil && paso.esClaveNueva) || recuperar == "nueva" {
            let r = ClerkMail.requisitos(clave)
            if !r.largo || !r.comun { error = "La contraseña todavía no cumple los requisitos."; return }
        }
        error = ""
        ocupado = true
        Task {
            defer { ocupado = false }
            do {
                if let recuperar { try await seguirRecuperar(flujo, recuperar) } else { try await seguirIngreso(flujo) }
            } catch {
                self.error = ClerkMail.mensaje(de: error)
                Sistema.vibrar(false)
            }
        }
    }

    private func seguirIngreso(_ flujo: ClerkMail) async throws {
        let sig: ClerkMail.Paso
        switch paso {
        case nil: sig = try await flujo.empezar(email: mail)
        case .clave: sig = try await flujo.entrarConClave(clave)
        case .codigo: sig = try await flujo.confirmarCodigo(codigo)
        case .claveNueva: sig = try await flujo.guardarClave(clave)
        case .listo: return
        }
        try await mostrar(sig)
    }

    private func seguirRecuperar(_ flujo: ClerkMail, _ r: String) async throws {
        switch r {
        case "mail":
            let hay = try await flujo.pedirRecuperar(email: mail)
            enviado = true
            if hay { codigo = ""; recuperar = "codigo" }
        case "codigo", "confianza":
            let sig = try await flujo.confirmarCodigo(codigo, recuperar: r == "codigo")
            if case .claveNueva = sig { clave = ""; recuperar = "nueva" }
            if case .listo(let token) = sig { try await canjear(token) }
        default:
            let sig = try await flujo.guardarClave(clave)
            if case .listo(let token) = sig { try await canjear(token) }
            // Teléfono nuevo para Clerk: un código más por mail para terminar.
            if case .codigo = sig { codigo = ""; enviado = false; recuperar = "confianza" }
        }
    }

    private func mostrar(_ sig: ClerkMail.Paso) async throws {
        if case .listo(let token) = sig { return try await canjear(token) }
        if case .claveNueva = sig { clave = "" }
        if case .codigo = sig { codigo = "" }
        paso = sig
    }

    /// Igual que Google y Apple (Ingresar.canjear). Si la API responde 409 `cuenta_borrada`, se ve su detalle.
    private func canjear(_ jwt: String) async throws {
        let s = try await c.api.crearSesion(jwtClerk: jwt)
        c.entrar(token: s.token, usuarioId: s.usuarioId)
        Sistema.vibrar()
        ir.alta(try await ingresoRutaDeAlta(c))
    }
}

private extension Optional where Wrapped == ClerkMail.Paso {
    var esClaveNueva: Bool {
        if case .claveNueva = self { return true }
        return false
    }
}

/// Los requisitos de la contraseña nueva, que se tildan solos mientras se escribe (los chips de la selfie).
struct IngresoRequisitosClave: View {
    var clave: String

    var body: some View {
        let r = ClerkMail.requisitos(clave)
        HStack(spacing: 8) {
            IngresoChipRequisito(texto: "8 caracteres o más", ok: r.largo)
            IngresoChipRequisito(texto: "No es de las más usadas", ok: r.comun)
        }
        .accessibilityElement(children: .combine)
    }
}
