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

    var body: some View {
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
        .environment(\.colorScheme, .light)
        .task { await cargar() }
    }

    private func cargar() async {
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
            fila(!v.telefono.isEmpty, 1, "Teléfono", v.telefono.isEmpty ? "Te mandamos un código por SMS" : telefonoLindo(v.telefono), primera: true) {
                if v.telefono.isEmpty { ir.telefono() }
            }
            fila(v.selfie, 2, "Selfie con prueba de vida", "Confirma que sos una persona real") {
                if !v.selfie { ir.selfie(volver: true) }
            }
            fila(v.dni == "aprobada", 3, "DNI · badge Verificado", {
                switch v.dni {
                case "pendiente": return "Ya estamos revisando tu DNI."
                case "rechazada": return v.dniMotivo.isEmpty ? "Habilita planes «solo verificados»" : v.dniMotivo
                default: return "Habilita planes «solo verificados»"
                }
            }()) {}
        }
        .padding(6)
        .gel(26, gelTarjetaOscura)
        if v.dni == "ninguna" || v.dni == "rechazada" {
            CtaGrande("Verificar con DNI · 2 minutos") { ir.dni() }
        }
        T("Herramientas para salir tranqui", 20, 700, C.carbon).padding(.top, 4)
        VStack(spacing: 0) {
            herramienta(I.pin, C.violeta, "Compartir mi plan en vivo", "Tu contacto ve dónde estás hasta que termina", borde: true)
            herramienta(I.usuario, C.violeta, "Contacto de confianza", nil, borde: true)
            herramienta(I.bloqueo, C.coral2, "Bloqueos y reportes", "Los revisa una persona del equipo, no un bot", borde: false)
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

    private func herramienta(_ icono: String, _ color: Color, _ titulo: String, _ sub: String?, borde: Bool) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Icono(icono, 20, color, trazo: 2.2)
                VStack(alignment: .leading, spacing: 0) {
                    T(titulo, 14, 800, C.carbon)
                    if let sub { T(sub, 12, 400, C.texto2) }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(.horizontal, 16).padding(.vertical, 14)
            if borde { Rectangle().fill(C.lavanda).frame(height: 1) }
        }
    }
}
