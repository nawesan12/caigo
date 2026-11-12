import SwiftUI
import Nucleo

/// Pestañas de la tab bar flotante.
enum Pestana: String, Hashable { case ahora, explorar, mis, perfil }

/// Lo que va en la base de la pila (lo que en Android limpia la pila con popUpTo(0)).
enum Base: Hashable {
    case arranque
    case intro
    case ingresar
    case debug
    case datos
    case telefono
    case selfie
    case intenciones
    case completarPerfil
    case principal(Pestana, modo: String?)
}

/// Las pantallas que se apilan (las mismas rutas que Navegacion.kt de Android).
enum Ruta: Hashable {
    case ingresar, debug
    /// Entrar con mail y contraseña (o código) por Clerk.
    case ingresoMail
    case telefono
    case selfie(volver: Bool)
    case intenciones(editar: Bool)
    case verificacion
    case dni(plan: String?)
    case plan(String)
    case planSlug(String)
    case crear
    case publicado(String)
    case persona(String, desde: String)
    case recibidos(modo: String, pestana: Int)
    case solicitudes(String)
    case chat(String)
    case privados
    case privado(String, nombre: String)
    case checkin(String)
    case repetimos(String)
    case qrOrganizador(String)
    case escanearQr(String)
    case plus(motivo: String)
    case checkout(String, cantidad: Int)
    case entrada(String, plan: String)
    case qrEntrada(String)
    case pases
    case tap(plan: String?)
    case adentro
    case editarPerfil
    case gente
    /// El organizador edita su plan (PATCH /v1/planes/{id}).
    case editarPlan(String)
    /// Gente que bloqueé (GET/DELETE /v1/bloqueos).
    case bloqueos
    /// Ajustes de avisos push (planes cerca, tu gente).
    case ajustesAvisos
}

/// Las acciones de navegación que usan las pantallas. Ninguna pantalla toca la pila: así cada una se
/// puede armar y probar sola.
@MainActor
@Observable
final class Ir {
    var base: Base = .arranque
    var camino: [Ruta] = []
    /// Pestaña de Principal (se puede cambiar desde cualquier pantalla).
    var pestana: Pestana = .ahora
    var modoAhora: String?
    /// Mientras una hoja está abierta adentro de una pestaña, la tab bar se esconde.
    var tabBarOculta = false

    func atras() {
        // Sin nada apilado no se hace nada: desde el alta no se saltean pasos.
        if !camino.isEmpty { camino.removeLast() }
    }

    func ir(_ r: Ruta) {
        if camino.last == r { return }
        camino.append(r)
    }

    func principal(_ p: Pestana = .ahora, modo: String? = nil) {
        pestana = p
        modoAhora = modo
        tabBarOculta = false
        base = .principal(p, modo: modo)
        camino = []
    }

    func intro() { base = .intro; camino = [] }
    func ingresar() { ir(.ingresar) }
    func debug() { ir(.debug) }
    func ingresoMail() { ir(.ingresoMail) }
    /// Siguiente paso del alta (datos → teléfono → selfie → intenciones) o la app.
    func alta(_ b: Base) {
        if case let .principal(p, m) = b { principal(p, modo: m) } else { base = b; camino = [] }
    }
    func telefono() { ir(.telefono) }
    func selfie(volver: Bool = false) { ir(.selfie(volver: volver)) }
    func intenciones(editar: Bool = false) { ir(.intenciones(editar: editar)) }

    func plan(_ id: String) { ir(.plan(id)) }
    func planSlug(_ slug: String) { ir(.planSlug(slug)) }
    func crear() { ir(.crear) }
    /// Después de publicar: reemplaza Crear por la confirmación.
    func publicado(_ id: String) {
        if camino.last == .crear { camino.removeLast() }
        camino.append(.publicado(id))
    }
    func solicitudes(_ planId: String) { ir(.solicitudes(planId)) }
    func chat(_ planId: String) { ir(.chat(planId)) }
    func privados() { ir(.privados) }
    func privado(_ conId: String, nombre: String) { ir(.privado(conId, nombre: nombre)) }
    func checkin(_ planId: String) { ir(.checkin(planId)) }
    func repetimos(_ planId: String) { ir(.repetimos(planId)) }
    func qrOrganizador(_ planId: String) { ir(.qrOrganizador(planId)) }
    func escanearQr(_ planId: String) { ir(.escanearQr(planId)) }
    func persona(_ id: String, desde: String = "perfil") { ir(.persona(id, desde: desde)) }
    func recibidos(modo: String, pestana: Int = 0) { ir(.recibidos(modo: modo, pestana: pestana)) }
    func plus(motivo: String = "perfil") { ir(.plus(motivo: motivo)) }
    func verificacion() { ir(.verificacion) }
    func dni(plan: String? = nil) { ir(.dni(plan: plan)) }
    func checkout(_ planId: String, cantidad: Int) { ir(.checkout(planId, cantidad: cantidad)) }
    func entrada(_ entradaId: String, plan: String) { ir(.entrada(entradaId, plan: plan)) }
    func qrEntrada(_ entradaId: String) { ir(.qrEntrada(entradaId)) }
    func pases() { ir(.pases) }
    func tap(plan: String? = nil) { ir(.tap(plan: plan)) }
    /// Después de un tap: reemplaza la pantalla de la placa por "Adentro".
    func adentro() {
        if case .tap = camino.last { camino.removeLast() }
        camino.append(.adentro)
    }
    func editarPerfil() { ir(.editarPerfil) }
    func gente() { ir(.gente) }
    func editarPlan(_ id: String) { ir(.editarPlan(id)) }
    func bloqueos() { ir(.bloqueos) }
    func ajustesAvisos() { ir(.ajustesAvisos) }
}
