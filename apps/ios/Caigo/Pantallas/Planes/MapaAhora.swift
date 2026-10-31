import SwiftUI
import MapKit
import Nucleo

private let sombraFlotante = [externa(8, 24, rgba(36, 33, 45, 0.1))]

/// Los filtros del mapa (03).
private let filtrosMapa: [(String?, String)] = [
    (nil, "Próximas 3 h"), ("deporte", "Deporte"), ("previa", "Previa"), ("juego", "Juego"),
    ("salida", "Salida"), ("cita_doble", "Cita doble"), ("taller", "Taller"),
]

/// 03 · Ahora mapa (MapKit): pines con lo que falta, buscador, filtros y la tarjeta flotante del plan
/// elegido.
struct MapaAhora: View {
    var planes: [Plan]
    var punto: (Double, Double)?
    @Binding var categoria: String?
    var alLista: () -> Void
    @Environment(Ir.self) private var ir
    @State private var busqueda = ""
    @State private var elegido: String?
    @State private var camara: MapCameraPosition = .automatic
    @State private var ubicado = false

    private var visibles: [Plan] {
        let q = busqueda.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return planes }
        return planes.filter {
            $0.titulo.localizedCaseInsensitiveContains(q) || $0.lugarNombre.localizedCaseInsensitiveContains(q)
                || ($0.lugar?.nombre.localizedCaseInsensitiveContains(q) ?? false)
        }
    }

    private func region(_ la: Double, _ ln: Double, _ metros: Double = 3500) -> MapCameraPosition {
        .region(MKCoordinateRegion(center: CLLocationCoordinate2D(latitude: la, longitude: ln), latitudinalMeters: metros, longitudinalMeters: metros))
    }

    var body: some View {
        let vis = visibles
        let seleccionado = vis.first { $0.id == elegido } ?? vis.first
        ZStack(alignment: .bottom) {
            Map(position: $camara) {
                if let (la, ln) = punto {
                    Annotation("", coordinate: CLLocationCoordinate2D(latitude: la, longitude: ln), anchor: .center) {
                        ZStack {
                            Circle().fill(rgba(116, 86, 241, 0.18)).frame(width: 56, height: 56)
                            Circle().fill(C.violeta).frame(width: 18, height: 18)
                                .overlay(Circle().strokeBorder(C.blanco, lineWidth: 3))
                                .shadow(color: rgba(36, 33, 45, 0.3), radius: 3, y: 2)
                        }
                    }
                }
                ForEach(vis) { pl in
                    Annotation(pl.titulo, coordinate: CLLocationCoordinate2D(latitude: pl.lat, longitude: pl.lng), anchor: .bottom) {
                        PinMapa(texto: Cupo.badge(pl.faltan), seleccionado: pl.id == seleccionado?.id, completo: Cupo.completo(pl.faltan))
                            .onTapGesture { withAnimation(.easeOut(duration: 0.2)) { elegido = pl.id } }
                    }
                    .annotationTitles(.hidden)
                }
            }
            .mapStyle(.standard(pointsOfInterest: .excludingAll))
            .mapControls { MapCompass().mapControlVisibility(.hidden) }
            .ignoresSafeArea()

            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    HStack(spacing: 10) {
                        Icono(I.lupaChica, 18, C.carbon, trazo: 2.2)
                        PlanesCampo(texto: $busqueda, pista: "Buscá un plan o un lugar", color: C.carbon)
                    }
                    .padding(.horizontal, 16)
                    .frame(height: 50)
                    .gel(18, .liso(C.blanco), sombraFlotante)
                    HStack(spacing: 0) {
                        Button(action: alLista) {
                            T("Lista", 14, 600, C.texto2).padding(.horizontal, 12).frame(height: 42)
                        }
                        .buttonStyle(EstiloPlano())
                        T("Mapa", 14, 700, C.blancoCalido).padding(.horizontal, 12).frame(height: 42).gel(12, Gels.oscuroSel)
                    }
                    .padding(4)
                    .gel(16, .liso(C.blanco), sombraFlotante)
                }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(filtrosMapa, id: \.1) { k, l in
                            let on = k == categoria
                            Button { categoria = k } label: {
                                T(l, 13, on ? 700 : 600, on ? C.blanco : C.carbon, unRenglon: true)
                                    .padding(.horizontal, 13).padding(.vertical, 8)
                                    .gel(13, on ? planesVioletaChico : Gel(fondo: .liso(C.blanco)))
                            }
                            .buttonStyle(EstiloToque())
                        }
                    }
                    .padding(.bottom, 8).padding(.trailing, 16)
                }
            }
            .padding(.horizontal, 16).padding(.top, 8)
            .frame(maxHeight: .infinity, alignment: .top)

            VStack(alignment: .trailing, spacing: 14) {
                Button {
                    if let (la, ln) = punto { withAnimation { camara = region(la, ln, 2200) } }
                } label: {
                    Icono(I.ubicacion, 20, C.violeta, trazo: 2.2)
                        .frame(width: 46, height: 46)
                        .gel(16, .liso(C.blanco), [externa(8, 20, rgba(36, 33, 45, 0.12))])
                }
                .buttonStyle(EstiloToque())
                .accessibilityLabel("Volver a mi ubicación")
                if let s = seleccionado {
                    TarjetaFlotante(plan: s).padding(.horizontal, -4)
                }
            }
            .padding(.horizontal, 16)
            .padding(.bottom, altoTabBar)
        }
        .onAppear {
            if !ubicado, let (la, ln) = punto { camara = region(la, ln); ubicado = true }
        }
        .onChange(of: punto?.0) { _, _ in
            if !ubicado, let (la, ln) = punto { camara = region(la, ln); ubicado = true }
        }
    }
}

/// Pin con el contador: coral (falta), lima (completo) o violeta (elegido), borde blanco y pico.
private struct PinMapa: View {
    var texto: String
    var seleccionado: Bool
    var completo: Bool

    var body: some View {
        let fondo: [Gradient.Stop] = seleccionado
            ? Fondo.paradas((0, Color(hex: 0x9A84FF)), (0.55, C.violeta), (1, Color(hex: 0x6243E6)))
            : completo
                ? Fondo.paradas((0, C.cupoLleno1), (0.5, C.lima), (1, C.cupoLleno2))
                : Fondo.paradas((0, Color(hex: 0xFFA597)), (0.5, C.coral), (1, Color(hex: 0xF2604F)))
        let gradiente = LinearGradient(stops: fondo, startPoint: .top, endPoint: .bottom)
        VStack(spacing: 0) {
            T(texto, 15, 700, seleccionado ? C.blanco : C.carbon, alto: 1, unRenglon: true)
                .padding(.horizontal, 9).padding(.vertical, 5)
                .background(RoundedRectangle(cornerRadius: 11, style: .continuous).fill(gradiente))
                .padding(2.5)
                .background(RoundedRectangle(cornerRadius: 13, style: .continuous).fill(C.blanco))
                .shadow(color: rgba(36, 33, 45, 0.18), radius: 7, y: 6)
            Rectangle().fill(gradiente)
                .frame(width: 7, height: 7)
                .padding(EdgeInsets(top: 0, leading: 0, bottom: 2.5, trailing: 2.5))
                .background(C.blanco)
                .rotationEffect(.degrees(45))
                .offset(y: -6)
                .frame(height: 4, alignment: .top)
        }
        .zIndex(seleccionado ? 2 : 1)
        .scaleEffect(seleccionado ? 1.06 : 1)
    }
}

/// Tarjeta flotante del plan elegido (foto 96×120, radio 26).
private struct TarjetaFlotante: View {
    var plan: Plan
    @Environment(Ir.self) private var ir

    var body: some View {
        HStack(spacing: 12) {
            Foto(url: fotoDe(plan), tinte: colorDeCategoria(plan.categoria))
                .frame(width: 96, height: 120)
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            VStack(alignment: .leading, spacing: 7) {
                HStack(spacing: 6) {
                    T(Categorias.nombre(plan.categoria), 11, 700, C.carbon, unRenglon: true)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(C.lavanda))
                    T(Fecha.cuando(plan.empiezaEn), 11, 700, C.blancoCalido, unRenglon: true)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(C.carbon))
                }
                T(plan.titulo, 18, 700, C.carbon, alto: 1.15, renglones: 2)
                T(lugarYDistancia(plan), 12, 400, C.texto2, unRenglon: true)
                HStack {
                    BadgeFalta(faltan: plan.faltan, tam: 16)
                    Spacer(minLength: 0)
                    if plan.miEstado == "ninguno" && !Cupo.completo(plan.faltan) {
                        Button { ir.plan(plan.id) } label: {
                            Icono(I.mas, 18, C.carbon, trazo: 4).frame(width: 40, height: 40).gel(14, Gels.limaChico)
                        }
                        .buttonStyle(EstiloToque())
                        .accessibilityLabel("Sumarme")
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .gel(26, .liso(C.blanco), [externa(16, 40, rgba(36, 33, 45, 0.18))])
        .contentShape(Rectangle())
        .onTapGesture { ir.plan(plan.id) }
    }
}
