import Foundation
import Security

/// La sesión de Caigo (`cs_…`, 60 días) y el id de la persona, en el Keychain del dispositivo
/// (sólo este dispositivo, accesible después del primer desbloqueo para que el push la pueda usar).
///
/// La app y el App Clip de Caigo Tap la comparten: los dos guardan en el grupo de llavero
/// `<Team ID>.ar.com.caigo.app` (entitlement `keychain-access-groups`), así quien entró desde la placa
/// ya está adentro al instalar la app. Si el grupo no está firmado (simulador sin firma), se usa el
/// llavero propio de cada una.
public enum Llavero {
    static let servicio = "ar.com.caigo.app.sesion"

    /// El grupo compartido (`$(AppIdentifierPrefix)ar.com.caigo.app`, sale del Info.plist).
    nonisolated(unsafe) public static var grupo: String?

    private static func base(_ cuenta: String, grupo: String?) -> [String: Any] {
        var q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servicio,
            kSecAttrAccount as String: cuenta,
        ]
        if let grupo, !grupo.isEmpty { q[kSecAttrAccessGroup as String] = grupo }
        return q
    }

    public static func guardar(_ valor: String, cuenta: String) {
        borrar(cuenta)
        for g in [grupo, nil] {
            var q = base(cuenta, grupo: g)
            q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            q[kSecValueData as String] = Data(valor.utf8)
            if SecItemAdd(q as CFDictionary, nil) == errSecSuccess { return }
            if grupo == nil { return }
        }
    }

    public static func leer(_ cuenta: String) -> String? {
        for g in [grupo, nil] {
            var q = base(cuenta, grupo: g)
            q[kSecReturnData as String] = true
            q[kSecMatchLimit as String] = kSecMatchLimitOne
            var r: CFTypeRef?
            if SecItemCopyMatching(q as CFDictionary, &r) == errSecSuccess, let d = r as? Data { return String(data: d, encoding: .utf8) }
            if grupo == nil { break }
        }
        return nil
    }

    public static func borrar(_ cuenta: String) {
        for g in [grupo, nil] {
            SecItemDelete(base(cuenta, grupo: g) as CFDictionary)
            if grupo == nil { break }
        }
    }
}

/// El contenedor del App Group `group.ar.com.caigo.app`: el camino que Apple documenta para pasarle
/// datos del App Clip a la app completa. El Clip deja la sesión acá; la app, al abrir, la pasa a su
/// llavero y la borra.
public enum Traspaso {
    public static let grupo = "group.ar.com.caigo.app"
    static var defaults: UserDefaults? { UserDefaults(suiteName: grupo) }

    public static func dejar(token: String, usuarioId: String) {
        defaults?.set(token, forKey: "sesion.token")
        defaults?.set(usuarioId, forKey: "sesion.usuario")
    }

    /// Lo que dejó el Clip (y lo borra).
    public static func tomar() -> (token: String, usuarioId: String)? {
        guard let d = defaults, let t = d.string(forKey: "sesion.token"), !t.isEmpty else { return nil }
        let u = d.string(forKey: "sesion.usuario") ?? ""
        d.removeObject(forKey: "sesion.token")
        d.removeObject(forKey: "sesion.usuario")
        return (t, u)
    }

    public static func borrar() {
        defaults?.removeObject(forKey: "sesion.token")
        defaults?.removeObject(forKey: "sesion.usuario")
    }
}
