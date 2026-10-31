# Estado

## 31/10/2026 — arranque
Decisiones de Nahuel y plan por etapas: ver `docs/decisiones.md`.

Hecho (etapas 1 y 2 de la API):
- Esqueleto calcado de TengoLibre + Clerk (verificación local del JWT con JWKS; la primera vez que
  alguien entra se crea su fila en `usuarios`).
- Migraciones: `usuarios` / `usuarios_privado` (RLS), `planes`, `planes_direccion` (RLS: sólo
  organizador y aceptados si se oculta), `solicitudes`, `mi_gente`, `volveria` (RLS), `bloqueos`,
  `reportes` (RLS).
- Rutas: `/v1/yo`, `/v1/personas/{id}`, `/v1/gente`, `/v1/bloqueos/{id}`, `/v1/reportes`,
  `/v1/planes` (crear, ahora, explorar, mios, ver, cancelar), `/v1/planes/{id}/sumarme`,
  `/v1/planes/{id}/solicitudes`, `/v1/solicitudes/{id}/aceptar|rechazar`, `/v1/publico/planes/{slug}`.
- Prueba de punta a punta del ciclo del plan (`internal/plataforma/servidor/flujo_test.go`).

## Lo que sigue
3. ~~Chat por WebSocket, push (APNs + FCM) con outbox y worker.~~ Hecho: `/v1/planes/{id}/mensajes`, `/v1/privados`, `/v1/tiempo-real` (WebSocket, LISTEN/NOTIFY `caigo_tiempo_real`), `/v1/dispositivos`, `cmd/worker`.
4. ~~Check-in, ¿Repetimos?, verificación y fotos.~~ Hecho: `/v1/archivos` (cuerpo crudo, ?tipo=foto|selfie|dni; fotos públicas, el resto sólo dueño y equipo), `/v1/verificacion` (teléfono con SMS simulado que devuelve `codigoDePrueba`, selfie, DNI), `/v1/equipo/dni` y `/v1/equipo/reportes` (suspender), `/v1/planes/{id}/qr|checkin|repetimos|en-vivo`, `/v1/publico/vivo/{token}`. `caigoctl equipo user_…` da de alta al equipo.
5. Modos Gente/Citas y Caigo Plus.
6. Lugares, entradas/señas con Mercado Pago, Caigo Tap y Wallet.
7. Web. 8. VPS. 9. iOS. 10. Android.

## Clerk de producción (aviso de la sesión de Tu Local Web, 31/10)
- Instancia compartida `clerk.grupovimo.com.ar` (app «Grupo Vimo»), Google y Apple activos, Access
  mode Open. Claves pk_live/sk_live: dashboard o CREDENCIALES.md de mostrador-v2, nunca al repo.
- No cambiar métodos de ingreso, access mode ni conexiones sociales sin avisar a esa sesión (afecta
  a las cuatro apps). Sumar orígenes y redirects propios sí se puede.
- Android con Google: hace falta un cliente OAuth Android en el proyecto `grupo-vimo` con el SHA-1
  de la firma (pedirlo a esa sesión). iOS con Apple: agrupar `ar.com.caigo.app` con el App ID
  primario `ar.com.tulocalweb.app` (Services ID `ar.com.grupovimo.ingreso`), coordinado.
- Caigo verifica el JWT de Clerk en cada pedido (JWKS en memoria), sin sesión propia: la API no
  guarda sesiones y las apps renuevan el token con el SDK de Clerk.
