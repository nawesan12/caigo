import XCTest
@testable import Caigo

/// Cada link de `apps/pruebas/enlaces.json` que abre la app lleva a su pantalla (o a la web dentro de la app),
/// nunca a nada. La misma lista la sirve Caddy (generar.py) y la prueba Android en EnlacesTest.kt.
@MainActor
final class EnlacesTests: XCTestCase {
    private struct Lista: Decodable { var dominio: String; var ejemplos: [String: String] }

    func testCadaLinkAbreSuPantalla() throws {
        let url = try XCTUnwrap(Bundle(for: EnlacesTests.self).url(forResource: "enlaces", withExtension: "json"),
                                "Falta enlaces.json en el paquete de pruebas")
        let lista = try JSONDecoder().decode(Lista.self, from: Data(contentsOf: url))
        for (camino, destino) in lista.ejemplos where destino != "web" {
            let d = Contenedor.destino(de: URL(string: "https://\(lista.dominio)\(camino)")!)
            XCTAssertEqual(d.map(clave), destino, camino)
        }
    }

    func testOtroDominioNoEsDeLaApp() {
        XCTAssertNil(Contenedor.destino(de: URL(string: "https://otro.com.ar/p/x")!))
    }

    /// Cómo se escribe el destino en enlaces.json.
    private func clave(_ d: Destino) -> String {
        switch d {
        case let .plan(id): "plan:\(id)"
        case let .planSlug(s): "planSlug:\(s)"
        case let .tap(placa, _, _): "tap:\(placa)"
        case let .pantalla(p, _, _, _): "pantalla:\(p)"
        case let .seccion(p): "seccion:\(p.rawValue)"
        case .web: "web-en-app"
        case let .ruta(r):
            switch r {
            case .crear: "ruta:crear"
            case .verificacion: "ruta:verificacion"
            case .privados: "ruta:privados"
            case let .privado(id, _): "ruta:privado:\(id)"
            case let .persona(id, _): "ruta:persona:\(id)"
            case .editarPerfil: "ruta:editarPerfil"
            case .bloqueos: "ruta:bloqueos"
            case .gente: "ruta:gente"
            case .ajustesAvisos: "ruta:ajustesAvisos"
            case let .chat(id): "ruta:chat:\(id)"
            case let .editarPlan(id): "ruta:editarPlan:\(id)"
            case let .repetimos(id): "ruta:repetimos:\(id)"
            case let .solicitudes(id): "ruta:solicitudes:\(id)"
            default: "ruta:?"
            }
        }
    }
}
