import Foundation

/*
 * El día del plan, después del plan y el ritual de los lunes (informe del 18/11): lo de quien mira en un
 * plan (`mio`), «¿Todo bien?», la lista de espera, las fotos del plan y «Esta semana». Igual que
 * nucleo/Dia.kt en Android.
 */

/// Lo de quien mira en un plan donde está.
public struct Mio: Codable, Hashable, Sendable {
    public var llegue = false
    public var confirme = false
    public var carita: Int?
    public var todoBien = false
    public var fotos = 0
    public var espera: EsperaMia?
    public init() {}
    public init(from d: Decoder) throws {
        let c = try d.lector()
        llegue = c.v("llegue", false); confirme = c.v("confirme", false); carita = c.o("carita")
        todoBien = c.v("todoBien", false); fotos = c.v("fotos", 0); espera = c.o("espera")
    }
}

/// En la lista de espera: `esperando` (con su lugar en la fila) o `avisado` (se liberó un lugar, hasta `venceEn`).
public struct EsperaMia: Codable, Hashable, Sendable {
    public var estado = "esperando"
    public var posicion = 0
    public var venceEn = ""
    public init() {}
    public init(from d: Decoder) throws {
        let c = try d.lector(); estado = c.v("estado", "esperando"); posicion = c.v("posicion", 0); venceEn = c.v("venceEn", "")
    }
}

public struct Caritas: Codable, Hashable, Sendable {
    public var promedio = 0.0
    public var cantidad = 0
    public init(from d: Decoder) throws { let c = try d.lector(); promedio = c.v("promedio", 0.0); cantidad = c.v("cantidad", 0) }
}

/// Contacto de confianza: nombre y teléfono o mail. `avisa` (en «¿Todo bien?»): "contacto" (es un mail: le
/// escribimos nosotros) o "vos" (es un teléfono: no hay SMS, te avisamos a vos para que le escribas).
public struct Contacto: Codable, Hashable, Sendable {
    public var nombre = ""
    public var contacto = ""
    public var avisa = ""
    public init(nombre: String = "", contacto: String = "") { self.nombre = nombre; self.contacto = contacto }
    public init(from d: Decoder) throws {
        let c = try d.lector(); nombre = c.v("nombre", ""); contacto = c.v("contacto", ""); avisa = c.v("avisa", "")
    }
    public func encode(to e: Encoder) throws {
        var c = e.container(keyedBy: Claves.self)
        try c.encode(nombre, forKey: .nombre); try c.encode(contacto, forKey: .contacto)
    }
    enum Claves: String, CodingKey { case nombre, contacto }
    /// Le escribimos nosotros (mail); si no, te avisamos a vos.
    public var leEscribimos: Bool { avisa.isEmpty ? contacto.contains("@") : avisa == "contacto" }
    /// «Le escribimos a Mamá si no llegás» / «Te avisamos para que le escribas a Sofi».
    public var filaTodoBien: String {
        leEscribimos ? "Le escribimos a \(mostrar) si no llegás" : "Te avisamos para que le escribas a \(mostrar)"
    }
    /// El aviso al activarlo.
    public var activado: String { leEscribimos ? "Si no llegás, le escribimos a \(mostrar)." : "Si no hacés check-in, te avisamos." }
    /// Lo que se muestra: el nombre, o el teléfono o mail si no tiene.
    public var mostrar: String { nombre.isEmpty ? contacto : nombre }
    /// Es un teléfono (se puede llamar o escribirle por WhatsApp).
    public var esTelefono: Bool { !contacto.contains("@") && contacto.filter(\.isNumber).count >= 6 }
}

/// GET /v1/planes/{id}/todo-bien.
public struct TodoBien: Decodable, Sendable {
    public var activo = false
    public var contacto: Contacto?
    public var avisaEn = ""
    public var avisado = false
    /// Ya le preguntamos «¿Llegaste?» (el aviso sale 10 min después).
    public var preguntado = false
    public var contactos: [Contacto] = []
    public init() {}
    public init(from d: Decoder) throws {
        let c = try d.lector()
        activo = c.v("activo", false); contacto = c.o("contacto"); avisaEn = c.v("avisaEn", "")
        avisado = c.v("avisado", false); preguntado = c.v("preguntado", false); contactos = c.v("contactos", [])
    }
}

public struct FotoPlan: Decodable, Hashable, Identifiable, Sendable {
    public var id = ""
    public var url = ""
    public var autor = PersonaCorta()
    public var mia = false
    public var creadaEn = ""
    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); url = c.v("url", ""); autor = c.v("autor", PersonaCorta()); mia = c.v("mia", false); creadaEn = c.v("creadaEn", "")
    }
}

public struct FotosPlan: Decodable, Sendable {
    public var fotos: [FotoPlan] = []
    public init(from d: Decoder) throws { fotos = try d.lector().v("fotos", []) }
}

public struct DiaSemana: Decodable, Hashable, Sendable {
    public var dia = ""
    public var cantidad = 0
    public init(from d: Decoder) throws { let c = try d.lector(); dia = c.v("dia", ""); cantidad = c.v("cantidad", 0) }
}

/// GET /v1/semana: «Esta semana en <ciudad>».
public struct Semana: Decodable, Sendable {
    public var ciudad = ""
    public var total = 0
    public var aliados = 0
    public var deTuGente = 0
    public var dias: [DiaSemana] = []
    public var planes: [Plan] = []
    public init(from d: Decoder) throws {
        let c = try d.lector()
        ciudad = c.v("ciudad", ""); total = c.v("total", 0); aliados = c.v("aliados", 0); deTuGente = c.v("deTuGente", 0)
        dias = c.v("dias", []); planes = c.v("planes", [])
    }
    /// «14 planes · 3 en lugares aliados · 1 de tu gente» (cada parte sin cortes adentro).
    public var resumen: String {
        [total == 1 ? "1 plan" : "\(total) planes",
         aliados == 0 ? nil : (aliados == 1 ? "1 en un lugar aliado" : "\(aliados) en lugares aliados"),
         deTuGente == 0 ? nil : "\(deTuGente) de tu gente"].compactMap { $0?.replacingOccurrences(of: " ", with: "\u{00A0}") }
            .joined(separator: " · ")
    }
    /// Las cifras de la pantalla: sólo las que dicen algo (un cero grande hace parecer vacía la ciudad).
    public var cifras: [(numero: Int, texto: String)] {
        [(total, total == 1 ? "plan" : "planes"), (aliados, "aliados"), (deTuGente, "de tu gente")]
            .enumerated().filter { $0.offset == 0 || $0.element.0 > 0 }.map { $0.element }
    }
}

/// Las caritas del 1 al 5 (como Timeleft): privadas, sólo suman al promedio de quien organiza. Se dibujan
/// con los íconos `carita1`…`carita5` (del mismo trazo que el resto), no con emojis del sistema.
public enum Caras {
    public static let todas: [(valor: Int, icono: String, texto: String)] = [
        (1, "carita1", "Mal"), (2, "carita2", "Flojo"), (3, "carita3", "Bien"), (4, "carita4", "Muy bien"), (5, "carita5", "Genial"),
    ]
    /// «4,5 · 10+ opiniones» (el promedio llega a medio punto y la cantidad en escalones).
    public static func texto(_ c: Caritas?) -> String? {
        guard let c, c.cantidad >= 3 else { return nil }
        let p = String(format: "%.1f", c.promedio).replacingOccurrences(of: ".", with: ",")
        return "\(p)\u{00A0}·\u{00A0}\(c.cantidad)+\u{00A0}opiniones"
    }
}

public enum Dia {
    /// «Hoy» va desde 3 h antes hasta 2 h después del final: la vuelta también es parte del plan («Me
    /// quiero ir», compartir la ubicación). Activar «¿Todo bien?» sigue hasta el final (puedeActivarTodoBien).
    public static func enElDia(_ p: Plan, ahora: Date = Date()) -> Bool {
        guard !p.cancelado, let t = Fecha.parse(p.empiezaEn) else { return false }
        return ahora >= t.addingTimeInterval(-3 * 3600) && ahora < t.addingTimeInterval(Double(p.duracionMin) * 60 + 2 * 3600)
    }

    /// «Llegué» (el check-in) anda desde media hora antes hasta una hora después del final, como la API
    /// (planes/checkin.go: checkinAntes y checkinDespues). Antes, el botón dice desde qué hora.
    public static func puedeLlegar(_ p: Plan, ahora: Date = Date()) -> Bool {
        guard let t = Fecha.parse(p.empiezaEn) else { return false }
        return ahora >= t.addingTimeInterval(-30 * 60) && ahora <= t.addingTimeInterval(Double(p.duracionMin) * 60 + 3600)
    }

    /// «Check-in desde 20:30» (cuando todavía es temprano para «Llegué»).
    public static func llegarDesde(_ p: Plan) -> String {
        guard let t = Fecha.parse(p.empiezaEn) else { return "Llegué" }
        return "Check-in desde \(Fecha.hora(t.addingTimeInterval(-30 * 60)))"
    }

    /// «¿Todo bien?» se activa hasta el final del plan (como la API).
    public static func puedeActivarTodoBien(_ p: Plan, ahora: Date = Date()) -> Bool {
        guard let t = Fecha.parse(p.empiezaEn) else { return false }
        return ahora < t.addingTimeInterval(Double(p.duracionMin) * 60)
    }

    /// Los minutos que faltan, para arriba: la misma cuenta en el chip «Ahora · en N» y en «Hoy».
    public static func minutosHasta(_ t: Date, ahora: Date = Date()) -> Int {
        Int((t.timeIntervalSince(ahora) / 60).rounded(.up))
    }

    /// «Es tuyo hasta las 21:40» (el lugar guardado de la lista de espera); nil si ya venció.
    public static func lugarGuardado(_ venceEn: String, ahora: Date = Date()) -> String? {
        guard let t = Fecha.parse(venceEn), t > ahora else { return nil }
        return "Es tuyo hasta las \(Fecha.hora(t))"
    }

    /// «en 25 min» · «en 2 h 10» · «Ya arrancó».
    public static func cuentaRegresiva(_ empieza: String, ahora: Date = Date()) -> String {
        guard let t = Fecha.parse(empieza) else { return "" }
        let min = minutosHasta(t, ahora: ahora)
        if min <= 0 { return "Ya arrancó" }
        if min < 60 { return "en \(min) min" }
        let h = min / 60, m = min % 60
        return m == 0 ? "en \(h) h" : String(format: "en %d h %02d", h, m)
    }

    /// La víspera: el plan es mañana.
    public static func esVispera(_ p: Plan, ahora: Date = Date()) -> Bool {
        guard let t = Fecha.parse(p.empiezaEn) else { return false }
        return Fecha.diasEntre(ahora, t) == 1
    }

    /// La tarjeta «Esta semana» arriba de Ahora: de lunes a miércoles.
    public static func semanaVisible(ahora: Date = Date()) -> Bool {
        let s = Fecha.calendario.component(.weekday, from: ahora) // 1 = domingo
        return s >= 2 && s <= 4
    }

    public static func comoLlegar(lat: Double, lng: Double) -> URL? {
        URL(string: "https://www.google.com/maps/dir/?api=1&destination=\(lat),\(lng)")
    }

    public static func linkUbicacion(lat: Double, lng: Double) -> String {
        String(format: "https://maps.google.com/?q=%.5f,%.5f", lat, lng)
    }

    /// El número para WhatsApp. Con «+», tal cual (otro país). Si no, Argentina: sin el 0 de larga distancia
    /// y sin el 15 de celular que va después de la característica (2 a 4 dígitos), con 549 adelante.
    /// «0223 15 592-6772», «223 15 5926772» y «2235926772» dan 5492235926772. Igual que dia.ts y Dia.kt.
    public static func numeroWhatsapp(_ tel: String) -> String {
        let d = tel.filter(\.isNumber)
        if tel.trimmingCharacters(in: .whitespaces).hasPrefix("+") { return d }
        var n = d.hasPrefix("0") ? String(d.dropFirst()) : d
        if n.hasPrefix("549") && n.count == 13 { return n }
        if n.hasPrefix("54") && n.count == 12 { return "549" + n.dropFirst(2) }
        if n.count == 12 {
            let a = Array(n)
            for k in 2...4 where String(a[k..<k + 2]) == "15" {
                n = String(a[0..<k]) + String(a[(k + 2)...])
                break
            }
        }
        return n.count == 10 ? "549" + n : d
    }

    /// wa.me con el número y el texto.
    public static func whatsapp(_ tel: String, _ texto: String) -> URL? {
        let n = numeroWhatsapp(tel)
        var c = URLComponents(string: "https://wa.me/\(n)")
        c?.queryItems = [URLQueryItem(name: "text", value: texto)]
        return c?.url
    }

    /// Pedir un auto para volver ya.
    public static let pedirAuto = URL(string: "https://m.uber.com/ul/?action=setPickup&pickup=my_location")!
}

extension Api {
    // MARK: El día del plan

    /// «¿Seguís yendo?» → Voy. («Me bajo» es `salir`.)
    public func confirmo(_ id: String) async throws { try await sinCuerpo("POST", "/v1/planes/\(id)/confirmo") }

    struct Espera: Encodable { var cantidad: Int; var mujer: Bool; var diversidad: Bool }
    /// Plan completo: a la lista de espera (409 `hay_lugar` si hay lugar).
    public func esperar(_ id: String, cantidad: Int = 1, confirma: Confirma = Confirma()) async throws -> EsperaMia {
        try await enviar("POST", "/v1/planes/\(id)/espera", Espera(cantidad: cantidad, mujer: confirma.mujer, diversidad: confirma.diversidad))
    }
    public func dejarDeEsperar(_ id: String) async throws { try await sinCuerpo("DELETE", "/v1/planes/\(id)/espera") }

    public func todoBien(_ id: String) async throws -> TodoBien { try await get("/v1/planes/\(id)/todo-bien") }
    struct ElegirContacto: Encodable { var contacto: Int }
    /// «Avisale a mi contacto si no hago check-in» (índice de los contactos del perfil).
    public func activarTodoBien(_ id: String, contacto: Int) async throws -> TodoBien {
        try await enviar("POST", "/v1/planes/\(id)/todo-bien", ElegirContacto(contacto: contacto))
    }
    public func apagarTodoBien(_ id: String) async throws { try await sinCuerpo("DELETE", "/v1/planes/\(id)/todo-bien") }

    // MARK: Después del plan

    struct Calificar: Encodable { var repetir: Bool; var volveria: [Opinion]; var carita: Int? }
    /// «¿Repetimos?» con la carita (1 a 5, privada). Devuelve el plan nuevo y con quiénes quedó mutuo.
    public func repetimos(_ id: String, repetir: Bool, volveria: [Opinion], carita: Int?) async throws -> Nuevo {
        try await enviar("POST", "/v1/planes/\(id)/repetimos", Calificar(repetir: repetir, volveria: volveria, carita: carita))
    }

    public func fotosPlan(_ id: String) async throws -> FotosPlan { try await get("/v1/planes/\(id)/fotos") }
    public func subirFotoPlan(_ id: String, jpeg: Data) async throws -> FotoPlan {
        try JSON.leer(FotoPlan.self, try await crudo("POST", url("/v1/planes/\(id)/fotos"), cuerpo: jpeg, tipo: "image/jpeg"))
    }
    public func borrarFotoPlan(_ id: String, foto: String) async throws { try await sinCuerpo("DELETE", "/v1/planes/\(id)/fotos/\(foto)") }
    /// motivo: "salgo_yo" (quien aparece y no quiere estar: se oculta al instante) o "inapropiada".
    public func reportarFotoPlan(_ id: String, foto: String, motivo: String = "inapropiada") async throws {
        try await sinCuerpo("POST", "/v1/planes/\(id)/fotos/\(foto)/reportar?motivo=\(motivo)")
    }
    /// «Marcar que vino»: quien organiza corrige a alguien que fue y no tocó «Llegué».
    public func marcarVino(_ id: String, persona: String) async throws {
        try await sinCuerpo("POST", "/v1/planes/\(id)/personas/\(persona)/vino")
    }

    // MARK: Esta semana

    public func semana() async throws -> Semana { try await get("/v1/semana") }
}
