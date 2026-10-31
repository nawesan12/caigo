import SwiftUI
import Nucleo

/// Para sacar fotos desde una vista de cámara (la vista lo conecta al aparecer).
@MainActor
final class IngresoDisparador {
    var sacar: (() async throws -> Data)?
    func foto() async throws -> Data {
        guard let sacar else { throw CancellationError() }
        return try await sacar()
    }
}

/// Cámara frontal con la detección de caras conectada a `vida`.
struct IngresoCamaraVida: View {
    var vida: IngresoVida
    var disparador: IngresoDisparador
    #if os(iOS)
    @State private var camara = IngresoCamara(frontal: true)
    #endif

    var body: some View {
        #if os(iOS)
        IngresoVistaCamara(camara: camara)
            .onAppear {
                let v = vida
                camara.analizar = { b in IngresoAnalisisCara.analizar(b, vida: v) }
                let cam = camara
                disparador.sacar = { try await cam.sacarFoto() }
            }
            .onDisappear { camara.apagar() }
        #else
        Color.black
        #endif
    }
}

/// Cámara trasera que lee el PDF417 del DNI y saca la foto.
struct IngresoCamaraDni: View {
    var disparador: IngresoDisparador
    var alLeer: (String) -> Void
    #if os(iOS)
    @State private var camara = IngresoCamara(frontal: false)
    #endif

    var body: some View {
        #if os(iOS)
        IngresoVistaCamara(camara: camara)
            .onAppear {
                let avisar = alLeer
                var cuenta = 0
                camara.analizar = { b in
                    // Un cuadro de cada tres alcanza para leer el código sin calentar el teléfono.
                    cuenta += 1
                    guard cuenta % 3 == 0, let s = IngresoLectorPdf417.leer(b) else { return }
                    DispatchQueue.main.async { avisar(s) }
                }
                let cam = camara
                disparador.sacar = { try await cam.sacarFoto() }
            }
            .onDisappear { camara.apagar() }
        #else
        Color.black
        #endif
    }
}

/// 01e · Selfie con prueba de vida (Paso 2 de 2).
struct Selfie: View {
    var volver: Bool
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var vida = IngresoVida()
    @State private var disparador = IngresoDisparador()
    @State private var foto: Data?
    @State private var cargando = false
    @State private var permiso = IngresoPermisoCamara.concedido

    private var instruccion: String {
        if !permiso { return "Activá la cámara para seguir" }
        if foto != nil { return "Listo" }
        switch vida.fase {
        case 1: return "Girá la cabeza despacio a la izquierda"
        case 2: return "Ahora a la derecha"
        default: return "Mirá a la cámara"
        }
    }

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                IngresoBarraArriba(volver: { if volver { ir.atras() } else { ir.alta(.telefono) } }) {
                    if !volver { T("Paso 2 de 2", 14, 700, C.texto2) }
                }
                .padding(.leading, 20).padding(.trailing, 24).padding(.top, 12)
                IngresoTitulo(titulo: "Ahora, una selfie", bajada: "Es para confirmar que sos una persona real. No se publica.")
                    .padding(.horizontal, 24).padding(.top, 32)
                Spacer(minLength: 20)
                IngresoAnillo(tam: 276, grosor: 8, progreso: foto != nil ? 1 : vida.progreso, color: C.violeta, pista: C.pista) {
                    if let foto {
                        IngresoImagenDatos(datos: foto)
                    } else if permiso {
                        IngresoCamaraVida(vida: vida, disparador: disparador)
                    } else {
                        Icono(I.usuario, 64, C.violeta, trazo: 2)
                    }
                }
                Spacer().frame(height: 24)
                IngresoPillOscura(texto: instruccion)
                Spacer().frame(height: 18)
                // En pantallas angostas los chips bajan enteros al renglón de abajo.
                IngresoFilas(espacio: 10, alineacion: .center) {
                    IngresoChipRequisito(texto: "Buena luz", ok: (vida.luz && permiso) || foto != nil)
                    IngresoChipRequisito(texto: "Cara centrada", ok: vida.centrada || foto != nil)
                    IngresoChipRequisito(texto: "Sin lentes de sol", ok: vida.ojos || foto != nil)
                }
                .padding(.horizontal, 24)
                Spacer(minLength: 20)
                VStack(spacing: 16) {
                    HStack(spacing: 6) {
                        Icono(I.escudo, 16, C.violeta, trazo: 2)
                        T("Prueba de vida · tus datos viajan encriptados", 13, 400, C.texto2)
                    }
                    Cta(texto: "Verificarme", habilitado: !permiso || foto != nil, cargando: cargando) { verificarme() }
                }
                .padding(.horizontal, 24).padding(.bottom, 36)
            }
        }
        .environment(\.colorScheme, .light)
        .task {
            if !permiso { permiso = await IngresoPermisoCamara.pedir() }
        }
        // Al terminar la prueba de vida se saca la foto sola.
        .onChange(of: vida.fase) { _, f in
            guard f == 3, foto == nil else { return }
            Task {
                do {
                    foto = try await disparador.foto()
                    Sistema.vibrar()
                } catch {
                    c.avisos.error("No pudimos sacar la foto. Probá de nuevo.")
                    vida.reiniciar()
                }
            }
        }
    }

    private func verificarme() {
        guard permiso else {
            Task { permiso = await IngresoPermisoCamara.pedir() }
            return
        }
        guard let foto else { return }
        cargando = true
        Task {
            defer { cargando = false }
            do {
                let a = try await c.api.subir(tipo: "selfie", jpeg: foto)
                try await c.api.selfie(a.id)
                if volver { ir.atras() } else { ir.alta(try await ingresoRutaDeAlta(c)) }
            } catch {
                c.avisos.error(ingresoMensaje(error))
            }
        }
    }
}
