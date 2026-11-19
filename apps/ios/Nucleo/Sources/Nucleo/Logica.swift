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

/// Lo que se usa del PDF417 del frente del DNI argentino. El sexo que trae no se lee: del DNI sólo
/// se valida la edad (AAIP 1.2).
public struct DatosDni: Equatable, Sendable {
    public var tramite, apellido, nombres, numero, ejemplar: String
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
            return DatosDni(tramite: c[0], apellido: limpio(c[1]), nombres: limpio(c[2]),
                            numero: numero, ejemplar: c[5], nacimiento: nac)
        }
        if c[0].isEmpty, c.count >= 10 {
            let numero = digitos(c[1])
            guard let nac = iso(c[6]), (7...8).contains(numero.count) else { return nil }
            let tramite = c.dropFirst(8).first { $0.count >= 9 && $0.allSatisfy(\.isASCIIDigit) } ?? ""
            return DatosDni(tramite: tramite, apellido: limpio(c[3]), nombres: limpio(c[4]),
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

/// 13a · Ahora sin planes: la primera frase cambia según el día y la hora (en Argentina).
public enum VacioAhora {
    public static let titulo = "No hay planes cerca. ¿Armás uno?"

    public static func cuerpo(_ d: Date = Date()) -> String {
        let c = Fecha.calendario
        let h = c.component(.hour, from: d)
        let dia = Fecha.diaLargo(d)
        let frase: String
        switch h {
        case 0..<6: frase = "\(dia) de madrugada, la costa duerme."
        case 6..<12: frase = "\(dia) a la mañana, la costa recién se despierta."
        case 12..<17: frase = "\(dia) a la siesta, la costa duerme."
        case 17..<20: frase = "\(dia) a la tarde, la costa está tranqui."
        default: frase = "\(dia) a la noche y nadie armó nada todavía."
        }
        return frase + " Si publicás algo ahora, sos lo primero que ven."
    }
}

/// Moderación automática: lo que ve el autor de un mensaje o el organizador de un plan. La API manda
/// `moderacion` sólo al autor, así que a nadie más le aparece.
public enum Moderacion {
    /// Línea chica debajo de un mensaje propio, o en la tarjeta del plan propio.
    public static func etiqueta(_ m: String) -> String? {
        switch m {
        case "revision": return "En revisión"
        case "oculto": return "Oculto por las normas"
        default: return nil
        }
    }

    /// Aviso del detalle del plan, sólo para quien lo organiza. El corte de renglón es a propósito:
    /// así «ve.» no queda sola abajo.
    public static func avisoPlan(_ p: Plan) -> String? {
        guard p.esMio else { return nil }
        switch p.moderacion {
        case "revision": return "Tu plan está en revisión."
        case "oculto": return "Tu plan está oculto por las normas:\nnadie más lo ve."
        default: return nil
        }
    }

    /// Evento `moderado` del WebSocket: un mensaje ajeno que quedó oculto se saca; uno propio se reemplaza.
    public static func sacar(_ m: String, mio: Bool) -> Bool { m == "oculto" && !mio }
}

/// La asistencia, igual en todas las pantallas: «fue a 9 de 10» (sin porcentaje: no es un puntaje).
public func asistenciaTexto(_ a: Asist) -> String? {
    a.de > 0 ? "fue a \(a.fue) de \(a.de)" : nil
}

/// La línea de confianza, sólo con lo que distingue a un plan (si no hay nada, ninguna). En la tarjeta:
/// «Martín fue a 9 de 10 · Anfitrión · Plan de chicas»; de un lugar aliado, «Organiza La Fonda». En el detalle
/// (`detalle`), quien organiza ya tiene su fila: sólo «Todos verificados · Plan de chicas», que con el DNI
/// obligatorio casi siempre es cierto y por eso no va en la tarjeta. Nunca cuántas chicas van. Cada parte
/// lleva espacios duros y el «·» va pegado a la anterior: el renglón sólo corta después de un «·». Igual que
/// lineaConfianza de plan.ts y Logica.kt.
public func lineaConfianza(_ p: Plan, detalle: Bool = false) -> String? {
    var partes: [String] = []
    if detalle {
        if p.confianza.todosVerificados { partes.append("Todos verificados") }
    } else if let l = p.lugar, !l.nombre.isEmpty {
        partes.append("Organiza \(l.nombre)")
    } else {
        let nombre = p.organizador.nombre.trimmingCharacters(in: .whitespaces).split(separator: " ").first.map(String.init) ?? ""
        if !nombre.isEmpty, let a = asistenciaTexto(p.organizador.asistencia) { partes.append("\(nombre) \(a)") }
        if p.organizador.anfitrion { partes.append("Anfitrión") }
    }
    if p.confianza.soloChicas { partes.append("Plan de chicas") }
    if partes.isEmpty { return nil }
    return partes.map { $0.replacingOccurrences(of: " ", with: "\u{00A0}") }.joined(separator: "\u{00A0}· ")
}

/// Los gestos sobre la tarjeta de un plan (Ahora y Explorar) y «Decidí rápido».
public enum Gestos {
    /// Cuánto del ancho de la tarjeta hay que deslizar para que cuente.
    public static let umbral = 0.35
    /// Desde cuántos planes para decidir aparece «Decidí rápido».
    public static let minimoDecidir = 8

    /// «Caigo» se suma sin pasar por el detalle sólo si no hay nada que confirmar: ingreso libre, gratis, para
    /// cualquiera, sin pedido previo y con lugar. Si no, se abre el detalle (ahí se confirma).
    public static func directo(_ p: Plan) -> Bool {
        p.ingreso == "libre" && p.cobro == "gratis" && p.genero == "cualquiera" && p.miEstado == "ninguno" && p.faltan > 0
    }

    /// Los planes de «Decidí rápido»: sólo los que se suman con un toque (directo). Los que piden aprobación,
    /// entrada o confirmar algo («Solo mujeres») se deciden en el detalle, con su confirmación.
    public static func paraDecidir(_ planes: [Plan], yo: String) -> [Plan] {
        planes.filter { directo($0) && (yo.isEmpty || $0.organizador.id != yo) }
    }
}

/// Lo que se muestra de los logros en un perfil.
public enum TextoLogros {
    /// «Habitué · 7 con check-in» (con 0, sólo el nivel). No dice «planes»: en el mismo perfil está la cifra de
    /// planes a los que se anotó, que es otra cosa.
    public static func cabecera(_ l: Logros) -> String {
        l.planes > 0 ? "\(l.nivel) · \(l.planes)\u{00A0}con\u{00A0}check-in" : l.nivel
    }

    /// «Racha: 3 semanas» (sólo desde 2). El comodín se usa solo, sin anunciarlo.
    public static func racha(_ l: Logros) -> String? {
        guard let r = l.racha, r >= 2 else { return nil }
        return "Racha: \(r)\u{00A0}semanas"
    }

    /// Planes con check-in desde los que se es Habitué (logros.Habitue).
    public static let habitue = 5

    /// El beneficio por nivel de un lugar para quien mira: «Tenés: 10 % off» o «Con 3 planes más: 10 % off».
    public static func beneficio(_ b: String, _ l: Logros?) -> String {
        guard let l else { return "Habitué o más: \(b)" }
        let falta = max(habitue - l.planes, 0)
        if falta == 0 { return "Tenés: \(b)" }
        return "Con \(falta == 1 ? "1 plan más" : "\(falta) planes más"): \(b)"
    }

    /// «Te faltan 3 para De fierro».
    public static func siguiente(_ l: Logros) -> String? {
        guard let s = l.siguiente, !s.isEmpty, let f = l.faltan, f > 0 else { return nil }
        return "Te faltan \(f) para \(s)"
    }
}

/// El muro de Caigo Plus: las filas de la tabla Gratis / Plus y el texto chico de la suscripción, iguales en
/// iOS, Android y la web (App Review 3.1.2: precio por período, que se renueva sola, Términos y Privacidad).
public enum PlusTabla {
    /// (fila, gratis, plus). «✓» es un tilde, «—» que no lo tiene. Lo de seguridad no va: es gratis siempre.
    public static let filas: [(String, String, String)] = [
        ("Sumadas en Gente", "3 por día", "Sin límite"),
        ("Ver quién te sumó", "—", "✓"),
        ("Me encanta en Citas", "1 por día", "5 por día"),
        ("Tu plan destacado", "—", "1 por semana"),
        ("Filtros de edad, cupo y precio", "—", "✓"),
    ]

    /// Cómo se lee una celda con VoiceOver.
    public static func leer(_ v: String) -> String { v == "✓" ? "sí" : (v == "—" ? "no" : v) }

    /// «$X por semana, se renueva sola. Cancelás cuando quieras desde App Store.» `tienda`: App Store,
    /// Google Play o Mercado Pago. Con prueba gratis (sólo el mes): «Gratis 7 días, después $X por mes…».
    public static func textoFino(plan: String, precio: String, diasGratis: Int, tienda: String) -> String {
        let nb = "\u{00A0}"
        let periodo: String
        switch plan {
        case "semana": periodo = "\(precio) por\(nb)semana"
        case "temporada": periodo = "\(precio) cada 3\(nb)meses"
        default: periodo = "\(precio) por\(nb)mes"
        }
        let inicio = plan == "mes" && diasGratis > 0 ? "Gratis \(diasGratis)\(nb)días, después \(periodo)" : periodo
        let t = tienda.replacingOccurrences(of: " ", with: nb)
        return "\(inicio), se renueva sola. Cancelás cuando quieras desde\(nb)\(t)."
    }
}
