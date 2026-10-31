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
5. ~~Modos Gente/Citas y Caigo Plus.~~ Hecho: `/v1/gente/mazo|{id}/reaccion|recibidos`, `/v1/planes/{id}/invitar`, `/v1/invitaciones`, `/v1/plus` (+ `/apple` con JWS de StoreKit 2 verificado contra Apple Root CA G3, `/google` con la Play Developer API, `/mercadopago` con preapproval), webhooks `/v1/webhooks/apple|google|mercadopago`. Productos en las tiendas: `plus_semana`, `plus_mes`, `plus_temporada`; la app pone appAccountToken (iOS) / obfuscatedAccountId (Android) = id de la persona en Caigo.
6. ~~Lugares, entradas/señas, Caigo Tap y Wallet.~~ Hecho: `/v1/lugares` (alta pendiente, edición, pase de socio), `/v1/equipo/lugares` (aprobar), `/v1/lugares/{id}/planes|asistentes|validar|cobros|placas|mercadopago`, `/v1/mercadopago/vuelta` (OAuth con PKCE), `/v1/planes/{id}/entradas` (Checkout Pro con `marketplace_fee` = cargo 7,5 % redondeado a $100), pagos por webhook → outbox `mp.pago` → worker, `/v1/pases` (entradas y socios), `/v1/tap` (placa común con URL firmada o NTAG 424 con SUN verificado por AES-CMAC), `/v1/pases/entradas|socios/.../apple|google` (.pkpass firmado con PKCS#7 + WWDR G4; link de Google Wallet con JWT), web service de PassKit en `/v1/wallet/v1/...` y aviso de pases cambiados por APNs con el certificado del pase.
7. ~~Web.~~ Hecho en la rama `web`: `apps/web` (SvelteKit 3 + adapter-node, Geist, bun). Un solo
   proyecto para dos hosts: `hooks.ts` (reroute) manda `lugares.*` a `src/routes/panel`; en
   caigo.com.ar `/panel` da 404. Páginas: landing, `/p/<slug>` (pantalla 12), `/vivo/<token>`,
   `/t/<placa>`, `/plus`, `/ingresar` + `/sso-callback` (Clerk en modo nativo de la Frontend API,
   patrón de Tu Local Web), `/equipo`, `/terminos` y `/privacidad` (borrador), y el panel (registro con
   mapa, Tus planes, Nuevo plan, Quiénes vienen + check-in, Caigo Tap, pase de socio, Cobros, vuelta de
   Mercado Pago). La sesión `cs_…` vive en la cookie httpOnly `caigo_sesion` (por host, 60 días) y
   todas las llamadas a la API salen del servidor. Variables en `apps/web/src/env.ts` y
   `.env.ejemplo`. En el VPS: `PORT=3500 HOST=127.0.0.1 node build`, sin `ORIGIN` (son dos hosts):
   hay que poner `PROTOCOL_HEADER=x-forwarded-proto HOST_HEADER=x-forwarded-host` (Caddy los manda)
   para que la URL sea https://<host> y el chequeo de origen de los formularios dé bien. En local, `/ingresar` acepta un token `cs_…` de
   prueba (sólo con `vite dev`). Capturas en `apps/web/capturas/` (no van al repo).
   **Caigo desde la web** (`/app`): las pantallas de Mockups v5 a 390 px (en la compu, dentro del
   marco de teléfono) con la tab bar flotante: Ahora (lista y mapa), Explorar, detalle con Sumarme /
   Somos 2+ / entrada, crear en 3 pasos, Mis planes, solicitudes, chat (sondeo cada 4 s por
   `/app/plan/<id>/chat/mensajes`: el WebSocket pide el token en la URL) y perfil. Antes de entrar,
   una sola vez, la verificación (01d teléfono + 01e selfie con `getUserMedia`, o una foto si no hay
   cámara). `/p/<slug>` con sesión lleva al detalle de `/app`. Gente, Citas, Plus, pases y check-in
   quedan para la app (tarjeta "Bajate la app" con las insignias). El servidor web le pasa a la API la
   IP real (`Cf-Connecting-Ip`) para que los topes por IP no cuenten a todos como 127.0.0.1.
8. VPS (31/10, andando): `/opt/caigo` (compose `caigo`: postgres 256m, api 128m en 127.0.0.1:4300, worker 96m). Secretos generados en el VPS (api.conf y pg.secret, 600); Clerk producción cargado por la sesión de Tu Local Web. DNS en Cloudflare: caigo.com.ar, www, api y lugares → 187.41.102.190 con proxy; zona en Full (strict); cert de origen en /etc/caddy/certs/caigo.{pem,key} (vence 2041, clave generada en el VPS). Caddy: `/etc/caddy/sitios/caigo.caddy` = `infra/vps/caigo-temporal.caddy` (caigo.com.ar y lugares sirven /opt/caigo/proximamente); cuando la web esté en :3500, pasar a `infra/vps/caigo.caddy`. Desplegar la API: `infra/vps/desplegar-api.sh [--migrar]`.
Enlaces universales: `infra/vps/well-known/` servido por Caddy en /.well-known (`apple-app-site-association` y `assetlinks.json`; a este le falta la huella SHA-256 de la firma de Android). CI: `.github/workflows/ci.yml` (API con Postgres y web).
Caigo desde la web (`/app`, 31/10): Ahora (lista y mapa), Explorar y detalle se miran sin cuenta (`/v1/publico/planes/ahora|explorar|{id}`); sumarse, crear, Mis planes, solicitudes, chat (sondeo cada 4 s) y perfil piden ingresar y, una vez, teléfono + selfie con la cámara del navegador (`/app/verificar`). Gente/Citas, Plus, pases y check-in: tarjeta "Bajate la app".
Web publicada (31/10) en https://caigo.com.ar y https://lugares.caigo.com.ar: contenedor `caigo-web` (node:22-alpine, 192m, 127.0.0.1:3500, build en /opt/caigo/web, web.conf con las PUBLIC_*). Desplegar: `infra/vps/desplegar-web.sh`. Caddy ya en `infra/vps/caigo.caddy`.
9. Android (rama `android`, worktree `~/Trabajo/vimo/.wt/caigo-android`): Kotlin + Compose en `apps/android`, todas las pantallas del handoff contra la API. Debug apunta a `caigo.api.debug` de `local.properties` (por defecto `http://10.0.2.2:4300`); en debug hay "Entrar con token" para sesiones `cs_…` creadas a mano. Clave de Clerk (`caigo.clerk.publicable`) y Firebase (`caigo.firebase.*`) van en `local.properties`. Integrada a main el 31/10. Firma de release: llave de subida en `~/Trabajo/vimo/credenciales-android/caigo-subida.jks` (clave al lado, 600; SHA-256 57:21:D7:…:33:16), leída de `local.properties` (`caigo.firma.*`). `assembleRelease` saca APKs por arquitectura (arm64 18 MB). ML Kit por Play Services. `assetlinks.json` con la huella de subida y la de debug (Google: linked). Falta: productos de Play, Firebase, y sumar la huella de Play App Signing cuando exista la app en Play. 10. iOS.

## Clerk de producción (aviso de la sesión de Tu Local Web, 31/10)
- Instancia compartida `clerk.grupovimo.com.ar` (app «Grupo Vimo»), Google y Apple activos, Access
  mode Open. Claves pk_live/sk_live: dashboard o CREDENCIALES.md de mostrador-v2, nunca al repo.
- No cambiar métodos de ingreso, access mode ni conexiones sociales sin avisar a esa sesión (afecta
  a las cuatro apps). Sumar orígenes y redirects propios sí se puede.
- Android con Google: hace falta un cliente OAuth Android en el proyecto `grupo-vimo` con el SHA-1
  de la firma (pedirlo a esa sesión). iOS con Apple: `ar.com.caigo.app` es su propio primary App ID
  (Nahuel decidió NO agruparlas: Apple mostraría «Tu Local Web» al entrar a Caigo). El ingreso web con
  Apple usa el Services ID compartido `ar.com.grupovimo.ingreso` («Grupo Vimo»).
- Orígenes y redirects de Caigo ya cargados: https://caigo.com.ar, https://lugares.caigo.com.ar,
  `/sso-callback` en los dos y `ar.com.caigo.app://callback` (iOS y Android). Cambios: pedirlos a la
  sesión de Tu Local Web.
- Sesión: `POST /v1/sesion` canjea el JWT de Clerk (Authorization) por un token de Caigo `cs_…` (60 días,
  se renueva con el uso); `DELETE /v1/sesion` lo cierra. La API también acepta el JWT de Clerk directo.
