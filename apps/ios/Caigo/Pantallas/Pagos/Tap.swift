import SwiftUI
import Nucleo
#if os(iOS)
import CoreNFC
#endif

private let fondoTap = Fondo.radial(cx: 0.5, cy: 0.6, rx: 0.9, ry: 0.55, Fondo.paradas((0, Color(hex: 0x3B2F78)), (0.75, C.oscuro6)))
private let textoTenue = Color(hex: 0xC9C4DA)

/// Lector de placas Caigo Tap con Core NFC: lee el registro NDEF con la URL firmada de la placa.
@MainActor
final class PagosLectorNfc: NSObject {
    var alLeer: (URL) -> Void = { _ in }
    var alFallar: (String) -> Void = { _ in }

    static var disponible: Bool {
        #if os(iOS)
        return NFCNDEFReaderSession.readingAvailable
        #else
        return false
        #endif
    }

    #if os(iOS)
    private var sesion: NFCNDEFReaderSession?
    #endif

    func leer() {
        #if os(iOS)
        guard NFCNDEFReaderSession.readingAvailable else { return }
        let s = NFCNDEFReaderSession(delegate: self, queue: nil, invalidateAfterFirstRead: true)
        s.alertMessage = "Apoyá el celu en la placa Caigo Tap."
        s.begin()
        sesion = s
        #endif
    }
}

#if os(iOS)
extension PagosLectorNfc: NFCNDEFReaderSessionDelegate {
    nonisolated func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) {
        let url = messages.lazy.flatMap(\.records).compactMap { $0.wellKnownTypeURIPayload() }.first
        Task { @MainActor in
            if let url { self.alLeer(url) } else { self.alFallar("Esa placa no es de Caigo Tap.") }
        }
    }

    nonisolated func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
        let e = error as? NFCReaderError
        // Cancelar, o la sesión que se cierra sola después de leer, no son errores para mostrar.
        guard let e, e.code != .readerSessionInvalidationErrorUserCanceled,
              e.code != .readerSessionInvalidationErrorFirstNDEFTagRead else { return }
        Task { @MainActor in self.alFallar("No pudimos leer la placa. Probá de nuevo o escaneá el QR.") }
    }
}
#endif

/// Onda concéntrica que late.
private struct Onda: View {
    var tam: CGFloat
    var borde: CGFloat
    var alfaBorde: Double
    var relleno: Double = 0
    var retraso: Double = 0
    var late = true
    @State private var grande = false
    var body: some View {
        Color.clear.frame(width: tam, height: tam)
            .gel(tam / 2, .liso(rgba(200, 243, 107, relleno)), [interna(0, 0, rgba(200, 243, 107, alfaBorde), spread: -borde)])
            .scaleEffect(late ? (grande ? 1.04 : 0.94) : 1)
            .onAppear {
                guard late else { return }
                withAnimation(.easeInOut(duration: 1.4).repeatForever(autoreverses: true).delay(retraso)) { grande = true }
            }
    }
}

/// Caigo Tap (Pases NFC 1d / prototipo `tap`): "Apoyá el celu en la placa". Lee la placa por NFC o
/// su QR; también toma una placa ya leída por un link (`/t/…`).
struct Tap: View {
    var planId: String?
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @State private var plan: Plan?
    @State private var escaneando = false
    @State private var mandando = false
    @State private var lector = PagosLectorNfc()

    var body: some View {
        Pantalla(fondo: AnyView(ZStack { C.oscuro6; VistaFondo(fondo: fondoTap); Grano() })) {
            VStack(spacing: 18) {
                if let p = plan {
                    HStack(spacing: 12) {
                        Image("CaigoIcono").resizable().frame(width: 40, height: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            T(p.titulo, 14, 800, C.blancoCalido, unRenglon: true)
                            T(pagosCuandoDonde(p.empiezaEn, pagosLugarDe(p)), 12, 600, textoTenue, unRenglon: true)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(12)
                    .gel(18, Gel(fondo: .liso(C.carbon), sombras: [interna(0, 0, rgba(255, 255, 255, 0.08), spread: -1)]))
                }
                ZStack {
                    if escaneando {
                        PagosEscanerQr { texto in Task { await leido(texto) } }
                            .frame(width: 280, height: 280)
                            .background(C.oscuro8)
                            .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 30, style: .continuous).strokeBorder(C.lima, lineWidth: 3))
                    } else {
                        Onda(tam: 300, borde: 1.5, alfaBorde: 0.14)
                        Onda(tam: 225, borde: 2, alfaBorde: 0.28, retraso: 0.2)
                        Onda(tam: 150, borde: 2.5, alfaBorde: 0.55, relleno: 0.1, late: false)
                        Button { lector.leer() } label: {
                            ZStack {
                                if mandando { Puntitos(color: C.carbon) } else { Icono(I.nfc, 44, C.carbon, trazo: 2.4) }
                            }
                            .frame(width: 92, height: 92)
                            .gel(30, Gel(fondo: Gels.fondoLima, sombras: [
                                interna(3, 2, rgba(255, 255, 255, 0.85)), interna(-5, 0, rgba(96, 140, 10, 0.32)),
                                externa(5, 0, Color(hex: 0x7FA82E)), externa(0, 60, rgba(200, 243, 107, 0.5)),
                            ]))
                        }
                        .buttonStyle(EstiloToque())
                        .accessibilityLabel("Leer la placa")
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                T(escaneando ? "Escaneá el QR de la placa" : "Apoyá el celu en la placa Caigo Tap",
                  30, 800, C.blancoCalido, alto: 1.05, espaciado: -1, alinear: .center)
                T(escaneando ? "Apuntá la cámara al código de la placa del lugar."
                  : PagosLectorNfc.disponible ? "Tocá el botón y acercá la parte de arriba del celu a la placa del lugar."
                  : "Este teléfono no lee placas. Escaneá el QR de la placa.",
                  14, 600, textoTenue, alto: 1.5, alinear: .center)
                HStack(spacing: 6) {
                    Icono(escaneando ? I.nfc : I.qr, 16, C.lima, trazo: 2.2)
                    T(escaneando ? "Usar la placa" : "Escanear el QR de la placa", 14, 800, C.lima)
                }
                .padding(.horizontal, 14).padding(.vertical, 6)
                .tocable {
                    escaneando.toggle()
                    if !escaneando { lector.leer() }
                }
                T("Cancelar", 14, 700, textoTenue).padding(.horizontal, 18).padding(.vertical, 12).tocable { ir.atras() }
            }
            .padding(.horizontal, 24).padding(.top, 14).padding(.bottom, 30)
        }
        .environment(\.paleta, .oscura)
        .task {
            lector.alLeer = { url in Task { await leido(url.absoluteString) } }
            lector.alFallar = { m in c.avisos.error(m) }
            if let planId { plan = try? await c.api.plan(planId) }
            // Placa ya leída (link /t/… o NFC con la app cerrada).
            if case let .tap(placa, picc, cmac)? = c.tapPendiente {
                c.tapPendiente = nil
                await mandar(Lectura(placa: placa, picc: picc, cmac: cmac))
            } else if PagosLectorNfc.disponible {
                lector.leer()
            } else {
                escaneando = true
            }
        }
        .onChange(of: c.destino) { _, d in
            // Un link de placa que llega con la pantalla abierta.
            if case let .tap(placa, picc, cmac)? = d {
                c.destino = nil
                Task { await mandar(Lectura(placa: placa, picc: picc, cmac: cmac)) }
            }
        }
    }

    private func leido(_ texto: String) async {
        guard let url = URL(string: texto.trimmingCharacters(in: .whitespacesAndNewlines)),
              case let .tap(placa, picc, cmac)? = c.destinoDe(url) else {
            c.avisos.error("Ese código no es de una placa de Caigo Tap.")
            return
        }
        await mandar(Lectura(placa: placa, picc: picc, cmac: cmac))
    }

    private func mandar(_ l: Lectura) async {
        guard !mandando else { return }
        mandando = true
        defer { mandando = false }
        do {
            c.ultimoTap = try await c.api.tap(l)
            Sistema.vibrar()
            ir.adentro()
        } catch {
            c.avisos.error(error)
            escaneando = false
        }
    }
}
