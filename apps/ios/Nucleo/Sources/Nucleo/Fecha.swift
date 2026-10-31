import Foundation

/// Fechas en hora de Argentina: todo lo que se muestra va en esta zona.
public enum Fecha {
    public static let zona = TimeZone(identifier: "America/Argentina/Buenos_Aires")!

    public static var calendario: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = zona
        c.locale = Locale(identifier: "es_AR")
        return c
    }

    private static let sinFraccion: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    /// RFC 3339 como lo manda Go (con o sin fracción de segundo, de cualquier largo).
    public static func parse(_ iso: String) -> Date? {
        guard !iso.isEmpty else { return nil }
        var s = iso
        var fraccion = 0.0
        if let punto = s.firstIndex(of: "."), let fin = s[punto...].firstIndex(where: { $0 == "Z" || $0 == "+" || $0 == "-" }) {
            fraccion = Double("0" + s[punto..<fin]) ?? 0
            s.removeSubrange(punto..<fin)
        }
        return sinFraccion.date(from: s).map { $0.addingTimeInterval(fraccion) }
    }

    /// Para mandar a la API: RFC 3339 con el desfase de Argentina.
    public static func iso(_ d: Date) -> String {
        let f = ISO8601DateFormatter()
        f.timeZone = zona
        f.formatOptions = [.withInternetDateTime]
        return f.string(from: d)
    }

    static let dias = ["Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"]
    static let diasLargos = ["Domingo", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado"]
    static let meses = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"]

    public static func hora(_ d: Date) -> String {
        let c = calendario.dateComponents([.hour, .minute], from: d)
        return String(format: "%02d:%02d", c.hour ?? 0, c.minute ?? 0)
    }

    public static func hora(_ iso: String) -> String { parse(iso).map(hora) ?? "" }

    public static func diaCorto(_ d: Date) -> String { dias[calendario.component(.weekday, from: d) - 1] }
    public static func diaLargo(_ d: Date) -> String { diasLargos[calendario.component(.weekday, from: d) - 1] }
    public static func mes(_ d: Date) -> String { meses[calendario.component(.month, from: d) - 1] }

    /// Días de calendario entre dos fechas (en Argentina).
    public static func diasEntre(_ a: Date, _ b: Date) -> Int {
        let c = calendario
        return c.dateComponents([.day], from: c.startOfDay(for: a), to: c.startOfDay(for: b)).day ?? 0
    }

    /// "Hoy 20:30", "Mañana 21:00", "Sáb 10:00".
    public static func cuando(_ iso: String, ahora: Date = Date()) -> String {
        guard let t = parse(iso) else { return "" }
        switch diasEntre(ahora, t) {
        case 0: return "Hoy \(hora(t))"
        case 1: return "Mañana \(hora(t))"
        default: return "\(diaCorto(t)) \(hora(t))"
        }
    }

    /// "Ahora · en 2 h", "Ahora · en 40 min" o nil si falta más de 3 h.
    public static func enCuanto(_ iso: String, ahora: Date = Date()) -> String? {
        guard let t = parse(iso) else { return nil }
        let min = Int(t.timeIntervalSince(ahora) / 60)
        if min <= 0 { return "Ahora" }
        if min < 60 { return "Ahora · en \(min) min" }
        if min <= 180 { return "Ahora · en \((min + 30) / 60) h" }
        return nil
    }

    /// "Hoy, 20:30 a 21:30"
    public static func rango(_ iso: String, duracionMin: Int, ahora: Date = Date()) -> String {
        guard let t = parse(iso) else { return "" }
        let fin = t.addingTimeInterval(Double(duracionMin) * 60)
        let dia: String
        switch diasEntre(ahora, t) {
        case 0: dia = "Hoy"
        case 1: dia = "Mañana"
        default: dia = "\(diaLargo(t)) \(calendario.component(.day, from: t))"
        }
        return "\(dia), \(hora(t)) a \(hora(fin))"
    }

    /// "hace 20 min", "hace 2 h", "ayer", "hace 3 días".
    public static func hace(_ iso: String, ahora: Date = Date()) -> String {
        guard let t = parse(iso) else { return "" }
        let min = Int(ahora.timeIntervalSince(t) / 60)
        let dias = diasEntre(t, ahora)
        if min < 1 { return "recién" }
        if min < 60 { return "hace \(min) min" }
        if dias == 0 { return "hace \(min / 60) h" }
        if dias == 1 { return "ayer" }
        return "hace \(dias) días"
    }
}
