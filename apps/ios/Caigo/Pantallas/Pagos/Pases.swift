import SwiftUI
import Nucleo

/// Fondo del pase de socio: violeta con brillo arriba a la derecha y grano.
private let gelSocio = Gel(fondo: .radial(cx: 1, cy: 0, rx: 1.2, ry: 0.8, Fondo.paradas((0, C.violeta2), (0.6, Color(hex: 0x5A3DD8)), (1, Color(hex: 0x3B2A9E)))),
                           sombras: [], grano: true)

private let numeros = ["", "Una", "Dos", "Tres", "Cuatro", "Cinco", "Seis", "Siete", "Ocho", "Nueve", "Diez"]

/// "Dos visitas más y tenés: Taller gratis" (o el beneficio ganado, si hay para canjear).
func pagosTextoSocio(_ s: PaseSocio) -> String {
    if s.pendientes > 0 { return "Ya ganaste: \(s.beneficio). Mostralo en el lugar." }
    let faltan = max(0, s.meta - s.visitas)
    if faltan == 0 { return s.beneficio }
    let n = faltan < numeros.count ? numeros[faltan] : "\(faltan)"
    return "\(n) \(faltan == 1 ? "visita" : "visitas") más y tenés: \(s.beneficio)"
}

/// Mis pases (Pases NFC 1f): las entradas pagadas y los pases de socio de los lugares.
struct VistaPases: View {
    @Environment(Contenedor.self) private var c
    @Environment(Ir.self) private var ir
    @Environment(\.paleta) private var p
    @State private var pases: Pases?
    @State private var cargando = true
    @State private var error: String?
    @State private var fotos: [String: String] = [:]
    @State private var bajando: String?

    var body: some View {
        Pantalla {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack(spacing: 12) {
                        BotonIcono(d: I.atras) { ir.atras() }
                        T("Mis pases", 36, 800, alto: 1, espaciado: -1.3, unRenglon: true)
                    }
                    contenido
                    HStack(spacing: 12) {
                        Icono(I.nfc, 22, p.oscuro ? C.violeta1 : C.violetaTexto, trazo: 2.4)
                        T("Los lugares con Caigo Tap te dejan entrar apoyando el celu.", 13, 700, p.texto, alto: 1.4)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal, 16).padding(.vertical, 14)
                    .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(p.oscuro ? p.superficie : C.lavanda))
                    .tocable { ir.tap() }
                }
                .padding(.horizontal, 18).padding(.top, 6).padding(.bottom, 24)
            }
        }
        .task { await cargar() }
        .refreshable { await cargar() }
    }

    private func cargar() async {
        do { pases = try await c.api.pases(); error = nil } catch { self.error = mensajeDe(error) }
        cargando = false
        // La foto sale del plan (la entrada no la trae).
        for e in pases?.entradas ?? [] where fotos[e.planId] == nil {
            if let f = try? await c.api.plan(e.planId).foto { fotos[e.planId] = f }
        }
    }

    @ViewBuilder
    private var contenido: some View {
        if let d = pases {
            if d.entradas.isEmpty && d.socios.isEmpty {
                MensajeCentro(titulo: "Todavía no tenés pases",
                              cuerpo: "Cuando compres una entrada o sumes visitas en un lugar con Caigo Tap, aparecen acá.")
                    .padding(.top, 60).padding(.bottom, 10)
                Cta(texto: "Ver planes cerca") { ir.principal() }
            } else {
                if !d.entradas.isEmpty {
                    T("ENTRADAS", 12, 800, p.texto2, espaciado: 1.2)
                    ForEach(d.entradas) { e in filaEntrada(e) }
                }
                if !d.socios.isEmpty {
                    T("PASES DE SOCIO", 12, 800, p.texto2, espaciado: 1.2).padding(.top, 6)
                    ForEach(d.socios) { s in socio(s) }
                }
            }
        } else if cargando {
            Puntitos().frame(maxWidth: .infinity).padding(40)
        } else {
            MensajeCentro(titulo: "No pudimos cargar tus pases", cuerpo: error ?? "").padding(.top, 60)
        }
    }

    private func filaEntrada(_ e: EntradaMia) -> some View {
        HStack(spacing: 12) {
            Foto(url: fotos[e.planId]).frame(width: 62, height: 62).clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            VStack(alignment: .leading, spacing: 3) {
                T(e.titulo, 15, 800, C.carbon, renglones: 2)
                let detalle = e.modo == "sena" ? "Señada" : (e.cantidad > 1 ? "\(e.cantidad) entradas" : "1 entrada")
                T("\(Fecha.cuando(e.empiezaEn)) · \(detalle)", 12, 600, C.texto2, unRenglon: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if PagosWallet.disponible {
                Button {
                    agregar(e.id) { try await c.api.pkpassEntrada(e.id) }
                } label: {
                    ZStack {
                        if bajando == e.id { Puntitos(color: C.blanco, tam: 5) } else { T("+ Wallet", 11, 800, C.blanco, alinear: .center, unRenglon: true) }
                    }
                    .padding(.horizontal, 11).padding(.vertical, 8)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(C.carbon))
                }
                .buttonStyle(EstiloToque())
            }
        }
        .padding(12)
        .gel(22, Gels.neutra)
        .tocable { ir.entrada(e.id, plan: e.planId) }
    }

    private func socio(_ s: PaseSocio) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                T(s.lugar, 15, 800, C.blanco)
                Spacer()
                T("\(s.visitas) de \(s.meta)", 12, 800, C.lima, unRenglon: true)
            }
            HStack(spacing: 6) {
                ForEach(0..<min(max(s.meta, 1), 20), id: \.self) { i in
                    Capsule().fill(i < s.visitas ? C.lima : rgba(255, 255, 255, 0.22)).frame(height: 9)
                }
            }
            HStack {
                T(pagosTextoSocio(s), 12, 600, Color(hex: 0xE4DDFF))
                Spacer(minLength: 8)
                if PagosWallet.disponible {
                    if bajando == "socio:" + s.lugarId { Puntitos(color: C.lima, tam: 5) } else { Icono(I.billetera, 18, C.lima, trazo: 2) }
                }
            }
        }
        .padding(16)
        .gel(24, gelSocio)
        .tocable {
            if PagosWallet.disponible { agregar("socio:" + s.lugarId) { try await c.api.pkpassSocio(s.lugarId) } }
        }
    }

    private func agregar(_ clave: String, _ bajar: @escaping () async throws -> Data) {
        guard bajando == nil else { return }
        Task {
            bajando = clave
            do { _ = try await PagosWallet.agregar(bajar) } catch { c.avisos.error(error) }
            bajando = nil
        }
    }
}
