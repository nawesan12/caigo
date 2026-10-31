import SwiftUI
import MapKit
import PhotosUI
import Nucleo

private let horasCrear = ["20:30", "21:30", "22:30"]
/// Cuánto dura (minutos): las mismas piezas que las horas de 06a.
private let duracionesCrear: [(Int, String)] = [(60, "1 h"), (120, "2 h"), (180, "3 h"), (300, "5 h")]
private let nivelesCrear = ["Recién aprendo", "Me defiendo", "Canto falta envido"]
private let edadMin: Double = 18
private let edadMax: Double = 60

/// Borrador del plan con los valores por defecto del README: Hoy 21:30, Somos 3 / Falta 1, con
/// aprobación, verificados y dirección oculta.
@Observable
private final class Borrador {
    var titulo = ""
    var categoria = "juego"
    var cuando = 1 // 0 Ahora · 1 Hoy · 2 Otro día
    var hora = "21:30"
    var horaOtra = false
    var h = 21
    var m = 30
    var dia = 1
    var repite = false
    var lugar = ""
    var direccion = ""
    var zona = ""
    var lat = latMDP
    var lng = lngMDP
    var somos = 3
    var faltan = 1
    var edadDesde = edadMin
    var edadHasta = edadMax
    var genero = "cualquiera"
    var nivel = ""
    var ingreso = "aprobacion"
    var visibilidad = 0
    var verificados = true
    var ocultar = true
    var costo: Int?
    var descripcion = ""
    var duracion = 120
    var foto = ""
    var subiendoFoto = false
    /// Ciudad que salió de la geocodificación (o del perfil); si no hay, se deduce de la latitud.
    var ciudadElegida = ""
    /// Editando: los que ya van no se pueden sacar del contador (el cupo no baja de ahí).
    var somosMin = 1
    var editando = false

    func empieza() -> Date {
        let cal = Fecha.calendario
        let ahora = Date()
        if cuando == 0 { return ahora.addingTimeInterval(15 * 60) }
        let (hh, mm): (Int, Int) = horaOtra ? (h, m) : {
            let p = hora.split(separator: ":").compactMap { Int($0) }
            return (p.first ?? 21, p.last ?? 30)
        }()
        let base = cal.date(byAdding: .day, value: cuando == 1 ? 0 : dia, to: cal.startOfDay(for: ahora))!
        var t = cal.date(bySettingHour: hh, minute: mm, second: 0, of: base)!
        if cuando == 1 && t < ahora { t = cal.date(byAdding: .day, value: 1, to: t)! }
        return t
    }

    var ciudad: String { !ciudadElegida.isEmpty ? ciudadElegida : (lat > -37.6 ? "Villa Gesell" : "Mar del Plata") }
    /// Lo que va después de «en» en el título por defecto: la zona, o la ciudad.
    var dondeCorto: String { zona.isEmpty ? ciudad : zona }
    var nombreCategoria: String { Categorias.nombre(categoria) }
}

/// 06a–06c · Crear plan en tres pasos (menos de 30 s). Con `editar`, los mismos pasos cargados con
/// el plan y «Guardar cambios» (PATCH /v1/planes/{id}, contrato §2).
struct Crear: View {
    var editar: String?
    init() {}
    init(editar: String) { self.editar = editar }
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var pal
    @State private var b = Borrador()
    @State private var paso = 0
    @State private var eligiendoLugar = false
    @State private var editandoCosto = false
    @State private var publicando = false
    /// El plan como estaba (editando) y la fecha tal como quedó al cargarlo.
    @State private var original: Plan?
    @State private var empiezaCargada: Date?
    @State private var errorCarga: String?

    var body: some View {
        Pantalla {
            VStack(spacing: 0) {
                VStack(spacing: 14) {
                    HStack {
                        BotonIcono(d: paso == 0 ? I.cruz : I.atras) { if paso == 0 { ir.atras() } else { withAnimation { paso -= 1 } } }
                            .accessibilityLabel(paso == 0 ? "Cerrar" : "Atrás")
                        Spacer()
                        T("\(paso + 1) de 3", 14, 700, pal.texto2)
                    }
                    BarraPasos(paso: paso + 1, total: 3)
                }
                .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 14)

                ScrollView {
                    VStack(alignment: .leading, spacing: paso == 2 ? 16 : 20) {
                        if editar != nil && original == nil {
                            if let errorCarga { T(errorCarga, 16, 600, pal.texto, alinear: .center).frame(maxWidth: .infinity).padding(.top, 60) }
                            else { Puntitos(color: pal.acento).frame(maxWidth: .infinity).padding(.top, 80) }
                        } else {
                            switch paso {
                            case 0: Paso1(b: b, editando: editar != nil, alLugar: { eligiendoLugar = true })
                            case 1: Paso2(b: b)
                            default: Paso3(b: b, alCosto: { editandoCosto = true }, alFoto: subirFoto)
                            }
                        }
                    }
                    .padding(.horizontal, 20).padding(.top, 6).padding(.bottom, 130)
                    .id(paso)
                    .transition(.opacity)
                }
                .scrollDismissesKeyboard(.interactively)
            }
            VStack {
                Spacer()
                ZStack(alignment: .bottom) {
                    // Velo del color del fondo detrás del botón, como la barra de abajo del prototipo.
                    LinearGradient(stops: Fondo.paradas((0, pal.fondo.opacity(0)), (0.35, pal.fondo)), startPoint: .top, endPoint: .bottom)
                        .frame(height: 120)
                        .allowsHitTesting(false)
                    Cta(texto: paso < 2 ? "Siguiente" : (editar != nil ? "Guardar cambios" : "Publicar plan"),
                        gel: paso < 2 ? Gels.violeta : Gels.limaChico, alto: 58,
                        color: paso < 2 ? C.blanco : C.carbon, icono: paso == 2 && editar == nil ? I.mas : nil, iconoTrazo: 4, iconoTam: 18,
                        habilitado: !(editar != nil && original == nil) && !b.subiendoFoto, cargando: publicando) {
                        if paso < 2 { withAnimation { paso += 1 } } else if editar != nil { guardar() } else { publicar() }
                    }
                    .padding(.horizontal, 20).padding(.bottom, 14)
                }
            }
            .ignoresSafeArea(.keyboard)
            if eligiendoLugar { ElegirLugar(b: b) { withAnimation { eligiendoLugar = false } } }
            if editandoCosto { EditarCosto(b: b) { withAnimation { editandoCosto = false } } }
        }
        .task {
            if let editar { await cargar(editar); return }
            if c.yo == nil { await c.recargarYo() }
            // Sin ubicación: la zona y la ciudad del perfil (y el centro de esa ciudad).
            let y = c.yo
            if b.zona.isEmpty { b.zona = y?.zona ?? "" }
            if b.ciudadElegida.isEmpty { b.ciudadElegida = y?.ciudad ?? "" }
            if b.ciudadElegida == "Villa Gesell" { b.lat = latGesell; b.lng = lngGesell }
            if let (la, ln) = await c.ubicacion() {
                b.lat = la; b.lng = ln
                // Con ubicación: la zona y la ciudad de donde está (geocodificación inversa).
                if let (zona, ciudad) = await crearZonaDe(lat: la, lng: ln) {
                    if !zona.isEmpty && b.lugar.isEmpty && b.direccion.isEmpty { b.zona = zona }
                    if !ciudad.isEmpty { b.ciudadElegida = ciudad }
                }
            }
        }
    }

    /// Editar: carga el plan en el borrador.
    private func cargar(_ id: String) async {
        do {
            let p = try await c.api.plan(id)
            b.titulo = p.titulo
            b.categoria = p.categoria
            b.descripcion = p.descripcion
            b.duracion = p.duracionMin
            b.foto = p.foto
            b.repite = p.recurrente
            b.lugar = p.lugarNombre
            b.direccion = p.direccion?.texto ?? ""
            b.zona = p.zona
            b.ciudadElegida = p.ciudad
            b.lat = p.direccion.map { $0.lat != 0 ? $0.lat : p.lat } ?? p.lat
            b.lng = p.direccion.map { $0.lng != 0 ? $0.lng : p.lng } ?? p.lng
            b.somos = max(1, p.somos)
            b.somosMin = max(1, p.somos)
            b.editando = true
            b.faltan = max(0, p.total - p.somos)
            b.edadDesde = Double(p.edadMin ?? Int(edadMin))
            b.edadHasta = Double(min(p.edadMax ?? Int(edadMax), Int(edadMax)))
            b.genero = p.genero
            b.nivel = p.nivel
            b.ingreso = p.ingreso
            b.visibilidad = ["publico", "mi_gente", "link"].firstIndex(of: p.visibilidad) ?? 0
            b.verificados = p.soloVerificados
            b.ocultar = p.ocultarDireccion
            b.costo = p.costoCentavos
            if let t = Fecha.parse(p.empiezaEn) {
                let cal = Fecha.calendario
                let k = cal.dateComponents([.hour, .minute], from: t)
                b.h = k.hour ?? 21
                b.m = k.minute ?? 30
                b.horaOtra = true
                let d = Fecha.diasEntre(Date(), t)
                if d <= 0 { b.cuando = 1 } else { b.cuando = 2; b.dia = min(d, 6) }
            }
            empiezaCargada = b.empieza()
            original = p
        } catch {
            errorCarga = mensajeDe(error)
        }
    }

    /// Manda sólo lo que cambió. La fecha viaja si se tocó el día o la hora.
    private func guardar() {
        guard let o = original, let id = editar else { return }
        if b.lugar.isEmpty && b.direccion.isEmpty {
            c.avisos.error("Marcá dónde es el plan.")
            withAnimation { eligiendoLugar = true }
            return
        }
        var k = CambiosPlan()
        func cambio<T: Equatable>(_ nuevo: T, _ viejo: T) -> T? { nuevo == viejo ? nil : nuevo }
        let titulo = b.titulo.trimmingCharacters(in: .whitespaces)
        if !titulo.isEmpty { k.titulo = cambio(titulo, o.titulo) }
        k.descripcion = cambio(b.descripcion.trimmingCharacters(in: .whitespacesAndNewlines), o.descripcion)
        k.duracionMin = cambio(b.duracion, o.duracionMin)
        if let cargada = empiezaCargada, abs(b.empieza().timeIntervalSince(cargada)) > 59 { k.empiezaEn = Fecha.iso(b.empieza()) }
        k.total = cambio(b.somos + b.faltan, o.total)
        k.ingreso = cambio(b.ingreso, o.ingreso)
        k.visibilidad = cambio(["publico", "mi_gente", "link"][b.visibilidad], o.visibilidad)
        k.soloVerificados = cambio(b.verificados, o.soloVerificados)
        k.ocultarDireccion = cambio(b.ocultar, o.ocultarDireccion)
        // Sin límite de edad: el rango entero (la API no borra con null).
        let dMin = Int(b.edadDesde.rounded()), dMax = Int(b.edadHasta.rounded())
        let nuevoMin: Int? = dMin > Int(edadMin) ? dMin : nil
        let nuevoMax: Int? = dMax < Int(edadMax) ? dMax : nil
        if nuevoMin != o.edadMin { k.edadMin = nuevoMin ?? Int(edadMin) }
        if nuevoMax != o.edadMax { k.edadMax = nuevoMax ?? 120 }
        k.genero = cambio(b.genero, o.genero)
        k.nivel = cambio(b.nivel, o.nivel)
        if b.costo != o.costoCentavos { k.costoCentavos = b.costo ?? 0 }
        k.foto = cambio(b.foto, o.foto)
        let lugar = b.lugar.trimmingCharacters(in: .whitespaces)
        let dir = b.direccion.trimmingCharacters(in: .whitespaces)
        let zona = b.zona.trimmingCharacters(in: .whitespaces)
        let lat0 = o.direccion.map { $0.lat != 0 ? $0.lat : o.lat } ?? o.lat
        let lng0 = o.direccion.map { $0.lng != 0 ? $0.lng : o.lng } ?? o.lng
        if lugar != o.lugarNombre || dir != (o.direccion?.texto ?? "") || zona != o.zona
            || abs(b.lat - lat0) > 0.00001 || abs(b.lng - lng0) > 0.00001 {
            k.lugarNombre = lugar; k.direccion = dir; k.zona = zona; k.lat = b.lat; k.lng = b.lng
        }
        Task {
            publicando = true
            defer { publicando = false }
            do {
                _ = try await c.api.editarPlan(id, k)
                Sistema.vibrar()
                c.avisos.ok(k.empiezaEn != nil || k.lat != nil ? "Listo. Les avisamos del cambio a los que van." : "Listo, guardamos los cambios.")
                ir.atras()
            } catch let e as ErrorApi {
                // 409 cupo_menor: el detalle dice cuántos van.
                c.avisos.error(e.problema.issues.first?.message ?? e.detalle)
            } catch {
                c.avisos.error(error)
            }
        }
    }

    private func subirFoto(_ item: PhotosPickerItem) {
        b.subiendoFoto = true
        Task {
            do {
                guard let d = try await item.loadTransferable(type: Data.self), let jpeg = miosJpeg(d) else {
                    throw ErrorApi(estado: 0, problema: Problema(detail: "No pudimos leer esa foto. Probá con otra."))
                }
                b.foto = try await c.api.subir(tipo: "foto", jpeg: jpeg).url
            } catch {
                c.avisos.error(error)
            }
            b.subiendoFoto = false
        }
    }

    private func publicar() {
        if b.lugar.isEmpty && b.direccion.isEmpty {
            c.avisos.error("Marcá dónde es el plan.")
            withAnimation { eligiendoLugar = true }
            return
        }
        Task {
            publicando = true
            defer { publicando = false }
            var n = PlanNuevo(categoria: b.categoria, empiezaEn: Fecha.iso(b.empieza()), ciudad: b.ciudad, lat: b.lat, lng: b.lng,
                              somos: b.somos, faltan: b.faltan)
            let titulo = b.titulo.trimmingCharacters(in: .whitespaces)
            n.titulo = titulo.isEmpty ? "\(b.nombreCategoria) en \(b.dondeCorto)" : titulo
            n.recurrente = b.repite
            n.zona = b.zona.trimmingCharacters(in: .whitespaces)
            n.lugarNombre = b.lugar.trimmingCharacters(in: .whitespaces)
            n.direccion = b.direccion.trimmingCharacters(in: .whitespaces)
            n.ingreso = b.ingreso
            n.visibilidad = ["publico", "mi_gente", "link"][b.visibilidad]
            n.soloVerificados = b.verificados
            n.ocultarDireccion = b.ocultar
            n.edadMin = Int(b.edadDesde.rounded()) > Int(edadMin) ? Int(b.edadDesde.rounded()) : nil
            n.edadMax = Int(b.edadHasta.rounded()) < Int(edadMax) ? Int(b.edadHasta.rounded()) : nil
            n.genero = b.genero
            n.nivel = b.nivel
            n.costoCentavos = b.costo
            n.descripcion = b.descripcion.trimmingCharacters(in: .whitespacesAndNewlines)
            n.duracionMin = b.duracion
            n.foto = b.foto
            do {
                let p = try await c.api.crearPlan(n)
                Sistema.vibrar()
                ir.publicado(p.id)
            } catch let e as ErrorApi {
                c.avisos.error(e.problema.issues.first?.message ?? e.detalle)
            } catch {
                c.avisos.error(error)
            }
        }
    }
}

/// Centro de Villa Gesell (si el perfil dice esa ciudad y no hay ubicación).
private let latGesell = -37.2639
private let lngGesell = -56.9730

/// Zona (barrio) y ciudad de un punto, con la geocodificación inversa de Apple.
func crearZonaDe(lat: Double, lng: Double) async -> (String, String)? {
    guard let pm = try? await CLGeocoder().reverseGeocodeLocation(CLLocation(latitude: lat, longitude: lng)).first else { return nil }
    return (pm.subLocality ?? "", pm.locality ?? "")
}

/// Campo del alta (58, radio 18, borde violeta).
private struct CampoCrear: View {
    @Binding var texto: String
    var pista: String
    var tam: CGFloat = 18
    var alto: CGFloat = 58
    var tipo: TipoTeclado = .texto
    @Environment(\.paleta) private var pal
    var body: some View {
        PlanesCampo(texto: $texto, pista: pista, tam: tam, peso: 600, colorPista: C.apagado, tipo: tipo)
            .padding(.horizontal, 18)
            .frame(height: alto)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(pal.oscuro ? C.oscuro3 : C.blanco))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(C.violeta, lineWidth: 2))
    }
}

private func limitado(_ b: Binding<String>, _ n: Int) -> Binding<String> {
    Binding(get: { b.wrappedValue }, set: { b.wrappedValue = String($0.prefix(n)) })
}

private struct Paso1: View {
    @Bindable var b: Borrador
    var editando = false
    var alLugar: () -> Void
    @Environment(\.paleta) private var pal

    var body: some View {
        T(editando ? "Editá tu plan" : "¿Qué hacemos?", 32, 700, pal.texto, alto: 1.05, espaciado: -0.8)
        CampoCrear(texto: limitado($b.titulo, 80), pista: "\(b.nombreCategoria) en \(b.dondeCorto)")
        // La categoría no se cambia después de publicar (no está entre lo que se edita).
        if !editando {
            PlanesFlujo(espacio: 8, renglon: 8) {
                ForEach(Categorias.paraCrear, id: \.0) { k, l in
                    Chip(texto: l, on: b.categoria == k, gelOn: planesVioletaChico, gelOff: Gels.chipClaro, padH: 13, radio: 13) { b.categoria = k }
                }
            }
        }
        CampoLargo(texto: limitado($b.descripcion, 500), pista: "Contá un poco más (opcional)")
        VStack(alignment: .leading, spacing: 10) {
            T("¿Cuándo?", 15, 700, pal.texto)
            Segmentado(opciones: ["Ahora", "Hoy", "Otro día"], seleccion: $b.cuando, alto: 46)
            if b.cuando == 0 {
                T("Arranca en los próximos 30 minutos", 13, 500, pal.texto2)
            } else {
                if b.cuando == 2 {
                    HStack(spacing: 6) {
                        ForEach(1...6, id: \.self) { i in
                            let d = Fecha.calendario.date(byAdding: .day, value: i, to: Date())!
                            Chip(texto: "\(Fecha.diaCorto(d)) \(Fecha.calendario.component(.day, from: d))", on: b.dia == i, tam: 12, padH: 4, radio: 12) { b.dia = i }
                                .frame(maxWidth: .infinity)
                        }
                    }
                }
                HStack(spacing: 8) {
                    ForEach(horasCrear, id: \.self) { h in
                        ChipHora(texto: h, on: !b.horaOtra && b.hora == h) { b.hora = h; b.horaOtra = false }
                    }
                    ChipHora(texto: b.horaOtra ? String(format: "%02d:%02d", b.h, b.m) : "Otra", on: b.horaOtra) { b.horaOtra = true }
                }
                if b.horaOtra {
                    HStack(spacing: 10) {
                        Stepper(valor: String(format: "%02d", b.h), menos: { b.h = (b.h + 23) % 24 }, mas: { b.h = (b.h + 1) % 24 })
                        T(":", 28, 800, pal.texto)
                        Stepper(valor: String(format: "%02d", b.m), menos: { b.m = (b.m + 55) % 60 }, mas: { b.m = (b.m + 5) % 60 })
                    }
                }
            }
            // Cuánto dura: con las mismas piezas que las horas.
            HStack(spacing: 8) {
                T("Dura", 14, 700, pal.texto2).padding(.trailing, 2)
                ForEach(duracionesCrear, id: \.0) { m, l in
                    ChipHora(texto: l, on: b.duracion == m) { b.duracion = m }
                }
            }
            .padding(.top, 2)
        }
        VStack(alignment: .leading, spacing: 10) {
            T("¿Dónde?", 15, 700, pal.texto)
            Button(action: alLugar) {
                HStack(spacing: 12) {
                    Icono(I.pin, 20, C.violeta, trazo: 2)
                    VStack(alignment: .leading, spacing: 0) {
                        T(!b.lugar.isEmpty ? b.lugar : (b.direccion.isEmpty ? "Elegí el lugar" : b.direccion), 15, 700, pal.texto, unRenglon: true)
                        let sub = [b.lugar.isEmpty ? "" : b.direccion, b.zona].filter { !$0.isEmpty }.joined(separator: ", ")
                        if !sub.isEmpty { T(sub, 12, 400, pal.texto2, unRenglon: true) }
                    }
                    Spacer(minLength: 4)
                    T("Cambiar", 13, 700, C.violeta)
                }
                .padding(.horizontal, 16).padding(.vertical, 14)
                .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(pal.oscuro ? C.oscuro3 : C.blanco))
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(pal.oscuro ? Color(hex: 0x3A3550) : Color(hex: 0xECE8F2), lineWidth: 1.5))
            }
            .buttonStyle(EstiloPlano())
        }
        if !editando {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    T("Se repite cada semana", 15, 700, pal.texto)
                    T("Ideal para el truco de los jueves", 12, 400, pal.texto2)
                }
                Spacer()
                Interruptor(on: $b.repite)
            }
            .padding(.horizontal, 2).padding(.vertical, 4)
        }
    }
}

/// Descripción: el campo del alta en varios renglones.
private struct CampoLargo: View {
    @Binding var texto: String
    var pista: String
    @Environment(\.paleta) private var pal
    var body: some View {
        ZStack(alignment: .topLeading) {
            if texto.isEmpty { T(pista, 15, 400, C.apagado).allowsHitTesting(false) }
            TextField("", text: $texto, axis: .vertical)
                .textFieldStyle(.plain)
                .font(Geist.fuente(15, 500))
                .foregroundStyle(pal.texto)
                .tint(C.violeta)
                .lineLimit(3...6)
        }
        .padding(.horizontal, 18).padding(.vertical, 16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(pal.oscuro ? C.oscuro3 : C.blanco))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
            .strokeBorder(pal.oscuro ? Color(hex: 0x3A3550) : Color(hex: 0xECE8F2), lineWidth: 1.5))
    }
}

private struct ChipHora: View {
    var texto: String
    var on: Bool
    var accion: () -> Void
    @Environment(\.paleta) private var pal
    var body: some View {
        Button(action: accion) {
            T(texto, 14, on ? 700 : 600, on ? C.blancoCalido : pal.texto, unRenglon: true)
                .padding(.horizontal, 13).padding(.vertical, 9)
                .background {
                    if on {
                        Color.clear.gel(13, Gels.oscuroSel)
                    } else {
                        RoundedRectangle(cornerRadius: 13, style: .continuous).fill(pal.oscuro ? C.oscuro3 : C.blanco)
                            .overlay(RoundedRectangle(cornerRadius: 13, style: .continuous).strokeBorder(pal.oscuro ? Color(hex: 0x3A3550) : Color(hex: 0xECE8F2), lineWidth: 1.5))
                    }
                }
        }
        .buttonStyle(EstiloToque())
    }
}

/// Stepper − N + de 06b.
private struct Stepper: View {
    var valor: String
    var menos: () -> Void
    var mas: () -> Void
    var oscuro = false
    var color: Color?
    var tam: CGFloat = 28
    @Environment(\.paleta) private var pal

    var body: some View {
        HStack(spacing: 14) {
            boton("−", menos).accessibilityLabel("Menos")
            T(valor, tam, 800, color ?? (oscuro ? C.blancoCalido : pal.texto), alto: 1)
                .monospacedDigit()
            boton("+", mas).accessibilityLabel("Más")
        }
    }

    private func boton(_ t: String, _ f: @escaping () -> Void) -> some View {
        Button(action: f) {
            T(t, 20, 700, oscuro ? C.blancoCalido : C.carbon, alto: 1)
                .frame(width: 36, height: 36)
                .background {
                    if oscuro { RoundedRectangle(cornerRadius: 12, style: .continuous).fill(Color(hex: 0x3A3550)) } else { Color.clear.gel(12, Gels.chipClaro) }
                }
        }
        .buttonStyle(EstiloToque())
    }
}

private struct Paso2: View {
    @Bindable var b: Borrador
    @Environment(\.paleta) private var pal

    var body: some View {
        T("¿Cuántos son?", 32, 700, pal.texto, alto: 1.05, espaciado: -0.8)
        HStack(spacing: 10) {
            VStack(spacing: 8) {
                T("Somos", 13, 700, pal.texto2)
                Stepper(valor: "\(b.somos)", menos: { b.somos = max(b.somosMin, b.somos - 1) }, mas: { if !b.editando { b.somos = min(60 - b.faltan, b.somos + 1) } }, tam: 48)
            }
            .frame(maxWidth: .infinity)
            .padding(14)
            .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(pal.oscuro ? C.oscuro3 : C.blanco))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(pal.oscuro ? Color(hex: 0x3A3550) : Color(hex: 0xECE8F2), lineWidth: 1.5))
            VStack(spacing: 8) {
                T("Faltan", 13, 700, C.coral)
                Stepper(valor: "\(b.faltan)", menos: { b.faltan = max(b.editando ? 0 : 1, b.faltan - 1) }, mas: { b.faltan = min(60 - b.somos, b.faltan + 1) },
                        oscuro: true, color: C.coral, tam: 48)
            }
            .frame(maxWidth: .infinity)
            .padding(14)
            .gel(22, Gels.oscuroSel)
        }
        HStack(spacing: 10) {
            T("Se va a ver así:", 14, 600, C.texto2)
            T(Cupo.badge(b.faltan), 15, 700, C.carbon, alto: 1).padding(.horizontal, 10).padding(.vertical, 5).gel(10, Gels.coralBadge)
            T("· \(b.categoria == "juego" ? "mesa" : "grupo") de \(b.somos + b.faltan)", 14, 600, C.carbon)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14).padding(.vertical, 12)
        .gel(16, Gels.chipClaro)
        HStack(alignment: .lastTextBaseline) {
            T("¿A quién buscás?", 20, 700, pal.texto)
            Spacer()
            T("Todo opcional", 13, 400, pal.texto2)
        }
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                T("Edad", 14, 700, pal.texto)
                Spacer()
                let d = Int(b.edadDesde.rounded()), h = Int(b.edadHasta.rounded())
                T(d <= Int(edadMin) && h >= Int(edadMax) ? "Cualquier edad" : "\(d) a \(h)\(h >= Int(edadMax) ? "+" : "")", 14, 700, C.violeta)
            }
            RangoEdad(desde: $b.edadDesde, hasta: $b.edadHasta)
        }
        VStack(alignment: .leading, spacing: 10) {
            T("Quiénes", 14, 700, pal.texto)
            PlanesFlujo(espacio: 8, renglon: 8) {
                ForEach([("cualquiera", "Cualquiera"), ("mujeres", "Solo mujeres"), ("diversidades", "Solo diversidades")], id: \.0) { k, l in
                    Chip(texto: l, on: b.genero == k, gelOff: Gels.chipClaro, padH: 13, radio: 13) { b.genero = k }
                }
            }
        }
        VStack(alignment: .leading, spacing: 10) {
            T("Nivel", 14, 700, pal.texto)
            PlanesFlujo(espacio: 8, renglon: 8) {
                ForEach(nivelesCrear, id: \.self) { l in
                    Chip(texto: l, on: b.nivel == l, gelOff: Gels.chipClaro, padH: 13, radio: 13) { b.nivel = b.nivel == l ? "" : l }
                }
            }
        }
    }
}

/// Rango de edad con dos perillas (06b).
private struct RangoEdad: View {
    @Binding var desde: Double
    @Binding var hasta: Double

    var body: some View {
        GeometryReader { g in
            let ancho = max(g.size.width, 1)
            let x: (Double) -> CGFloat = { CGFloat(($0 - edadMin) / (edadMax - edadMin)) * ancho }
            let v: (CGFloat) -> Double = { min(max(edadMin + Double($0 / ancho) * (edadMax - edadMin), edadMin), edadMax) }
            ZStack(alignment: .topLeading) {
                Capsule().fill(C.pista).frame(height: 4).offset(y: 12)
                Rectangle().fill(C.violeta).frame(width: max(0, x(hasta) - x(desde)), height: 4).offset(x: x(desde), y: 12)
                perilla.offset(x: x(desde) - 12, y: 2)
                    .gesture(DragGesture(coordinateSpace: .named("rango")).onChanged { desde = min(v($0.location.x), hasta - 1) })
                    .accessibilityLabel("Edad desde")
                    .accessibilityValue("\(Int(desde))")
                    .accessibilityAdjustableAction { d in desde = d == .increment ? min(desde + 1, hasta - 1) : max(desde - 1, edadMin) }
                perilla.offset(x: x(hasta) - 12, y: 2)
                    .gesture(DragGesture(coordinateSpace: .named("rango")).onChanged { hasta = max(v($0.location.x), desde + 1) })
                    .accessibilityLabel("Edad hasta")
                    .accessibilityValue("\(Int(hasta))")
                    .accessibilityAdjustableAction { d in hasta = d == .increment ? min(hasta + 1, edadMax) : max(hasta - 1, desde + 1) }
            }
            .coordinateSpace(name: "rango")
        }
        .frame(height: 28)
    }

    private var perilla: some View {
        Circle().fill(C.blanco).overlay(Circle().strokeBorder(C.violeta, lineWidth: 3)).frame(width: 24, height: 24).contentShape(Circle().inset(by: -10))
    }
}

private struct Paso3: View {
    @Bindable var b: Borrador
    var alCosto: () -> Void
    var alFoto: (PhotosPickerItem) -> Void
    @State private var eleccion: PhotosPickerItem?
    @Environment(\.paleta) private var pal

    var body: some View {
        T("¿Cómo se suman?", 32, 700, pal.texto, alto: 1.05, espaciado: -0.8)
        VStack(spacing: 8) {
            ForEach([("libre", "Libre", "El que llega, entra"),
                     ("aprobacion", "Con aprobación", "Vos elegís quién entra · recomendado"),
                     ("mi_gente", "Solo mi gente", "Gente con la que ya compartiste un plan")], id: \.0) { k, l, d in
                let on = b.ingreso == k
                HStack(spacing: 12) {
                    Circle().fill(C.blanco)
                        .overlay(Circle().strokeBorder(on ? C.violeta : C.borde6, lineWidth: on ? 7 : 2))
                        .frame(width: 22, height: 22)
                    VStack(alignment: .leading, spacing: 0) {
                        T(l, 15, 700, on ? C.carbon : pal.texto)
                        T(d, 12, 400, on ? C.texto2 : pal.texto2)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 14).padding(.vertical, 12)
                .background {
                    if on {
                        Color.clear.gel(18, Gels.chipClaro)
                            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(C.violeta, lineWidth: 2))
                    } else {
                        RoundedRectangle(cornerRadius: 18, style: .continuous).fill(pal.oscuro ? C.oscuro3 : C.blanco)
                            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(pal.oscuro ? Color(hex: 0x3A3550) : Color(hex: 0xECE8F2), lineWidth: 1.5))
                    }
                }
                .contentShape(Rectangle())
                .onTapGesture { b.ingreso = k }
                .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
            }
        }
        VStack(alignment: .leading, spacing: 10) {
            T("Quién lo ve", 14, 700, pal.texto)
            Segmentado(opciones: ["Público", "Mi gente", "Por link"], seleccion: $b.visibilidad, alto: 46)
        }
        VStack(spacing: 0) {
            filaInterruptor("Solo verificados", $b.verificados)
            Rectangle().fill(pal.separador).frame(height: 1)
            filaInterruptor("Ocultar dirección hasta aceptar", $b.ocultar)
            Rectangle().fill(pal.separador).frame(height: 1)
            Button(action: alCosto) {
                HStack {
                    T("Costo por persona", 14, 600, pal.texto)
                    Spacer()
                    T(b.costo.map { Plata.pesos($0) } ?? "Cada uno lo suyo", 14, 700, C.violeta)
                }
                .padding(.horizontal, 14).padding(.vertical, 13)
            }
            .buttonStyle(EstiloPlano())
        }
        .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(pal.oscuro ? C.oscuro3 : C.blanco))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(pal.oscuro ? Color(hex: 0x3A3550) : Color(hex: 0xECE8F2), lineWidth: 1.5))
        foto
    }

    /// Foto del plan (opcional): la celda punteada de las fotos del perfil, a lo ancho.
    private var foto: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .lastTextBaseline) {
                T("Foto del plan", 14, 700, pal.texto)
                Spacer()
                T("Opcional", 13, 400, pal.texto2)
            }
            Color.clear.frame(height: 150)
                .overlay {
                    if b.foto.isEmpty {
                        PhotosPicker(selection: $eleccion, matching: .images) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 20, style: .continuous).fill(pal.superficie)
                                RoundedRectangle(cornerRadius: 20, style: .continuous)
                                    .strokeBorder(Color(hex: 0xB9ACF5), style: StrokeStyle(lineWidth: 2, dash: [6, 4]))
                                if b.subiendoFoto {
                                    Puntitos(color: C.violeta)
                                } else {
                                    VStack(spacing: 6) {
                                        Icono(I.mas, 26, C.violeta, trazo: 3)
                                        T("Sin foto, usamos una de la categoría", 12, 600, pal.texto2, alinear: .center)
                                    }
                                }
                            }
                        }
                        .disabled(b.subiendoFoto)
                        .accessibilityLabel("Sumar foto del plan")
                    } else {
                        Foto(url: b.foto).clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                            .overlay(alignment: .topTrailing) {
                                Button { b.foto = "" } label: {
                                    Icono(I.cruz, 14, C.blanco, trazo: 3).frame(width: 28, height: 28).background(Circle().fill(C.carbon))
                                }
                                .buttonStyle(EstiloPlano())
                                .padding(8)
                                .accessibilityLabel("Sacar foto")
                            }
                    }
                }
        }
        .onChange(of: eleccion) { _, item in
            guard let item else { return }
            eleccion = nil
            alFoto(item)
        }
    }

    private func filaInterruptor(_ t: String, _ v: Binding<Bool>) -> some View {
        HStack {
            T(t, 14, 600, pal.texto)
            Spacer()
            Interruptor(on: v)
        }
        .padding(.horizontal, 14).padding(.vertical, 13)
    }
}

/// Hoja con un panel (para elegir lugar o costo).
private struct HojaCrear<Contenido: View>: View {
    var cerrar: () -> Void
    @ViewBuilder var contenido: () -> Contenido
    @Environment(\.paleta) private var pal
    var body: some View {
        ZStack(alignment: .bottom) {
            rgba(32, 29, 43, 0.45).ignoresSafeArea().onTapGesture(perform: cerrar)
            VStack(alignment: .leading, spacing: 14) { contenido() }
                .padding(EdgeInsets(top: 22, leading: 20, bottom: 20, trailing: 20))
                .background {
                    UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28, style: .continuous)
                        .fill(pal.oscuro ? C.grafito : C.blancoCalido)
                        .ignoresSafeArea(edges: .bottom)
                }
                .transition(.move(edge: .bottom))
        }
        .zIndex(30)
    }
}

private struct EditarCosto: View {
    var b: Borrador
    var cerrar: () -> Void
    @State private var texto = ""
    @Environment(\.paleta) private var pal

    var body: some View {
        HojaCrear(cerrar: cerrar) {
            T("Costo por persona", 22, 700, pal.texto)
            CampoCrear(texto: Binding(get: { texto }, set: { texto = String($0.filter(\.isNumber).prefix(7)) }), pista: "Cada uno lo suyo", tipo: .numeros)
            HStack(spacing: 10) {
                Cta(texto: "Cada uno lo suyo", gel: Gels.blanco, alto: 52, tam: 15) { b.costo = nil; cerrar() }
                Cta(texto: "Listo", alto: 52, tam: 15) {
                    if let n = Int(texto), n > 0 { b.costo = n * 100 } else { b.costo = nil }
                    cerrar()
                }
            }
        }
        .onAppear { texto = b.costo.map { String($0 / 100) } ?? "" }
    }
}

/// Elegir el lugar: buscador de MapKit, mapa con el pin al centro, nombre, dirección y zona.
private struct ElegirLugar: View {
    var b: Borrador
    var cerrar: () -> Void
    @State private var lugar = ""
    @State private var direccion = ""
    @State private var zona = ""
    @State private var ciudad = ""
    @State private var buscar = ""
    @State private var resultados: [MKMapItem] = []
    @State private var camara: MapCameraPosition = .automatic
    @State private var centro = CLLocationCoordinate2D(latitude: latMDP, longitude: lngMDP)
    @Environment(\.paleta) private var pal

    var body: some View {
        HojaCrear(cerrar: cerrar) {
            T("¿Dónde?", 22, 700, pal.texto)
            HStack(spacing: 10) {
                Icono(I.lupaChica, 18, C.violeta, trazo: 2.2)
                PlanesCampo(texto: $buscar, pista: "Buscá un bar, una cancha o una dirección")
            }
            .padding(.horizontal, 16).frame(height: 48)
            .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(pal.oscuro ? Color(hex: 0x2E2A3D) : Color(hex: 0xF2F0EC)))
            if !resultados.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(resultados.prefix(4).enumerated()), id: \.offset) { i, r in
                        if i > 0 { Rectangle().fill(C.lavanda).frame(height: 1) }
                        Button { elegir(r) } label: {
                            VStack(alignment: .leading, spacing: 1) {
                                T(r.name ?? "", 14, 700, C.carbon, unRenglon: true)
                                T(planesDireccion(r.placemark), 12, 400, C.texto2, unRenglon: true)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 14).padding(.vertical, 10)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(EstiloPlano())
                    }
                }
                .gel(18, Gels.neutra)
            }
            ZStack {
                Map(position: $camara)
                    .mapStyle(.standard(pointsOfInterest: .including([.cafe, .restaurant, .nightlife, .park, .stadium, .beach])))
                    .onMapCameraChange { ctx in centro = ctx.region.center }
                Icono(I.pin, 34, C.violeta, trazo: 2.4, relleno: C.blanco).offset(y: -14).allowsHitTesting(false)
            }
            .frame(height: 200)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            CampoCrear(texto: limitado($lugar, 80), pista: "Nombre del lugar", tam: 16, alto: 52)
            CampoCrear(texto: limitado($direccion, 160), pista: "Dirección", tam: 16, alto: 52)
            CampoCrear(texto: limitado($zona, 60), pista: "Barrio o zona", tam: 16, alto: 52)
            Cta(texto: "Listo", alto: 56) {
                b.lat = centro.latitude
                b.lng = centro.longitude
                b.lugar = lugar.trimmingCharacters(in: .whitespaces)
                b.direccion = direccion.trimmingCharacters(in: .whitespaces)
                b.zona = zona.trimmingCharacters(in: .whitespaces)
                if !ciudad.isEmpty { b.ciudadElegida = ciudad }
                let (la, ln, sinZona) = (centro.latitude, centro.longitude, b.zona.isEmpty)
                cerrar()
                // Moviendo el mapa a mano no hay zona: la saca la geocodificación inversa.
                Task {
                    guard let (z, ci) = await crearZonaDe(lat: la, lng: ln) else { return }
                    if sinZona && b.zona.isEmpty { b.zona = String(z.prefix(60)) }
                    if !ci.isEmpty { b.ciudadElegida = ci }
                }
            }
        }
        .onAppear {
            lugar = b.lugar; direccion = b.direccion; zona = b.zona
            centro = CLLocationCoordinate2D(latitude: b.lat, longitude: b.lng)
            camara = .region(MKCoordinateRegion(center: centro, latitudinalMeters: 900, longitudinalMeters: 900))
        }
        .task(id: buscar) {
            let q = buscar.trimmingCharacters(in: .whitespaces)
            guard q.count >= 3 else { resultados = []; return }
            try? await Task.sleep(nanoseconds: 350_000_000)
            if Task.isCancelled { return }
            let pedido = MKLocalSearch.Request()
            pedido.naturalLanguageQuery = q
            pedido.region = MKCoordinateRegion(center: centro, latitudinalMeters: 30_000, longitudinalMeters: 30_000)
            resultados = (try? await MKLocalSearch(request: pedido).start().mapItems) ?? []
        }
    }

    private func elegir(_ r: MKMapItem) {
        let pm = r.placemark
        lugar = String((r.name ?? "").prefix(80))
        direccion = String(planesDireccion(pm).prefix(160))
        zona = String((pm.subLocality ?? pm.locality ?? "").prefix(60))
        ciudad = pm.locality ?? ""
        centro = pm.coordinate
        withAnimation { camara = .region(MKCoordinateRegion(center: pm.coordinate, latitudinalMeters: 600, longitudinalMeters: 600)) }
        buscar = ""
        resultados = []
    }
}

/// "Güemes 2950" a partir de un resultado de MapKit.
private func planesDireccion(_ pm: MKPlacemark) -> String {
    [pm.thoroughfare, pm.subThoroughfare].compactMap { $0 }.joined(separator: " ")
}

/// Publicado (prototipo): fondo violeta, isotipo con tk-pop, "Plan publicado" y compartir el link.
struct Publicado: View {
    var id: String
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var plan: Plan?

    var body: some View {
        Pantalla(fondo: AnyView(ZStack {
            VistaFondo(fondo: .radial(cx: 0.5, cy: 0.3, rx: 1, ry: 0.7, Fondo.paradas((0, Color(hex: 0x8A70FF)), (1, C.violeta4))))
            Grano()
        })) {
            VStack(spacing: 16) {
                Spacer()
                Image("CaigoIcono").resizable().frame(width: 200, height: 200).pop()
                T("Plan publicado", 36, 800, C.blanco, alto: 1, espaciado: -1, alinear: .center)
                T("«\(plan?.titulo ?? "")» ya aparece en Ahora. Te avisamos cuando alguien se prenda.", 15, 500,
                  rgba(255, 255, 255, 0.9), alto: 1.5, alinear: .center)
                Color.clear.frame(height: 4)
                if let p = plan {
                    let link = "https://caigo.com.ar/p/\(p.slug)"
                    Cta(texto: "Compartir link por WhatsApp", alto: 58, tam: 16) {
                        Sistema.compartirWhatsApp("\(p.titulo) · \(Cupo.badge(p.faltan)). Sumate en Caigo: \(link)")
                    }
                    Button {
                        Sistema.copiar(link)
                        c.avisos.ok("Link copiado: \(linkPlan(p.slug))")
                    } label: { T("Copiar link", 14, 800, C.blanco) }
                        .buttonStyle(EstiloPlano())
                } else {
                    Puntitos(color: C.blanco)
                }
                Button { ir.principal(.mis) } label: { T("Ver mis planes", 14, 800, C.blanco) }
                    .buttonStyle(EstiloPlano())
                Spacer()
            }
            .padding(.horizontal, 30)
        }
        .task { plan = try? await c.api.plan(id) }
    }
}
