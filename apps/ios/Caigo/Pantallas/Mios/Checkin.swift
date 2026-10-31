import SwiftUI
import Nucleo

struct Checkin: View { var planId: String; var body: some View { Pendiente(titulo: "Check-in") } }

struct Repetimos: View { var planId: String; var body: some View { Pendiente(titulo: "¿Repetimos?") } }

struct QrOrganizador: View { var planId: String; var body: some View { Pendiente(titulo: "QR") } }

struct EscanearQr: View { var planId: String; var body: some View { Pendiente(titulo: "Escanear") } }
