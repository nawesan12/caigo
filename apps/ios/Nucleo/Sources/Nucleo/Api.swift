import Foundation

/// Error de la API: `detalle` es lo que se le muestra a la persona, tal cual.
public struct ErrorApi: Error, LocalizedError, Sendable {
    public let estado: Int
    public let problema: Problema
    public init(estado: Int, problema: Problema) { self.estado = estado; self.problema = problema }
    public var codigo: String { problema.code }
    public var detalle: String {
        if !problema.detail.isEmpty { return problema.detail }
        if !problema.title.isEmpty { return problema.title }
        return "Algo salió mal. Probá de nuevo."
    }
    public var errorDescription: String? { detalle }
}

/// Mensaje para mostrar de cualquier error (de la API o de la red).
public func mensajeDe(_ e: Error) -> String {
    if let a = e as? ErrorApi { return a.detalle }
    if e is URLError { return "No hay conexión. Revisá internet y probá de nuevo." }
    if let c = e as? Clerk.ErrorClerk { return c.mensaje }
    return (e as? LocalizedError)?.errorDescription ?? "Algo salió mal. Probá de nuevo."
}

/// Valor de query: nil o vacío no se manda.
public typealias Query = [(String, String?)]

/// Cliente de la API de Caigo. Todo pedido lleva `Authorization: Bearer <token cs_…>`. Si la API
/// responde 401, se avisa a `alVencer` (la app cierra la sesión).
public final class Api: @unchecked Sendable {
    public let base: URL
    public let sesion: URLSession
    private let token: @Sendable () -> String?
    private let alVencer: @Sendable () -> Void

    public init(base: URL, sesion: URLSession = Api.sesionHttp(), token: @escaping @Sendable () -> String?, alVencer: @escaping @Sendable () -> Void = {}) {
        self.base = base
        self.sesion = sesion
        self.token = token
        self.alVencer = alVencer
    }

    public static func sesionHttp() -> URLSession {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 30
        c.timeoutIntervalForResource = 90
        c.waitsForConnectivity = false
        return URLSession(configuration: c)
    }

    public func url(_ ruta: String, _ query: Query = []) -> URL {
        var c = URLComponents(url: base, resolvingAgainstBaseURL: false)!
        c.path = (c.path.hasSuffix("/") ? String(c.path.dropLast()) : c.path) + ruta
        let q = query.compactMap { k, v -> URLQueryItem? in
            guard let v, !v.isEmpty else { return nil }
            return URLQueryItem(name: k, value: v)
        }
        if !q.isEmpty { c.queryItems = q }
        return c.url!
    }

    /// Hace el pedido y devuelve el cuerpo. Con error, tira `ErrorApi` con el problem+json.
    public func crudo(_ metodo: String, _ url: URL, cuerpo: Data? = nil, tipo: String = "application/json", autorizacion: String? = nil) async throws -> Data {
        var r = URLRequest(url: url)
        r.httpMethod = metodo
        r.setValue("application/json", forHTTPHeaderField: "Accept")
        if let cuerpo {
            r.httpBody = cuerpo
            r.setValue(tipo, forHTTPHeaderField: "Content-Type")
        }
        let t = autorizacion ?? token()
        if let t, !t.isEmpty { r.setValue("Bearer \(t)", forHTTPHeaderField: "Authorization") }
        let (datos, resp) = try await sesion.data(for: r)
        let estado = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200..<300).contains(estado) else {
            var p = (try? JSON.leer(Problema.self, datos)) ?? Problema(title: "Error \(estado)")
            p.status = estado
            if estado == 401 && autorizacion == nil { alVencer() }
            throw ErrorApi(estado: estado, problema: p)
        }
        return datos
    }

    public func get<R: Decodable>(_ ruta: String, _ query: Query = [], como: R.Type = R.self) async throws -> R {
        try JSON.leer(R.self, try await crudo("GET", url(ruta, query)))
    }

    public func enviar<B: Encodable, R: Decodable>(_ metodo: String, _ ruta: String, _ cuerpo: B, _ query: Query = [], como: R.Type = R.self) async throws -> R {
        try JSON.leer(R.self, try await crudo(metodo, url(ruta, query), cuerpo: try JSON.codificador.encode(cuerpo)))
    }

    public func mandar<B: Encodable>(_ metodo: String, _ ruta: String, _ cuerpo: B) async throws {
        _ = try await crudo(metodo, url(ruta), cuerpo: try JSON.codificador.encode(cuerpo))
    }

    public func sinCuerpo(_ metodo: String, _ ruta: String, _ query: Query = []) async throws {
        _ = try await crudo(metodo, url(ruta, query), cuerpo: metodo == "GET" || metodo == "DELETE" ? nil : Data())
    }

    // MARK: Sesión

    /// Canjea el JWT de Clerk por el token propio de Caigo (cs_…, 60 días).
    public func crearSesion(jwtClerk: String) async throws -> SesionNueva {
        try JSON.leer(SesionNueva.self, try await crudo("POST", url("/v1/sesion", [("cliente", "ios")]), cuerpo: Data(), autorizacion: jwtClerk))
    }

    public func cerrarSesion() async { try? await sinCuerpo("DELETE", "/v1/sesion") }

    // MARK: Yo y personas

    public func yo() async throws -> Perfil { try await get("/v1/yo") }
    public func actualizarYo(_ c: CambiosPerfil) async throws -> Perfil { try await enviar("PATCH", "/v1/yo", c) }
    /// Da de baja la cuenta: se desactiva ahora y los datos personales se borran a los 30 días. 204.
    public func borrarCuenta() async throws { try await sinCuerpo("DELETE", "/v1/cuenta") }
    public func persona(_ id: String) async throws -> Perfil { try await get("/v1/personas/\(id)") }
    public func miGente() async throws -> Gente { try await get("/v1/gente") }
    public func sumarAMiGente(_ id: String) async throws { try await sinCuerpo("PUT", "/v1/gente/\(id)") }
    public func sacarDeMiGente(_ id: String) async throws { try await sinCuerpo("DELETE", "/v1/gente/\(id)") }
    public func bloquear(_ id: String) async throws { try await sinCuerpo("PUT", "/v1/bloqueos/\(id)") }
    public func bloqueos() async throws -> Bloqueos { try await get("/v1/bloqueos") }
    public func desbloquear(_ id: String) async throws { try await sinCuerpo("DELETE", "/v1/bloqueos/\(id)") }
    /// Se manda al abrir la app (redondeada a ~1 km; la API la borra a las 24 h).
    public func ubicacion(lat: Double, lng: Double) async throws { try await mandar("PUT", "/v1/yo/ubicacion", Ubicacion(lat: lat, lng: lng)) }
    public func version() async throws -> Version { try await get("/v1/version") }
    public func reportar(_ r: Reporte) async throws { try await mandar("POST", "/v1/reportes", r) }

    // MARK: Planes

    public static func num(_ d: Double?) -> String? { d.map { String(format: "%.6f", $0) } }

    /// `q` (≥ 2 letras) busca en título, zona y lugar; `ciudad` se usa cuando no hay ubicación.
    public func ahora(lat: Double?, lng: Double?, categoria: String?, filtros: FiltrosFinos = FiltrosFinos(),
                      q: String? = nil, zona: String? = nil, ciudad: String? = nil) async throws -> Planes {
        try await get("/v1/planes/ahora", [("lat", Api.num(lat)), ("lng", Api.num(lng)), ("categoria", categoria),
                                           ("q", Api.busqueda(q)), ("zona", zona), ("ciudad", ciudad)] + filtros.query)
    }

    public func explorar(desde: String? = nil, hasta: String? = nil, categoria: String? = nil, ciudad: String? = nil,
                         filtros: FiltrosFinos = FiltrosFinos(), lat: Double? = nil, lng: Double? = nil,
                         q: String? = nil, zona: String? = nil) async throws -> Planes {
        try await get("/v1/planes/explorar", [("desde", desde), ("hasta", hasta), ("categoria", categoria), ("ciudad", ciudad),
                                              ("lat", Api.num(lat)), ("lng", Api.num(lng)),
                                              ("q", Api.busqueda(q)), ("zona", zona)] + filtros.query)
    }

    /// La búsqueda sólo viaja con 2 letras o más.
    public static func busqueda(_ q: String?) -> String? {
        guard let t = q?.trimmingCharacters(in: .whitespacesAndNewlines), t.count >= 2 else { return nil }
        return t
    }

    /// Las zonas de una ciudad (las de planes de los próximos 30 días + las fijas).
    public func zonas(ciudad: String) async throws -> Zonas { try await get("/v1/publico/zonas", [("ciudad", ciudad)]) }

    /// Sin sesión: lo público de Ahora, Explorar y el detalle.
    public func publicoExplorar() async throws -> Planes { try await get("/v1/publico/planes/explorar") }

    public func mios(_ cual: String) async throws -> Planes { try await get("/v1/planes/mios", [("cual", cual)]) }
    public func plan(_ id: String, lat: Double? = nil, lng: Double? = nil) async throws -> Plan {
        try await get("/v1/planes/\(id)", [("lat", Api.num(lat)), ("lng", Api.num(lng))])
    }
    public func publico(slug: String) async throws -> Plan { try await get("/v1/publico/planes/\(slug)") }
    public func crearPlan(_ p: PlanNuevo) async throws -> Plan { try await enviar("POST", "/v1/planes", p) }
    public func cancelarPlan(_ id: String) async throws { try await sinCuerpo("DELETE", "/v1/planes/\(id)") }
    /// Organizador, plan que no empezó. 409 `cupo_menor` si `total` queda por debajo de los que van.
    public func editarPlan(_ id: String, _ c: CambiosPlan) async throws -> Plan { try await enviar("PATCH", "/v1/planes/\(id)", c) }
    /// Organizador: saca a alguien aceptado (409 `con_entrada` si pagó entrada).
    public func sacarPersona(plan: String, usuario: String) async throws { try await sinCuerpo("DELETE", "/v1/planes/\(plan)/personas/\(usuario)") }
    public func destacar(_ id: String) async throws { try await sinCuerpo("POST", "/v1/planes/\(id)/destacar") }

    struct Sumarse: Encodable { var cantidad: Int; var mensaje: String }
    public func sumarme(_ id: String, cantidad: Int, mensaje: String = "") async throws -> Estado {
        try await enviar("POST", "/v1/planes/\(id)/sumarme", Sumarse(cantidad: cantidad, mensaje: mensaje))
    }
    public func salir(_ id: String) async throws { try await sinCuerpo("DELETE", "/v1/planes/\(id)/sumarme") }
    public func solicitudes(_ id: String) async throws -> Solicitudes { try await get("/v1/planes/\(id)/solicitudes") }
    public func responder(solicitud: String, aceptar: Bool) async throws {
        try await sinCuerpo("POST", "/v1/solicitudes/\(solicitud)/\(aceptar ? "aceptar" : "rechazar")")
    }
    public func qr(_ id: String) async throws -> Codigo { try await get("/v1/planes/\(id)/qr") }

    public struct Llegada: Encodable, Sendable {
        public var metodo: String
        public var lat: Double?
        public var lng: Double?
        public var codigo = ""
        public init(metodo: String, lat: Double? = nil, lng: Double? = nil, codigo: String = "") {
            self.metodo = metodo; self.lat = lat; self.lng = lng; self.codigo = codigo
        }
    }
    public func checkin(_ id: String, _ l: Llegada) async throws { try await mandar("POST", "/v1/planes/\(id)/checkin", l) }

    struct Repetir: Encodable { var repetir: Bool; var volveria: [Opinion] }
    public func repetimos(_ id: String, repetir: Bool, volveria: [Opinion]) async throws -> Nuevo {
        try await enviar("POST", "/v1/planes/\(id)/repetimos", Repetir(repetir: repetir, volveria: volveria))
    }
    public func enVivo(_ id: String) async throws -> Link {
        try JSON.leer(Link.self, try await crudo("POST", url("/v1/planes/\(id)/en-vivo"), cuerpo: Data()))
    }

    // MARK: Gente, Citas, invitaciones

    public func mazo(_ modo: String) async throws -> Mazo { try await get("/v1/gente/mazo", [("modo", modo)]) }
    struct Reaccion: Encodable { var modo: String; var accion: String }
    /// accion: prendo | interesa | encanta | paso
    public func reaccionar(_ id: String, modo: String, accion: String) async throws -> ResultadoReaccion {
        try await enviar("POST", "/v1/gente/\(id)/reaccion", Reaccion(modo: modo, accion: accion))
    }
    public func recibidos(_ modo: String) async throws -> Recibidos { try await get("/v1/gente/recibidos", [("modo", modo)]) }
    public func descartarRecibido(_ id: String, modo: String) async throws { try await sinCuerpo("DELETE", "/v1/gente/recibidos/\(id)", [("modo", modo)]) }
    struct Invitar: Encodable { var usuarioId: String }
    public func invitar(plan: String, usuario: String) async throws { try await mandar("POST", "/v1/planes/\(plan)/invitar", Invitar(usuarioId: usuario)) }
    public func invitaciones() async throws -> Invitaciones { try await get("/v1/invitaciones") }
    struct Respuesta: Encodable { var aceptar: Bool }
    public func responderInvitacion(_ planId: String, aceptar: Bool) async throws -> Adentro {
        try await enviar("POST", "/v1/invitaciones/\(planId)", Respuesta(aceptar: aceptar))
    }

    // MARK: Chat

    struct Texto: Encodable { var texto: String }
    public func mensajes(_ planId: String, antes: Int64? = nil) async throws -> Mensajes {
        try await get("/v1/planes/\(planId)/mensajes", [("antes", antes.map(String.init))])
    }
    public func escribir(_ planId: String, _ texto: String) async throws -> Mensaje { try await enviar("POST", "/v1/planes/\(planId)/mensajes", Texto(texto: texto)) }
    public func conversaciones() async throws -> Conversaciones { try await get("/v1/privados") }
    public func privados(_ conId: String) async throws -> Privados { try await get("/v1/privados/\(conId)") }
    public func escribirPrivado(_ conId: String, _ texto: String) async throws -> Privado { try await enviar("POST", "/v1/privados/\(conId)", Texto(texto: texto)) }

    // MARK: Confianza

    /// Sube un JPEG crudo. tipo: foto | selfie | dni.
    public func subir(tipo: String, jpeg: Data) async throws -> Archivo {
        try JSON.leer(Archivo.self, try await crudo("POST", url("/v1/archivos", [("tipo", tipo)]), cuerpo: jpeg, tipo: "image/jpeg"))
    }
    public func verificacion() async throws -> EstadoVerificacion { try await get("/v1/verificacion") }
    struct Telefono: Encodable { var telefono: String }
    struct CodigoTelefono: Encodable { var codigo: String }
    struct ArchivoId: Encodable { var archivoId: String }
    public func pedirCodigo(_ telefono: String) async throws -> EnvioDeCodigo { try await enviar("POST", "/v1/verificacion/telefono", Telefono(telefono: telefono)) }
    public func confirmarCodigo(_ codigo: String) async throws { try await mandar("POST", "/v1/verificacion/telefono/confirmar", CodigoTelefono(codigo: codigo)) }
    public func selfie(_ archivoId: String) async throws { try await mandar("POST", "/v1/verificacion/selfie", ArchivoId(archivoId: archivoId)) }
    public func dni(_ d: DatosDNI) async throws { try await mandar("POST", "/v1/verificacion/dni", d) }

    // MARK: Push

    struct Dispositivo: Encodable { var plataforma: String; var token: String }
    public func registrarDispositivo(_ token: String) async throws { try await mandar("PUT", "/v1/dispositivos", Dispositivo(plataforma: "ios", token: token)) }
    public func borrarDispositivo(_ token: String) async throws { try await sinCuerpo("DELETE", "/v1/dispositivos/\(token)") }

    // MARK: Plus

    public func plus() async throws -> Plus { try await get("/v1/plus") }
    struct Jws: Encodable { var jws: String }
    /// Manda la transacción firmada de StoreKit 2 (JWS) para que la API la verifique.
    public func plusApple(jws: String) async throws -> EstadoPlus { try await enviar("POST", "/v1/plus/apple", Jws(jws: jws)) }

    // MARK: Entradas, pases y tap

    struct CompraEntradas: Encodable { var cantidad: Int; var modo: String }
    /// modo: `entrada` (pagar todo) o `sena` (sólo si el plan tiene seña).
    public func comprarEntradas(_ planId: String, cantidad: Int, modo: String) async throws -> Compra {
        try await enviar("POST", "/v1/planes/\(planId)/entradas", CompraEntradas(cantidad: cantidad, modo: modo))
    }
    public func pases() async throws -> Pases { try await get("/v1/pases") }
    /// Cancela mi entrada según la política del plan (409 `sin_reembolso` si es estricta).
    public func cancelarEntrada(_ id: String) async throws -> Reembolso {
        try JSON.leer(Reembolso.self, try await crudo("DELETE", url("/v1/entradas/\(id)")))
    }
    /// El .pkpass firmado de una entrada o de un pase de socio.
    public func pkpassEntrada(_ id: String) async throws -> Data { try await crudo("GET", url("/v1/pases/entradas/\(id)/apple")) }
    public func pkpassSocio(_ lugarId: String) async throws -> Data { try await crudo("GET", url("/v1/pases/socios/\(lugarId)/apple")) }
    public func tap(_ l: Lectura) async throws -> ResultadoTap { try await enviar("POST", "/v1/tap", l) }
}

/// Los filtros finos de Ahora/Explorar (Plus). Sin Plus, la API responde 403 `plus`.
public struct FiltrosFinos: Equatable, Sendable {
    public var verificados = false
    public var faltan: Int?
    public var edadDesde: Int?
    public var edadHasta: Int?
    public var gratis = false
    public init(verificados: Bool = false, faltan: Int? = nil, edadDesde: Int? = nil, edadHasta: Int? = nil, gratis: Bool = false) {
        self.verificados = verificados; self.faltan = faltan; self.edadDesde = edadDesde; self.edadHasta = edadHasta; self.gratis = gratis
    }
    public var activos: Bool { verificados || faltan != nil || edadDesde != nil || edadHasta != nil || gratis }
    public var query: Query {
        [("verificados", verificados ? "1" : nil), ("faltan", faltan.map(String.init)),
         ("edadDesde", edadDesde.map(String.init)), ("edadHasta", edadHasta.map(String.init)), ("gratis", gratis ? "1" : nil)]
    }
}
