// swift-tools-version: 5.9
// Lo que no depende de la pantalla: modelos de la API, cliente HTTP, Clerk, tiempo real, llavero
// y la lógica pura (cupo, plata, DNI, mazo, fechas, íconos). Se prueba con `swift test` en la Mac,
// sin Xcode ni el SDK de iOS.
import PackageDescription

let package = Package(
    name: "Nucleo",
    defaultLocalization: "es",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [.library(name: "Nucleo", targets: ["Nucleo"])],
    targets: [
        .target(name: "Nucleo"),
        .testTarget(name: "NucleoTests", dependencies: ["Nucleo"], resources: [.copy("Ejemplos")]),
    ]
)
