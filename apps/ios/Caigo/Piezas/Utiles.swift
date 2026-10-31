import SwiftUI
import Nucleo
#if os(iOS)
import UIKit
typealias ImagenNativa = UIImage
#else
import AppKit
typealias ImagenNativa = NSImage
#endif

extension Image {
    init(nativa: ImagenNativa) {
        #if os(iOS)
        self.init(uiImage: nativa)
        #else
        self.init(nsImage: nativa)
        #endif
    }
}

/// Caché de fotos en memoria (y en disco por URLCache): las listas no vuelven a bajarlas.
final class CacheImagenes: @unchecked Sendable {
    static let compartido = CacheImagenes()
    private let memoria = NSCache<NSString, ImagenNativa>()
    private let sesion: URLSession = {
        let c = URLSessionConfiguration.default
        c.urlCache = URLCache(memoryCapacity: 16 << 20, diskCapacity: 200 << 20)
        c.requestCachePolicy = .returnCacheDataElseLoad
        return URLSession(configuration: c)
    }()

    func enMemoria(_ url: String) -> ImagenNativa? { memoria.object(forKey: url as NSString) }

    func cargar(_ url: String) async -> ImagenNativa? {
        if let i = enMemoria(url) { return i }
        guard let u = URL(string: url), let (d, _) = try? await sesion.data(from: u), let i = ImagenNativa(data: d) else { return nil }
        memoria.setObject(i, forKey: url as NSString)
        return i
    }
}

/// Foto de red que llena su caja (recortada, como `object-fit: cover`).
struct ImagenRed: View {
    var url: String
    @State private var imagen: ImagenNativa?

    var body: some View {
        GeometryReader { g in
            if let i = imagen ?? CacheImagenes.compartido.enMemoria(url) {
                Image(nativa: i).resizable().scaledToFill().frame(width: g.size.width, height: g.size.height).clipped()
                    .transition(.opacity)
            }
        }
        .task(id: url) {
            if imagen == nil || CacheImagenes.compartido.enMemoria(url) == nil {
                let i = await CacheImagenes.compartido.cargar(url)
                withAnimation(.easeOut(duration: 0.2)) { imagen = i }
            }
        }
    }
}

// MARK: - Toasts (2,6 s, arriba, como el prototipo)

struct Aviso: Equatable {
    var texto: String
    var error = false
    var id = UUID()
}

@Observable
final class Avisos {
    var actual: Aviso?
    func ok(_ t: String) { actual = Aviso(texto: t) }
    func error(_ t: String) { actual = Aviso(texto: t, error: true) }
    func error(_ e: Error) { actual = Aviso(texto: mensajeDe(e), error: true) }
    func limpiar() { actual = nil }
}

struct CapaAvisos: View {
    var avisos: Avisos
    var body: some View {
        VStack {
            if let a = avisos.actual {
                HStack(spacing: 10) {
                    Icono(a.error ? I.cruz : I.check, 14, C.carbon, trazo: 3.4)
                        .frame(width: 28, height: 28)
                        .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(a.error ? C.coral : C.lima))
                    T(a.texto, 14, 700, C.blancoCalido, alto: 1.4)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 16).padding(.vertical, 14)
                .gel(20, .liso(C.carbon), [externa(18, 30, rgba(0, 0, 0, 0.5), spread: -12)])
                .padding(.horizontal, 20).padding(.top, 10)
                .transition(.move(edge: .top).combined(with: .opacity))
                .id(a.id)
                .task(id: a.id) {
                    try? await Task.sleep(nanoseconds: 2_600_000_000)
                    if avisos.actual?.id == a.id { withAnimation(.easeOut(duration: 0.2)) { avisos.limpiar() } }
                }
                .onTapGesture { withAnimation { avisos.limpiar() } }
            }
            Spacer()
        }
        .animation(.easeOut(duration: 0.25), value: avisos.actual)
    }
}

// MARK: - Cargas de la API

/// Estado de una carga: lo que trae la API, el error para mostrar y si está cargando.
@Observable
final class Carga<V> {
    var dato: V?
    var error: String?
    var cargando = true
    @ObservationIgnored private let cargar: () async throws -> V

    init(_ cargar: @escaping () async throws -> V) { self.cargar = cargar }

    @MainActor
    func correr() async {
        cargando = true
        do {
            dato = try await cargar()
            error = nil
        } catch is CancellationError {
        } catch let e as URLError where e.code == .cancelled {
        } catch {
            self.error = mensajeDe(error)
        }
        cargando = false
    }
}

/// Lo que se muestra mientras una carga no trae nada: los puntitos o el error con "Reintentar".
struct EstadoCarga<V>: View {
    var carga: Carga<V>
    @Environment(\.paleta) private var p
    var body: some View {
        if carga.dato == nil {
            if let e = carga.error {
                VStack(spacing: 18) {
                    MensajeCentro(titulo: "Uy, algo falló", cuerpo: e)
                    Cta(texto: "Reintentar", gel: Gels.violeta, alto: 52, radio: 18, tam: 16, color: C.blanco) {
                        Task { await carga.correr() }
                    }
                    .frame(width: 200)
                }
                .padding(32)
            } else {
                Puntitos(color: p.acento).padding(40)
            }
        }
    }
}

// MARK: - Compartir y abrir

enum Sistema {
    @MainActor
    static func abrir(_ url: String) {
        guard let u = URL(string: url) else { return }
        #if os(iOS)
        UIApplication.shared.open(u)
        #else
        NSWorkspace.shared.open(u)
        #endif
    }

    @MainActor
    static func copiar(_ texto: String) {
        #if os(iOS)
        UIPasteboard.general.string = texto
        #else
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(texto, forType: .string)
        #endif
    }

    /// Comparte por WhatsApp si está instalado; si no, la hoja común de compartir.
    @MainActor
    static func compartirWhatsApp(_ texto: String) {
        #if os(iOS)
        let q = texto.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
        if let u = URL(string: "whatsapp://send?text=\(q)"), UIApplication.shared.canOpenURL(u) {
            UIApplication.shared.open(u)
        } else {
            compartir(texto)
        }
        #endif
    }

    @MainActor
    static func compartir(_ texto: String) {
        #if os(iOS)
        let vc = UIActivityViewController(activityItems: [texto], applicationActivities: nil)
        arriba()?.present(vc, animated: true)
        #endif
    }

    #if os(iOS)
    @MainActor
    static func arriba() -> UIViewController? {
        let escena = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first { $0.activationState == .foregroundActive }
            ?? UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        var vc = escena?.windows.first { $0.isKeyWindow }?.rootViewController ?? escena?.windows.first?.rootViewController
        while let p = vc?.presentedViewController { vc = p }
        return vc
    }

    @MainActor
    static func ventana() -> UIWindow? {
        let escena = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first
        return escena?.windows.first { $0.isKeyWindow } ?? escena?.windows.first
    }
    #endif

    /// Vibración corta de confirmación.
    @MainActor
    static func vibrar(_ exito: Bool = true) {
        #if os(iOS)
        UINotificationFeedbackGenerator().notificationOccurred(exito ? .success : .error)
        #endif
    }
}

extension View {
    /// Teclado numérico / de teléfono en iOS (en la Mac no existe).
    func teclado(_ tipo: TipoTeclado) -> some View {
        #if os(iOS)
        let t: UIKeyboardType = switch tipo {
        case .numeros: .numberPad
        case .telefono: .phonePad
        case .texto: .default
        }
        return self.keyboardType(t).textInputAutocapitalization(tipo == .texto ? .sentences : .never)
        #else
        return self
        #endif
    }
}

enum TipoTeclado { case numeros, telefono, texto }
