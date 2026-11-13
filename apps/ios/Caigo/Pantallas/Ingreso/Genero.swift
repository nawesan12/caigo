import SwiftUI
import Nucleo

/// Género autodeclarado (contrato §1): chips Mujer / Varón / No binario. No se muestra a nadie; sólo
/// sirve para los planes «Solo mujeres». La diversidad no se pregunta acá ni se guarda: se confirma al
/// sumarse a un plan «Solo diversidades» (HojaDiversidad, AAIP 1.2).
/// No está en el handoff: se arma con los chips del alta.
struct ElegirGenero: View {
    @Binding var genero: String
    @Environment(\.paleta) private var p

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 8) {
                ForEach(Generos.todos, id: \.0) { g in
                    Chip(texto: g.1, on: genero == g.0) { genero = genero == g.0 ? "" : g.0 }
                }
            }
            T("No se lo mostramos a nadie. Sólo sirve para sumarte a planes de mujeres.", 12, 400, p.texto2, alto: 1.4)
        }
    }
}

/// Hoja que aparece cuando la API responde 422 `falta_genero` (plan «Solo mujeres»): lo pregunta, lo
/// guarda con PATCH /v1/yo y vuelve a intentar lo que se estaba haciendo (`reintentar`).
struct HojaGenero: View {
    var abierta: Bool
    var cerrar: () -> Void
    var reintentar: () -> Void
    @Environment(Contenedor.self) private var c
    @State private var genero = ""
    @State private var guardando = false

    var body: some View {
        MiosHoja(abierta: abierta, titulo: "Antes de sumarte", cerrar: { if !guardando { cerrar() } }) {
            T("Este plan es sólo para mujeres. Contanos con qué género te identificás.", 15, 400, alto: 1.45)
            ElegirGenero(genero: $genero)
            Cta(texto: "Guardar y seguir", habilitado: !genero.isEmpty, cargando: guardando) {
                guardando = true
                Task {
                    var cambios = CambiosPerfil()
                    cambios.genero = genero
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
            if a, let y = c.yo { genero = y.genero }
        }
    }
}

/// Hoja que aparece cuando la API responde 422 `confirmar_diversidad` (plan «Solo diversidades»): la
/// persona lo confirma (el interruptor arranca apagado) y se reintenta el pedido con
/// `diversidad: true` (`confirmar`). No se guarda en ningún lado.
struct HojaDiversidad: View {
    var abierta: Bool
    var cerrar: () -> Void
    var confirmar: () -> Void
    @Environment(\.paleta) private var p
    @State private var soy = false

    var body: some View {
        MiosHoja(abierta: abierta, titulo: "Antes de sumarte", cerrar: cerrar) {
            T("Este plan es sólo para la diversidad LGBTIQ+.", 15, 400, alto: 1.45)
            HStack(spacing: 12) {
                T("Soy parte de la diversidad LGBTIQ+", 14, 600)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Interruptor(on: $soy)
            }
            T("No lo guardamos: sólo vale para sumarte a este plan.", 12, 400, p.texto2, alto: 1.4)
            Cta(texto: "Sumarme", habilitado: soy) {
                cerrar()
                confirmar()
            }
        }
        .onChange(of: abierta, initial: true) { _, a in if a { soy = false } }
    }
}
