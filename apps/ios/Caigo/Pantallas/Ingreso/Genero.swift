import SwiftUI
import Nucleo

/// Género autodeclarado (contrato §1): chips Mujer / Varón / No binario y la casilla de diversidad.
/// No se muestra a nadie; sólo sirve para los planes «Solo mujeres» y «Solo diversidades».
/// No está en el handoff: se arma con los chips y el interruptor del alta.
struct ElegirGenero: View {
    @Binding var genero: String
    @Binding var diversidad: Bool
    @Environment(\.paleta) private var p

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                ForEach(Generos.todos, id: \.0) { g in
                    Chip(texto: g.1, on: genero == g.0) { genero = genero == g.0 ? "" : g.0 }
                }
            }
            HStack(spacing: 12) {
                T("Me identifico como parte de la diversidad", 14, 600)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Interruptor(on: $diversidad)
            }
            T("No se lo mostramos a nadie. Sólo sirve para sumarte a planes de mujeres o de diversidades.", 12, 400, p.texto2, alto: 1.4)
        }
    }
}

/// Hoja que aparece cuando la API responde 422 `falta_genero`: lo pregunta, lo guarda con
/// PATCH /v1/yo y vuelve a intentar lo que se estaba haciendo (`reintentar`).
struct HojaGenero: View {
    var abierta: Bool
    var cerrar: () -> Void
    var reintentar: () -> Void
    @Environment(Contenedor.self) private var c
    @State private var genero = ""
    @State private var diversidad = false
    @State private var guardando = false

    var body: some View {
        MiosHoja(abierta: abierta, titulo: "Antes de sumarte", cerrar: { if !guardando { cerrar() } }) {
            T("Este plan es sólo para mujeres o diversidades. Contanos cómo te identificás.", 15, 400, alto: 1.45)
            ElegirGenero(genero: $genero, diversidad: $diversidad)
            Cta(texto: "Guardar y seguir", habilitado: !genero.isEmpty || diversidad, cargando: guardando) {
                guardando = true
                Task {
                    var cambios = CambiosPerfil()
                    cambios.genero = genero
                    cambios.diversidad = diversidad
                    do {
                        c.yo = try await c.api.actualizarYo(cambios)
                        cerrar()
                        reintentar()
                    } catch {
                        c.avisos.error(error)
                    }
                    guardando = false
                }
            }
        }
        .onChange(of: abierta, initial: true) { _, a in
            if a, let y = c.yo { genero = y.genero; diversidad = y.diversidad }
        }
    }
}
