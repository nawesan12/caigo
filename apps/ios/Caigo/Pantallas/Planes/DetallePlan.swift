import SwiftUI
import Nucleo

struct DetallePlan: View {
    var id: String?
    var slug: String?
    init(id: String) { self.id = id }
    init(slug: String) { self.slug = slug }
    var body: some View { Pendiente(titulo: "Plan") }
}
