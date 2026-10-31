import SwiftUI
import Nucleo

/// Confirmación antes de algo que no tiene vuelta (cancelar un plan, sacar a alguien, cancelar una
/// entrada). Misma hoja que «¿Borrar tu cuenta?» del perfil: texto, la acción en coral y «Volver».
struct HojaConfirmar: View {
    var abierta: Bool
    var titulo: String
    var cuerpo: String
    /// Sin acción, la hoja sólo explica (ej.: entrada sin reembolso) y ofrece «Volver».
    var accion: String?
    var cerrar: () -> Void
    /// Hace lo que hay que hacer; si tira, se muestra el error y la hoja queda abierta.
    var confirmar: () async throws -> Void
    @Environment(Contenedor.self) private var c
    @Environment(\.paleta) private var p
    @State private var ocupado = false

    var body: some View {
        MiosHoja(abierta: abierta, titulo: titulo, cerrar: { if !ocupado { cerrar() } }) {
            T(cuerpo, 15, 400, p.texto2, alto: 1.5)
            MiosGrupo {
                if let accion {
                Button {
                    guard !ocupado else { return }
                    ocupado = true
                    Task {
                        do {
                            try await confirmar()
                            cerrar()
                        } catch {
                            c.avisos.error(error)
                        }
                        ocupado = false
                    }
                } label: {
                    Group {
                        if ocupado { Puntitos(color: C.coral2) } else { T(accion, 15, 700, C.coral2) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 18).padding(.vertical, 16)
                    .contentShape(Rectangle())
                }
                .buttonStyle(EstiloPlano())
                Rectangle().fill(p.oscuro ? p.separador : C.lavanda).frame(height: 1)
                }
                Button { if !ocupado { cerrar() } } label: {
                    T("Volver", 15, 700).frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 18).padding(.vertical, 16).contentShape(Rectangle())
                }
                .buttonStyle(EstiloPlano())
            }
        }
    }
}
