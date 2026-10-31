import Foundation

/// El cupo del plan: el badge "Falta 1 / Faltan N / Completo" (README · Interactions).
public enum Cupo {
    public static func badge(_ faltan: Int) -> String {
        if faltan <= 0 { return "Completo" }
        if faltan == 1 { return "Falta 1" }
        return "Faltan \(faltan)"
    }

    public static func somos(_ somos: Int, _ total: Int) -> String { "Somos \(somos) de \(total)" }

    public static func completo(_ faltan: Int) -> Bool { faltan <= 0 }

    /// Cuántos faltan si se suman `extra` personas más: f = total − (sumados + extra).
    public static func faltan(total: Int, somos: Int, extra: Int = 0) -> Int { max(0, total - (somos + extra)) }

    /// Segmentos de la barra: true = ocupado (lima), false = vacío (borde coral punteado).
    public static func segmentos(somos: Int, total: Int) -> [Bool] {
        (0..<min(max(total, 0), 60)).map { $0 < somos }
    }
}

/// Plata en centavos → "$4.500" (pesos argentinos, sin decimales si son redondos).
public enum Plata {
    private static func miles(_ n: Int) -> String {
        let s = String(abs(n))
        var out = ""
        for (i, ch) in s.enumerated() {
            if i > 0 && (s.count - i) % 3 == 0 { out += "." }
            out.append(ch)
        }
        return (n < 0 ? "-" : "") + out
    }

    public static func pesos(_ centavos: Int) -> String {
        if centavos % 100 == 0 { return "$" + miles(centavos / 100) }
        let c = abs(centavos % 100)
        return "$" + miles(centavos / 100) + "," + (c < 10 ? "0\(c)" : "\(c)")
    }

    /// Redondeo a $100 (10.000 centavos), como Math.round en la API y en Android.
    static func aCien(_ centavos: Double) -> Int { Int((centavos / 10000).rounded(.toNearestOrAwayFromZero)) * 10000 }

    /// Cargo por servicio por persona: 7,5 % del precio, redondeado a $100 (planes.Cargo en la API).
    public static func cargo(_ precioCentavos: Int) -> Int { aCien(Double(precioCentavos) * 0.075) }

    /// Lo que se le paga al lugar por persona ahora: el precio entero o la seña redondeada a $100.
    public static func alLugar(_ precioCentavos: Int, cobro: String, senaPct: Int?) -> Int {
        if cobro == "sena", let pct = senaPct { return aCien(Double(precioCentavos) * Double(pct) / 100) }
        return precioCentavos
    }

    public struct Desglose: Equatable, Sendable {
        public var alLugar: Int, cargo: Int, total: Int, saldo: Int
    }

    /// El desglose del checkout para `cantidad` entradas (igual que lugares.Comprar).
    public static func desglose(_ precioCentavos: Int, cobro: String, senaPct: Int?, cantidad: Int, cargoPorPersona: Int? = nil) -> Desglose {
        let c = cargoPorPersona ?? cargo(precioCentavos)
        let lugar = alLugar(precioCentavos, cobro: cobro, senaPct: senaPct)
        return Desglose(alLugar: lugar * cantidad, cargo: c * cantidad, total: (lugar + c) * cantidad, saldo: (precioCentavos - lugar) * cantidad)
    }
}

/// Lo que trae el PDF417 del frente del DNI argentino.
public struct DatosDni: Equatable, Sendable {
    public var tramite, apellido, nombres, sexo, numero, ejemplar: String
    /// YYYY-MM-DD
    public var nacimiento: String
}

/// Formato nuevo (desde 2012): `trámite@apellido@nombres@sexo@número@ejemplar@nacimiento@emisión@…`.
/// Formato viejo: `@número@ejemplar@?@apellido@nombres@nacionalidad@nacimiento@sexo@emisión@trámite@…`.
public enum Pdf417Dni {
    static func iso(_ f: String) -> String? {
        let p = f.trimmingCharacters(in: .whitespaces).split(separator: "/", omittingEmptySubsequences: false).map(String.init)
        guard p.count == 3, p[0].count == 2, p[1].count == 2, p[2].count == 4, p.allSatisfy({ !$0.isEmpty && $0.allSatisfy(\.isASCIIDigit) }) else { return nil }
        return "\(p[2])-\(p[1])-\(p[0])"
    }

    static func limpio(_ s: String) -> String {
        s.split(whereSeparator: { $0.isWhitespace }).joined(separator: " ")
    }

    static func digitos(_ s: String) -> String { String(s.filter(\.isASCIIDigit)) }

    public static func leer(_ crudo: String) -> DatosDni? {
        let c = crudo.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: "@", omittingEmptySubsequences: false)
            .map { $0.trimmingCharacters(in: .whitespaces) }
        guard c.count >= 8 else { return nil }
        if !c[0].isEmpty, c[0].allSatisfy(\.isASCIIDigit), let nac = iso(c[6]) {
            let numero = digitos(c[4])
            guard (7...8).contains(numero.count) else { return nil }
            return DatosDni(tramite: c[0], apellido: limpio(c[1]), nombres: limpio(c[2]), sexo: c[3].uppercased(),
                            numero: numero, ejemplar: c[5], nacimiento: nac)
        }
        if c[0].isEmpty, c.count >= 10 {
            let numero = digitos(c[1])
            guard let nac = iso(c[6]), (7...8).contains(numero.count) else { return nil }
            let tramite = c.dropFirst(8).first { $0.count >= 9 && $0.allSatisfy(\.isASCIIDigit) } ?? ""
            return DatosDni(tramite: tramite, apellido: limpio(c[3]), nombres: limpio(c[4]), sexo: c[7].uppercased(),
                            numero: numero, ejemplar: c[2], nacimiento: nac)
        }
        return nil
    }
}

extension Character {
    var isASCIIDigit: Bool { ("0"..."9").contains(self) }
}

/// El mazo arrastrable de Gente / Citas, con las físicas del prototipo: rotación dx/16°, salida a
/// ±520 pt, umbral de 100 pt, tinte lima/coral según el lado y la carta de abajo de .93 a 1.
public enum FisicaMazo {
    public static let umbral: Double = 100
    public static let salida: Double = 520

    public struct Estado: Equatable, Sendable {
        public var indice = 0
        public var dx: Double = 0
        public var dy: Double = 0
        public var arrastrando = false
        /// -1 sale a la izquierda, 1 a la derecha, 0 nada.
        public var vuela = 0
        public var foto = 0
        public init(indice: Int = 0) { self.indice = indice }
    }

    public enum Accion: Sendable {
        case empieza
        case mueve(Double, Double)
        /// Soltó: si pasó el umbral, vuela; si no, vuelve.
        case suelta
        case vuela(Int)
        /// Terminó la animación de salida: pasa a la siguiente carta.
        case siguiente
        case foto(delta: Int, cantidad: Int)
    }

    public static func reducir(_ e: Estado, _ a: Accion) -> Estado {
        var e = e
        switch a {
        case .empieza: e.arrastrando = true; e.dx = 0; e.dy = 0
        case let .mueve(dx, dy): if e.arrastrando { e.dx = dx; e.dy = dy }
        case .suelta:
            guard e.arrastrando else { return e }
            e.arrastrando = false
            if abs(e.dx) > umbral { e.vuela = e.dx > 0 ? 1 : -1 } else { e.dx = 0; e.dy = 0 }
        case let .vuela(lado): e.vuela = lado; e.arrastrando = false
        case .siguiente: return Estado(indice: e.indice + 1)
        case let .foto(delta, cantidad): e.foto = min(max(e.foto + delta, 0), max(cantidad - 1, 0))
        }
        return e
    }

    /// Desplazamiento a dibujar: si vuela, sale a ±520 y sube 40.
    public static func desplazamiento(_ e: Estado) -> (Double, Double) {
        e.vuela != 0 ? (Double(e.vuela) * salida, e.dy - 40) : (e.dx, e.dy)
    }

    public static func rotacion(_ dx: Double) -> Double { dx / 16 }
    /// 0..1: cuánto se nota el arrastre.
    public static func fuerza(_ dx: Double) -> Double { min(abs(dx) / 140, 1) }
    public static func escalaSiguiente(_ dx: Double) -> Double { 0.93 + 0.07 * fuerza(dx) }
    public static func alfaSiguiente(_ dx: Double) -> Double { 0.55 + 0.45 * fuerza(dx) }
    public static func alfaMeGusta(_ dx: Double) -> Double { min(max(dx / 90, 0), 1) }
    public static func alfaPaso(_ dx: Double) -> Double { min(max(-dx / 90, 0), 1) }
}

/// Categorías de plan (planes.go · Categorias), en el orden del handoff.
public enum Categorias {
    public static let todas: [(String, String)] = [
        ("deporte", "Deporte"), ("juego", "Juego"), ("previa", "Previa"), ("salida", "Salida"),
        ("cita_doble", "Cita doble"), ("cultura", "Cultura"), ("gastronomia", "Gastronomía"), ("otro", "Otro"),
        ("taller", "Taller"), ("musica", "Música en vivo"), ("fiesta", "Fiesta"),
    ]
    /// Las 8 que se eligen al crear un plan (06a).
    public static let paraCrear = Array(todas.prefix(8))

    public static func nombre(_ clave: String) -> String {
        todas.first { $0.0 == clave }?.1 ?? clave.prefix(1).uppercased() + clave.dropFirst()
    }
}

/// "800 m", "1,2 km".
public func distancia(_ m: Int?) -> String {
    guard let m else { return "" }
    if m < 100 { return "\(m) m" }
    if m < 1000 { return "\(((m + 50) / 100) * 100) m" }
    if m < 10_000 { return String(format: "%.1f km", Double(m) / 1000).replacingOccurrences(of: ".", with: ",") }
    return "\(m / 1000) km"
}

/// Link público de un plan.
public func linkPlan(_ slug: String) -> String { "caigo.com.ar/p/\(slug)" }

/// Centro de Mar del Plata: donde arranca el mapa si no hay permiso de ubicación.
public let latMDP = -38.0023
public let lngMDP = -57.5575
