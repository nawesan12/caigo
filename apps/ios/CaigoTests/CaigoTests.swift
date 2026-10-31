import XCTest
import Nucleo
@testable import Caigo

/// Lo que se puede probar de la app sin pantalla: la lógica vive en Nucleo (ver Nucleo/Tests).
final class CaigoTests: XCTestCase {
    func testCupo() {
        XCTAssertEqual(Cupo.badge(1), "Falta 1")
    }
}
