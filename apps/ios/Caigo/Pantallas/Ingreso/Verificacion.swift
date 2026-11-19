import SwiftUI
import Nucleo

/// "+5492235550142" → "+54 223 555 0142".
private func telefonoLindo(_ t: String) -> String {
    var d = t
    if d.hasPrefix("+549") { d.removeFirst(4) } else if d.hasPrefix("+54") { d.removeFirst(3) }
    guard d.count == 10, d.allSatisfy(\.isNumber) else { return t }
    let a = Array(d)
    return "+54 \(String(a[0..<3])) \(String(a[3..<6])) \(String(a[6...]))"
}

/// Casilla de 36: lima con check si está hecho, o el número con borde coral punteado.
struct IngresoCasillaPaso: View {
    var hecho: Bool
    var numero: Int
    var body: some View {
        if hecho {
            Icono(I.check, 16, C.carbon, trazo: 3.4)
                .frame(width: 36, height: 36)
                .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(C.lima))
        } else {
            T("\(numero)", 16, 800, C.coral)
                .frame(width: 36, height: 36)
                .overlay(RoundedRectangle(cornerRadius: 11, style: .continuous).inset(by: 1)
                    .stroke(C.coral, style: StrokeStyle(lineWidth: 2, dash: [4, 3])))
        }
    }
}

private let gelTarjetaOscura = Gel(fondo: Gels.oscuroSel.fondo, sombras: [externa(16, 28, rgba(36, 33, 45, 0.6), spread: -14)], grano: true)

/// Proto · "Tu confianza en Caigo": teléfono, selfie y DNI con el estado real de la API.
struct Verificacion: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var estado: EstadoVerificacion?
    @State private var error: String?
    @State private var compartiendo = false
    @State private var editandoContacto = false
    /// Los renglones de la hoja: los contactos que hay y uno vacío para sumar (hasta 3).
    @State private var contactos: [Contacto] = []
    @State private var guardandoContacto = false

    var body: some View {
        ZStack {
        Pantalla {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    IngresoBarraArriba(icono: I.cruz, volver: { ir.atras() }) { IngresoEncriptado() }
                    VStack(alignment: .leading, spacing: 8) {
                        T("Tu confianza en Caigo", 34, 800, C.carbon, alto: 1.02, espaciado: -1)
                        T("Así la gente sabe que sos vos de verdad.", 15, 400, C.texto2, alto: 1.5)
                    }
                    if let v = estado {
                        contenido(v)
                    } else {
                        ZStack {
                            if let error { T(error, 15, 600, C.texto2, alinear: .center) } else { Puntitos() }
                        }
                        .frame(maxWidth: .infinity).frame(height: 180)
                    }
                }
                .padding(.horizontal, 20).padding(.top, 8).padding(.bottom, 40)
            }
        }
        hojaContacto
        }
        .environment(\.colorScheme, .light)
        .environment(\.paleta, .clara)
        .task { await cargar() }
    }

    /// Contactos de confianza (PATCH /v1/yo `contactosConfianza`, hasta 3): nombre y teléfono o mail.
    private var hojaContacto: some View {
        MiosHoja(abierta: editandoContacto, titulo: "Contactos de confianza", cerrar: { if !guardandoContacto { editandoContacto = false } }) {
            T("Sólo se usan si activás «¿Todo bien?» en un plan. Si es un mail, le escribimos si no llegás.", 15, 400, C.texto2, alto: 1.45)
            ForEach(contactos.indices, id: \.self) { i in
                HStack(spacing: 8) {
                    MiosCampo(valor: $contactos[i].nombre, placeholder: "Nombre", maximo: 40).frame(width: 120)
                    MiosCampo(valor: $contactos[i].contacto, placeholder: "Teléfono o mail", maximo: 80)
                }
            }
            Cta(texto: "Guardar", cargando: guardandoContacto) {
                guardandoContacto = true
                Task {
                    var cambios = CambiosPerfil()
                    cambios.contactosConfianza = contactos
                        .map { Contacto(nombre: $0.nombre.trimmingCharacters(in: .whitespaces), contacto: $0.contacto.trimmingCharacters(in: .whitespaces)) }
                        .filter { !$0.contacto.isEmpty }
                    do {
                        c.yo = try await c.api.actualizarYo(cambios)
                        editandoContacto = false
                    } catch {
                        c.avisos.error(error)
                    }
                    guardandoContacto = false
                }
            }
        }
    }

    /// «Compartir mi plan en vivo»: el link de /vivo del próximo plan en el que estoy adentro.
    private func compartirEnVivo() {
        guard !compartiendo else { return }
        compartiendo = true
        Task {
            defer { compartiendo = false }
            do {
                let ahora = Date()
                let planes = try await c.api.mios("me_sume").planes.filter { pl in
                    guard pl.miEstado == "aceptada", let d = Fecha.parse(pl.empiezaEn) else { return false }
                    return d.addingTimeInterval(Double(pl.duracionMin) * 60) > ahora
                }
                guard let pl = planes.min(by: { $0.empiezaEn < $1.empiezaEn }) else {
                    c.avisos.error("Cuando te acepten en un plan, lo compartís desde acá.")
                    return
                }
                Sistema.compartir(try await c.api.enVivo(pl.id).link)
            } catch {
                c.avisos.error(error)
            }
        }
    }

    private func cargar() async {
        if c.yo == nil { await c.recargarYo() }
        do {
            estado = try await c.api.verificacion()
            error = nil
        } catch {
            self.error = mensajeDe(error)
        }
    }

    @ViewBuilder
    private func contenido(_ v: EstadoVerificacion) -> some View {
        VStack(spacing: 0) {
            fila(!v.telefono.isEmpty, 1, "Teléfono", v.telefono.isEmpty ? "Te mandamos un código por mail" : telefonoLindo(v.telefono), primera: true) {
                if v.telefono.isEmpty { ir.telefono() }
            }
            fila(v.selfie, 2, "Selfie con prueba de vida", "Confirma que sos una persona real") {
                if !v.selfie { ir.selfie(volver: true) }
            }
            fila(v.dni == "aprobada", 3, "DNI · badge Verificado", {
                switch v.dni {
                case "aprobada": return "Tu perfil muestra el badge"
                case "pendiente": return "Ya estamos revisando tu DNI."
                case "rechazada": return v.dniMotivo.isEmpty ? "No pudimos verificarlo" : v.dniMotivo
                default: return "Lo pedimos a todos para usar Caigo"
                }
            }()) {
                if v.dni == "ninguna" || v.dni == "rechazada" { ir.dni() }
            }
        }
        .padding(6)
        .gel(26, gelTarjetaOscura)
        if v.dni == "ninguna" {
            CtaGrande("Verificar con DNI · 2 minutos") { ir.dni() }
        }
        T("Herramientas para salir tranqui", 20, 700, C.carbon).padding(.top, 4)
        VStack(spacing: 0) {
            herramienta(I.pin, C.violeta, "Compartir mi plan en vivo", "Tu contacto ve dónde estás hasta que termina", borde: true,
                        cargando: compartiendo) { compartirEnVivo() }
            herramienta(I.usuario, C.violeta, "Contactos de confianza",
                        (c.yo?.contactosConfianza ?? []).isEmpty ? "Te cuidan el día del plan con «¿Todo bien?»"
                            : (c.yo?.contactosConfianza ?? []).map(\.mostrar).joined(separator: " · "), borde: true) {
                contactos = Array(((c.yo?.contactosConfianza ?? []) + [Contacto()]).prefix(3))
                editandoContacto = true
            }
            herramienta(I.bloqueo, C.coral2, "Bloqueos y reportes", "Los revisa una persona del equipo, no un bot", borde: false) { ir.bloqueos() }
        }
        .gel(22, Gels.neutra)
        T("Nunca mostramos tu DNI ni tu dirección. Solo el badge.", 12, 400, C.texto2, alto: 1.5, alinear: .center)
            .frame(maxWidth: .infinity)
    }

    private func fila(_ hecho: Bool, _ n: Int, _ titulo: String, _ sub: String, primera: Bool = false, accion: @escaping () -> Void) -> some View {
        VStack(spacing: 0) {
            if !primera { Rectangle().fill(Color(hex: 0x3A3550)).frame(height: 1) }
            Button(action: accion) {
                HStack(spacing: 12) {
                    IngresoCasillaPaso(hecho: hecho, numero: n)
                    VStack(alignment: .leading, spacing: 0) {
                        T(titulo, 15, 800, C.blancoCalido)
                        T(sub, 12, 400, Color(hex: 0xC9C4DA))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(12)
                .contentShape(Rectangle())
            }
            .buttonStyle(EstiloPlano())
        }
    }

    private func herramienta(_ icono: String, _ color: Color, _ titulo: String, _ sub: String?, borde: Bool,
                             cargando: Bool = false, accion: @escaping () -> Void) -> some View {
        VStack(spacing: 0) {
            Button(action: accion) {
                HStack(spacing: 12) {
                    Icono(icono, 20, color, trazo: 2.2)
                    VStack(alignment: .leading, spacing: 0) {
                        T(titulo, 14, 800, C.carbon)
                        if let sub { T(sub, 12, 400, C.texto2) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    if cargando { Puntitos(color: C.violeta) }
                }
                .padding(.horizontal, 16).padding(.vertical, 14)
                .contentShape(Rectangle())
            }
            .buttonStyle(EstiloPlano())
            if borde { Rectangle().fill(C.lavanda).frame(height: 1) }
        }
    }
}
