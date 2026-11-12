import SwiftUI
import XCTest
import Nucleo
@testable import Caigo

/// Responde a la app con datos fijos, por «MÉTODO ruta» o por ruta (GET). La query no se mira.
/// Lo que no está: GET → 404, lo demás → 200 con `{}` (como ApiFalsa en Android).
final class ApiFalsa: URLProtocol {
    nonisolated(unsafe) static var respuestas: [String: String] = [:]
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let metodo = request.httpMethod ?? "GET"
        let ruta = request.url?.path ?? ""
        let cuerpo = ApiFalsa.respuestas["\(metodo) \(ruta)"] ?? (metodo == "GET" ? ApiFalsa.respuestas[ruta] : "{}")
        let r = HTTPURLResponse(url: request.url!, statusCode: cuerpo == nil ? 404 : 200, httpVersion: nil,
                                headerFields: ["content-type": "application/json"])!
        client?.urlProtocol(self, didReceive: r, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data((cuerpo ?? "{}").utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

/// IngresoCasillas pide un FocusState: la envuelve con uno propio.
private struct ConFoco: View {
    @FocusState private var foco: Bool
    @State private var codigo = "12"
    var body: some View { IngresoCasillas(codigo: $codigo, foco: $foco) }
}

/// Monta cada pantalla entera en una ventana, con la API simulada y la sesión de Martina (u1) puesta, para que un
/// crash al abrirla salte en el CI (en la Mac no se usa el simulador). Los nombres son los de las capturas de Android
/// (PantallasTest.kt: `testX` acá es `x()` y la captura `X` allá). Al sumar una pantalla, sumarla acá y en Android.
@MainActor
final class PantallasTests: XCTestCase {
    /// Las respuestas compartidas con Android (apps/pruebas/respuestas.json): «MÉTODO ruta» o sólo la ruta.
    static func respuestas() -> [String: String] {
        guard let url = Bundle(for: PantallasTests.self).url(forResource: "respuestas", withExtension: "json"),
              let datos = try? Data(contentsOf: url),
              let raiz = try? JSONSerialization.jsonObject(with: datos) as? [String: Any] else { return [:] }
        var r: [String: String] = [:]
        for (k, v) in raiz where !k.hasPrefix("_") {
            if let d = try? JSONSerialization.data(withJSONObject: v, options: [.fragmentsAllowed]) { r[k] = String(decoding: d, as: UTF8.self) }
        }
        return r
    }

    /// Lo que la API simulada responde en `ruta`, leído con el modelo del núcleo.
    static func dato<T: Decodable>(_ ruta: String, _ t: T.Type = T.self) -> T? {
        guard let s = respuestas()[ruta] else { return nil }
        return try? JSON.leer(T.self, Data(s.utf8))
    }

    static func plan(_ id: String) -> Plan { dato("/v1/planes/\(id)") ?? Plan(id: id) }

    override func setUp() {
        ApiFalsa.respuestas = Self.respuestas()
        XCTAssertFalse(ApiFalsa.respuestas.isEmpty, "Falta respuestas.json en el bundle de CaigoTests (project.yml)")
    }

    override func tearDown() { Contenedor.prueba = nil }

    /// El Contenedor de la app contra la API simulada, con sesión, mi perfil y Plus ya cargados.
    private func contenedor() -> Contenedor {
        let conf = URLSessionConfiguration.ephemeral
        conf.protocolClasses = [ApiFalsa.self]
        Contenedor.prueba = (base: URL(string: "https://api.prueba")!, sesion: URLSession(configuration: conf), token: "cs_prueba", usuarioId: "u1")
        let c = Contenedor()
        c.yo = Self.dato("/v1/yo")
        c.plus = Self.dato("/v1/plus", Plus.self)?.estado
        return c
    }

    /// Monta la vista en una NavigationStack con el Contenedor, el Ir y la paleta, como CaigoRaiz.
    private func montar<V: View>(_ vista: V, segundos: Double = 3, preparar: (Contenedor) -> Void = { _ in }) {
        let c = contenedor()
        preparar(c)
        let raiz = NavigationStack { vista }
            .environment(c)
            .environment(Ir())
            .modifier(ConPaleta())
        let ventana = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        ventana.rootViewController = UIHostingController(rootView: raiz)
        ventana.makeKeyAndVisible()
        RunLoop.main.run(until: Date().addingTimeInterval(segundos))
        ventana.isHidden = true
    }

    /// La respuesta de la API simulada se lee con los modelos del núcleo (si cambia una forma, salta acá).
    func testRespuestasDecodifican() {
        XCTAssertEqual(Self.dato("/v1/yo", Perfil.self)?.nombre, "Martina")
        XCTAssertEqual(Self.dato("/v1/planes/explorar", Planes.self)?.planes.count, 6)
        XCTAssertEqual(Self.plan("p3").cobro, "entrada")
        XCTAssertEqual(Self.dato("/v1/planes/p2/solicitudes", Solicitudes.self)?.solicitudes.count, 2)
        XCTAssertEqual(Self.dato("/v1/gente/mazo", Mazo.self)?.personas.count, 4)
        XCTAssertEqual(Self.dato("/v1/pases", Pases.self)?.entradas.first?.codigo, "TK-7Q2M9X")
        XCTAssertEqual(Self.dato("/v1/plus", Plus.self)?.planes.count, 3)
        XCTAssertEqual(Self.dato("POST /v1/tap", ResultadoTap.self)?.lugar, "El Faro Bar")
    }

    // MARK: Principal (las cuatro pestañas)

    func testPrincipal() { montar(Principal(inicial: .ahora, modo: nil)) }
    func testPrincipalExplorar() { montar(Principal(inicial: .explorar, modo: nil)) }
    func testPrincipalMis() { montar(Principal(inicial: .mis, modo: nil)) }
    func testPrincipalPerfil() { montar(Principal(inicial: .perfil, modo: nil)) }

    // MARK: Planes

    func testAhora() { montar(Ahora(modoInicial: nil)) }
    func testAhoraGente() { montar(Ahora(modoInicial: "gente")) }
    func testAhoraCitas() { montar(Ahora(modoInicial: "citas")) }
    func testAhoraVacio() {
        ApiFalsa.respuestas["/v1/planes/ahora"] = "{\"planes\": []}"
        montar(Ahora(modoInicial: nil))
    }
    func testPlanesVacioAhora() { montar(PlanesVacioAhora()) }
    func testMapaAhora() {
        let planes = Self.dato("/v1/planes/ahora", Planes.self)?.planes ?? []
        montar(MapaAhora(planes: planes, punto: (-38.0023, -57.5575), categoria: .constant(nil), busqueda: .constant(""), alLista: {}))
    }
    func testExplorar() { montar(Explorar()) }
    func testDetallePlan() { montar(DetallePlan(id: "p1")) }
    func testDetallePlanMio() { montar(DetallePlan(id: "p2")) }
    func testDetallePlanEntrada() { montar(DetallePlan(id: "p3")) }
    func testDetallePlanSena() { montar(DetallePlan(id: "p6")) }
    func testDetallePlanAjeno() { montar(DetallePlan(id: "p4")) }
    func testDetallePlanSlug() { montar(DetallePlan(slug: "futbol-5-playa-grande")) }
    func testCrear() { montar(Crear()) }
    func testCrearEditar() { montar(Crear(editar: "p2")) }
    func testPublicado() { montar(Publicado(id: "p2")) }
    func testHojaConfirmar() {
        montar(HojaConfirmar(abierta: true, titulo: "¿Cancelás el plan?", cuerpo: "Les avisamos a los que van.", accion: "Cancelar plan",
                             cerrar: {}, confirmar: {}))
    }
    func testPiezasPlanes() {
        let p = Self.plan("p1")
        montar(ScrollView {
            VStack(spacing: 12) {
                TarjetaPlan(plan: p)
                TarjetaPlan(plan: Self.plan("p3"))
                AccionTarjeta(plan: Self.plan("p4"), alCambiar: { _ in })
                PlanesContador(p: p)
                PlanesBeneficio(texto: "2x1 en tragos hasta las 23", desbloqueado: false)
                PlanesBeneficio(texto: "2x1 en tragos hasta las 23", desbloqueado: true)
                PlanesCampo(texto: .constant(""), pista: "Buscá un plan", color: nil)
                ChipFoto(texto: "Faltan 3", oscuro: true, fondo: nil, color: nil)
                ChipDestacado()
                PlanesChipPlus()
            }
        }, segundos: 1)
    }

    // MARK: Gente

    func testMazoGente() { montar(MazoGente(modo: "gente")) }
    func testMazoCitas() { montar(MazoGente(modo: "citas")) }
    func testMazoVacio() {
        ApiFalsa.respuestas["/v1/gente/mazo"] = "{\"personas\": []}"
        montar(MazoGente(modo: "gente"))
    }
    func testGenteCarta() {
        guard let t = Self.dato("/v1/gente/mazo", Mazo.self)?.personas.first else { return XCTFail("Sin mazo") }
        montar(GenteCarta(t: t, esCitas: false, foto: 0, tinte: 0, abrirPerfil: {}).frame(height: 600).padding(16), segundos: 1)
    }
    func testGenteHojaInvitar() { montar(GenteHojaInvitar(personaId: "u3", nombre: "Lucía", cerrar: {}, alInvitar: { _ in })) }
    func testPerfilPersona() { montar(PerfilPersona(id: "u3", desde: "mazo")) }
    func testPerfilPersonaDeMiGente() { montar(PerfilPersona(id: "u2", desde: "perfil")) }
    func testRecibidos() { montar(VistaRecibidos(modo: "gente", pestana: 0)) }
    func testRecibidosInvitaciones() { montar(VistaRecibidos(modo: "gente", pestana: 1)) }
    func testRecibidosCitas() { montar(VistaRecibidos(modo: "citas", pestana: 0)) }

    // MARK: Ingreso

    func testArranque() { montar(Arranque()) }
    func testIntro() { montar(Intro()) }
    func testIngresoSlides() {
        montar(IngresoSlidePlanes(saltar: {}, avanzar: {}), segundos: 1)
        montar(IngresoSlideAmigos(saltar: {}, avanzar: {}), segundos: 1)
        montar(IngresoSlideAfuera(terminar: {}), segundos: 1)
        montar(IngresoZonaFogon(), segundos: 1)
        montar(IngresoComposicionFogon(e: IngresoEscala(w: 390, h: 844)), segundos: 1)
    }
    func testIngresar() { montar(Ingresar()) }
    func testDebugToken() { montar(DebugToken()) }
    func testIngresoMail() { montar(IngresoMail()) }
    func testDatos() { montar(Datos()) }
    func testTelefono() { montar(Telefono()) }
    func testSelfie() { montar(Selfie(volver: false)) }
    func testIntenciones() { montar(Intenciones(editar: false)) }
    func testIntencionesEditar() { montar(Intenciones(editar: true)) }
    func testVerificacion() { montar(Verificacion()) }
    func testVerificacionPendiente() {
        ApiFalsa.respuestas["/v1/verificacion"] = "{\"telefono\": \"+5492235550142\", \"selfie\": true, \"dni\": \"ninguna\"}"
        montar(Verificacion())
    }
    func testDni() { montar(Dni(plan: nil)) }
    func testElegirGenero() { montar(ElegirGenero(genero: .constant("mujer"), diversidad: .constant(false)), segundos: 1) }
    func testHojaGenero() { montar(HojaGenero(abierta: true, cerrar: {}, reintentar: {})) }
    func testPiezasIngreso() {
        let jpeg = UIGraphicsImageRenderer(size: CGSize(width: 40, height: 30)).jpegData(withCompressionQuality: 0.8) { ctx in
            UIColor.systemPurple.setFill(); ctx.fill(CGRect(x: 0, y: 0, width: 40, height: 30))
        }
        montar(ScrollView {
            VStack(spacing: 12) {
                IngresoTitulo(titulo: "¿Cómo te llamás?", bajada: "Así te ven en los planes.")
                IngresoCampo(valor: .constant("Martina"))
                ConFoco()
                IngresoEncriptado()
                IngresoPuntos(activo: 1, total: 3, on: C.violeta, off: C.pista)
                IngresoChipRequisito(texto: "Buena luz", ok: true)
                IngresoChipRequisito(texto: "Sin anteojos", ok: false)
                IngresoObturador(accion: {})
                IngresoSaltar(alfa: 1, accion: {})
                IngresoPillOscura(texto: "Mirá a la cámara")
                IngresoImagenDatos(datos: jpeg).frame(width: 200, height: 150)
                IngresoCasillaPaso(hecho: true, numero: 1)
                IngresoCasillaPaso(hecho: false, numero: 2)
                IngresoMancha(tam: 200, claro: C.lima, medio: C.violeta, oscuro: C.carbon, alfa: 0.8)
                IngresoFondoSplash().frame(height: 200)
                IngresoFondoVioleta().frame(height: 200)
            }
        }, segundos: 1)
    }
    func testIngresoCamaras() {
        montar(IngresoCamaraVida(vida: IngresoVida(), disparador: IngresoDisparador()), segundos: 1)
        montar(IngresoCamaraDni(disparador: IngresoDisparador(), alLeer: { _ in }), segundos: 1)
    }

    // MARK: Míos

    func testMisPlanes() { montar(MisPlanes()) }
    func testMisPlanesVacio() {
        ApiFalsa.respuestas["/v1/planes/mios"] = "{\"planes\": []}"
        montar(MisPlanes())
    }
    func testMiPerfil() { montar(MiPerfil()) }
    func testMiGente() { montar(MiGente()) }
    func testEditarPerfil() { montar(EditarPerfil()) }
    func testCompletarPerfil() { montar(EditarPerfil(completar: true)) }
    func testSolicitudes() { montar(VistaSolicitudes(planId: "p2")) }
    func testSolicitudesVacio() { montar(VistaSolicitudes(planId: "p1")) }
    func testMiosVacioSolicitudes() { montar(MiosVacioSolicitudes(pl: Self.plan("p2"), compartir: {}), segundos: 1) }
    func testChat() { montar(Chat(planId: "p1")) }
    func testMiosVistaHilo() { montar(MiosVistaHilo(hilo: MiosHilo(), foto: nil), segundos: 1) }
    func testMiosEntrada() { montar(MiosEntrada(alTipear: {}, enviar: { _ in true }), segundos: 1) }
    func testPrivados() { montar(VistaPrivados()) }
    func testPrivado() { montar(VistaPrivado(conId: "u3", nombre: "Lucía")) }
    func testCheckin() { montar(Checkin(planId: "p1")) }
    func testRepetimos() { montar(Repetimos(planId: "p8")) }
    func testQrOrganizador() { montar(QrOrganizador(planId: "p2")) }
    func testEscanearQr() { montar(EscanearQr(planId: "p2")) }
    func testMiosCodigoQr() { montar(MiosCodigoQr(texto: "CG-P2-8F3K"), segundos: 1) }
    func testMiosEscaner() { montar(MiosEscaner(alLeer: { _ in }), segundos: 1) }
    func testAjustesDeAvisos() { montar(AjustesDeAvisos()) }
    func testBloqueos() { montar(VistaBloqueos()) }
    func testActualizarApp() { montar(ActualizarApp(), segundos: 1) }
    func testPiezasMios() {
        montar(ScrollView {
            VStack(spacing: 12) {
                MiosVerificado()
                MiosSeccion(texto: "Tus planes")
                MiosOverline(texto: "Próximos")
                MiosVacio(titulo: "Todavía no tenés planes", cuerpo: "Sumate a uno o armá el tuyo.", cta: "Ver planes", accion: {})
                MiosCampo(valor: .constant("Diseñadora UX"))
                MiosFila(icono: I.gente, titulo: "Mi gente", sub: "8 personas", accion: {})
                MiosPill(texto: "Verificada", fondo: C.lima, color: C.carbon, gel: nil)
            }
        }, segundos: 1)
    }

    // MARK: Pagos

    func testPlus() { montar(VistaPlus(motivo: "perfil")) }
    func testPlusLimite() { montar(VistaPlus(motivo: "limite")) }
    func testCheckout() { montar(Checkout(planId: "p3", cantidad: 2)) }
    func testCheckoutSena() { montar(Checkout(planId: "p6", cantidad: 1)) }
    func testEntrada() { montar(VistaEntrada(entradaId: "e1", planId: "p3")) }
    func testEntradaSena() { montar(VistaEntrada(entradaId: "e2", planId: "p6")) }
    func testQrEntrada() { montar(QrEntrada(entradaId: "e1")) }
    func testPases() { montar(VistaPases()) }
    func testPasesVacio() {
        ApiFalsa.respuestas["/v1/pases"] = "{\"entradas\": [], \"socios\": []}"
        montar(VistaPases())
    }
    func testTap() { montar(Tap(planId: nil)) }
    func testTapConPlaca() { montar(Tap(planId: "p3")) { $0.tapPendiente = .tap(placa: "placa-faro", picc: "", cmac: "") } }
    func testAdentro() { montar(VistaAdentro(alTerminar: { _ in })) { $0.ultimoTap = Self.dato("POST /v1/tap") } }
    func testPiezasPagos() {
        montar(ScrollView {
            VStack(spacing: 12) {
                PagosPillPlus()
                PagosBoton(texto: "Pagar con Mercado Pago", estilo: .negro, icono: nil, accion: {})
                PagosBoton(texto: "Seña", estilo: .lima, icono: nil, accion: {})
                PagosEncabezado(titulo: "Tu entrada", volver: {})
                PagosCodigoQr(texto: "TK-7Q2M9X")
                PagosBotonWallet(bajar: { Data() })
            }
        }, segundos: 1)
    }
    func testPagosEscanerQr() { montar(PagosEscanerQr(leido: { _ in }), segundos: 1) }
}
