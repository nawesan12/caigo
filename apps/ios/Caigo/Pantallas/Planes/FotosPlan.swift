import SwiftUI
import PhotosUI
import Nucleo

/*
 * Fotos del plan (informe del 18/11, B4): las suben y las ven sólo quienes fueron; los links vencen a la
 * hora. Sin pantalla en el handoff: el encabezado de Solicitudes, la grilla con el radio de las tarjetas,
 * el Cta lima y la hoja que sube para cada foto. Igual que FotosPlan.kt en Android.
 */
struct FotosDelPlan: View {
    var planId: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var plan: Plan?
    @State private var fotos: [FotoPlan]?
    @State private var cerrado: String?
    @State private var abierta: FotoPlan?
    @State private var eleccion: PhotosPickerItem?
    @State private var subiendo = false

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                Encabezado(titulo: "Fotos del plan", sub: plan.map { "\($0.titulo) · \(Fecha.cuando($0.empiezaEn))" }) { ir.atras() }
                    .padding(.horizontal, 16).padding(.top, 6)
                if let cerrado {
                    MiosVacio(titulo: "Sólo para quienes fueron", cuerpo: cerrado, cta: "Volver") { ir.atras() }.padding(28)
                } else if let fotos {
                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 14) {
                            T("Sólo las ven quienes fueron. Se borran a los 6\u{00A0}meses.", 13, 400, p.texto2)
                            if fotos.isEmpty {
                                T("Todavía no hay fotos. Subí la primera.", 15, 600, p.texto2).padding(.vertical, 24)
                            } else {
                                LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 3), spacing: 6) {
                                    ForEach(fotos) { f in
                                        Foto(url: f.url)
                                            .aspectRatio(1, contentMode: .fill)
                                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                                            .onTapGesture { withAnimation { abierta = f } }
                                            .accessibilityLabel("Foto de \(miosPrimer(f.autor.nombre))")
                                    }
                                }
                            }
                            PhotosPicker(selection: $eleccion, matching: .images) {
                                Group {
                                    if subiendo { Puntitos(color: C.carbon) } else { T("Subir una foto", 17, 800, C.carbon) }
                                }
                                .frame(maxWidth: .infinity).frame(height: 56)
                                .gel(20, Gels.limaChico)
                            }
                            .disabled(subiendo)
                        }
                        .padding(.horizontal, 20).padding(.vertical, 18)
                    }
                } else {
                    Puntitos(color: p.acento).frame(maxHeight: .infinity)
                }
            }
            MiosHoja(abierta: abierta != nil, titulo: abierta.map { "De \(miosPrimer($0.autor.nombre))" } ?? "Foto",
                     cerrar: { withAnimation { abierta = nil } }) {
                if let f = abierta {
                    Foto(url: f.url).aspectRatio(1, contentMode: .fit)
                        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                    T(f.mia ? "Borrar mi foto" : "Reportar esta foto", 14, 700, p.oscuro ? C.lima : C.violetaTexto)
                        .frame(maxWidth: .infinity)
                        .tocable { accion(f) }
                }
            }
        }
        .task { await cargar() }
        .onChange(of: eleccion) { _, item in if let item { subir(item) } }
    }

    private func cargar() async {
        plan = try? await c.api.plan(planId)
        do {
            fotos = try await c.api.fotosPlan(planId).fotos
            cerrado = nil
        } catch let e as ErrorApi where e.estado == 404 {
            cerrado = e.detalle
        } catch {
            c.avisos.error(error)
            fotos = []
        }
    }

    private func subir(_ item: PhotosPickerItem) {
        subiendo = true
        Task {
            do {
                guard let d = try await item.loadTransferable(type: Data.self), let jpeg = miosJpeg(d) else {
                    throw ErrorApi(estado: 0, problema: Problema(detail: "No pudimos leer esa foto. Probá con otra."))
                }
                _ = try await c.api.subirFotoPlan(planId, jpeg: jpeg)
                c.avisos.ok("Foto subida.")
                await cargar()
            } catch {
                c.avisos.error(error)
            }
            eleccion = nil
            subiendo = false
        }
    }

    private func accion(_ f: FotoPlan) {
        Task {
            do {
                if f.mia {
                    try await c.api.borrarFotoPlan(planId, foto: f.id)
                    c.avisos.ok("Foto borrada.")
                } else {
                    try await c.api.reportarFotoPlan(planId, foto: f.id)
                    c.avisos.ok("Gracias. La revisamos.")
                }
                withAnimation { abierta = nil }
                await cargar()
            } catch { c.avisos.error(error) }
        }
    }
}
