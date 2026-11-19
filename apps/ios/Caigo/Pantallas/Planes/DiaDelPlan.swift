import SwiftUI
import Nucleo

/*
 * El día del plan, la víspera y después (informe del 18/11). Se arma con piezas del handoff: el contador
 * oscuro del detalle (05), los Cta gel lima y blanco, la tarjeta neutra con filas de «Herramientas para
 * salir tranqui» y la hoja que sube (MiosHoja). Igual que DiaDelPlan.kt en Android.
 */

/// «Hoy»: cuenta regresiva, Cómo llegar, «Llego en 10», Llegué y el escudo «¿Todo bien?».
struct DiaTarjeta: View {
    var p: Plan
    var todoBien: () -> Void
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var ahora = Date()
    @State private var avisando = false

    var body: some View {
        let llegaron = p.van.filter { $0.llego && $0.id != c.usuarioId }
        let llegue = p.mio?.llegue == true
        VStack(spacing: 12) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    T(Dia.cuentaRegresiva(p.empiezaEn, ahora: ahora), 34, 800, C.lima, alto: 1, espaciado: -1.2, unRenglon: true)
                        // El dato principal no se corta con letra grande: crece hasta un tope.
                        .dynamicTypeSize(...DynamicTypeSize.accessibility2)
                    T(llegaron.isEmpty ? Cupo.somos(p.somos, p.total)
                        : "\(miosNombres(llegaron.map { miosPrimer($0.nombre) })) ya \(llegaron.count == 1 ? "llegó" : "llegaron")",
                      14, 600, C.blancoCalido)
                }
                Spacer(minLength: 8)
                Avatares(personas: p.van.map { ($0.nombre, $0.foto) }, total: p.somos, max: 4, tam: 30)
            }
            HStack(spacing: 10) {
                let destino = p.direccion.map { ($0.lat, $0.lng) } ?? (p.lat, p.lng)
                Cta(texto: "Cómo llegar", gel: Gels.blanco, alto: 48, radio: 16, tam: 15, icono: I.pin, iconoTrazo: 2.2, iconoTam: 18) {
                    if let u = Dia.comoLlegar(lat: destino.0, lng: destino.1) { Sistema.abrir(u.absoluteString) }
                }
                Cta(texto: "Llego en 10", gel: Gels.blanco, alto: 48, radio: 16, tam: 15, icono: I.relojCirculo, iconoTrazo: 2.2, iconoTam: 18,
                    cargando: avisando) { llegoEn10() }
            }
            if llegue {
                HStack(spacing: 8) {
                    Icono(I.check, 18, C.carbon, trazo: 3.4)
                    T("Llegaste", 17, 800, C.carbon)
                }
                .frame(maxWidth: .infinity).frame(height: 56)
                .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(C.lima))
            } else {
                // Antes de la media hora previa, la API no deja: el botón dice desde cuándo (y «Cómo llegar» queda arriba).
                let abre = Dia.puedeLlegar(p)
                Cta(texto: abre ? "Llegué" : Dia.llegarDesde(p), alto: 56, color: C.carbon, icono: abre ? I.check : nil, iconoTrazo: 3.4, iconoTam: 20,
                    habilitado: abre) { ir.checkin(p.id) }
            }
            Button(action: todoBien) {
                HStack(spacing: 6) {
                    Icono(I.escudo, 18, C.lima, trazo: 2.2)
                    T("¿Todo bien?", 15, 700, C.blancoCalido)
                    if p.mio?.todoBien == true {
                        T("Activo", 11, 800, C.carbon).padding(.horizontal, 8).padding(.vertical, 2)
                            .background(RoundedRectangle(cornerRadius: 8, style: .continuous).fill(C.lima))
                    }
                }
                .padding(4)
            }
            .buttonStyle(EstiloPlano())
        }
        .padding(16)
        .gel(24, miosOscura)
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                ahora = Date()
            }
        }
    }

    /// «Llego en 10»: un mensaje en el chat del plan (pasa por la moderación como cualquier otro).
    private func llegoEn10() {
        guard !avisando else { return }
        avisando = true
        Task {
            do {
                _ = try await c.api.escribir(p.id, "Llego en 10")
                c.avisos.ok("Les avisamos en el chat.")
            } catch { c.avisos.error(error) }
            avisando = false
        }
    }
}

/// El plan de hoy, fijo arriba de Ahora y de Mis planes (6.8 del informe): la cuenta regresiva y las dos
/// cosas del día, «Llegué» y «¿Todo bien?», a un toque. El contador oscuro de DiaTarjeta en chico; tocar
/// arriba abre el plan. Igual que TarjetaHoy.svelte y HoyTarjeta en DiaDelPlan.kt.
struct HoyTarjeta: View {
    var p: Plan
    var todoBien: () -> Void
    @Environment(Ir.self) private var ir
    @State private var ahora = Date()

    var body: some View {
        VStack(spacing: 12) {
            Button { ir.plan(p.id) } label: {
                HStack(alignment: .center, spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        T("HOY", 12, 800, C.lima, espaciado: 0.6, unRenglon: true)
                        T(p.titulo, 16, 700, C.blancoCalido, unRenglon: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    T(Dia.cuentaRegresiva(p.empiezaEn, ahora: ahora), 22, 800, C.lima, espaciado: -0.6, unRenglon: true)
                        .dynamicTypeSize(...DynamicTypeSize.accessibility2)
                }
            }
            .buttonStyle(EstiloPlano())
            .accessibilityLabel("Hoy: \(p.titulo), \(Dia.cuentaRegresiva(p.empiezaEn, ahora: ahora))")
            HStack(spacing: 10) {
                if p.mio?.llegue == true {
                    HStack(spacing: 6) {
                        Icono(I.check, 18, C.carbon, trazo: 3.4)
                        T("Llegaste", 15, 800, C.carbon)
                    }
                    .frame(maxWidth: .infinity).frame(minHeight: 48)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(C.lima))
                } else {
                    let abre = Dia.puedeLlegar(p)
                    Cta(texto: abre ? "Llegué" : Dia.llegarDesde(p), alto: 48, radio: 16, tam: abre ? 15 : 13, color: C.carbon,
                        icono: abre ? I.check : nil, iconoTrazo: 3.4, iconoTam: 18, habilitado: abre) {
                        ir.checkin(p.id)
                    }
                }
                Cta(texto: p.mio?.todoBien == true ? "¿Todo bien? · Activo" : "¿Todo bien?", gel: Gels.blanco, alto: 48, radio: 16, tam: 15,
                    color: C.carbon, icono: I.escudo, iconoTrazo: 2.2, iconoTam: 18, accion: todoBien)
            }
        }
        .padding(14)
        .gel(24, miosOscura)
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 30_000_000_000)
                ahora = Date()
            }
        }
    }
}

/// El plan de hoy de quien mira (el próximo en el que está adentro y «en el día»), para HoyTarjeta.
@MainActor
func planDeHoy(_ c: Contenedor) async -> Plan? {
    let o = (try? await c.api.mios("organizo").planes) ?? []
    let m = (try? await c.api.mios("me_sume").planes) ?? []
    let todos = o + m
    return todos.filter { ($0.miEstado == "aceptada" || $0.miEstado == "organizador") && Dia.enElDia($0) }
        .sorted { $0.empiezaEn < $1.empiezaEn }.first
}

/// «¿Seguís yendo?» la víspera: Voy confirma; Me bajo libera el lugar (con la confirmación de siempre).
struct VisperaTarjeta: View {
    var p: Plan
    var bajarme: () -> Void
    var recargar: () -> Void
    @Environment(Contenedor.self) private var c
    @Environment(\.paleta) private var pal
    @State private var enviando = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            T("\(Fecha.cuando(p.empiezaEn)). ¿Seguís\u{00A0}yendo?", 18, 700, pal.texto, alto: 1.2)
            HStack(spacing: 10) {
                Cta(texto: "Voy", alto: 50, radio: 18, tam: 16, color: C.carbon, cargando: enviando) {
                    enviando = true
                    Task {
                        do {
                            try await c.api.confirmo(p.id)
                            c.avisos.ok("¡Genial! Te esperan.")
                            recargar()
                        } catch { c.avisos.error(error) }
                        enviando = false
                    }
                }
                Cta(texto: "Me bajo", gel: Gels.blanco, alto: 50, radio: 18, tam: 16, accion: bajarme)
            }
        }
        .padding(16)
        .gel(22, pal.oscuro ? Gels.tarjetaOscura : Gels.neutra)
    }
}

/// Después del plan: «¿Cómo estuvo?» (o «¿Repetimos?» si organizó) y las fotos del plan.
struct DespuesTarjeta: View {
    var p: Plan
    @Environment(Ir.self) private var ir

    var body: some View {
        MiosGrupo {
            MiosFila(icono: I.estrella, titulo: p.esMio ? "¿Repetimos?" : "¿Cómo estuvo?",
                     sub: p.mio?.carita != nil ? "Ya nos contaste. Gracias." : "Una carita y tu gente") { ir.repetimos(p.id) }
            MiosFila(icono: I.gente, titulo: (p.mio?.fotos ?? 0) > 0 ? "Fotos del plan · \(p.mio!.fotos)" : "Fotos del plan",
                     sub: "Sólo las ven quienes fueron", ultima: true) { ir.fotosPlan(p.id) }
        }
    }
}

/// Anota a la lista de espera. Un plan «Solo mujeres» / «Solo diversidades» pide confirmarlo (como al sumarse).
@MainActor
func esperaAnotar(_ c: Contenedor, _ p: Plan, confirma: Confirma = Confirma(), pedirConfirmacion: (String) -> Void) async -> Bool {
    do {
        _ = try await c.api.esperar(p.id, confirma: confirma)
        c.avisos.ok("Te anotamos. Si se libera un lugar, te avisamos.")
        return true
    } catch let e as ErrorApi {
        if let pide = Generos.pide(e.codigo) { pedirConfirmacion(pide) } else { c.avisos.error(e.detalle) }
        return false
    } catch {
        c.avisos.error(error)
        return false
    }
}

/// La lista de espera, abajo del detalle: anotarse en un plan completo, el lugar en la fila o «Se liberó un lugar».
struct EsperaAbajo: View {
    var p: Plan
    var entrar: () -> Void
    var recargar: () -> Void
    var enviandoEntrar: Bool
    var pedirConfirmacion: (String) -> Void
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var pal
    @State private var enviando = false

    var body: some View {
        if let e = p.mio?.espera {
            if e.estado == "avisado" {
                // Con la hora de verdad (venceEn), refrescada: si venció, no se ofrece más «Entrar».
                TimelineView(.periodic(from: .now, by: 30)) { t in
                    if let guardado = Dia.lugarGuardado(e.venceEn, ahora: t.date) {
                        VStack(spacing: 12) {
                            T("Se liberó un lugar. \(guardado.replacingOccurrences(of: " ", with: "\u{00A0}")).", 14, 700, C.carbon, alinear: .center)
                                .padding(10).frame(maxWidth: .infinity)
                                .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(C.lima))
                            Cta(texto: "Entrar al plan", alto: 58, color: C.carbon, cargando: enviandoEntrar, accion: entrar)
                        }
                    } else {
                        T("Se venció tu lugar. Si se libera otro, te avisamos.", 14, 700, pal.texto2, alinear: .center)
                            .padding(10).frame(maxWidth: .infinity)
                            .task { recargar() }
                    }
                }
            } else {
                T("En la lista · \(e.posicion)°", 17, 800, C.violetaTexto, alinear: .center)
                    .frame(maxWidth: .infinity).frame(height: 58)
                    .gel(20, .liso(C.tinte1), [interna(3, 6, rgba(116, 86, 241, 0.2))])
                T("Salir de la lista", 14, 700, pal.oscuro ? C.lima : C.violetaTexto).tocable { salir() }
            }
        } else {
            T("Completo", 17, 800, C.carbon).frame(maxWidth: .infinity).frame(height: 50).gel(20, Gels.limaBadge)
            CtaVioleta("Anotame en la lista", cargando: enviando) {
                guard !enviando else { return }
                // Sin DNI se mira: anotarse pide verificarse, con el pedido de «sumarte» (no el genérico).
                guard c.conDni(ir, "sumarte", plan: p.id) else { return }
                enviando = true
                Task {
                    if await esperaAnotar(c, p, pedirConfirmacion: pedirConfirmacion) { recargar() }
                    enviando = false
                }
            }
        }
    }

    private func salir() {
        Task {
            do {
                try await c.api.dejarDeEsperar(p.id)
                recargar()
            } catch { c.avisos.error(error) }
        }
    }
}

/// «¿Todo bien?»: avisarle a un contacto si no hay check-in, compartir la ubicación, «Me quiero ir»,
/// 911, Línea 144 y reportar. Dice a quién le llega el aviso sin prometer de más: con un mail le escribimos
/// al contacto; con un teléfono (no hay SMS), te avisamos a vos para que le escribas.
struct HojaTodoBien: View {
    var p: Plan
    var abierta: Bool
    var cerrar: () -> Void
    var reportar: () -> Void
    var recargar: () -> Void
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var pal
    @State private var estado: TodoBien?
    @State private var cargando = false
    @State private var fallo = false
    @State private var nombreNuevo = ""
    @State private var contactoNuevo = ""
    @State private var guardando = false

    /// Mientras carga (o si falló), los contactos del perfil: nadie con contactos ve «no tenés a quién avisar».
    private var contactos: [Contacto] { estado?.contactos ?? (c.yo?.contactosConfianza ?? []) }

    var body: some View {
        MiosHoja(abierta: abierta, titulo: "¿Todo bien?", cerrar: cerrar) {
            let contacto = estado?.contacto ?? contactos.first
            MiosGrupo {
                if let t = estado, t.activo, let k = t.contacto {
                    MiosFila(icono: I.escudo, titulo: k.leEscribimos ? "Le escribimos a \(k.mostrar)" : "Te avisamos para que le escribas a \(k.mostrar)",
                             sub: t.avisado ? "Ya avisamos." : "Si a las \(Fecha.hora(t.avisaEn)) no hiciste check-in. Antes te preguntamos. Tocá para apagarlo.") { apagar() }
                } else if !Dia.puedeActivarTodoBien(p) {
                    EmptyView()
                } else if fallo && estado == nil && contactos.isEmpty {
                    MiosFila(icono: I.escudo, titulo: "No pudimos cargar «¿Todo bien?»", sub: "Probar de nuevo") { Task { await cargar() } }
                } else if !contactos.isEmpty {
                    ForEach(Array(contactos.enumerated()), id: \.offset) { i, k in
                        MiosFila(icono: I.escudo, titulo: k.filaTodoBien,
                                 sub: k.leEscribimos ? "A la media hora, si no hice check-in" : "No mandamos SMS: con un mail, le escribimos nosotros") {
                            activar(i)
                        }
                    }
                } else if !cargando {
                    // Sin contactos: se carga acá mismo y queda activo, sin ir a otra pantalla.
                    VStack(alignment: .leading, spacing: 10) {
                        T("¿A quién avisamos si no llegás?", 15, 700, pal.texto)
                        MiosCampo(valor: $nombreNuevo, placeholder: "Nombre (Mamá, Sofi…)", maximo: 40)
                        MiosCampo(valor: $contactoNuevo, placeholder: "Mail (le escribimos) o teléfono", maximo: 80)
                        Cta(texto: "Activar", alto: 52, radio: 18, tam: 16, color: C.carbon, cargando: guardando) { sumarYActivar() }
                            .disabled(contactoNuevo.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                    .padding(16)
                }
                MiosFila(icono: I.ubicacion, titulo: "Compartir mi ubicación", sub: "Sale de tu teléfono; no la guardamos") {
                    compartirUbicacion(contacto)
                }
                if let k = contacto, k.esTelefono {
                    MiosFila(icono: I.telefono, titulo: "Me quiero ir", sub: "Llamar a \(k.mostrar)") {
                        Sistema.abrir("tel:" + k.contacto.filter { $0.isNumber || $0 == "+" })
                    }
                }
                MiosFila(icono: I.auto, titulo: "Pedir un auto", sub: "Para volver ya", ultima: true) {
                    Sistema.abrir(Dia.pedirAuto.absoluteString)
                }
            }
            HStack(spacing: 10) {
                Cta(texto: "Llamar al 911", gel: Gels.coralBadge, alto: 54, radio: 18, tam: 16, color: C.carbon) { Sistema.abrir("tel:911") }
                CtaVioleta("Línea 144") { Sistema.abrir("tel:144") }
            }
            T("La 144 atiende violencia de género, gratis y las 24\u{00A0}h.", 12, 400, pal.texto2, alinear: .center)
                .frame(maxWidth: .infinity)
            T("Reportar algo del plan", 14, 700, pal.oscuro ? C.lima : C.violetaTexto).frame(maxWidth: .infinity)
                .tocable {
                    cerrar()
                    reportar()
                }
        }
        .task(id: abierta) {
            if abierta { await cargar() }
        }
    }

    private func cargar() async {
        cargando = true
        fallo = false
        do { estado = try await c.api.todoBien(p.id) } catch { fallo = true }
        cargando = false
    }

    /// Sin contactos: lo suma al perfil (al final, sin tocar los demás) y activa «¿Todo bien?» con él.
    private func sumarYActivar() {
        guard !guardando else { return }
        guardando = true
        Task {
            do {
                var cambios = CambiosPerfil()
                let lista = Array(((c.yo?.contactosConfianza ?? []) + [Contacto(nombre: nombreNuevo.trimmingCharacters(in: .whitespaces),
                                                                               contacto: contactoNuevo.trimmingCharacters(in: .whitespaces))]).prefix(3))
                cambios.contactosConfianza = lista
                _ = try await c.api.actualizarYo(cambios)
                _ = await c.recargarYo()
                guardando = false
                activar(lista.count - 1)
            } catch {
                guardando = false
                c.avisos.error(error)
            }
        }
    }

    private func activar(_ i: Int) {
        Task {
            do {
                estado = try await c.api.activarTodoBien(p.id, contacto: i)
                c.avisos.ok(estado?.contacto?.activado ?? "Listo.")
                cerrar()
                recargar()
            } catch { c.avisos.error(error) }
        }
    }

    private func apagar() {
        Task {
            do {
                try await c.api.apagarTodoBien(p.id)
                estado?.activo = false
                c.avisos.ok("Listo, lo apagamos.")
                cerrar()
                recargar()
            } catch { c.avisos.error(error) }
        }
    }

    /// La ubicación actual va del teléfono al contacto: no pasa por Caigo ni se guarda.
    private func compartirUbicacion(_ k: Contacto?) {
        Task {
            guard let u = await c.ubicacion() else {
                c.avisos.error("No pudimos saber dónde estás.")
                return
            }
            let texto = "Estoy en \(p.titulo): \(Dia.linkUbicacion(lat: u.0, lng: u.1))"
            if let k, k.esTelefono, let w = Dia.whatsapp(k.contacto, texto) {
                Sistema.abrir(w.absoluteString)
            } else {
                Sistema.compartir(texto)
            }
        }
    }
}
