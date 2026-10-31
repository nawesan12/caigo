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
                    if c.conSesion {
                        c.tiempoReal.encender()
                        c.mandarUbicacion()
                    }
                }
        }
        .onChange(of: fase) { _, f in
            // El WebSocket vive mientras la app está en primer plano.
            if f == .active, c.conSesion {
                c.tiempoReal.encender()
                c.mandarUbicacion()
            }
            if f == .background { c.tiempoReal.apagar() }
        }
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
