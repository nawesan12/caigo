import Foundation

/*
 * Lo que manda y recibe la API (apps/api/internal/dominio/<dominio>/ (archivos .go)). Los nombres de los
 * campos son los del JSON. Cada modelo se lee campo por campo con un valor por defecto (ver
 * `Lector.v`), igual que los modelos de Android (datos/Modelos.kt).
 */

public struct Asist: Codable, Hashable, Sendable {
    public var fue = 0
    public var de = 0
    public init(fue: Int = 0, de: Int = 0) { self.fue = fue; self.de = de }
    public init(from d: Decoder) throws { let c = try d.lector(); fue = c.v("fue", 0); de = c.v("de", 0) }
}

/// Vista corta de alguien en un plan (avatares, organizador, solicitudes).
public struct PersonaCorta: Codable, Hashable, Identifiable, Sendable {
    public var id = ""
    public var nombre = ""
    public var foto = ""
    public var edad: Int?
    public var verificada = false
    public var asistencia = Asist()
    public var enMiGente = false
    public var organizador = false
    public var llego = false

    public init(id: String = "", nombre: String = "", foto: String = "", edad: Int? = nil, verificada: Bool = false,
                asistencia: Asist = Asist(), enMiGente: Bool = false, organizador: Bool = false, llego: Bool = false) {
        self.id = id; self.nombre = nombre; self.foto = foto; self.edad = edad; self.verificada = verificada
        self.asistencia = asistencia; self.enMiGente = enMiGente; self.organizador = organizador; self.llego = llego
    }

    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); nombre = c.v("nombre", ""); foto = c.v("foto", ""); edad = c.o("edad")
        verificada = c.v("verificada", false); asistencia = c.v("asistencia", Asist())
        enMiGente = c.v("enMiGente", false); organizador = c.v("organizador", false); llego = c.v("llego", false)
    }
}

public struct Direccion: Codable, Hashable, Sendable {
    public var texto = ""
    public var lat = 0.0
    public var lng = 0.0
    public init(texto: String = "", lat: Double = 0, lng: Double = 0) { self.texto = texto; self.lat = lat; self.lng = lng }
    public init(from d: Decoder) throws { let c = try d.lector(); texto = c.v("texto", ""); lat = c.v("lat", 0.0); lng = c.v("lng", 0.0) }
}

public struct LugarCorto: Codable, Hashable, Sendable {
    public var id = "", slug = "", nombre = "", zona = "", foto = ""
    public init(id: String = "", slug: String = "", nombre: String = "", zona: String = "", foto: String = "") {
        self.id = id; self.slug = slug; self.nombre = nombre; self.zona = zona; self.foto = foto
    }
    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); slug = c.v("slug", ""); nombre = c.v("nombre", ""); zona = c.v("zona", ""); foto = c.v("foto", "")
    }
}

public struct Plan: Codable, Hashable, Identifiable, Sendable {
    public var id = ""
    public var slug = ""
    public var titulo = ""
    public var categoria = ""
    public var descripcion = ""
    public var empiezaEn = ""
    public var duracionMin = 120
    public var recurrente = false
    public var ciudad = ""
    public var zona = ""
    public var lugarNombre = ""
    public var lat = 0.0
    public var lng = 0.0
    public var distanciaM: Int?
    public var somos = 0
    public var total = 0
    public var faltan = 0
    public var ingreso = "aprobacion"
    public var visibilidad = "publico"
    public var soloVerificados = false
    public var ocultarDireccion = false
    public var edadMin: Int?
    public var edadMax: Int?
    public var genero = "cualquiera"
    public var nivel = ""
    public var costoCentavos: Int?
    public var foto = ""
    public var cancelado = false
    public var destacado = false
    public var organizador = PersonaCorta()
    public var van: [PersonaCorta] = []
    /// ninguno | pendiente | aceptada | rechazada | cancelada | organizador
    public var miEstado = "ninguno"
    public var miCantidad = 0
    public var direccion: Direccion?
    public var pendientes = 0
    public var lugar: LugarCorto?
    /// gratis | entrada | sena
    public var cobro = "gratis"
    public var precioCentavos: Int?
    public var senaPct: Int?
    public var cargoCentavos = 0
    public var incluye = ""
    public var cancelacion = ""
    public var beneficio = ""
    public var aceptaGrupos = true

    public init(id: String) { self.id = id }

    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); slug = c.v("slug", ""); titulo = c.v("titulo", ""); categoria = c.v("categoria", "")
        descripcion = c.v("descripcion", ""); empiezaEn = c.v("empiezaEn", ""); duracionMin = c.v("duracionMin", 120)
        recurrente = c.v("recurrente", false); ciudad = c.v("ciudad", ""); zona = c.v("zona", "")
        lugarNombre = c.v("lugarNombre", ""); lat = c.v("lat", 0.0); lng = c.v("lng", 0.0); distanciaM = c.o("distanciaM")
        somos = c.v("somos", 0); total = c.v("total", 0); faltan = c.v("faltan", 0)
        ingreso = c.v("ingreso", "aprobacion"); visibilidad = c.v("visibilidad", "publico")
        soloVerificados = c.v("soloVerificados", false); ocultarDireccion = c.v("ocultarDireccion", false)
        edadMin = c.o("edadMin"); edadMax = c.o("edadMax"); genero = c.v("genero", "cualquiera"); nivel = c.v("nivel", "")
        costoCentavos = c.o("costoCentavos"); foto = c.v("foto", ""); cancelado = c.v("cancelado", false)
        destacado = c.v("destacado", false); organizador = c.v("organizador", PersonaCorta()); van = c.v("van", [])
        miEstado = c.v("miEstado", "ninguno"); miCantidad = c.v("miCantidad", 0); direccion = c.o("direccion")
        pendientes = c.v("pendientes", 0); lugar = c.o("lugar"); cobro = c.v("cobro", "gratis")
        precioCentavos = c.o("precioCentavos"); senaPct = c.o("senaPct"); cargoCentavos = c.v("cargoCentavos", 0)
        incluye = c.v("incluye", ""); cancelacion = c.v("cancelacion", ""); beneficio = c.v("beneficio", "")
        aceptaGrupos = c.v("aceptaGrupos", true)
    }

    /// Plan de un lugar aliado con entrada o seña: el botón es "Entrada" en vez de "Sumarme".
    public var conEntrada: Bool { cobro == "entrada" || cobro == "sena" }
    public var adentro: Bool { miEstado == "aceptada" || miEstado == "organizador" }
    public var esMio: Bool { miEstado == "organizador" }
}

public struct Planes: Decodable, Sendable {
    public var planes: [Plan] = []
    public init(planes: [Plan] = []) { self.planes = planes }
    public init(from d: Decoder) throws { planes = try d.lector().v("planes", []) }
}

/// Lo que se manda para crear un plan (struct Entrada de planes.go).
public struct PlanNuevo: Encodable, Sendable {
    public var titulo = ""
    public var categoria: String
    public var descripcion = ""
    public var empiezaEn: String
    public var duracionMin = 0
    public var recurrente = false
    public var ciudad: String
    public var zona = ""
    public var lugarNombre = ""
    public var direccion = ""
    public var lat: Double
    public var lng: Double
    public var somos: Int
    public var faltan: Int
    public var ingreso = "aprobacion"
    public var visibilidad = "publico"
    public var soloVerificados: Bool?
    public var ocultarDireccion: Bool?
    public var edadMin: Int?
    public var edadMax: Int?
    public var genero = "cualquiera"
    public var nivel = ""
    public var costoCentavos: Int?
    public var foto = ""

    public init(categoria: String, empiezaEn: String, ciudad: String, lat: Double, lng: Double, somos: Int, faltan: Int) {
        self.categoria = categoria; self.empiezaEn = empiezaEn; self.ciudad = ciudad
        self.lat = lat; self.lng = lng; self.somos = somos; self.faltan = faltan
    }
}

public struct Estado: Decodable, Sendable {
    public var estado = ""
    public init(from d: Decoder) throws { estado = try d.lector().v("estado", "") }
}

public struct Solicitud: Decodable, Hashable, Identifiable, Sendable {
    public var id = ""
    public var persona = PersonaCorta()
    public var cantidad = 1
    public var mensaje = ""
    public var estado = ""
    public var creadaEn = ""
    public init(id: String) { self.id = id }
    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); persona = c.v("persona", PersonaCorta()); cantidad = c.v("cantidad", 1)
        mensaje = c.v("mensaje", ""); estado = c.v("estado", ""); creadaEn = c.v("creadaEn", "")
    }
}

public struct Solicitudes: Decodable, Sendable {
    public var solicitudes: [Solicitud] = []
    public init(from d: Decoder) throws { solicitudes = try d.lector().v("solicitudes", []) }
}

public struct Codigo: Decodable, Sendable {
    public var codigo = ""
    public init(from d: Decoder) throws { codigo = try d.lector().v("codigo", "") }
}

public struct Link: Decodable, Sendable {
    public var link = ""
    public init(from d: Decoder) throws { link = try d.lector().v("link", "") }
}

public struct Nuevo: Decodable, Sendable {
    public var nuevo: Plan?
    public init(from d: Decoder) throws { nuevo = try d.lector().o("nuevo") }
}

public struct Opinion: Codable, Hashable, Sendable {
    public var usuarioId: String
    public var si: Bool
    public init(usuarioId: String, si: Bool) { self.usuarioId = usuarioId; self.si = si }
}

// MARK: - Personas

public struct Prompt: Codable, Hashable, Sendable {
    public var p = ""
    public var r = ""
    public init(p: String = "", r: String = "") { self.p = p; self.r = r }
    public init(from d: Decoder) throws { let c = try d.lector(); p = c.v("p", ""); r = c.v("r", "") }
}

public struct Perfil: Decodable, Hashable, Identifiable, Sendable {
    public var id = ""
    public var nombre = ""
    public var edad: Int?
    public var zona = ""
    public var ciudad = ""
    public var bio = ""
    public var trabajo = ""
    public var intereses: [String] = []
    public var intenciones: [String] = []
    public var prompts: [Prompt] = []
    public var fotos: [String] = []
    public var selfie = false
    public var verificada = false
    public var asistencia = Asist()
    public var planes = 0
    public var organizados = 0
    public var miGente = 0
    public var enMiGente = false
    public var compartidos = 0
    public var equipo = false
    public var telefono = ""
    /// "" | mujer | varon | no_binario (autodeclarado, no se muestra a nadie).
    public var genero = ""
    /// «Me identifico como parte de la diversidad».
    public var diversidad = false
    public var avisos = AjustesAvisos()
    public var contactoConfianza = ""
    /// Sus próximos planes que quien mira puede ver (GET /v1/personas/{id}).
    public var proximos: [ProximoPlan] = []
    /// Lo que le falta al perfil propio (GET /v1/yo): edad, ciudad, zona, bio, fotos, intereses.
    public var faltan: [String] = []

    public init(id: String = "", nombre: String = "") { self.id = id; self.nombre = nombre }

    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); nombre = c.v("nombre", ""); edad = c.o("edad"); zona = c.v("zona", ""); ciudad = c.v("ciudad", "")
        bio = c.v("bio", ""); trabajo = c.v("trabajo", ""); intereses = c.v("intereses", []); intenciones = c.v("intenciones", [])
        prompts = c.v("prompts", []); fotos = c.v("fotos", []); selfie = c.v("selfie", false); verificada = c.v("verificada", false)
        asistencia = c.v("asistencia", Asist()); planes = c.v("planes", 0); organizados = c.v("organizados", 0)
        miGente = c.v("miGente", 0); enMiGente = c.v("enMiGente", false); compartidos = c.v("compartidos", 0)
        equipo = c.v("equipo", false); telefono = c.v("telefono", "")
        genero = c.v("genero", ""); diversidad = c.v("diversidad", false); avisos = c.v("avisos", AjustesAvisos())
        contactoConfianza = c.v("contactoConfianza", ""); proximos = c.v("proximos", []); faltan = c.v("faltan", [])
    }

    /// Le falta algo del alta (nombre, teléfono o selfie).
    public var altaIncompleta: Bool { nombre.isEmpty || telefono.isEmpty || !selfie }
}

public struct CambiosPerfil: Encodable, Sendable {
    public var nombre: String?
    public var nacimiento: String?
    public var zona: String?
    public var ciudad: String?
    public var bio: String?
    public var trabajo: String?
    public var intereses: [String]?
    public var intenciones: [String]?
    public var prompts: [Prompt]?
    public var fotos: [String]?
    public var genero: String?
    public var diversidad: Bool?
    public var avisos: AjustesAvisos?
    public var contactoConfianza: String?
    public init() {}
}

/// Ajustes de avisos push (contrato §3). Por defecto, los dos prendidos.
public struct AjustesAvisos: Codable, Hashable, Sendable {
    public var planesCerca = true
    public var tuGente = true
    public init(planesCerca: Bool = true, tuGente: Bool = true) { self.planesCerca = planesCerca; self.tuGente = tuGente }
    public init(from d: Decoder) throws { let c = try d.lector(); planesCerca = c.v("planesCerca", true); tuGente = c.v("tuGente", true) }
}

/// Géneros que se pueden declarar (contrato §1).
public enum Generos {
    public static let todos: [(String, String)] = [("mujer", "Mujer"), ("varon", "Varón"), ("no_binario", "No binario")]
    /// Si la persona cumple con lo que pide el plan (`cualquiera` | `mujeres` | `diversidades`).
    public static func puedeSumarse(genero planGenero: String, miGenero: String, diversidad: Bool) -> Bool {
        switch planGenero {
        case "mujeres": return miGenero == "mujer"
        case "diversidades": return diversidad || miGenero == "no_binario"
        default: return true
        }
    }
}

/// Mi gente: la API devuelve perfiles; las listas usan la vista corta.
public struct Gente: Decodable, Sendable {
    public var perfiles: [Perfil] = []
    public init(from d: Decoder) throws { perfiles = try d.lector().v("gente", []) }
    public var gente: [PersonaCorta] {
        perfiles.map { PersonaCorta(id: $0.id, nombre: $0.nombre, foto: $0.fotos.first ?? "", edad: $0.edad,
                                    verificada: $0.verificada, asistencia: $0.asistencia, enMiGente: true) }
    }
}

public struct Reporte: Encodable, Sendable {
    public var usuarioId = ""
    public var planId = ""
    public var motivo: String
    public var detalle = ""
    public init(usuarioId: String = "", planId: String = "", motivo: String, detalle: String = "") {
        self.usuarioId = usuarioId; self.planId = planId; self.motivo = motivo; self.detalle = detalle
    }
}

// MARK: - Gente / Citas

public struct ProximoPlan: Codable, Hashable, Identifiable, Sendable {
    public var id = "", titulo = "", categoria = "", empiezaEn = "", foto = ""
    public init(id: String, titulo: String = "") { self.id = id; self.titulo = titulo }
    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); titulo = c.v("titulo", ""); categoria = c.v("categoria", ""); empiezaEn = c.v("empiezaEn", ""); foto = c.v("foto", "")
    }
}

public struct Tarjeta: Decodable, Hashable, Identifiable, Sendable {
    public var id = ""
    public var nombre = ""
    public var edad: Int?
    public var zona = ""
    public var trabajo = ""
    public var bio = ""
    public var intereses: [String] = []
    public var intenciones: [String] = []
    public var prompts: [Prompt] = []
    public var fotos: [String] = []
    public var verificada = false
    public var asistencia = Asist()
    public var planes = 0
    public var enComun: [String] = []
    public var proximos: [ProximoPlan] = []
    public init(id: String, nombre: String = "") { self.id = id; self.nombre = nombre }
    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); nombre = c.v("nombre", ""); edad = c.o("edad"); zona = c.v("zona", ""); trabajo = c.v("trabajo", "")
        bio = c.v("bio", ""); intereses = c.v("intereses", []); intenciones = c.v("intenciones", []); prompts = c.v("prompts", [])
        fotos = c.v("fotos", []); verificada = c.v("verificada", false); asistencia = c.v("asistencia", Asist())
        planes = c.v("planes", 0); enComun = c.v("enComun", []); proximos = c.v("proximos", [])
    }
}

public struct Mazo: Decodable, Sendable {
    public var personas: [Tarjeta] = []
    public init(from d: Decoder) throws { personas = try d.lector().v("personas", []) }
}

public struct EstadoPlus: Decodable, Hashable, Sendable {
    public var activo = false
    public var plan = ""
    public var origen = ""
    public var vence: String?
    public var renueva = false
    public var sumadasHoy = 0
    public var limite = 3
    public var reiniciaEn = ""
    public var encantasHoy = 0
    public var limiteEncantas = 1
    /// Vacío: puede destacar ya (si tiene Plus).
    public var destacarDesde: String?
    public init() {}
    public init(from d: Decoder) throws {
        let c = try d.lector()
        activo = c.v("activo", false); plan = c.v("plan", ""); origen = c.v("origen", ""); vence = c.o("vence")
        renueva = c.v("renueva", false); sumadasHoy = c.v("sumadasHoy", 0); limite = c.v("limite", 3)
        reiniciaEn = c.v("reiniciaEn", ""); encantasHoy = c.v("encantasHoy", 0); limiteEncantas = c.v("limiteEncantas", 1)
        destacarDesde = c.o("destacarDesde")
    }
}

public struct ResultadoReaccion: Decodable, Sendable {
    public var mutuo = false
    public var plus = EstadoPlus()
    public init(from d: Decoder) throws { let c = try d.lector(); mutuo = c.v("mutuo", false); plus = c.v("plus", EstadoPlus()) }
}

public struct Recibido: Decodable, Hashable, Identifiable, Sendable {
    public var id = "", nombre = ""
    public var edad: Int?
    public var foto = "", accion = "", cuando = ""
    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); nombre = c.v("nombre", ""); edad = c.o("edad"); foto = c.v("foto", "")
        accion = c.v("accion", ""); cuando = c.v("cuando", "")
    }
}

public struct Recibidos: Decodable, Sendable {
    public var personas: [Recibido] = []
    public var bloqueada = false
    public init(from d: Decoder) throws { let c = try d.lector(); personas = c.v("personas", []); bloqueada = c.v("bloqueada", false) }
}

public struct Invitacion: Decodable, Hashable, Identifiable, Sendable {
    public var planId = "", titulo = "", categoria = "", empiezaEn = ""
    public var de: Recibido?
    public var foto = "", cuando = ""
    public var id: String { planId }
    public init(from d: Decoder) throws {
        let c = try d.lector()
        planId = c.v("planId", ""); titulo = c.v("titulo", ""); categoria = c.v("categoria", ""); empiezaEn = c.v("empiezaEn", "")
        de = c.o("de"); foto = c.v("foto", ""); cuando = c.v("cuando", "")
    }
}

public struct Invitaciones: Decodable, Sendable {
    public var invitaciones: [Invitacion] = []
    public var bloqueada = false
    public init(from d: Decoder) throws { let c = try d.lector(); invitaciones = c.v("invitaciones", []); bloqueada = c.v("bloqueada", false) }
}

public struct Adentro: Decodable, Sendable {
    public var adentro = false
    public init(from d: Decoder) throws { adentro = try d.lector().v("adentro", false) }
}

// MARK: - Chat

public struct Autor: Codable, Hashable, Sendable {
    public var id = "", nombre = "", foto = ""
    public init(id: String = "", nombre: String = "", foto: String = "") { self.id = id; self.nombre = nombre; self.foto = foto }
    public init(from d: Decoder) throws { let c = try d.lector(); id = c.v("id", ""); nombre = c.v("nombre", ""); foto = c.v("foto", "") }
}

public struct Mensaje: Decodable, Hashable, Identifiable, Sendable {
    public var id: Int64 = 0
    public var planId = ""
    /// Sin autor: mensaje del sistema ("Plan completo. A disfrutar.").
    public var autor: Autor?
    public var texto = ""
    public var creadoEn = ""
    public init(id: Int64, planId: String = "", autor: Autor? = nil, texto: String = "", creadoEn: String = "") {
        self.id = id; self.planId = planId; self.autor = autor; self.texto = texto; self.creadoEn = creadoEn
    }
    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", Int64(0)); planId = c.v("planId", ""); autor = c.o("autor"); texto = c.v("texto", ""); creadoEn = c.v("creadoEn", "")
    }
}

public struct Mensajes: Decodable, Sendable {
    public var mensajes: [Mensaje] = []
    public init(from d: Decoder) throws { mensajes = try d.lector().v("mensajes", []) }
}

public struct Privado: Decodable, Hashable, Identifiable, Sendable {
    public var id: Int64 = 0
    public var deId = "", aId = "", texto = "", creadoEn = ""
    public var leidoEn: String?
    public init(id: Int64 = 0) { self.id = id }
    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", Int64(0)); deId = c.v("deId", ""); aId = c.v("aId", ""); texto = c.v("texto", "")
        creadoEn = c.v("creadoEn", ""); leidoEn = c.o("leidoEn")
    }
}

public struct Privados: Decodable, Sendable {
    public var privados: [Privado] = []
    public init(from d: Decoder) throws { privados = try d.lector().v("privados", []) }
}

public struct Conversacion: Decodable, Hashable, Identifiable, Sendable {
    public var con = Autor()
    public var ultimo = Privado()
    public var sinLeer = 0
    public var id: String { con.id }
    public init(from d: Decoder) throws {
        let c = try d.lector(); con = c.v("con", Autor()); ultimo = c.v("ultimo", Privado()); sinLeer = c.v("sinLeer", 0)
    }
}

public struct Conversaciones: Decodable, Sendable {
    public var conversaciones: [Conversacion] = []
    public init(from d: Decoder) throws { conversaciones = try d.lector().v("conversaciones", []) }
}

/// Evento del WebSocket de tiempo real.
public struct Evento: Decodable, Sendable {
    public var tipo = ""
    public var mensaje: Mensaje?
    public var privado: Privado?
    public var planId = ""
    public var quien: Autor?
    /// En `escribiendo` de un privado: el id de quien escribe (el campo `privado` viene como texto).
    public var privadoDe = ""
    public init(from d: Decoder) throws {
        let c = try d.lector()
        tipo = c.v("tipo", ""); mensaje = c.o("mensaje"); privado = c.o("privado"); planId = c.v("planId", ""); quien = c.o("quien")
        privadoDe = c.v("privado", "")
    }
}

// MARK: - Confianza

public struct Archivo: Decodable, Sendable {
    public var id = "", url = ""
    public init(from d: Decoder) throws { let c = try d.lector(); id = c.v("id", ""); url = c.v("url", "") }
}

/// Respuesta de POST /v1/verificacion/telefono: el código va por mail (`via` "mail", `destino` la
/// casilla enmascarada, n•••@gmail.com). Sin correo en la API, simulado: viene `codigoDePrueba`.
public struct EnvioDeCodigo: Decodable, Sendable {
    public var via = "", destino = "", codigoDePrueba = ""
    public init(from d: Decoder) throws {
        let c = try d.lector()
        via = c.v("via", ""); destino = c.v("destino", ""); codigoDePrueba = c.v("codigoDePrueba", "")
    }
}

public struct EstadoVerificacion: Decodable, Sendable {
    public var telefono = ""
    public var selfie = false
    /// ninguna | pendiente | aprobada | rechazada
    public var dni = "ninguna"
    public var dniMotivo = ""
    public init() {}
    public init(from d: Decoder) throws {
        let c = try d.lector()
        telefono = c.v("telefono", ""); selfie = c.v("selfie", false); dni = c.v("dni", "ninguna"); dniMotivo = c.v("dniMotivo", "")
    }
}

public struct DatosDNI: Encodable, Sendable {
    public var frenteId, dorsoId, selfieId, numero, apellido, nombres, sexo, nacimiento, tramite: String
    public init(frenteId: String, dorsoId: String, selfieId: String, numero: String, apellido: String,
                nombres: String, sexo: String, nacimiento: String, tramite: String) {
        self.frenteId = frenteId; self.dorsoId = dorsoId; self.selfieId = selfieId; self.numero = numero
        self.apellido = apellido; self.nombres = nombres; self.sexo = sexo; self.nacimiento = nacimiento; self.tramite = tramite
    }
}

// MARK: - Sesión

public struct SesionNueva: Decodable, Sendable {
    public var token = "", usuarioId = ""
    public var equipo = false
    public init(from d: Decoder) throws {
        let c = try d.lector(); token = c.v("token", ""); usuarioId = c.v("usuarioId", ""); equipo = c.v("equipo", false)
    }
}

// MARK: - Plus

public struct PlanPlus: Decodable, Hashable, Identifiable, Sendable {
    public var plan = "", producto = ""
    public var centavos = 0
    public var nombre = ""
    public var id: String { plan }
    public init(from d: Decoder) throws {
        let c = try d.lector(); plan = c.v("plan", ""); producto = c.v("producto", ""); centavos = c.v("centavos", 0); nombre = c.v("nombre", "")
    }
}

public struct Plus: Decodable, Sendable {
    public var estado = EstadoPlus()
    public var planes: [PlanPlus] = []
    public init(from d: Decoder) throws { let c = try d.lector(); estado = c.v("estado", EstadoPlus()); planes = c.v("planes", []) }
}

// MARK: - Lugares, entradas, pases, tap

public struct Compra: Decodable, Sendable {
    public var entradaId = "", link = ""
    public var totalCentavos = 0, cargoCentavos = 0, saldoCentavos = 0
    public init(from d: Decoder) throws {
        let c = try d.lector()
        entradaId = c.v("entradaId", ""); link = c.v("link", ""); totalCentavos = c.v("totalCentavos", 0)
        cargoCentavos = c.v("cargoCentavos", 0); saldoCentavos = c.v("saldoCentavos", 0)
    }
}

public struct EntradaMia: Decodable, Hashable, Identifiable, Sendable {
    public var id = "", codigo = "", planId = "", titulo = "", lugar = "", empiezaEn = ""
    public var cantidad = 1
    public var modo = "", estado = ""
    public var saldoCentavos = 0
    public var beneficio = ""
    public var validada = false
    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); codigo = c.v("codigo", ""); planId = c.v("planId", ""); titulo = c.v("titulo", "")
        lugar = c.v("lugar", ""); empiezaEn = c.v("empiezaEn", ""); cantidad = c.v("cantidad", 1); modo = c.v("modo", "")
        estado = c.v("estado", ""); saldoCentavos = c.v("saldoCentavos", 0); beneficio = c.v("beneficio", ""); validada = c.v("validada", false)
    }
}

public struct PaseSocio: Decodable, Hashable, Identifiable, Sendable {
    public var lugarId = "", lugar = ""
    public var visitas = 0, meta = 0
    public var beneficio = ""
    public var pendientes = 0
    public var id: String { lugarId }
    public init(from d: Decoder) throws {
        let c = try d.lector()
        lugarId = c.v("lugarId", ""); lugar = c.v("lugar", ""); visitas = c.v("visitas", 0); meta = c.v("meta", 0)
        beneficio = c.v("beneficio", ""); pendientes = c.v("pendientes", 0)
    }
}

public struct Pases: Decodable, Sendable {
    public var entradas: [EntradaMia] = []
    public var socios: [PaseSocio] = []
    public init(from d: Decoder) throws { let c = try d.lector(); entradas = c.v("entradas", []); socios = c.v("socios", []) }
}

public struct Lectura: Encodable, Sendable {
    public var placa: String
    public var picc = ""
    public var cmac = ""
    public init(placa: String, picc: String = "", cmac: String = "") { self.placa = placa; self.picc = picc; self.cmac = cmac }
}

public struct PlanTap: Decodable, Sendable {
    public var id = "", titulo = "", beneficio = ""
    public var saldoCentavos = 0
    public var repetido = false
    public init(from d: Decoder) throws {
        let c = try d.lector()
        id = c.v("id", ""); titulo = c.v("titulo", ""); beneficio = c.v("beneficio", ""); saldoCentavos = c.v("saldoCentavos", 0)
        repetido = c.v("repetido", false)
    }
}

public struct SocioTap: Decodable, Sendable {
    public var visitas = 0, meta = 0
    public var beneficio = ""
    public var gano = false, canjeo = false, sumo = false
    public init(from d: Decoder) throws {
        let c = try d.lector()
        visitas = c.v("visitas", 0); meta = c.v("meta", 0); beneficio = c.v("beneficio", "")
        gano = c.v("gano", false); canjeo = c.v("canjeo", false); sumo = c.v("sumo", false)
    }
}

public struct ResultadoTap: Decodable, Sendable {
    public var lugar = "", uso = ""
    public var plan: PlanTap?
    public var socio: SocioTap?
    public init(from d: Decoder) throws {
        let c = try d.lector(); lugar = c.v("lugar", ""); uso = c.v("uso", ""); plan = c.o("plan"); socio = c.o("socio")
    }
}

/// Error de la API (application/problem+json). `detail` se muestra tal cual.
public struct Problema: Decodable, Sendable {
    public var title = "", detail = "", code = ""
    public var status = 0
    public var issues: [Campo] = []
    public init(title: String = "", detail: String = "", code: String = "", status: Int = 0) {
        self.title = title; self.detail = detail; self.code = code; self.status = status
    }
    public init(from d: Decoder) throws {
        let c = try d.lector()
        title = c.v("title", ""); detail = c.v("detail", ""); code = c.v("code", ""); status = c.v("status", 0); issues = c.v("issues", [])
    }
}

public struct Campo: Decodable, Sendable {
    public var path = "", message = ""
    public init(from d: Decoder) throws { let c = try d.lector(); path = c.v("path", ""); message = c.v("message", "") }
}

// MARK: - Lo nuevo del contrato (docs/contrato-faltantes.md)

/// Cambios de un plan (PATCH /v1/planes/{id}): sólo se manda lo que no es nil.
public struct CambiosPlan: Encodable, Sendable {
    public var titulo: String?
    public var descripcion: String?
    public var empiezaEn: String?
    public var duracionMin: Int?
    public var total: Int?
    public var ingreso: String?
    public var visibilidad: String?
    public var soloVerificados: Bool?
    public var ocultarDireccion: Bool?
    public var edadMin: Int?
    public var edadMax: Int?
    public var genero: String?
    public var nivel: String?
    public var costoCentavos: Int?
    public var foto: String?
    public var zona: String?
    public var lugarNombre: String?
    public var direccion: String?
    public var lat: Double?
    public var lng: Double?
    public init() {}
}

public struct Reembolso: Decodable, Sendable {
    public var reembolsoCentavos = 0
    public init(from d: Decoder) throws { reembolsoCentavos = try d.lector().v("reembolsoCentavos", 0) }
}

public struct Bloqueado: Decodable, Hashable, Identifiable, Sendable {
    public var id = "", nombre = "", foto = ""
    public init(id: String, nombre: String = "", foto: String = "") { self.id = id; self.nombre = nombre; self.foto = foto }
    public init(from d: Decoder) throws { let c = try d.lector(); id = c.v("id", ""); nombre = c.v("nombre", ""); foto = c.v("foto", "") }
}

public struct Bloqueos: Decodable, Sendable {
    public var bloqueos: [Bloqueado] = []
    public init(from d: Decoder) throws { bloqueos = try d.lector().v("bloqueos", []) }
}

public struct Zonas: Decodable, Sendable {
    public var zonas: [String] = []
    public init(from d: Decoder) throws { zonas = try d.lector().v("zonas", []) }
}

/// GET /v1/version: `minIos` es el build (CFBundleVersion) mínimo que sigue andando.
public struct Version: Decodable, Sendable {
    public var version = ""
    public var minIos = 0
    public init(version: String = "", minIos: Int = 0) { self.version = version; self.minIos = minIos }
    public init(from d: Decoder) throws { let c = try d.lector(); version = c.v("version", ""); minIos = c.v("minIos", 0) }

    /// La app con este build tiene que actualizarse.
    public func pideActualizar(build: Int) -> Bool { minIos > 0 && build < minIos }
}

public struct Ubicacion: Encodable, Sendable {
    public var lat: Double
    public var lng: Double
    /// Redondeada a dos decimales (~1 km), como pide el contrato.
    public init(lat: Double, lng: Double) {
        self.lat = (lat * 100).rounded() / 100
        self.lng = (lng * 100).rounded() / 100
    }
}
