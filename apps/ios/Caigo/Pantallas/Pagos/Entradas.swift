import SwiftUI
import Nucleo

struct Checkout: View { var planId: String; var cantidad: Int; var body: some View { Pendiente(titulo: "Entradas") } }

struct VistaEntrada: View { var entradaId: String; var planId: String; var body: some View { Pendiente(titulo: "Tu entrada") } }

struct QrEntrada: View { var entradaId: String; var body: some View { Pendiente(titulo: "QR de entrada") } }
