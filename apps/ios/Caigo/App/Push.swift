import SwiftUI
#if os(iOS)
import UIKit
import UserNotifications
#endif

/// Push con APNs: se pide permiso al entrar, el token se manda a `PUT /v1/dispositivos` y al tocar un
/// aviso se abre lo que diga `pantalla` / `plan` / `privado` / `modo` (como Android).
@MainActor
enum Push {
    static weak var contenedor: Contenedor?

    static func pedirPermiso() {
        #if os(iOS)
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { ok, _ in
            guard ok else { return }
            Task { @MainActor in UIApplication.shared.registerForRemoteNotifications() }
        }
        #endif
    }

    static func token(_ datos: Data) {
        let hex = datos.map { String(format: "%02x", $0) }.joined()
        contenedor?.tokenPush = hex
        contenedor?.registrarPush()
    }

    static func abrir(_ info: [AnyHashable: Any]) {
        func s(_ k: String) -> String? { (info[k] as? String).flatMap { $0.isEmpty ? nil : $0 } }
        let pantalla = s("pantalla"), plan = s("plan"), privado = s("privado")
        guard pantalla != nil || plan != nil || privado != nil else { return }
        contenedor?.destino = .pantalla(pantalla: pantalla ?? "", plan: plan, privado: privado, modo: s("modo"))
    }
}
