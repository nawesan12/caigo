import SwiftUI
import Nucleo

struct VistaPrivados: View { var body: some View { Pendiente(titulo: "Mensajes") } }

struct VistaPrivado: View { var conId: String; var nombre: String; var body: some View { Pendiente(titulo: nombre) } }
