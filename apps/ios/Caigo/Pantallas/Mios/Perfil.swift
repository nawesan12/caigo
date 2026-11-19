import SwiftUI
import PhotosUI
import Nucleo

/// Lo que buscás (intenciones de la API → los nombres cortos del prototipo).
private let intencionesCortas: [(String, String)] = [("planes", "Planes"), ("gente", "Gente nueva"), ("completar", "Organizar"), ("citas", "Citas")]

/// Intereses del onboarding (LIKES del prototipo).
private let intereses = [
    "Fútbol 5", "Pádel", "Truco", "Previas", "After beach", "Surf", "Cine", "Asado", "Running",
    "Juegos de mesa", "Música en vivo", "Mate en la playa",
]

/// Las preguntas de los perfiles del prototipo.
private let preguntas = [
    "Mi domingo ideal", "Me sumo seguro si…", "Te gano en…", "Un plan que nunca falla",
    "Lo más espontáneo que hice", "Mi plan ideal", "Lo que más me gusta de Mar del Plata",
    "Mi talento oculto", "Estoy buscando", "Una cita ideal",
]

/// Tarjeta violeta de Caigo Plus del perfil del prototipo.
private let gelPlus = Gel(
    fondo: .radial(cx: 1, cy: 0, rx: 1.2, ry: 1, Fondo.paradas((0, Color(hex: 0xA38FFF)), (0.5, C.violeta), (1, Color(hex: 0x5A3DD8)))),
    sombras: [interna(2, 0, rgba(255, 255, 255, 0.3)), externa(4, 0, C.violetaBase), externa(16, 28, rgba(116, 86, 241, 0.7), spread: -12)],
    grano: true)

private let gelVerificacion = Gel(fondo: .liso(C.blanco), sombras: [
    interna(1, 0, C.blanco), externa(3, 0, C.borde1), externa(12, 24, rgba(80, 60, 160, 0.25), spread: -12),
])

/// Fila de chips que salta de renglón (el `flex-wrap` del handoff).
struct MiosFlujo: Layout {
    var espacio: CGFloat = 8
    var renglon: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let ancho = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, alto: CGFloat = 0, maxX: CGFloat = 0
        for s in subviews {
            let t = s.sizeThatFits(.unspecified)
            if x > 0 && x + t.width > ancho { x = 0; y += alto + renglon; alto = 0 }
            x += t.width + espacio
            maxX = max(maxX, x - espacio)
            alto = max(alto, t.height)
        }
        return CGSize(width: proposal.width ?? maxX, height: y + alto)
    }

    func placeSubviews(in b: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = b.minX, y = b.minY, alto: CGFloat = 0
        for s in subviews {
            let t = s.sizeThatFits(.unspecified)
            if x > b.minX && x + t.width > b.maxX { x = b.minX; y += alto + renglon; alto = 0 }
            s.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(t))
            x += t.width + espacio
            alto = max(alto, t.height)
        }
    }
}

/// JPEG de una foto elegida, achicada a `maximo` px del lado largo.
func miosJpeg(_ datos: Data, maximo: CGFloat = 1600, calidad: CGFloat = 0.86) -> Data? {
    #if os(iOS)
    guard let i = UIImage(data: datos) else { return nil }
    let k = min(1, maximo / max(i.size.width, i.size.height))
    let tam = CGSize(width: i.size.width * k, height: i.size.height * k)
    let f = UIGraphicsImageRendererFormat.default()
    f.scale = 1
    let chica = UIGraphicsImageRenderer(size: tam, format: f).image { _ in i.draw(in: CGRect(origin: .zero, size: tam)) }
    return chica.jpegData(compressionQuality: calidad)
    #else
    guard let rep = NSBitmapImageRep(data: datos) else { return nil }
    return rep.representation(using: .jpeg, properties: [.compressionFactor: calidad])
    #endif
}

/// 11 · Perfil propio (mockup 11 + perfil del prototipo: verificación, Plus, lo que buscás).
struct MiPerfil: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var opciones = false
    @State private var borrando = false
    @State private var borrandoAhora = false
    /// El mail que escribe la persona para confirmar la baja.
    @State private var mailBaja = ""
    @State private var fallo = false
    @State private var gente: [PersonaCorta] = []
    @State private var verif: EstadoVerificacion?
    /// «Traé a alguien» abierta, lo que trae la API y el código de quien me invitó.
    @State private var trayendo = false
    @State private var referidos: Referidos?
    @State private var codigo = ""
    @State private var usandoCodigo = false

    var body: some View {
        ZStack(alignment: .topTrailing) {
            if let y = c.yo {
                ScrollView(showsIndicators: false) { contenido(y) }
                    .refreshable { await cargar() }
            } else {
                Group {
                    if fallo {
                        MiosVacio(titulo: "No pudimos cargar tu perfil", cuerpo: "No hay conexión. Revisá internet y probá de nuevo.", cta: "Probar de nuevo") {
                            Task { await cargar() }
                        }
                    } else {
                        Puntitos(color: p.acento)
                    }
                }
                .padding(28)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            hojaCuenta
            hojaBorrar
            hojaTraer
        }
        .ocultarTabBar(opciones || borrando || trayendo, ir)
        .task { await cargar() }
        .task(id: trayendo) {
            guard trayendo else { return }
            do { referidos = try await c.api.referidos() } catch {
                c.avisos.error(error)
                trayendo = false
            }
        }
    }

    private func cargar() async {
        fallo = await c.recargarYo() == nil
        // En secuencia, sin async let: en otra app de Grupo Vimo, async let en la carga cerró la app en Release.
        gente = (try? await c.api.miGente().gente) ?? gente
        verif = (try? await c.api.verificacion()) ?? verif
        _ = await c.recargarPlus()
    }

    @ViewBuilder
    private func contenido(_ y: Perfil) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            cabecera(y)
            verificacion(y)
            tarjetaPlus
            // Sin DNI, lo que falta va primero (verificacion) y lo de ir a planes, después de verificarse.
            if let l = y.logros, y.verificada {
                cabeceraLogros(y, l)
            } else {
                HStack(spacing: 10) { asistenciaReal(y.asistencia).layoutPriority(1.2); planes(y) }
            }
            if y.verificada {
                MiosGrupo {
                    MiosFila(icono: I.gente, titulo: "Traé a alguien", ultima: y.logros == nil) { codigo = ""; trayendo = true }
                    if y.logros != nil {
                        let ocultas = c.yo?.logros?.ocultas ?? false
                        // Toda la fila es el interruptor (y se lee como uno, con su nombre).
                        HStack(spacing: 12) {
                            Icono(I.ojoTachado, 20, p.oscuro ? C.lima : C.violeta, trazo: 2)
                            T("Ocultar mis insignias", 14, 800).frame(maxWidth: .infinity, alignment: .leading)
                            Interruptor(on: Binding(get: { ocultas }, set: { ocultarInsignias($0) }))
                                .accessibilityHidden(true)
                        }
                        .padding(.horizontal, 16).padding(.vertical, 12)
                        .contentShape(Rectangle())
                        .onTapGesture { ocultarInsignias(!ocultas) }
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("Ocultar mis insignias")
                        .accessibilityValue(ocultas ? "Sí" : "No")
                        .accessibilityAddTraits(.isToggle)
                        .accessibilityAction { ocultarInsignias(!ocultas) }
                    }
                }
            }
            busca(y)
            if !y.intereses.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    MiosSeccion(texto: "Le copa")
                    MiosFlujo {
                        ForEach(y.intereses, id: \.self) { t in
                            T(t, 13, 600).padding(.horizontal, 12).padding(.vertical, 8).gel(13, Gels.chip(p))
                        }
                    }
                }
            }
            miGente(y)
            // Al final, la acción destructiva (con el texto de peligro de «Cancelar el plan»).
            Button { mailBaja = ""; borrando = true } label: {
                T("Borrar mi cuenta", 15, 700, C.coral2).padding(.horizontal, 18).padding(.vertical, 16)
            }
            .buttonStyle(EstiloPlano())
            .frame(maxWidth: .infinity)
            // Que no quede tapado por el botón de Crear de la barra.
            Spacer().frame(height: 28)
        }
        .padding(.horizontal, 20)
        .padding(.top, 16)
        .padding(.bottom, altoTabBar)
    }

    /// Cabecera: foto 112, nombre con badge y zona.
    private func cabecera(_ y: Perfil) -> some View {
        VStack(spacing: 10) {
            // La foto lleva a editar el perfil y las fotos, con el lápiz a la vista (como en Tinder).
            Button { ir.editarPerfil() } label: {
                Avatar(nombre: y.nombre, foto: y.fotos.first, tam: 112, borde: 4, colorBorde: C.blanco, tamTexto: 40)
                    .background(Circle().fill(Color.clear).gel(56, nil, [externa(14, 26, rgba(80, 60, 160, 0.45), spread: -10)]))
                    .overlay(alignment: .bottomTrailing) {
                        Icono(I.lapiz, 16, C.blanco, trazo: 2.4)
                            .frame(width: 36, height: 36)
                            .background(Circle().fill(C.violeta))
                            .overlay(Circle().strokeBorder(C.blanco, lineWidth: 3))
                    }
            }
            .buttonStyle(EstiloToque(baja: 2))
            .accessibilityLabel("Editar perfil y fotos")
            HStack(spacing: 8) {
                T([y.nombre.isEmpty ? nil : y.nombre, y.edad.map(String.init)].compactMap { $0 }.joined(separator: ", "), 28, 700, alto: 1)
                if y.verificada { MiosVerificado() }
            }
            let estado: String? = y.verificada ? "Verificada con DNI" : (y.selfie ? "Selfie verificada · falta DNI" : nil)
            let zona = [y.zona, y.ciudad].filter { !$0.isEmpty }.joined(separator: ", ")
            T([zona.isEmpty ? nil : zona, estado].compactMap { $0 }.joined(separator: " · "), 14, 400, p.texto2, alinear: .center)
            // Tres círculos, como en Tinder: Editar perfil al medio y más grande (la acción principal), Ajustes
            // y Mis pases a los costados. Un solo lugar para cada cosa (no hay engranaje arriba).
            HStack(alignment: .top, spacing: 30) {
                accionRapida(I.ajustes, "Ajustes", principal: false) { opciones = true }
                accionRapida(I.lapiz, "Editar perfil", principal: true) { ir.editarPerfil() }
                accionRapida(I.qr, "Mis pases", principal: false) { ir.pases() }
            }
            .padding(.top, 8)
        }
        .frame(maxWidth: .infinity)
    }

    private func accionRapida(_ icono: String, _ titulo: String, principal: Bool, accion: @escaping () -> Void) -> some View {
        let tam: CGFloat = principal ? 64 : 48
        return Button(action: accion) {
            VStack(spacing: 7) {
                Icono(icono, principal ? 26 : 20, principal ? C.carbon : (p.oscuro ? C.lima : C.violeta), trazo: principal ? 2.6 : 2.2)
                    .frame(width: tam, height: tam)
                    .gel(tam / 2, principal ? Gels.lima : Gels.chip(p))
                T(titulo, 12, principal ? 800 : 700, principal ? p.texto : p.texto2, unRenglon: true)
            }
            .frame(minWidth: 72)
        }
        .buttonStyle(EstiloToque(baja: 2))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(titulo)
        .accessibilityAddTraits(.isButton)
    }

    /// Verificación y seguridad.
    private func verificacion(_ y: Perfil) -> some View {
        let dni = y.verificada ? "aprobada" : (verif?.dni ?? "ninguna")
        // Sin DNI (ni uno en revisión): la pantalla amable de antes del DNI.
        return Button { if dni == "aprobada" || dni == "pendiente" { ir.verificacion() } else { ir.pedirDni("gente") } } label: {
            HStack(spacing: 14) {
                Icono(I.escudoCheck, 24, C.lima, trazo: 2.4).frame(width: 48, height: 48).gel(17, miosTileVioleta)
                VStack(alignment: .leading, spacing: 2) {
                    T("Verificación y seguridad", 15, 800)
                    T(dni == "aprobada" ? "Tu DNI ya está verificado" : (dni == "pendiente" ? "Ya estamos revisando tu DNI." : "Verificá tu DNI"),
                      12, 600, p.texto2)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if dni == "aprobada" {
                    MiosPill(texto: "Completa", fondo: C.lima, color: C.carbon, tam: 12)
                } else if dni != "pendiente" {
                    MiosPill(texto: "1 paso", fondo: C.coral, color: C.carbon, tam: 12)
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            .gel(24, p.oscuro ? Gels.tarjetaOscura : gelVerificacion)
        }
        .buttonStyle(EstiloToque(baja: 2))
    }

    /// Caigo Plus.
    private var tarjetaPlus: some View {
        let e = c.plus
        var vence: String?
        if let s = e?.vence, let d = Fecha.parse(s) {
            let k = Fecha.calendario.dateComponents([.day, .month], from: d)
            vence = String(format: "%02d/%02d", k.day ?? 0, k.month ?? 0)
        }
        let sub: String
        if e?.activo == true, let v = vence {
            sub = e?.renueva == true ? "Activo · se renueva el \(v)" : "Activo hasta el \(v)"
        } else if e?.activo == true {
            sub = "Activo"
        } else {
            sub = "Sumadas ilimitadas y planes destacados"
        }
        return Button { ir.plus(motivo: "perfil") } label: {
            HStack(spacing: 14) {
                Image("CaigoIcono").resizable().frame(width: 54, height: 54).rotationEffect(.degrees(-8))
                VStack(alignment: .leading, spacing: 2) {
                    T("Caigo Plus", 19, 800, C.blanco, alto: 1.1)
                    T(sub, 12, 600, C.blanco)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if e?.activo == true {
                    Icono(I.check, 16, C.carbon, trazo: 3.4).frame(width: 32, height: 32)
                        .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(C.lima))
                } else {
                    T("Ver", 13, 800, C.carbon).padding(.horizontal, 12).padding(.vertical, 8)
                        .background(RoundedRectangle(cornerRadius: 13, style: .continuous).fill(C.lima))
                }
            }
            .padding(16)
            .gel(24, gelPlus)
        }
        .buttonStyle(EstiloToque())
    }

    /// Cabecera sobria de lo que se gana yendo: nivel y planes que cuentan, la asistencia real, la racha (desde 2
    /// semanas), las insignias en chips chicos y cuánto falta para el siguiente nivel. Sin puntos ni festejos.
    private func cabeceraLogros(_ y: Perfil, _ l: Logros) -> some View {
        let a = y.asistencia
        return VStack(alignment: .leading, spacing: 12) {
            T(TextoLogros.cabecera(l), 15, 800, C.blancoCalido, unRenglon: true)
            HStack(spacing: 14) {
                T(a.de > 0 ? "\(a.fue * 100 / a.de)%" : "—", 40, 800, C.lima, alto: 1)
                VStack(alignment: .leading, spacing: 2) {
                    T("Asistencia real", 13, 600, C.blancoCalido)
                    T(a.de > 0 ? "\(a.fue) de \(a.de) con check-in" : "Todavía sin planes con check-in", 12, 400, C.apagado2)
                }
            }
            if let r = TextoLogros.racha(l) { T(r, 12, 600, C.apagado2) }
            if !l.insignias.isEmpty {
                // Cada insignia con su chip propio y el detalle a la vista (no escondido en un toque largo).
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(l.insignias) { i in
                        HStack(spacing: 10) {
                            ChipInsignia(texto: i.nombre, sobreOscuro: true)
                            T(i.detalle, 12, 400, C.apagado2, unRenglon: true)
                        }
                        .accessibilityElement(children: .combine)
                    }
                }
            }
            if let s = TextoLogros.siguiente(l) { T(s, 12, 400, C.apagado2) }
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .gel(22, miosOscura)
    }

    /// PATCH /v1/yo `ocultarInsignias`: se ve al toque; si la API falla, vuelve.
    private func ocultarInsignias(_ v: Bool) {
        guard var y = c.yo, var l = y.logros else { return }
        let antes = y
        l.ocultas = v
        y.logros = l
        c.yo = y
        Task {
            var cambios = CambiosPerfil()
            cambios.ocultarInsignias = v
            do { c.yo = try await c.api.actualizarYo(cambios) } catch {
                c.yo = antes
                c.avisos.error(error)
            }
        }
    }

    /// «Traé a alguien»: el link para compartir, el premio, cuántos invité y, en los primeros días, el código de
    /// quien me invitó (POST /v1/yo/invitado).
    private var hojaTraer: some View {
        MiosHoja(abierta: trayendo, titulo: "Traé a alguien", cerrar: { trayendo = false }) {
            if let r = referidos {
                HStack(spacing: 10) {
                    T(r.link, 14, 700, unRenglon: true)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 14).frame(height: 52)
                        .gel(16, Gels.chip(p))
                    Cta(texto: "Compartir", gel: Gels.violeta, alto: 52, radio: 16, tam: 15, color: C.blanco) { Sistema.compartir(r.link) }
                        .frame(width: 124)
                }
                T("Cuando vaya a su primer plan, los dos tienen una semana de Plus.", 14, 400, p.texto2, alto: 1.45)
                T("\(r.invitados) \(r.invitados == 1 ? "invitado" : "invitados") · \(r.premiados) ya fueron", 13, 700)
                if r.puedeUsarCodigo {
                    T("¿Te invitó alguien? Poné su código", 14, 700).padding(.top, 4)
                    HStack(spacing: 10) {
                        MiosCampo(valor: $codigo, placeholder: "Código", maximo: 24)
                            .autocorrectionDisabled()
                        Cta(texto: "Listo", alto: 60, radio: 18, tam: 16, color: C.carbon,
                            habilitado: !codigo.trimmingCharacters(in: .whitespaces).isEmpty, cargando: usandoCodigo) { usarCodigo() }
                            .frame(width: 100)
                    }
                }
            } else {
                Puntitos(color: p.acento).frame(maxWidth: .infinity).frame(height: 120)
            }
        }
    }

    private func usarCodigo() {
        let cod = codigo.trimmingCharacters(in: .whitespaces)
        guard !cod.isEmpty, !usandoCodigo else { return }
        usandoCodigo = true
        Task {
            do {
                try await c.api.usarCodigo(cod)
                codigo = ""
                c.avisos.ok("Listo.")
                referidos = (try? await c.api.referidos()) ?? referidos
            } catch {
                c.avisos.error(error)
            }
            usandoCodigo = false
        }
    }

    private func asistenciaReal(_ a: Asist) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            T(a.de > 0 ? "\(a.fue * 100 / a.de)%" : "—", 40, 800, C.lima, alto: 1)
            T("Asistencia real", 13, 600, C.blancoCalido)
            T(a.de > 0 ? "\(a.fue) de \(a.de) con check-in" : "Todavía sin planes con check-in", 12, 400, C.apagado2)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .gel(22, miosOscura)
    }

    private func planes(_ y: Perfil) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            T("\(y.planes)", 40, 800, p.oscuro ? C.lima : C.violetaTexto, alto: 1)
            T("Planes", 13, 600)
            T(y.organizados == 1 ? "1 organizado" : "\(y.organizados) organizados", 12, 400, p.texto2)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .gel(22, Gels.chip(p))
    }

    /// Lo que busca.
    private func busca(_ y: Perfil) -> some View {
        let b = y.intenciones.compactMap { i in intencionesCortas.first { $0.0 == i }?.1 }
        return VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .bottom) {
                MiosSeccion(texto: "Busca en Caigo").frame(maxWidth: .infinity, alignment: .leading)
                T("Cambiar", 13, 800, p.oscuro ? C.lima : C.violetaTexto).tocable { ir.intenciones(editar: true) }
            }
            MiosFlujo {
                ForEach(b.isEmpty ? ["Sin definir"] : b, id: \.self) { t in
                    T(t, 13, 800, C.blanco).padding(.horizontal, 13).padding(.vertical, 9).gel(14, Gels.sel)
                }
            }
        }
    }

    /// Mi gente.
    private func miGente(_ y: Perfil) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 8) {
                MiosSeccion(texto: "Mi gente · \(y.miGente)")
                if gente.isEmpty {
                    T("Los que sumás después de un plan aparecen acá.", 12, 400, p.texto2)
                } else {
                    Avatares(personas: gente.map { ($0.nombre, $0.foto) }, total: max(y.miGente, gente.count), max: 4, tam: 34)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Icono(I.adelante, 20, p.texto, trazo: 2.2)
        }
        .padding(14)
        .gel(22, miosGelTarjeta(p))
        .contentShape(Rectangle())
        .onTapGesture { ir.gente() }
    }

    private var hojaCuenta: some View {
        MiosHoja(abierta: opciones, titulo: "Ajustes", cerrar: { opciones = false }) {
            MiosGrupo {
                MiosFila(icono: I.corazonChico, titulo: "Lo que buscás") { opciones = false; ir.intenciones(editar: true) }
                MiosFila(icono: I.enviar, titulo: "Mensajes privados") { opciones = false; ir.privados() }
                MiosFila(icono: I.campana, titulo: "Avisos") { opciones = false; ir.ajustesAvisos() }
                MiosFila(icono: I.escudo, titulo: "Verificación y seguridad", ultima: true) { opciones = false; ir.verificacion() }
            }
            // Los legales, en la web (como el «Al entrar aceptás…» del ingreso).
            MiosGrupo {
                MiosFila(icono: I.candado, titulo: "Términos y condiciones") { opciones = false; Sistema.abrir(Legales.terminos) }
                MiosFila(icono: I.escudoCheck, titulo: "Política de privacidad", ultima: true) {
                    opciones = false
                    Sistema.abrir(Legales.privacidad)
                }
            }
            MiosGrupo {
                MiosFila(icono: I.flecha, titulo: "Salir", colorIcono: C.coral2, ultima: true) {
                    opciones = false
                    Task {
                        await c.salir()
                        ir.base = .ingresar
                        ir.camino = []
                    }
                }
            }
        }
    }

    /// El mail escrito coincide con el de la cuenta. Si la API todavía no lo manda, alcanza con un mail válido.
    private var mailBajaOk: Bool {
        let escrito = mailBaja.trimmingCharacters(in: .whitespaces).lowercased()
        let cuenta = (c.yo?.email ?? "").trimmingCharacters(in: .whitespaces).lowercased()
        if !cuenta.isEmpty { return escrito == cuenta }
        return escrito.contains("@") && escrito.contains(".")
    }

    /// «¿Borrar tu cuenta?» (App Store 5.1.1(v)): se confirma escribiendo el mail, DELETE /v1/cuenta, se olvida la
    /// sesión y vuelve al ingreso. La cuenta se desactiva ahora y los datos se borran a los 30 días.
    private var hojaBorrar: some View {
        MiosHoja(abierta: borrando, titulo: "¿Borrar tu cuenta?", cerrar: { if !borrandoAhora { borrando = false } }) {
            T("Tu cuenta se desactiva ahora y tus datos personales se borran a los 30 días.", 15, 600, p.texto, alto: 1.5)
            T("En esos 30 días la recuperás escribiendo a hola@caigo.com.ar. Después no se puede deshacer.", 15, 400, p.texto2, alto: 1.5)
            T("Si tenés Caigo Plus, cancelalo en tu cuenta de Apple.", 15, 400, p.texto2, alto: 1.5)
            VStack(alignment: .leading, spacing: 10) {
                T("Para confirmar, escribí tu mail", 14, 700)
                MiosCampo(valor: $mailBaja, placeholder: "tu@mail.com", maximo: 120)
                    .teclado(.email)
                    .autocorrectionDisabled()
            }
            MiosGrupo {
                Button {
                    guard !borrandoAhora, mailBajaOk else { return }
                    borrandoAhora = true
                    Task {
                        do {
                            try await c.borrarCuenta()
                            borrando = false
                            ir.base = .ingresar
                            ir.camino = []
                        } catch {
                            c.avisos.error(error)
                        }
                        borrandoAhora = false
                    }
                } label: {
                    Group {
                        if borrandoAhora { Puntitos(color: C.coral2) } else { T("Borrar mi cuenta", 15, 700, C.coral2.opacity(mailBajaOk ? 1 : 0.4)) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 18).padding(.vertical, 16)
                    .contentShape(Rectangle())
                }
                .buttonStyle(EstiloPlano())
                .disabled(!mailBajaOk)
                .accessibilityAddTraits(.isButton)
                Rectangle().fill(p.oscuro ? p.separador : C.lavanda).frame(height: 1)
                Button { if !borrandoAhora { borrando = false } } label: {
                    T("Cancelar", 15, 700).frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 18).padding(.vertical, 16).contentShape(Rectangle())
                }
                .buttonStyle(EstiloPlano())
            }
        }
    }
}

/// Mi gente: la gente que sumaste (GET /v1/gente).
struct MiGente: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var lista: [PersonaCorta]?
    @State private var error: String?

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                Encabezado(titulo: "Mi gente", sub: lista.map { $0.count == 1 ? "1 persona" : "\($0.count) personas" }) { ir.atras() }
                    .padding(.horizontal, 16).padding(.top, 6)
                Group {
                    if let lista {
                        if lista.isEmpty {
                            MiosVacio(titulo: "Todavía no sumaste a nadie", cuerpo: "Después de cada plan te preguntamos con quién volverías a salir.", cta: "Ver mis planes") {
                                ir.principal(.mis)
                            }
                            .padding(28)
                        } else {
                            ScrollView(showsIndicators: false) {
                                LazyVStack(spacing: 12) { ForEach(lista) { fila($0) } }
                                    .padding(.horizontal, 20).padding(.top, 18).padding(.bottom, 24)
                            }
                        }
                    } else if let error {
                        MiosVacio(titulo: "No pudimos cargar tu gente", cuerpo: error, cta: "Probar de nuevo") { Task { await cargar() } }.padding(28)
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
        do { lista = try await c.api.miGente().gente; error = nil } catch { self.error = mensajeDe(error) }
    }

    private func fila(_ g: PersonaCorta) -> some View {
        HStack(spacing: 12) {
            Avatar(nombre: g.nombre, foto: g.foto, tam: 44, borde: 0, tamTexto: 17)
            VStack(alignment: .leading, spacing: 1) {
                HStack(spacing: 6) {
                    T([g.nombre, g.edad.map(String.init)].compactMap { $0 }.joined(separator: ", "), 15, 700, unRenglon: true)
                    if g.verificada { MiosVerificado(tam: 15) }
                }
                T(miosAsistencia(g.asistencia), 12, 400, p.texto2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Icono(I.adelante, 18, p.texto3, trazo: 2.2)
        }
        .padding(12)
        .gel(20, miosGelTarjeta(p))
        .contentShape(Rectangle())
        .onTapGesture { ir.persona(g.id, desde: "gente") }
    }
}

/// Editar perfil: fotos, nombre, bio, trabajo, zona, preguntas e intereses (PATCH /v1/yo).
struct EditarPerfil: View {
    /// «Completá tu perfil», con los mínimos de la API: después del DNI del primer «Caigo» (apilado: al
    /// guardar vuelve a donde estaba) o como base vieja del alta (al guardar, a Ahora).
    var completar = false
    var apilado = false
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var listo = false
    @State private var fotos: [String] = []
    @State private var nombre = ""
    @State private var bio = ""
    @State private var trabajo = ""
    @State private var zona = ""
    @State private var prompts: [Prompt] = []
    @State private var gustos: [String] = []
    @State private var subiendo = false
    @State private var guardando = false
    @State private var eleccion: PhotosPickerItem?

    var body: some View {
        Pantalla {
            if !listo {
                Puntitos(color: p.acento)
            } else {
                VStack(spacing: 0) {
                    if completar {
                        // Con los colores de la paleta (IngresoTitulo es para el alta, que va sobre fondo claro).
                        VStack(alignment: .leading, spacing: 10) {
                            T("Completá tu perfil", 34, 700, p.texto, alto: 1.05, espaciado: -0.8)
                            T("Así la gente sabe con quién sale. Mínimo 2 fotos, unas líneas sobre vos y 3 cosas que te copen.", 15, 400, p.texto2, alto: 1.5)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 20).padding(.top, 14)
                    } else {
                        Encabezado(titulo: "Editar perfil") { ir.atras() }.padding(.horizontal, 16).padding(.top, 6)
                    }
                    ScrollView(showsIndicators: false) {
                        VStack(alignment: .leading, spacing: 22) {
                            seccionFotos
                            etiquetado("Nombre") { MiosCampo(valor: $nombre, maximo: 40) }
                            etiquetado("Sobre mí") { MiosCampo(valor: $bio, renglones: 3, maximo: 300) }
                            etiquetado("Trabajo") { MiosCampo(valor: $trabajo, maximo: 60) }
                            etiquetado("Zona") { MiosCampo(valor: $zona, placeholder: "Güemes", maximo: 60) }
                            seccionPreguntas
                            seccionGustos
                        }
                        .padding(.horizontal, 20).padding(.vertical, 18)
                    }
                    .scrollDismissesKeyboard(.interactively)
                    if completar && !faltan.isEmpty {
                        T("Falta: \(faltan.joined(separator: ", ")).", 13, 600, p.texto2).multilineTextAlignment(.center)
                            .padding(.horizontal, 20).padding(.top, 6)
                    }
                    Cta(texto: completar ? (apilado ? "Listo, seguir" : "Listo, entrar") : "Guardar",
                        habilitado: !nombre.trimmingCharacters(in: .whitespaces).isEmpty && !subiendo && (!completar || faltan.isEmpty),
                        cargando: guardando) { guardar() }
                        .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 20)
                }
            }
        }
        .task {
            if c.yo == nil { await c.recargarYo() }
            if let y = c.yo, !listo {
                fotos = y.fotos; nombre = y.nombre; bio = y.bio; trabajo = y.trabajo; zona = y.zona
                prompts = y.prompts; gustos = y.intereses; listo = true
            }
        }
        .onChange(of: eleccion) { _, item in
            guard let item else { return }
            eleccion = nil
            subir(item)
        }
    }

    /// Los mínimos que faltan (los mismos de personas.Faltantes en la API).
    private var faltan: [String] {
        var f: [String] = []
        if zona.trimmingCharacters(in: .whitespaces).isEmpty { f.append("tu zona") }
        if bio.trimmingCharacters(in: .whitespacesAndNewlines).count < 30 { f.append("contar algo sobre vos (30 letras o más)") }
        if fotos.count < 2 { f.append("2 fotos") }
        if gustos.count < 3 { f.append("3 cosas que te copen") }
        return f
    }

    private func etiquetado<V: View>(_ etiqueta: String, @ViewBuilder _ campo: () -> V) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            T(etiqueta, 14, 700)
            campo()
        }
    }

    private var seccionFotos: some View {
        VStack(alignment: .leading, spacing: 10) {
            MiosSeccion(texto: "Fotos")
            T("La primera es la principal. Tocá la estrella de otra para\u{00A0}cambiarla.", 13, 500, p.texto2, alto: 1.4)
            // Celdas por URL (son únicas) y "" para la de sumar: al reordenar, la foto se mueve (no cambia
            // el contenido de cada lugar).
            let celdas = fotos + (fotos.count < 6 ? [""] : [])
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 10), count: 3), spacing: 10) {
                ForEach(celdas, id: \.self) { url in
                    Color.clear.aspectRatio(0.8, contentMode: .fit)
                        .overlay {
                            if url.isEmpty { celdaSumar } else { celdaFoto(url) }
                        }
                }
            }
            .animation(.spring(response: 0.3, dampingFraction: 0.85), value: fotos)
        }
    }

    private var celdaSumar: some View {
        PhotosPicker(selection: $eleccion, matching: .images) {
            ZStack {
                RoundedRectangle(cornerRadius: 18, style: .continuous).fill(p.superficie)
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .strokeBorder(Color(hex: 0xB9ACF5), style: StrokeStyle(lineWidth: 2, dash: [6, 4]))
                if subiendo { Puntitos(color: C.violeta) } else { Icono(I.mas, 26, p.oscuro ? C.lima : C.violeta, trazo: 3) }
            }
        }
        .disabled(subiendo)
        .accessibilityLabel("Sumar foto")
    }

    private func principal(_ url: String) {
        guard let i = fotos.firstIndex(of: url), i > 0 else { return }
        fotos.remove(at: i)
        fotos.insert(url, at: 0)
    }

    private func celdaFoto(_ url: String) -> some View {
        let primera = fotos.first == url
        let forma = RoundedRectangle(cornerRadius: 18, style: .continuous)
        return Foto(url: url).clipShape(forma)
            .overlay { if primera { forma.strokeBorder(C.violeta, lineWidth: 3) } }
            .overlay(alignment: .bottomLeading) {
                if primera {
                    T("Principal", 11, 800, C.blanco, unRenglon: true)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Capsule().fill(C.violeta))
                        .padding(8)
                }
            }
            .contentShape(.contextMenuPreview, forma)
            .contextMenu {
                if !primera {
                    Button { withAnimation { principal(url) } } label: { Label("Usar como principal", systemImage: "star") }
                }
                Button(role: .destructive) { withAnimation { fotos.removeAll { $0 == url } } } label: { Label("Sacar", systemImage: "trash") }
            }
            .overlay(alignment: .bottomLeading) {
                // A la vista, como el ×: la estrella hace principal a esta foto (antes era un gesto escondido).
                if !primera {
                    Button { withAnimation { principal(url) } } label: {
                        Icono(I.estrella, 14, C.blanco, trazo: 2.4)
                            .frame(width: 30, height: 30)
                            .background(Circle().fill(rgba(32, 29, 43, 0.55)))
                            .overlay(Circle().strokeBorder(C.blanco, lineWidth: 2))
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(EstiloPlano())
                    .padding(1)
                    .accessibilityLabel("Hacer principal")
                }
            }
            .overlay(alignment: .topTrailing) {
                // Se ve de 30 y se toca en 44 (con borde blanco, para leerse sobre cualquier foto).
                Button { withAnimation { fotos.removeAll { $0 == url } } } label: {
                    Icono(I.cruz, 13, C.blanco, trazo: 3)
                        .frame(width: 30, height: 30)
                        .background(Circle().fill(C.carbon))
                        .overlay(Circle().strokeBorder(C.blanco, lineWidth: 2))
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(EstiloPlano())
                .padding(1)
                .accessibilityLabel("Sacar foto")
            }
            .accessibilityElement(children: .contain)
            .accessibilityLabel(primera ? "Foto principal" : "Foto")
            .accessibilityAction(named: "Usar como principal") { principal(url) }
    }

    private var seccionPreguntas: some View {
        VStack(alignment: .leading, spacing: 10) {
            MiosSeccion(texto: "Preguntas")
            ForEach(Array(prompts.enumerated()), id: \.offset) { i, pr in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        T(pr.p.uppercased(), 12, 800, p.texto2, espaciado: 1).frame(maxWidth: .infinity, alignment: .leading)
                        Icono(I.cruz, 16, p.texto3, trazo: 2.4).tocable { if i < prompts.count { prompts.remove(at: i) } }
                    }
                    MiosCampo(valor: Binding(get: { i < prompts.count ? prompts[i].r : "" },
                                             set: { if i < prompts.count { prompts[i].r = $0 } }),
                              renglones: 2, maximo: 150)
                }
                .padding(14)
                .gel(22, Gels.neutra(p))
            }
            if prompts.count < 3 {
                MiosFlujo {
                    ForEach(preguntas.filter { q in !prompts.contains { $0.p == q } }, id: \.self) { q in
                        Chip(texto: q, on: false, tam: 13) { prompts.append(Prompt(p: q, r: "")) }
                    }
                }
            }
        }
    }

    private var seccionGustos: some View {
        VStack(alignment: .leading, spacing: 10) {
            MiosSeccion(texto: "¿Qué te copa?")
            MiosFlujo(espacio: 8, renglon: 10) {
                ForEach(intereses + gustos.filter { !intereses.contains($0) }, id: \.self) { g in
                    Chip(texto: g, on: gustos.contains(g)) {
                        if gustos.contains(g) { gustos.removeAll { $0 == g } } else if gustos.count < 12 { gustos.append(g) }
                    }
                }
            }
        }
    }

    private func subir(_ item: PhotosPickerItem) {
        subiendo = true
        Task {
            do {
                guard let d = try await item.loadTransferable(type: Data.self), let jpeg = miosJpeg(d) else {
                    throw ErrorApi(estado: 0, problema: Problema(detail: "No pudimos leer esa foto. Probá con otra."))
                }
                fotos.append(try await c.api.subir(tipo: "foto", jpeg: jpeg).url)
            } catch {
                c.avisos.error(error)
            }
            subiendo = false
        }
    }

    private func guardar() {
        guardando = true
        Task {
            do {
                var cambios = CambiosPerfil()
                cambios.nombre = nombre.trimmingCharacters(in: .whitespaces)
                cambios.bio = bio.trimmingCharacters(in: .whitespacesAndNewlines)
                cambios.trabajo = trabajo.trimmingCharacters(in: .whitespaces)
                cambios.zona = zona.trimmingCharacters(in: .whitespaces)
                cambios.fotos = fotos
                cambios.intereses = gustos
                cambios.prompts = prompts.filter { !$0.r.trimmingCharacters(in: .whitespaces).isEmpty }
                    .map { Prompt(p: $0.p, r: $0.r.trimmingCharacters(in: .whitespacesAndNewlines)) }
                c.yo = try await c.api.actualizarYo(cambios)
                if completar {
                    c.avisos.ok("¡Listo! Ya podés sumarte a planes.")
                    if apilado { ir.atras() } else { ir.principal(.ahora, modo: nil) }
                } else {
                    c.avisos.ok("Listo, actualizamos tu perfil.")
                    ir.atras()
                }
            } catch {
                c.avisos.error(error)
            }
            guardando = false
        }
    }
}
