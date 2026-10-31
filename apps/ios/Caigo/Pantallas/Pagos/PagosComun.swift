import SwiftUI
import Nucleo
import CoreImage
import CoreImage.CIFilterBuiltins
#if os(iOS)
import PassKit
import VisionKit
#endif

/*
 * Piezas que comparten Plus, entradas, pases y Caigo Tap (LugaresComun.kt de Android). Salen del
 * prototipo y de "Caigo Pases NFC" (con Geist en vez de Bricolage).
 */

/// Lima gel de los Pases NFC y el paywall (base `#7FA82E` y brillo lima).
let pagosGelLimaNfc = Gel(fondo: Gels.fondoLima, sombras: [
    interna(3, 2, rgba(255, 255, 255, 0.85)), interna(-5, 0, rgba(96, 140, 10, 0.32)),
    externa(5, 0, Color(hex: 0x7FA82E)), externa(18, 30, rgba(200, 243, 107, 0.45), spread: -10),
])

/// Pill "PLUS" inclinada 10° (paywall).
struct PagosPillPlus: View {
    var tam: CGFloat = 20
    var body: some View {
        T("PLUS", tam, 800, C.carbon, alto: 1, unRenglon: true)
            .padding(.horizontal, 14).padding(.vertical, 8)
            .gel(14, pagosGelLimaNfc)
            .rotationEffect(.degrees(10))
    }
}

/// Botón de ancho completo de los Pases NFC: negro de Wallet, blanco con borde, grafito o lima.
enum PagosEstiloBoton { case negro, blanco, grafito, lima }

struct PagosBoton: View {
    var texto: String
    var estilo: PagosEstiloBoton
    var icono: String?
    var alto: CGFloat = 56
    var radio: CGFloat = 18
    var tam: CGFloat = 16
    var peso = 700
    var cargando = false
    var accion: () -> Void

    var body: some View {
        let (gel, color): (Gel, Color) = {
            switch estilo {
            case .negro: return (Gel(fondo: .liso(C.negro), sombras: [externa(4, 0, Color(hex: 0x333333))]), C.blanco)
            case .blanco: return (Gel(fondo: .liso(C.blanco), sombras: [interna(0, 0, C.borde3, spread: -1.5), externa(4, 0, C.borde3)]), C.carbon)
            case .grafito: return (Gel(fondo: .liso(C.carbon), sombras: [externa(5, 0, C.oscuro7)]), C.blanco)
            case .lima: return (Gel(fondo: .liso(C.lima), sombras: [externa(4, 0, C.limaBase)]), C.carbon)
            }
        }()
        Button(action: accion) {
            ZStack {
                if cargando {
                    Puntitos(color: color)
                } else {
                    HStack(spacing: 10) {
                        if let icono { Icono(icono, 22, color, trazo: 2) }
                        T(texto, tam, peso, color, unRenglon: true)
                    }
                }
            }
            .frame(maxWidth: .infinity).frame(height: alto)
            .gel(radio, gel)
        }
        .buttonStyle(EstiloToque())
        .disabled(cargando)
    }
}

/// "Hoy 19:30 · Café Molienda"
func pagosCuandoDonde(_ iso: String, _ lugar: String) -> String {
    [Fecha.cuando(iso), lugar].filter { !$0.isEmpty }.joined(separator: " · ")
}

/// El lugar de un plan: el del lugar aliado o el que escribió quien organiza.
func pagosLugarDe(_ p: Plan) -> String {
    if let n = p.lugar?.nombre, !n.isEmpty { return n }
    return p.lugarNombre
}

/// Botón volver blanco con base lavanda + título (Tu entrada, Mis pases).
struct PagosEncabezado: View {
    var titulo: String
    var icono: String = I.atras
    var tam: CGFloat = 28
    var volver: () -> Void
    @Environment(\.paleta) private var p
    var body: some View {
        HStack(spacing: 14) {
            BotonIcono(d: icono, iconoTam: icono == I.cruz ? 20 : 22, trazo: icono == I.cruz ? 2.6 : 2.4, gel: Gels.volver, accion: volver)
            T(titulo, tam, 800, p.texto, alto: 1, espaciado: -0.8 * tam / 28, unRenglon: true)
            Spacer(minLength: 0)
        }
    }
}

/// Código QR dibujado con Core Image (sin suavizar: módulos nítidos).
struct PagosCodigoQr: View {
    var texto: String
    var body: some View {
        if let cg = PagosQr.imagen(texto) {
            Image(decorative: cg, scale: 1).interpolation(.none).resizable().scaledToFit()
                .accessibilityLabel("Código QR \(texto)")
        } else {
            Color.clear
        }
    }
}

enum PagosQr {
    static func imagen(_ texto: String) -> CGImage? {
        let f = CIFilter.qrCodeGenerator()
        f.message = Data(texto.utf8)
        f.correctionLevel = "M"
        guard let salida = f.outputImage else { return nil }
        // Módulos carbón sobre transparente.
        let color = CIFilter.falseColor()
        color.inputImage = salida
        color.color0 = CIColor(red: 0x24 / 255.0, green: 0x21 / 255.0, blue: 0x2D / 255.0)
        color.color1 = CIColor(red: 1, green: 1, blue: 1, alpha: 0)
        guard let c = color.outputImage else { return nil }
        return CIContext().createCGImage(c, from: c.extent)
    }
}

// MARK: - Apple Wallet

enum PagosWallet {
    /// ¿Se pueden agregar pases en este dispositivo?
    static var disponible: Bool {
        #if os(iOS)
        return PKAddPassesViewController.canAddPasses()
        #else
        return false
        #endif
    }

    /// Baja el .pkpass firmado y muestra "Agregar a Apple Wallet". Devuelve si se presentó.
    @MainActor
    static func agregar(_ bajar: () async throws -> Data) async throws -> Bool {
        let datos = try await bajar()
        #if os(iOS)
        let pase = try PKPass(data: datos)
        if PKPassLibrary().containsPass(pase) { return true }
        guard let vc = PKAddPassesViewController(pass: pase) else { return false }
        Sistema.arriba()?.present(vc, animated: true)
        return true
        #else
        _ = datos
        return false
        #endif
    }
}

/// Botón negro "Agregar a Apple Wallet" (el de los Pases NFC, 1c).
struct PagosBotonWallet: View {
    var bajar: () async throws -> Data
    @Environment(Contenedor.self) private var c
    @State private var cargando = false
    @State private var agregado = false
    var body: some View {
        PagosBoton(texto: agregado ? "En tu Wallet" : "Agregar a Apple Wallet", estilo: .negro, icono: I.billetera,
                   radio: 20, peso: 800, cargando: cargando) {
            Task {
                cargando = true
                do { agregado = try await PagosWallet.agregar(bajar) } catch { c.avisos.error(error) }
                cargando = false
            }
        }
    }
}

// MARK: - Escáner de QR (VisionKit)

/// Escáner de códigos QR con la cámara (DataScannerViewController). Llama a `leido` con el texto.
struct PagosEscanerQr: View {
    var leido: (String) -> Void
    var body: some View {
        #if os(iOS)
        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
            PagosEscanerRepresentable(leido: leido)
        } else {
            T("La cámara no está disponible para escanear.", 14, 600, C.apagado, alinear: .center).padding(24)
        }
        #else
        Color.clear
        #endif
    }
}

#if os(iOS)
struct PagosEscanerRepresentable: UIViewControllerRepresentable {
    var leido: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let vc = DataScannerViewController(recognizedDataTypes: [.barcode(symbologies: [.qr])], qualityLevel: .balanced,
                                           isHighFrameRateTrackingEnabled: false, isHighlightingEnabled: true)
        vc.delegate = context.coordinator
        try? vc.startScanning()
        return vc
    }

    func updateUIViewController(_ vc: DataScannerViewController, context: Context) {}

    static func dismantleUIViewController(_ vc: DataScannerViewController, coordinator: Coordinador) { vc.stopScanning() }

    func makeCoordinator() -> Coordinador { Coordinador(leido: leido) }

    final class Coordinador: NSObject, DataScannerViewControllerDelegate {
        let leido: (String) -> Void
        private var ultimo = ""
        init(leido: @escaping (String) -> Void) { self.leido = leido }

        func dataScanner(_ s: DataScannerViewController, didAdd items: [RecognizedItem], allItems: [RecognizedItem]) {
            for i in items {
                if case let .barcode(b) = i, let v = b.payloadStringValue, v != ultimo {
                    ultimo = v
                    leido(v)
                    return
                }
            }
        }
    }
}
#endif
