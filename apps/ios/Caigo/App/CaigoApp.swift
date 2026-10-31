import SwiftUI
import Nucleo
#if os(iOS)
import UIKit
import UserNotifications
#endif

@main
struct CaigoApp: App {
    #if os(iOS)
    @UIApplicationDelegateAdaptor(Delegado.self) private var delegado
    #endif
    @State private var c = Contenedor()
    @State private var ir = Ir()
    @Environment(\.scenePhase) private var fase

    var body: some Scene {
        WindowGroup {
            CaigoRaiz()
                .environment(c)
                .environment(ir)
                .onOpenURL { url in
                    // La vuelta de Clerk la toma ASWebAuthenticationSession; el resto son planes y placas.
                    if let d = c.destinoDe(url) { c.destino = d }
                }
                .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { a in
                    if let url = a.webpageURL, let d = c.destinoDe(url) { c.destino = d }
                }
                .onAppear {
                    Push.contenedor = c
                    if c.conSesion { c.tiempoReal.encender() }
                }
        }
        .onChange(of: fase) { _, f in
            // El WebSocket vive mientras la app está en primer plano.
            if f == .active, c.conSesion { c.tiempoReal.encender() }
            if f == .background { c.tiempoReal.apagar() }
        }
    }
}

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

#if os(iOS)
final class Delegado: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().getNotificationSettings { s in
            if s.authorizationStatus == .authorized {
                Task { @MainActor in application.registerForRemoteNotifications() }
            }
        }
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { @MainActor in Push.token(deviceToken) }
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound]
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        await MainActor.run { Push.abrir(info) }
    }
}
#endif
