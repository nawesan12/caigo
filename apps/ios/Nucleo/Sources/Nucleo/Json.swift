import Foundation

/// Clave de JSON por nombre: los modelos leen campo por campo con un valor por defecto, así un campo
/// nuevo o que falte no rompe la app (como `ignoreUnknownKeys` + valores por defecto en Android).
public struct Clave: CodingKey {
    public var stringValue: String
    public var intValue: Int? { nil }
    public init(_ s: String) { stringValue = s }
    public init?(stringValue: String) { self.stringValue = stringValue }
    public init?(intValue: Int) { nil }
}

public typealias Lector = KeyedDecodingContainer<Clave>

extension KeyedDecodingContainer where K == Clave {
    /// El campo `k`, o `d` si falta, es null o tiene otro tipo.
    public func v<T: Decodable>(_ k: String, _ d: T) -> T {
        ((try? decodeIfPresent(T.self, forKey: Clave(k))) ?? nil) ?? d
    }

    /// El campo `k` si está; si no, nil.
    public func o<T: Decodable>(_ k: String, _ t: T.Type = T.self) -> T? {
        (try? decodeIfPresent(T.self, forKey: Clave(k))) ?? nil
    }
}

extension Decoder {
    public func lector() throws -> Lector { try container(keyedBy: Clave.self) }
}

public enum JSON {
    public static let decodificador = JSONDecoder()
    public static let codificador: JSONEncoder = {
        let e = JSONEncoder()
        e.outputFormatting = [.sortedKeys]
        return e
    }()

    public static func leer<T: Decodable>(_ t: T.Type, _ datos: Data) throws -> T {
        try decodificador.decode(T.self, from: datos.isEmpty ? Data("{}".utf8) : datos)
    }
}

/// Respuesta vacía (204 o `{}`).
public struct Nada: Decodable, Sendable {
    public init() {}
    public init(from decoder: Decoder) throws {}
}
