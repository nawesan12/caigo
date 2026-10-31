import SwiftUI
import Nucleo
#if os(iOS)
import AVFoundation
import Vision
import UIKit
#endif

/// Prueba de vida: mirar a la cámara, girar la cabeza a un lado y después al otro. Además lleva
/// los requisitos de la foto (buena luz, cara centrada, ojos a la vista). Es la misma lógica que
/// `IngresoVida` de Android; los cuadros los analiza Vision.
@Observable
final class IngresoVida {
    /// 0 mirar a la cámara · 1 girar a un lado · 2 girar al otro lado · 3 listo.
    var fase = 0
    var progreso: CGFloat = 0
    var luz = false
    var centrada = false
    var ojos = false
    var hayCara = false
    @ObservationIgnored private var quieto = 0
    @ObservationIgnored private var lado: CGFloat = 0

    func reiniciar() {
        fase = 0; progreso = 0; quieto = 0; lado = 0
    }

    /// Un cuadro analizado: `yaw` es el giro de la cabeza en grados.
    func cuadro(cara: Bool, yaw: CGFloat, centro: Bool, ojosAbiertos: Bool, luminancia: CGFloat) {
        luz = luminancia > 70
        hayCara = cara
        centrada = cara && centro
        ojos = cara && ojosAbiertos
        guard cara, fase != 3 else { return }
        switch fase {
        case 0:
            if centrada && abs(yaw) < 12 {
                quieto += 1
                progreso = min(0.25, CGFloat(quieto) * 0.03)
                if quieto >= 8 { fase = 1 }
            }
        case 1:
            progreso = 0.25 + 0.4 * min(abs(yaw) / 22, 1)
            if abs(yaw) >= 22 { lado = yaw > 0 ? 1 : -1; fase = 2 }
        case 2:
            let otro = -yaw * lado
            progreso = 0.65 + 0.3 * min(max(otro, 0) / 22, 1)
            if otro >= 22 { progreso = 1; fase = 3 }
        default: break
        }
    }
}

/// Estado del permiso de cámara.
enum IngresoPermisoCamara {
    static var concedido: Bool {
        #if os(iOS)
        return AVCaptureDevice.authorizationStatus(for: .video) == .authorized
        #else
        return false
        #endif
    }

    static func pedir() async -> Bool {
        #if os(iOS)
        if AVCaptureDevice.authorizationStatus(for: .video) == .denied {
            await MainActor.run { Sistema.abrir("app-settings:") }
            return false
        }
        return await AVCaptureDevice.requestAccess(for: .video)
        #else
        return false
        #endif
    }
}

#if os(iOS)
/// La cámara con AVFoundation: vista previa, cuadros para Vision y fotos JPEG.
/// `analizar` recibe cada cuadro ya derecho (retrato) en un hilo aparte.
final class IngresoCamara: NSObject, AVCaptureVideoDataOutputSampleBufferDelegate, AVCapturePhotoCaptureDelegate, @unchecked Sendable {
    let sesion = AVCaptureSession()
    let frontal: Bool
    private let cola = DispatchQueue(label: "ar.com.caigo.camara")
    private let colaCuadros = DispatchQueue(label: "ar.com.caigo.camara.cuadros")
    private let foto = AVCapturePhotoOutput()
    private let video = AVCaptureVideoDataOutput()
    private var configurada = false
    private var esperandoFoto: CheckedContinuation<Data, Error>?
    private var ocupado = false
    var analizar: ((CVPixelBuffer) -> Void)?

    init(frontal: Bool) {
        self.frontal = frontal
        super.init()
    }

    func encender() {
        cola.async { [self] in
            if !configurada { configurar() }
            if !sesion.isRunning { sesion.startRunning() }
        }
    }

    func apagar() {
        cola.async { [self] in if sesion.isRunning { sesion.stopRunning() } }
    }

    private func configurar() {
        configurada = true
        sesion.beginConfiguration()
        sesion.sessionPreset = .photo
        guard let dispositivo = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: frontal ? .front : .back),
              let entrada = try? AVCaptureDeviceInput(device: dispositivo), sesion.canAddInput(entrada) else {
            sesion.commitConfiguration()
            return
        }
        sesion.addInput(entrada)
        if sesion.canAddOutput(foto) { sesion.addOutput(foto) }
        video.videoSettings = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_420YpCbCr8BiPlanarFullRange]
        video.alwaysDiscardsLateVideoFrames = true
        video.setSampleBufferDelegate(self, queue: colaCuadros)
        if sesion.canAddOutput(video) { sesion.addOutput(video) }
        // Cuadros y fotos derechos (retrato). Los cuadros de la frontal van espejados, como se ven.
        if let c = video.connection(with: .video) {
            if c.isVideoRotationAngleSupported(90) { c.videoRotationAngle = 90 }
            if frontal && c.isVideoMirroringSupported { c.automaticallyAdjustsVideoMirroring = false; c.isVideoMirrored = true }
        }
        if let c = foto.connection(with: .video) {
            if c.isVideoRotationAngleSupported(90) { c.videoRotationAngle = 90 }
            // La foto que se sube va sin espejo (como Android).
            if c.isVideoMirroringSupported { c.automaticallyAdjustsVideoMirroring = false; c.isVideoMirrored = false }
        }
        if !frontal, (try? dispositivo.lockForConfiguration()) != nil {
            if dispositivo.isFocusModeSupported(.continuousAutoFocus) { dispositivo.focusMode = .continuousAutoFocus }
            dispositivo.unlockForConfiguration()
        }
        sesion.commitConfiguration()
    }

    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard !ocupado, let b = CMSampleBufferGetImageBuffer(sampleBuffer), let analizar else { return }
        ocupado = true
        analizar(b)
        ocupado = false
    }

    /// Saca una foto y la devuelve como JPEG (derecha, sin espejo, lado mayor 1600, calidad .88).
    func sacarFoto() async throws -> Data {
        let crudo: Data = try await withCheckedThrowingContinuation { c in
            cola.async { [self] in
                guard sesion.isRunning, foto.connection(with: .video) != nil else {
                    c.resume(throwing: CancellationError())
                    return
                }
                esperandoFoto = c
                foto.capturePhoto(with: AVCapturePhotoSettings(), delegate: self)
            }
        }
        guard let img = UIImage(data: crudo), let jpeg = IngresoCamara.jpeg(img) else { throw CancellationError() }
        return jpeg
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let c = esperandoFoto
        esperandoFoto = nil
        if let d = photo.fileDataRepresentation() { c?.resume(returning: d) } else { c?.resume(throwing: error ?? CancellationError()) }
    }

    static func jpeg(_ img: UIImage, ladoMax: CGFloat = 1600, calidad: CGFloat = 0.88) -> Data? {
        let k = min(1, ladoMax / max(img.size.width, img.size.height))
        let tam = CGSize(width: (img.size.width * k).rounded(), height: (img.size.height * k).rounded())
        let f = UIGraphicsImageRendererFormat()
        f.scale = 1
        return UIGraphicsImageRenderer(size: tam, format: f).image { _ in img.draw(in: CGRect(origin: .zero, size: tam)) }
            .jpegData(compressionQuality: calidad)
    }

    /// Luminancia media del plano Y (muestreada).
    static func luminancia(_ b: CVPixelBuffer) -> CGFloat {
        CVPixelBufferLockBaseAddress(b, .readOnly)
        defer { CVPixelBufferUnlockBaseAddress(b, .readOnly) }
        guard CVPixelBufferGetPlaneCount(b) > 0, let base = CVPixelBufferGetBaseAddressOfPlane(b, 0) else { return 128 }
        let w = CVPixelBufferGetWidthOfPlane(b, 0), h = CVPixelBufferGetHeightOfPlane(b, 0), fila = CVPixelBufferGetBytesPerRowOfPlane(b, 0)
        let p = base.assumingMemoryBound(to: UInt8.self)
        var suma = 0, n = 0
        var y = 0
        while y < h {
            var x = 0
            while x < w { suma += Int(p[y * fila + x]); n += 1; x += 24 }
            y += 24
        }
        return n == 0 ? 128 : CGFloat(suma) / CGFloat(n)
    }
}

/// Vista previa de la cámara (llena la caja, como `object-fit: cover`).
struct IngresoVistaCamara: UIViewRepresentable {
    var camara: IngresoCamara

    final class Vista: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var capa: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    func makeUIView(context: Context) -> Vista {
        let v = Vista()
        v.backgroundColor = .black
        v.capa.session = camara.sesion
        v.capa.videoGravity = .resizeAspectFill
        camara.encender()
        return v
    }

    func updateUIView(_ uiView: Vista, context: Context) {}

    static func dismantleUIView(_ uiView: Vista, coordinator: ()) {
        uiView.capa.session?.stopRunning()
    }
}

/// Analiza un cuadro de la frontal con Vision para la prueba de vida.
enum IngresoAnalisisCara {
    static func analizar(_ b: CVPixelBuffer, vida: IngresoVida) {
        let lum = IngresoCamara.luminancia(b)
        let rect = VNDetectFaceRectanglesRequest()
        rect.revision = VNDetectFaceRectanglesRequestRevision3
        let marcas = VNDetectFaceLandmarksRequest()
        try? VNImageRequestHandler(cvPixelBuffer: b, orientation: .up).perform([rect, marcas])
        let caras = (rect.results ?? [])
        guard let cara = caras.max(by: { $0.boundingBox.width * $0.boundingBox.height < $1.boundingBox.width * $1.boundingBox.height }),
              cara.boundingBox.width > 0.2 else {
            DispatchQueue.main.async { vida.cuadro(cara: false, yaw: 0, centro: false, ojosAbiertos: false, luminancia: lum) }
            return
        }
        let yaw = CGFloat(truncating: cara.yaw ?? 0) * 180 / .pi
        let cx = cara.boundingBox.midX, cy = cara.boundingBox.midY
        let centro = abs(cx - 0.5) < 0.17 && abs(cy - 0.5) < 0.22
        // Ojos abiertos: alto / ancho del contorno de cada ojo (con lentes oscuros no hay landmarks).
        var ojos = false
        if let m = (marcas.results ?? []).first?.landmarks, let i = m.leftEye, let d = m.rightEye {
            ojos = apertura(i.normalizedPoints) > 0.12 && apertura(d.normalizedPoints) > 0.12
        }
        DispatchQueue.main.async { vida.cuadro(cara: true, yaw: yaw, centro: centro, ojosAbiertos: ojos, luminancia: lum) }
    }

    private static func apertura(_ p: [CGPoint]) -> CGFloat {
        guard let minX = p.map(\.x).min(), let maxX = p.map(\.x).max(), let minY = p.map(\.y).min(), let maxY = p.map(\.y).max(), maxX > minX else { return 0 }
        return (maxY - minY) / (maxX - minX)
    }
}

/// Lee el PDF417 del DNI con Vision.
enum IngresoLectorPdf417 {
    static func leer(_ b: CVPixelBuffer) -> String? {
        let r = VNDetectBarcodesRequest()
        r.symbologies = [.pdf417]
        try? VNImageRequestHandler(cvPixelBuffer: b, orientation: .up).perform([r])
        return (r.results ?? []).compactMap(\.payloadStringValue).first
    }
}
#endif

/// Círculo con anillo de progreso conic (01e: violeta sobre `#E4DEFA`; DNI: lima). Adentro va la
/// cámara o la foto, con el borde de 6 del color del fondo.
struct IngresoAnillo<Contenido: View>: View {
    var tam: CGFloat
    var grosor: CGFloat
    var progreso: CGFloat
    var color: Color
    var pista: Color
    var borde: Color = C.blancoCalido
    @ViewBuilder var contenido: () -> Contenido

    var body: some View {
        ZStack {
            Circle().fill(pista)
            IngresoSector(fin: progreso).fill(color)
            ZStack {
                Color(hex: 0xE9E4F7)
                contenido()
            }
            .frame(width: tam - grosor * 2, height: tam - grosor * 2)
            .clipShape(Circle())
            .overlay(Circle().strokeBorder(borde, lineWidth: 6))
        }
        .frame(width: tam, height: tam)
        .animation(.easeOut(duration: 0.22), value: progreso)
    }
}

/// `conic-gradient(color 0 N%, …)`: el sector desde las 12 en sentido horario.
struct IngresoSector: Shape {
    var fin: CGFloat
    var animatableData: CGFloat {
        get { fin }
        set { fin = newValue }
    }
    func path(in r: CGRect) -> Path {
        var p = Path()
        guard fin > 0 else { return p }
        let c = CGPoint(x: r.midX, y: r.midY)
        p.move(to: c)
        p.addArc(center: c, radius: min(r.width, r.height) / 2, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * Double(min(fin, 1))), clockwise: false)
        p.closeSubpath()
        return p
    }
}

/// Pill oscura de la instrucción.
struct IngresoPillOscura: View {
    var texto: String
    var body: some View {
        T(texto, 14, 600, C.blancoCalido, alinear: .center)
            .padding(.horizontal, 16).padding(.vertical, 10)
            .gel(14, Gels.oscuroSel)
            .animation(.easeOut(duration: 0.2), value: texto)
    }
}

/// Imagen de un JPEG en memoria.
struct IngresoImagenDatos: View {
    var datos: Data
    var body: some View {
        if let i = ImagenNativa(data: datos) {
            Image(nativa: i).resizable().scaledToFill()
        }
    }
}
