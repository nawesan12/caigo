import CoreGraphics
import Foundation
import Testing
@testable import Nucleo

// Los mismos casos que apps/android/app/src/test/.../LogicaTest.kt, más la lectura de JSON real.

@Suite struct CupoTests {
    @Test func badge() {
        #expect(Cupo.badge(0) == "Completo")
        #expect(Cupo.badge(-2) == "Completo")
        #expect(Cupo.badge(1) == "Falta 1")
        #expect(Cupo.badge(2) == "Faltan 2")
        #expect(Cupo.badge(12) == "Faltan 12")
    }

    @Test func faltanConExtra() {
        #expect(Cupo.faltan(total: 10, somos: 8) == 2)
        #expect(Cupo.faltan(total: 10, somos: 8, extra: 2) == 0)
        #expect(Cupo.faltan(total: 4, somos: 3, extra: 5) == 0)
        #expect(Cupo.somos(8, 10) == "Somos 8 de 10")
    }

    @Test func segmentos() {
        #expect(Cupo.segmentos(somos: 3, total: 4) == [true, true, true, false])
    }
}

@Suite struct PlataTests {
    @Test func formato() {
        #expect(Plata.pesos(450000) == "$4.500")
        #expect(Plata.pesos(1720000) == "$17.200")
        #expect(Plata.pesos(1399900) == "$13.999")
        #expect(Plata.pesos(0) == "$0")
        #expect(Plata.pesos(1250) == "$12,50")
        #expect(Plata.pesos(123456700) == "$1.234.567")
    }

    @Test func cargoRedondeadoACien() {
        // 7,5 % de $8.000 = $600; de $4.500 = $337,50 → $300; de $5.000 = $375 → $400.
        #expect(Plata.cargo(800000) == 60000)
        #expect(Plata.cargo(450000) == 30000)
        #expect(Plata.cargo(500000) == 40000)
    }

    @Test func desgloseEntrada() {
        let d = Plata.desglose(800000, cobro: "entrada", senaPct: nil, cantidad: 2)
        #expect(d == Plata.Desglose(alLugar: 1600000, cargo: 120000, total: 1720000, saldo: 0))
    }

    @Test func desgloseSena() {
        let d = Plata.desglose(800000, cobro: "sena", senaPct: 30, cantidad: 1)
        #expect(d == Plata.Desglose(alLugar: 240000, cargo: 60000, total: 300000, saldo: 560000))
        // La seña se redondea a $100: round(precio·pct/100/100)·100.
        #expect(Plata.alLugar(1111000, cobro: "sena", senaPct: 30) == 330000)
        #expect(Plata.alLugar(550000, cobro: "sena", senaPct: 30) == 170000)
        #expect(Plata.alLugar(550000, cobro: "entrada", senaPct: 30) == 550000)
    }
}

@Suite struct Pdf417Tests {
    @Test func formatoNuevo() throws {
        let d = try #require(Pdf417Dni.leer("00654321987@GARCIA LOPEZ@MARIA  SOL@F@35123456@B@14/03/1994@20/05/2019@239"))
        #expect(d.tramite == "00654321987")
        #expect(d.apellido == "GARCIA LOPEZ")
        #expect(d.nombres == "MARIA SOL")
        #expect(d.numero == "35123456")
        #expect(d.ejemplar == "B")
        #expect(d.nacimiento == "1994-03-14")
    }

    @Test func formatoViejo() throws {
        let d = try #require(Pdf417Dni.leer("@17463537    A@1@PEREZ@JUAN CARLOS@ARGENTINA@08/07/1965@M@15/07/2009@00065327459@7174 @15/07/2024@317@0@ILR:2.01 C:110608.03 (No Cap.)@UNIDAD #20 || S/N: 0040>2008>>0006"))
        #expect(d.numero == "17463537")
        #expect(d.apellido == "PEREZ")
        #expect(d.nombres == "JUAN CARLOS")
        #expect(d.nacimiento == "1965-07-08")
        #expect(d.tramite == "00065327459")
    }

    @Test func basura() {
        #expect(Pdf417Dni.leer("hola") == nil)
        #expect(Pdf417Dni.leer("1@2@3@4@5@6@7@8") == nil)
        #expect(Pdf417Dni.leer("00654321987@A@B@F@12@B@14/03/1994@20/05/2019") == nil)
    }
}

@Suite struct MazoTests {
    func r(_ e: FisicaMazo.Estado, _ a: FisicaMazo.Accion...) -> FisicaMazo.Estado { a.reduce(e, FisicaMazo.reducir) }

    @Test func pasaElUmbralYVuela() {
        let e = r(.init(), .empieza, .mueve(140, 10), .suelta)
        #expect(e.vuela == 1)
        #expect(FisicaMazo.desplazamiento(e) == (520, -30))
        let sig = FisicaMazo.reducir(e, .siguiente)
        #expect(sig.indice == 1 && sig.vuela == 0 && sig.dx == 0)
    }

    @Test func noLlegaYVuelve() {
        let e = r(.init(), .empieza, .mueve(-90, 0), .suelta)
        #expect(e.vuela == 0 && e.dx == 0)
    }

    @Test func izquierdaEsPaso() {
        #expect(r(.init(), .empieza, .mueve(-101, 0), .suelta).vuela == -1)
    }

    @Test func fisicas() {
        #expect(abs(FisicaMazo.rotacion(160) - 10) < 0.001)
        #expect(abs(FisicaMazo.escalaSiguiente(0) - 0.93) < 0.001)
        #expect(abs(FisicaMazo.escalaSiguiente(200) - 1) < 0.001)
        #expect(FisicaMazo.alfaMeGusta(120) == 1)
        #expect(FisicaMazo.alfaPaso(120) == 0)
    }

    @Test func fotos() {
        let e = r(.init(), .foto(delta: 1, cantidad: 3), .foto(delta: 1, cantidad: 3), .foto(delta: 1, cantidad: 3))
        #expect(e.foto == 2)
        #expect(r(e, .foto(delta: -5, cantidad: 3)).foto == 0)
    }
}

@Suite struct ModelosTests {
    func ejemplo(_ nombre: String) throws -> Data {
        let url = try #require(Bundle.module.url(forResource: nombre, withExtension: "json", subdirectory: "Ejemplos"))
        return try Data(contentsOf: url)
    }

    /// Respuesta real de https://api.caigo.com.ar/v1/publico/planes/explorar (31/10/2026).
    @Test func planesDeProduccion() throws {
        let p = try JSON.leer(Planes.self, ejemplo("explorar")).planes
        #expect(p.count == 4)
        let previa = p[0]
        #expect(previa.titulo == "Previa al atardecer en Playa Grande")
        #expect(previa.categoria == "previa")
        #expect(previa.somos == 4 && previa.total == 8 && previa.faltan == 4)
        #expect(previa.ingreso == "libre")
        #expect(previa.soloVerificados)
        #expect(previa.organizador.organizador)
        #expect(previa.van.count == 1)
        #expect(previa.cobro == "gratis")
        #expect(previa.precioCentavos == nil)
        #expect(previa.aceptaGrupos)
        #expect(previa.duracionMin == 150)
        #expect(Fecha.parse(previa.empiezaEn) != nil)
        #expect(Fecha.hora(previa.empiezaEn) == "18:30")
    }

    @Test func problema() throws {
        let p = try JSON.leer(Problema.self, ejemplo("problema"))
        #expect(p.code == "sin_sesion")
        #expect(ErrorApi(estado: 401, problema: p).detalle == "Tu sesión venció o no ingresaste.")
    }

    @Test func planConEntradaYCamposQueFaltan() throws {
        let json = """
        {"id":"p1","titulo":"Taller de cerámica","cobro":"sena","precioCentavos":800000,"senaPct":30,"cargoCentavos":60000,
         "lugar":{"id":"l1","nombre":"Café Molienda","zona":"Güemes"},"distanciaM":900,"miEstado":"aceptada","miCantidad":2,
         "direccion":{"texto":"Güemes 2950","lat":-38.01,"lng":-57.54},"destacado":true,"campoNuevo":{"x":1},"total":"raro"}
        """
        let p = try JSON.leer(Plan.self, Data(json.utf8))
        #expect(p.conEntrada && p.adentro && p.destacado)
        #expect(p.senaPct == 30 && p.cargoCentavos == 60000)
        #expect(p.lugar?.nombre == "Café Molienda")
        #expect(p.direccion?.texto == "Güemes 2950")
        #expect(p.total == 0) // tipo equivocado: queda el valor por defecto
        #expect(p.ingreso == "aprobacion")
        #expect(distancia(p.distanciaM) == "900 m")
    }

    @Test func evento() throws {
        let json = #"{"tipo":"mensaje","mensaje":{"id":42,"planId":"p1","autor":{"id":"u1","nombre":"Lu"},"texto":"¡Llego en 10!","creadoEn":"2026-10-31T21:15:03.123456789-03:00"}}"#
        let e = try JSON.leer(Evento.self, Data(json.utf8))
        #expect(e.mensaje?.id == 42)
        #expect(e.mensaje?.autor?.nombre == "Lu")
        #expect(Fecha.hora(e.mensaje!.creadoEn) == "21:15")
    }

    @Test func cuerposQueSeMandan() throws {
        var p = PlanNuevo(categoria: "juego", empiezaEn: "2026-10-31T21:30:00-03:00", ciudad: "Mar del Plata", lat: -38, lng: -57.5, somos: 3, faltan: 1)
        p.soloVerificados = true
        let s = String(data: try JSON.codificador.encode(p), encoding: .utf8)!
        #expect(s.contains("\"categoria\":\"juego\""))
        #expect(s.contains("\"soloVerificados\":true"))
        #expect(!s.contains("costoCentavos"))
    }
}

@Suite struct OtrosTests {
    @Test func fechas() {
        let ahora = Fecha.parse("2026-10-31T20:00:00-03:00")!
        #expect(Fecha.cuando("2026-10-31T21:30:00-03:00", ahora: ahora) == "Hoy 21:30")
        #expect(Fecha.cuando("2026-11-01T00:30:00Z", ahora: ahora) == "Hoy 21:30")
        #expect(Fecha.cuando("2026-11-01T10:00:00-03:00", ahora: ahora) == "Mañana 10:00")
        #expect(Fecha.cuando("2026-11-06T10:00:00-03:00", ahora: ahora) == "Vie 10:00")
        #expect(Fecha.cuando("2026-11-07T10:00:00-03:00", ahora: ahora) == "Sáb 7/11 10:00")
        #expect(Fecha.cuando("2027-03-03T22:00:00-03:00", ahora: ahora) == "Mié 3/3 22:00")
        #expect(Fecha.rango("2027-03-03T22:00:00-03:00", duracionMin: 60, ahora: ahora) == "Miércoles 3 de marzo, 22:00 a 23:00")
        #expect(Fecha.enCuanto("2026-10-31T22:00:00-03:00", ahora: ahora) == "Ahora · en 2 h")
        #expect(Fecha.enCuanto("2026-10-31T20:40:00-03:00", ahora: ahora) == "Ahora · en 40 min")
        #expect(Fecha.enCuanto("2026-11-01T02:00:00-03:00", ahora: ahora) == nil)
        #expect(Fecha.rango("2026-10-31T20:30:00-03:00", duracionMin: 60, ahora: ahora) == "Hoy, 20:30 a 21:30")
        #expect(Fecha.hace("2026-10-31T19:40:00-03:00", ahora: ahora) == "hace 20 min")
        #expect(Fecha.hace("2026-10-30T19:40:00-03:00", ahora: ahora) == "ayer")
    }

    @Test func distancias() {
        #expect(distancia(80) == "80 m")
        #expect(distancia(860) == "900 m")
        #expect(distancia(1240) == "1,2 km")
        #expect(distancia(12400) == "12 km")
        #expect(distancia(nil) == "")
    }

    @Test func clerk() {
        // pk_test_ + base64("clerk.ejemplo.com$")
        let k = "pk_test_" + Data("clerk.ejemplo.com$".utf8).base64EncodedString().replacingOccurrences(of: "=", with: "")
        #expect(Clerk.fapiDe(k)?.absoluteString == "https://clerk.ejemplo.com")
        #expect(Clerk.fapiDe("hola") == nil)
        #expect(Clerk.formulario(["redirect_url": "ar.com.caigo.app://callback", "strategy": "oauth_apple"]) ==
                "redirect_url=ar.com.caigo.app%3A%2F%2Fcallback&strategy=oauth_apple")
        #expect(Clerk.nonce(de: URL(string: "ar.com.caigo.app://callback?rotating_token_nonce=abc")!) == "abc")
    }

    @Test func tiempoReal() {
        #expect(TiempoReal.url(base: URL(string: "https://api.caigo.com.ar")!, token: "cs_1").absoluteString ==
                "wss://api.caigo.com.ar/v1/tiempo-real?token=cs_1")
        #expect(TiempoReal.espera(0) == 1 && TiempoReal.espera(3) == 8 && TiempoReal.espera(9) == 30)
    }

    @Test func api() {
        let a = Api(base: URL(string: "https://api.caigo.com.ar")!, token: { nil })
        #expect(a.url("/v1/planes/ahora", [("lat", "-38.1"), ("categoria", nil), ("x", "")]).absoluteString ==
                "https://api.caigo.com.ar/v1/planes/ahora?lat=-38.1")
        #expect(FiltrosFinos(verificados: true, faltan: 2).query.compactMap { $0.1 } == ["1", "2"])
    }

    @Test func iconos() {
        for d in I.todos {
            let p = TrazoSVG.camino(d)
            #expect(!p.isEmpty)
            let b = p.boundingBoxOfPath
            #expect(b.minX >= -1 && b.maxX <= 25 && b.minY >= -1 && b.maxY <= 25, "\(d) → \(b)")
        }
        // Un círculo de radio 9 centrado en 12,12.
        let c = TrazoSVG.camino("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18z").boundingBoxOfPath
        #expect(abs(c.minX - 3) < 0.01 && abs(c.maxX - 21) < 0.01 && abs(c.minY - 3) < 0.01 && abs(c.maxY - 21) < 0.01)
    }
}

@Suite struct ContratoFaltantesTests {
    @Test func vacioSegunHora() {
        // Domingo 2 de noviembre de 2025, 15:00 en Argentina.
        let siesta = Fecha.parse("2025-11-02T15:00:00-03:00")!
        #expect(VacioAhora.cuerpo(siesta).hasPrefix("Domingo a la siesta, la costa duerme."))
        let noche = Fecha.parse("2025-11-07T22:30:00-03:00")!
        #expect(VacioAhora.cuerpo(noche).hasPrefix("Viernes a la noche"))
    }

    @Test func genero() {
        // «Solo mujeres» / «Solo diversidades» sólo con la confirmación del pedido (no se guarda nada).
        #expect(Generos.puedeSumarse(genero: "mujeres", Confirma(mujer: true)))
        #expect(!Generos.puedeSumarse(genero: "mujeres", Confirma(diversidad: true)))
        #expect(!Generos.puedeSumarse(genero: "diversidades", Confirma(mujer: true)))
        #expect(Generos.puedeSumarse(genero: "diversidades", Confirma(diversidad: true)))
        #expect(Generos.puedeSumarse(genero: "cualquiera", Confirma()))
        #expect(Generos.pide("confirmar_mujer") == "mujer")
        #expect(Generos.pide("confirmar_diversidad") == "diversidad")
        #expect(Generos.pide("falta_selfie") == nil)
        #expect(Generos.confirma("mujer") == Confirma(mujer: true))
    }

    @Test func perfilNuevo() throws {
        let json = #"{"id":"u1","genero":"mujer","diversidad":true,"avisos":{"planesCerca":false},"proximos":[{"id":"p1","titulo":"Truco"}]}"#
        let p = try JSON.leer(Perfil.self, Data(json.utf8))
        #expect(!p.avisos.planesCerca && p.avisos.tuGente)
        #expect(p.proximos.first?.titulo == "Truco")
    }

    @Test func moderacion() throws {
        // Ausente = normal; sólo al autor (u organizador) le llega "revision" u "oculto".
        #expect(try JSON.leer(Plan.self, Data("{}".utf8)).moderacion == "")
        let p = try JSON.leer(Plan.self, Data(#"{"id":"p1","miEstado":"organizador","moderacion":"oculto"}"#.utf8))
        #expect(p.moderacion == "oculto")
        let e = try JSON.leer(Evento.self, Data(#"{"tipo":"moderado","mensaje":{"id":5,"planId":"p1","autor":{"id":"u1"},"texto":"x","moderacion":"revision"}}"#.utf8))
        #expect(e.tipo == "moderado" && e.mensaje?.moderacion == "revision")
        let v = try JSON.leer(Evento.self, Data(#"{"tipo":"moderado","privado":{"id":7,"deId":"a","aId":"b","moderacion":"oculto"}}"#.utf8))
        #expect(v.privado?.moderacion == "oculto" && v.privadoDe == "")
        #expect(try JSON.leer(Mensaje.self, Data(#"{"id":1,"moderacion":null}"#.utf8)).moderacion == "")
    }

    @Test func avisosDeModeracion() {
        var p = Plan(id: "p1")
        p.moderacion = "revision"
        #expect(Moderacion.avisoPlan(p) == nil) // no es el organizador
        p.miEstado = "organizador"
        #expect(Moderacion.avisoPlan(p) == "Tu plan está en revisión.")
        p.moderacion = "oculto"
        #expect(Moderacion.avisoPlan(p) == "Tu plan está oculto por las normas:\nnadie más lo ve.")
        p.moderacion = ""
        #expect(Moderacion.avisoPlan(p) == nil)
        #expect(Moderacion.etiqueta("revision") == "En revisión")
        #expect(Moderacion.etiqueta("oculto") == "Oculto por las normas")
        #expect(Moderacion.etiqueta("") == nil)
        #expect(Moderacion.sacar("oculto", mio: false))
        #expect(!Moderacion.sacar("oculto", mio: true))
        #expect(!Moderacion.sacar("revision", mio: false))
    }

    @Test func eventoEscribiendoPrivado() throws {
        let e = try JSON.leer(Evento.self, Data(#"{"tipo":"escribiendo","privado":"u9"}"#.utf8))
        #expect(e.privadoDe == "u9" && e.privado == nil)
    }

    @Test func version() throws {
        let v = try JSON.leer(Version.self, Data(#"{"version":"abc","minIos":7}"#.utf8))
        #expect(v.pideActualizar(build: 6))
        #expect(!v.pideActualizar(build: 7))
        #expect(!Version().pideActualizar(build: 1))
    }

    @Test func ubicacionRedondeada() throws {
        let u = Ubicacion(lat: -38.00234, lng: -57.55751)
        #expect(u.lat == -38.0 && u.lng == -57.56)
    }

    @Test func busqueda() {
        #expect(Api.busqueda(" t ") == nil)
        #expect(Api.busqueda(" tr ") == "tr")
        #expect(Api.busqueda(nil) == nil)
    }

    @Test func cambiosPlanSoloLoQueCambia() throws {
        var c = CambiosPlan()
        c.total = 6
        let s = String(decoding: try JSON.codificador.encode(c), as: UTF8.self)
        #expect(s == #"{"total":6}"#)
    }

    @Test func lineaDeConfianza() throws {
        let p = try JSON.leer(Plan.self, Data(#"{"organizador":{"nombre":"Martín Pérez","asistencia":{"fue":9,"de":10}},"confianza":{"todosVerificados":true,"soloChicas":true,"chicas":2}}"#.utf8))
        #expect(lineaConfianza(p) == "Todos\u{00A0}verificados\u{00A0}· Organiza\u{00A0}Martín\u{00A0}(9/10)\u{00A0}· Van\u{00A0}2\u{00A0}chicas")
        var q = Plan(id: "x")
        q.organizador.nombre = "Lu"
        q.confianza.soloChicas = true
        q.confianza.chicas = 1
        #expect(lineaConfianza(q) == "Organiza\u{00A0}Lu\u{00A0}· Sólo\u{00A0}chicas")
        // Sin el campo (API vieja): sólo el organizador, sin asistencia si no hay datos.
        let r = try JSON.leer(Plan.self, Data(#"{"organizador":{"nombre":"Fede"}}"#.utf8))
        #expect(lineaConfianza(r) == "Organiza\u{00A0}Fede")
        #expect(lineaConfianza(Plan(id: "y")) == nil)
        var l = Plan(id: "z")
        l.organizador = PersonaCorta(nombre: "Ana", asistencia: Asist(fue: 1, de: 2))
        l.lugar = LugarCorto(nombre: "La Fonda")
        #expect(lineaConfianza(l) == "Organiza\u{00A0}La\u{00A0}Fonda")
    }
}

@Suite struct GestosTests {
    func plan(_ cambios: (inout Plan) -> Void = { _ in }) -> Plan {
        var p = Plan(id: "x")
        p.ingreso = "libre"; p.faltan = 2; p.organizador = PersonaCorta(id: "org")
        cambios(&p)
        return p
    }

    @Test func directoSoloSinNadaQueConfirmar() {
        #expect(Gestos.directo(plan()))
        #expect(!Gestos.directo(plan { $0.ingreso = "aprobacion" }))
        #expect(!Gestos.directo(plan { $0.cobro = "entrada" }))
        #expect(!Gestos.directo(plan { $0.genero = "mujeres" }))
        #expect(!Gestos.directo(plan { $0.miEstado = "cancelada" }))
        #expect(!Gestos.directo(plan { $0.faltan = 0 }))
    }

    @Test func paraDecidirSacaLosMiosYLosCompletos() {
        let ps = [plan(), plan { $0.faltan = 0 }, plan { $0.miEstado = "pendiente" }, plan { $0.organizador = PersonaCorta(id: "yo") }]
        #expect(Gestos.paraDecidir(ps, yo: "yo").count == 1)
    }

    @Test func logrosDesdeLaApi() throws {
        let json = #"{"id":"a","logros":{"nivel":"Habitué","planes":7,"siguiente":"De fierro","faltan":8,"racha":3,"comodin":true,"insignias":[{"id":"cancha","nombre":"En cancha","detalle":"5 planes de deporte"}],"anfitrion":false}}"#
        let p = try JSON.leer(Perfil.self, Data(json.utf8))
        let l = try #require(p.logros)
        #expect(TextoLogros.cabecera(l) == "Habitué · 7 planes")
        #expect(TextoLogros.racha(l) == "Racha: 3 semanas · comodín libre")
        #expect(TextoLogros.siguiente(l) == "Te faltan 8 para De fierro")
        #expect(l.insignias.first?.nombre == "En cancha")
        var corta = l; corta.racha = 1
        #expect(TextoLogros.racha(corta) == nil)
    }

    @Test func anfitrionYFaltazos() throws {
        let json = #"{"id":"p","organizador":{"nombre":"Lu","anfitrion":true},"lugar":{"id":"l","beneficioNivel":"Postre"}}"#
        let p = try JSON.leer(Plan.self, Data(json.utf8))
        #expect(p.organizador.anfitrion)
        #expect(p.organizador.faltazos == 0)
        #expect(p.lugar?.beneficioNivel == "Postre")
    }
}
