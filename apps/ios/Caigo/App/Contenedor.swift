import SwiftUI
import CoreLocation
import Nucleo

/// A dónde hay que ir por un link, un aviso o una placa.
enum Destino: Equatable {
    case plan(String)
    case planSlug(String)
    case pantalla(pantalla: String, plan: String?, privado: String?, modo: String?)
    case tap(placa: String, picc: String, cmac: String)
}

/// Configuración que viene del Info.plist (sale de Config/*.xcconfig).
enum Config {
    static var api: URL {
        let s = (Bundle.main.object(forInfoDictionaryKey: "CaigoAPI") as? String) ?? ""
        return URL(string: s.isEmpty ? "https://api.caigo.com.ar" : s) ?? URL(string: "https://api.caigo.com.ar")!
    }
    static var clerkPublicable: String { (Bundle.main.object(forInfoDictionaryKey: "CaigoClerkPublicable") as? String) ?? "" }
    /// Corre como App Clip (Caigo Tap).
    static var esClip: Bool { Bundle.main.bundleIdentifier?.hasSuffix(".Clip") ?? false }
    /// Grupo de llavero compartido con el App Clip (`$(AppIdentifierPrefix)ar.com.caigo.app`).
    static var grupoLlavero: String? {
        let s = (Bundle.main.object(forInfoDictionaryKey: "CaigoGrupoLlavero") as? String) ?? ""
        // Sin firma, AppIdentifierPrefix queda vacío: no hay grupo.
        return s.contains(".") && !s.hasPrefix(".") && s.split(separator: ".").first?.count == 10 ? s : nil
    }
}

/// Todo lo que comparten las pantallas: API, sesión, tiempo real, ubicación y destinos pendientes
/// (como Contenedor en App.kt de Android).
@MainActor
@Observable
final class Contenedor {
    let api: Api
    let clerk: Clerk
    let tiempoReal: TiempoReal
    let avisos = Avisos()
    let ubicador = Ubicador()

    /// La sesión de Caigo (`cs_…`), guardada en el Keychain.
    private(set) var token: String?
    private(set) var usuarioId: String
    /// Ya vio la intro (splash + 01a–01c).
    private(set) var vioIntro: Bool
    /// Mi perfil (se recarga al entrar y al editar).
    var yo: Perfil?
    /// Estado de Plus (se recarga al entrar a Perfil, al comprar y en el paywall).
    var plus: EstadoPlus?
    var destino: Destino?
    /// Resultado del último tap en una placa (pantalla "Adentro").
    var ultimoTap: ResultadoTap?
    /// Placa leída (link o NFC) que la pantalla de Caigo Tap tiene que mandar a la API.
    var tapPendiente: Destino?
    /// Token de APNs (hex) para registrarlo cuando haya sesión.
    var tokenPush: String?
    /// 01b · «Voy solo / Somos 2 / Somos 4»: con cuántos se suma por defecto (1, 2 o 4).
    var somos: Int {
        didSet { UserDefaults.standard.set(somos, forKey: "somos") }
    }

    /// Caja para que la API (fuera del actor principal) lea el token.
    final class CajaToken: @unchecked Sendable {
        private let l = NSLock()
        private var t: String?
        var valor: String? {
            get { l.lock(); defer { l.unlock() }; return t }
            set { l.lock(); t = newValue; l.unlock() }
        }
    }
    @ObservationIgnored private let caja: CajaToken

    /// Sólo en las pruebas de pantallas (CaigoTests): la API simulada y una sesión puesta.
    nonisolated(unsafe) static var prueba: (base: URL, sesion: URLSession, token: String, usuarioId: String)?

    init() {
        let caja = CajaToken()
        self.caja = caja
        Llavero.grupo = Config.grupoLlavero
        // Si se entró desde el App Clip, la sesión quedó en el App Group: pasa al llavero.
        if !Config.esClip, Llavero.leer("token") == nil, let s = Traspaso.tomar() {
            Llavero.guardar(s.token, cuenta: "token")
            Llavero.guardar(s.usuarioId, cuenta: "usuario")
        }
        let t = Contenedor.prueba?.token ?? Llavero.leer("token")
        caja.valor = t
        token = t
        usuarioId = Contenedor.prueba?.usuarioId ?? Llavero.leer("usuario") ?? ""
        vioIntro = UserDefaults.standard.bool(forKey: "vioIntro")
        let s = UserDefaults.standard.integer(forKey: "somos")
        somos = s > 0 ? s : 1
        let vencer = Vencimiento()
        self.vencer = vencer
        let base = Contenedor.prueba?.base ?? Config.api
        api = Api(base: base, sesion: Contenedor.prueba?.sesion ?? Api.sesionHttp(), token: { caja.valor }, alVencer: { vencer.f() })
        clerk = Clerk(publicable: Config.clerkPublicable)
        tiempoReal = TiempoReal(base: base, token: { caja.valor })
        vencer.f = { [weak self] in Task { @MainActor in self?.salirLocal() } }
    }

    /// Si la API responde 401, la sesión se cierra en la app.
    final class Vencimiento: @unchecked Sendable { var f: @Sendable () -> Void = {} }
    @ObservationIgnored private let vencer: Vencimiento

    var conSesion: Bool { token != nil }

    func entrar(token: String, usuarioId: String) {
        Llavero.guardar(token, cuenta: "token")
        Llavero.guardar(usuarioId, cuenta: "usuario")
        caja.valor = token
        self.token = token
        self.usuarioId = usuarioId
        tiempoReal.encender()
        registrarPush()
        mandarUbicacion()
    }

    func marcarIntro() {
        vioIntro = true
        UserDefaults.standard.set(true, forKey: "vioIntro")
    }

    func salir() async {
        if let t = tokenPush { try? await api.borrarDispositivo(t) }
        await api.cerrarSesion()
        salirLocal()
    }

    /// Borra la cuenta (DELETE /v1/yo) y la sesión local.
    func borrarCuenta() async throws {
        try await api.borrarCuenta()
        salirLocal()
    }

    func salirLocal() {
        tiempoReal.apagar()
        Llavero.borrar("token")
        Llavero.borrar("usuario")
        Traspaso.borrar()
        caja.valor = nil
        token = nil
        usuarioId = ""
        yo = nil
        plus = nil
    }

    @discardableResult
    func recargarYo() async -> Perfil? {
        guard let p = try? await api.yo() else { return yo }
        yo = p
        return p
    }

    @discardableResult
    func recargarPlus() async -> EstadoPlus? {
        guard let p = try? await api.plus() else { return plus }
        plus = p.estado
        return p.estado
    }

    func registrarPush() {
        guard conSesion, let t = tokenPush else { return }
        Task { try? await api.registrarDispositivo(t) }
    }

    @ObservationIgnored private var ubicacionMandada: Date?

    /// PUT /v1/yo/ubicacion (avisos de planes cerca): al abrir y al volver a primer plano, como mucho
    /// una vez cada 30 minutos. Sólo si ya hay permiso: no se pide permiso para esto.
    func mandarUbicacion() {
        guard conSesion, !Config.esClip, ubicador.permitido else { return }
        if let u = ubicacionMandada, Date().timeIntervalSince(u) < 30 * 60 { return }
        ubicacionMandada = Date()
        Task {
            guard let u = await ubicacion() else { ubicacionMandada = nil; return }
            do { try await api.ubicacion(lat: u.0, lng: u.1) } catch { ubicacionMandada = nil }
        }
    }

    /// Ubicación actual (o la última conocida). Sin permiso o sin señal: nil.
    func ubicacion() async -> (Double, Double)? { await ubicador.actual() }

    /// Interpreta un link entrante: ar.com.caigo.app://…, https://caigo.com.ar/p/… o /t/….
    func destinoDe(_ url: URL) -> Destino? {
        let c = URLComponents(url: url, resolvingAgainstBaseURL: false)
        func q(_ k: String) -> String { c?.queryItems?.first { $0.name == k }?.value ?? "" }
        let seg = url.pathComponents.filter { $0 != "/" }
        let esquema = url.scheme ?? ""
        let host = url.host ?? ""
        if esquema == Clerk.esquema {
            if host == "p", let s = seg.first { return .planSlug(s) }
            if host == "t", let s = seg.first { return .tap(placa: s, picc: q("picc_data"), cmac: q("cmac")) }
            if host == "plan", let s = seg.first { return .plan(s) }
            return nil
        }
        if esquema == "https", seg.count >= 2 {
            if seg[0] == "p" { return .planSlug(seg[1]) }
            if seg[0] == "t" { return .tap(placa: seg[1], picc: q("picc_data"), cmac: q("cmac")) }
        }
        return nil
    }
}

/// La ubicación con CoreLocation: pide permiso la primera vez y guarda la última conocida.
@MainActor
final class Ubicador: NSObject, CLLocationManagerDelegate {
    private let m = CLLocationManager()
    private var esperando: [CheckedContinuation<CLLocation?, Never>] = []
    private(set) var ultima: CLLocation?

    override init() {
        super.init()
        m.delegate = self
        m.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    var permitido: Bool {
        #if os(iOS)
        return m.authorizationStatus == .authorizedWhenInUse || m.authorizationStatus == .authorizedAlways
        #else
        return m.authorizationStatus == .authorizedAlways
        #endif
    }

    var conocida: (Double, Double)? { ultima.map { ($0.coordinate.latitude, $0.coordinate.longitude) } }

    func pedirPermiso() {
        if m.authorizationStatus == .notDetermined {
            #if os(iOS)
            m.requestWhenInUseAuthorization()
            #endif
        }
    }

    func actual() async -> (Double, Double)? {
        if let u = ultima, -u.timestamp.timeIntervalSinceNow < 300 { return (u.coordinate.latitude, u.coordinate.longitude) }
        if m.authorizationStatus == .notDetermined { pedirPermiso() }
        if m.authorizationStatus == .denied || m.authorizationStatus == .restricted { return nil }
        let loc: CLLocation? = await withCheckedContinuation { c in
            esperando.append(c)
            m.requestLocation()
            Task { @MainActor in
                try? await Task.sleep(nanoseconds: 4_000_000_000)
                self.responder(nil)
            }
        }
        return loc.map { ($0.coordinate.latitude, $0.coordinate.longitude) }
    }

    private func responder(_ l: CLLocation?) {
        if let l { ultima = l }
        let e = esperando
        esperando = []
        e.forEach { $0.resume(returning: l ?? ultima) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        let l = locations.last
        Task { @MainActor in self.responder(l) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.responder(nil) }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            if self.permitido && !self.esperando.isEmpty { self.m.requestLocation() }
        }
    }
}
