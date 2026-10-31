import Foundation
import Security

/// La sesión de Caigo (`cs_…`, 60 días) y el id de la persona, en el Keychain del dispositivo
/// (sólo este dispositivo, accesible después del primer desbloqueo para que el push la pueda usar).
public enum Llavero {
    static let servicio = "ar.com.caigo.app.sesion"

    public static func guardar(_ valor: String, cuenta: String) {
        borrar(cuenta)
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servicio,
            kSecAttrAccount as String: cuenta,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            kSecValueData as String: Data(valor.utf8),
        ]
        SecItemAdd(q as CFDictionary, nil)
    }

    public static func leer(_ cuenta: String) -> String? {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servicio,
            kSecAttrAccount as String: cuenta,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var r: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &r) == errSecSuccess, let d = r as? Data else { return nil }
        return String(data: d, encoding: .utf8)
    }

    public static func borrar(_ cuenta: String) {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: servicio,
            kSecAttrAccount as String: cuenta,
        ]
        SecItemDelete(q as CFDictionary)
    }
}
