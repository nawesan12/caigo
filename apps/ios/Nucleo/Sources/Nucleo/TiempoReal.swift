import Foundation

/// El WebSocket de tiempo real (`/v1/tiempo-real?token=cs_…`): mensajes del chat, privados y
/// "escribiendo". Se conecta mientras la app está abierta y se reconecta con espera creciente
/// (1 s, 2 s, 4 s… hasta 30 s). Los eventos se reparten a quien esté escuchando.
public final class TiempoReal: @unchecked Sendable {
    private let base: URL
    private let sesion: URLSession
    private let token: @Sendable () -> String?
    private var tarea: Task<Void, Never>?
    private var ws: URLSessionWebSocketTask?
    private let cerrojo = NSLock()
    private var oyentes: [UUID: (Evento) -> Void] = [:]
    public private(set) var conectado = false

    public init(base: URL, sesion: URLSession = .shared, token: @escaping @Sendable () -> String?) {
        self.base = base
        self.sesion = sesion
        self.token = token
    }

    public static func url(base: URL, token: String) -> URL {
        var c = URLComponents(url: base, resolvingAgainstBaseURL: false)!
        c.scheme = c.scheme == "http" ? "ws" : "wss"
        c.path = (c.path.hasSuffix("/") ? String(c.path.dropLast()) : c.path) + "/v1/tiempo-real"
        c.queryItems = [URLQueryItem(name: "token", value: token)]
        return c.url!
    }

    /// Espera creciente entre reconexiones: 1, 2, 4, 8, 16, 30, 30… segundos.
    public static func espera(_ intento: Int) -> TimeInterval { min(30, Double(1 << min(intento, 5))) }

    /// Escucha los eventos; devuelve una llave para dejar de escuchar.
    @discardableResult
    public func escuchar(_ f: @escaping (Evento) -> Void) -> UUID {
        let id = UUID()
        cerrojo.lock(); oyentes[id] = f; cerrojo.unlock()
        return id
    }

    public func dejar(_ id: UUID) {
        cerrojo.lock(); oyentes[id] = nil; cerrojo.unlock()
    }

    private func repartir(_ e: Evento) {
        cerrojo.lock(); let fs = Array(oyentes.values); cerrojo.unlock()
        DispatchQueue.main.async { fs.forEach { $0(e) } }
    }

    public func encender() {
        if tarea != nil { return }
        tarea = Task { [weak self] in
            var intentos = 0
            while !Task.isCancelled {
                guard let self else { return }
                guard let t = self.token(), !t.isEmpty else {
                    try? await Task.sleep(nanoseconds: 2_000_000_000)
                    continue
                }
                let ws = self.sesion.webSocketTask(with: TiempoReal.url(base: self.base, token: t))
                self.ws = ws
                ws.resume()
                do {
                    while !Task.isCancelled {
                        let m = try await ws.receive()
                        self.conectado = true
                        intentos = 0
                        var datos: Data?
                        switch m {
                        case let .string(s): datos = Data(s.utf8)
                        case let .data(d): datos = d
                        @unknown default: datos = nil
                        }
                        if let datos, let e = try? JSONDecoder().decode(Evento.self, from: datos) { self.repartir(e) }
                    }
                } catch {
                    self.conectado = false
                }
                if Task.isCancelled { return }
                try? await Task.sleep(nanoseconds: UInt64(TiempoReal.espera(intentos) * 1_000_000_000))
                intentos += 1
            }
        }
    }

    public func apagar() {
        tarea?.cancel()
        tarea = nil
        ws?.cancel(with: .normalClosure, reason: nil)
        ws = nil
        conectado = false
    }

    /// Avisa que la persona está escribiendo en el chat del plan (la API lo limita a uno cada 2 s).
    public func escribiendo(planId: String) {
        let s = "{\"tipo\":\"escribiendo\",\"planId\":\"\(planId)\"}"
        ws?.send(.string(s)) { _ in }
    }
}
