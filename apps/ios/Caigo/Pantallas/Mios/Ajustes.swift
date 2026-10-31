import SwiftUI
import Nucleo

// Pantallas que el handoff no dibuja (ajustes de avisos, bloqueos, actualizar la app): se arman con
// las piezas de Mis planes y Perfil (Encabezado, MiosGrupo, Interruptor, MiosVacio, Cta).

/// Ajustes de avisos push (contrato §3): planes cerca y planes de tu gente (PATCH /v1/yo `avisos`).
struct AjustesDeAvisos: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var avisos: AjustesAvisos?
    @State private var guardando = false

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                Encabezado(titulo: "Avisos") { ir.atras() }.padding(.horizontal, 16).padding(.top, 6)
                ScrollView(showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 14) {
                        if let a = avisos {
                            MiosGrupo {
                                fila("Planes cerca", "Cuando alguien arma algo a menos de 5 km que arranca en un rato.",
                                     Binding(get: { a.planesCerca }, set: { guardar(\.planesCerca, $0) }), ultima: false)
                                fila("De tu gente", "Cuando alguien de Mi gente publica un plan.",
                                     Binding(get: { a.tuGente }, set: { guardar(\.tuGente, $0) }), ultima: true)
                            }
                            T("Los avisos de tus planes (solicitudes, chat, recordatorios y cambios) llegan siempre.",
                              12, 400, p.texto2, alto: 1.5)
                        } else {
                            Puntitos(color: p.acento).frame(maxWidth: .infinity).frame(height: 160)
                        }
                    }
                    .padding(.horizontal, 20).padding(.vertical, 18)
                }
            }
        }
        .task {
            if c.yo == nil { await c.recargarYo() }
            avisos = c.yo?.avisos ?? AjustesAvisos()
        }
    }

    private func fila(_ titulo: String, _ sub: String, _ valor: Binding<Bool>, ultima: Bool) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    T(titulo, 14, 800)
                    T(sub, 12, 400, p.texto2, alto: 1.4)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Interruptor(on: valor)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            if !ultima { Rectangle().fill(p.oscuro ? p.separador : C.lavanda).frame(height: 1) }
        }
    }

    private func guardar(_ k: WritableKeyPath<AjustesAvisos, Bool>, _ v: Bool) {
        guard var a = avisos else { return }
        let antes = a
        a[keyPath: k] = v
        avisos = a
        Task {
            var cambios = CambiosPerfil()
            cambios.avisos = a
            do {
                c.yo = try await c.api.actualizarYo(cambios)
            } catch {
                avisos = antes
                c.avisos.error(error)
            }
        }
    }
}

/// Bloqueos (contrato §4): la gente que bloqueaste, con «Desbloquear».
struct VistaBloqueos: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var lista: [Bloqueado]?
    @State private var error: String?
    @State private var sacando: String?

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                Encabezado(titulo: "Bloqueos y reportes") { ir.atras() }.padding(.horizontal, 16).padding(.top, 6)
                Group {
                    if let lista {
                        if lista.isEmpty {
                            MiosVacio(titulo: "No bloqueaste a nadie",
                                      cuerpo: "Si alguien te incomoda, bloquealo o reportalo desde su perfil o el chat. Lo revisa una persona del equipo, no un bot.",
                                      cta: "Volver") { ir.atras() }
                                .padding(28)
                        } else {
                            ScrollView(showsIndicators: false) {
                                VStack(alignment: .leading, spacing: 12) {
                                    MiosGrupo {
                                        ForEach(Array(lista.enumerated()), id: \.element.id) { i, b in fila(b, ultima: i == lista.count - 1) }
                                    }
                                    T("No ven tus planes ni tu perfil, y vos no ves los suyos.", 12, 400, p.texto2, alto: 1.5)
                                }
                                .padding(.horizontal, 20).padding(.vertical, 18)
                            }
                        }
                    } else if let error {
                        MiosVacio(titulo: "No pudimos cargar tus bloqueos", cuerpo: error, cta: "Probar de nuevo") { Task { await cargar() } }
                            .padding(28)
                    } else {
                        Puntitos(color: p.acento)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task { await cargar() }
    }

    private func cargar() async {
        do { lista = try await c.api.bloqueos().bloqueos; error = nil } catch { self.error = mensajeDe(error) }
    }

    private func fila(_ b: Bloqueado, ultima: Bool) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Avatar(nombre: b.nombre, foto: b.foto, tam: 40, borde: 0, tamTexto: 16)
                T(b.nombre.isEmpty ? "Alguien" : b.nombre, 15, 700, unRenglon: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    guard sacando == nil else { return }
                    sacando = b.id
                    Task {
                        do {
                            try await c.api.desbloquear(b.id)
                            lista?.removeAll { $0.id == b.id }
                            c.avisos.ok("Listo, desbloqueaste a \(b.nombre.isEmpty ? "esa persona" : miosPrimer(b.nombre)).")
                        } catch {
                            c.avisos.error(error)
                        }
                        sacando = nil
                    }
                } label: {
                    Group {
                        if sacando == b.id { Puntitos(color: p.acento) } else { T("Desbloquear", 13, 800, p.oscuro ? C.lima : C.violetaTexto) }
                    }
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .gel(13, Gels.chip(p))
                }
                .buttonStyle(EstiloToque())
            }
            .padding(.horizontal, 16).padding(.vertical, 12)
            if !ultima { Rectangle().fill(p.oscuro ? p.separador : C.lavanda).frame(height: 1) }
        }
    }
}

/// GET /v1/version dice que este build ya no anda: a pantalla completa, sin seguir.
struct ActualizarApp: View {
    var body: some View {
        Pantalla {
            VStack(spacing: 26) {
                Spacer()
                Image("CaigoIcono").resizable().frame(width: 120, height: 120)
                MensajeCentro(titulo: "Hay una versión nueva",
                              cuerpo: "Esta versión de Caigo ya no anda. Actualizala para seguir sumándote a planes.")
                Spacer()
                Cta(texto: "Actualizar en la App Store") { Sistema.abrir("https://apps.apple.com/app/id6829274265") }
            }
            .padding(.horizontal, 28).padding(.bottom, 28)
        }
    }
}
