# Estado

## Cómo retomar (actualizado 31/10/2026, cierre del día)
**Todo está en `main`** (sin ramas ni worktrees abiertos). Producción: API 52cca06 y web en el VPS
(`/opt/caigo`). iOS en **TestFlight** (1.0.0 build 5; Nahuel la instaló y la vio bien). Android en
main, compilado en GitHub (sin APK nuevo publicado). CI verde: api, web, android, ios.

**Próximo paso acordado:** una revisión con Nahuel de la app en el iPhone (TestFlight) y de la web.

**Reglas de Nahuel para trabajar:**
- Android e iOS se compilan SÓLO en GitHub (CI y workflow `testflight`), nunca en la Mac ni con
  simulador. No armar APK salvo que lo pida. No repetir pruebas pesadas que ya pasaron.
- Todo a main; desplegar con `infra/vps/desplegar-api.sh [--migrar]` (hace pg_dump antes de migrar)
  y `infra/vps/desplegar-web.sh`.
- UI fiel al handoff, sin renglones viudos, copy rioplatense. No crear cuentas externas sin su ok
  (ya autorizó «configurá vos todo» para Apple, MP y Firebase).
- Coordinar Xcode/TestFlight y cuentas compartidas (Clerk, Firebase grupo-vimo, correo) con la sesión
  de Tu Local Web.

**Web (31/10, en producción):** además de todo lo de la auditoría: chats privados (`/app/mensajes`,
perfil de persona, Mi gente), menú del chat del plan (ver, reportar, salir/cancelar), mapa oscuro,
pedido de placas Caigo Tap desde el panel (+ pestaña «Placas» en /equipo).

**SEO y OG (31/10, en producción):** imágenes OG dinámicas por plan (`/p/<slug>/og.png`) y fijas
(`/og/<nombre>.png`) con satori + resvg en WASM (sin binarios nativos), JSON-LD (Event por plan,
Organization/WebSite en la portada), sitemap.xml y robots.txt por host, manifest e íconos
(`scripts/iconos.ts`). La primera vez una OG tarda ~3 s en el VPS; después sale de caché.

**Documentos clave:** `docs/auditoria.md` (lo que faltaba contra el handoff),
`docs/contrato-faltantes.md` (API de esos faltantes, ya implementada), `docs/api.md` (todas las
rutas), `docs/cuentas-externas.md` (estado de Apple, Google, MP), `docs/tiendas.md` (fichas),
`docs/semilla.md`.

**Pendiente (en orden):**
1. ~~Mercado Pago.~~ Hecho (1/11, desde la sesión de TengoLibre): app «Caigo» 1737058147653670 con
   credenciales de producción activas, PKCE en «Sí», vuelta https://lugares.caigo.com.ar/mercadopago/vuelta
   y webhook https://api.caigo.com.ar/v1/webhooks/mercadopago. Las 4 `CAIGO_MP_*` están cargadas en
   `/opt/caigo/api.conf` (copia previa: `api.conf.antes-mp`; sólo ahí, no hay CREDENCIALES.md local),
   con api y worker reiniciados; el token responde con la cuenta 1137276568. Migrado a la API de Orders
   (7/11, sin desplegar): las entradas y señas se cobran con `POST /v1/orders` (Checkout Pro, `marketplace_fee`
   = cargo) y el webhook entiende `type: order` además de `payment` (entradas viejas con preferencia) y del
   preapproval de Plus (sin cambios). Migración 00017 (`entradas.mp_orden`, `mp_transaccion`): pg_dump antes.
   Las apps no cambian (`link` sigue siendo la URL de pago). Falta: desplegar; tildar el evento «Order (Mercado
   Pago)» en los webhooks de la app «Caigo» en el panel de MP; y probar una entrada real (pago, aviso → entrada
   pagada con `mp_orden`/`mp_transaccion`, el cargo acreditado a Caigo, y una devolución parcial y una total).
2. Firebase: hecho (31/10). Caigo en el proyecto común `grupo-vimo`; `local.properties` con appId,
   remitente, proyecto y clave (sale de ~/Trabajo/vimo/firebase/google-services.json); servidor con
   `/opt/caigo/claves/fcm.json` (cuenta fcm-caigo@grupo-vimo) y el worker en `fcm=true`. Ojo: los
   builds de GitHub no tienen local.properties; para un APK desde CI hay que pasar esos valores y la
   llave de subida como secretos del repo.
3. Play Console (cuenta «Grupo Vimo»): Nahuel tiene que verificar un Android y su teléfono. Después:
   app `ar.com.caigo.app`, suscripciones plus_semana/mes/temporada, cuenta de servicio, Pub/Sub y
   huella de Play App Signing en assetlinks.json. Cuenta personal: prueba cerrada 12 testers × 14 días.
4. iPhone en TestFlight (31/10): build 5 (1.0.0) VALID. Se compila y sube SÓLO desde GitHub
   (workflow `testflight`, a mano: `gh workflow run testflight`; el build = número de corrida), nunca
   en la Mac ni con simulador (pedido de Nahuel). Firma manual en Release con el certificado de
   distribución 93F3KP32L3 y los perfiles «Caigo App Store» / «Caigo Clip App Store» (archivos en
   ~/Trabajo/vimo/credenciales-apple/caigo-distribucion/, 600; secretos del repo CAIGO_DIST_P12,
   CAIGO_DIST_P12_CLAVE, CAIGO_PERFIL_APP, CAIGO_PERFIL_CLIP, ASC_KEY_*, CAIGO_CLERK_PUBLICABLE).
   Grupo interno de TestFlight «Equipo» con Nahuel. Si se agregan capacidades, hay que regenerar los
   perfiles con `infra/apple/asc.py` (ver infra/apple/README.md). Hecho el 7/11 en App Store Connect: experiencia
   predeterminada del App Clip (versión 1.0, «Abrir», cabecera 1800×1200 con ingreso_previa.jpg) y avanzada para
   `https://caigo.com.ar/t` (Caigo Tap); las 3 suscripciones con nota y captura de revisión, grupo «Caigo Plus»
   (quedaron READY_TO_SUBMIT). Build 11 subida.
5. ~~SMS real~~ (7/11): el código del teléfono sale por mail a la casilla verificada de Clerk con el
   SMTP propio (`CAIGO_SMTP`, `CAIGO_SMTP_USUARIO`, `CAIGO_SMTP_CLAVE`, `CAIGO_CORREO_DE`; ver docs/api.md).
   Desplegado el 7/11 (2200d3d) y andando: casilla `sistema@caigo.com.ar` en el Stalwart (miembro del grupo
   hola@, como sistema@ de las otras apps), variables en `/opt/caigo/api.conf` (copia previa
   `api.conf.antes-smtp`). Al cambiar api.conf: `docker compose up -d --force-recreate api worker` (restart no
   relee el archivo).
6. Revisión con Nahuel de la app en el iPhone (TestFlight) y de la web: lo que salga, se arregla y
   se sube un build nuevo con `gh workflow run testflight`.
7. Probar en dispositivos reales lo que no se probó: Android en un teléfono (falta APK nuevo desde CI
   con Firebase y firma como secretos), Wallet, NFC con placa, pagos reales de MP, avisos push reales.


## Selfies y DNI cifrados, y quién los mira (12/11, sin commitear ni desplegar)

Lo pedía la AAIP (`~/Trabajo/vimo/legales/aaip/pendientes.md` › 5. Seguridad): las selfies y las fotos
del DNI estaban sin cifrar en `/opt/caigo/archivos` y no quedaba registro de quién del equipo las miraba.

- **Cifrado** (`internal/plataforma/archivos/verificacion.go`): AES-256-GCM con la clave de
  `CAIGO_CLAVE_VERIFICACION` (32 bytes en base64). Archivo = `CGV1` + nonce al azar (12 bytes) + cifrado;
  el nombre termina en `.cif` (`dni/2026-11/<azar>.jpg.cif`) y va como dato asociado (un archivo copiado
  encima de otro no abre). Sólo selfie y DNI: las fotos de perfil y de planes siguen igual, públicas y
  con caché. Se descifra en memoria y se sirve sólo por `GET /v1/archivos/{id}` (dueña o equipo), con
  `Cache-Control: private, no-store`. Los archivos viejos sin `.cif` se siguen leyendo hasta migrarlos.
- **Sin la clave** (o mal cargada): la API y el worker arrancan; el log dice `sin CAIGO_CLAVE_VERIFICACION`
  (o `inválida`) y subir o ver una selfie o un DNI responde 503 `sin_clave_verificacion`. Todo lo demás anda.
- **Auditoría** (migración `00019_accesos_verificacion.sql`): tabla `accesos_verificacion` (equipo_id,
  usuario_id, archivo_id, tipo, ip, visto_en), con RLS por `usuario_id` y la app con SELECT/INSERT/DELETE
  (sin UPDATE). Cada vez que alguien del equipo abre una selfie o un DNI ajeno se anota **antes** de
  mostrarlo (si no se puede anotar, no se muestra). La dueña viendo lo suyo no se anota. IP: `Cf-Connecting-Ip`
  (el proxy `/archivo/[id]` del backoffice web ahora la reenvía). El worker borra los de más de 2 años.
  Consultar: `SELECT * FROM accesos_verificacion ORDER BY visto_en DESC` (con `app.sistema = 'si'`, hay RLS forzada).
- **Migración de lo que ya hay**: `caigoctl cifrar-verificaciones [--probar]`. Por archivo: escribe el `.cif`,
  lo relee y confirma que descifra igual, cambia la ruta en la base y recién ahí borra el original.
  Idempotente (lo ya cifrado no se toca; si un corte dejó el original al lado del `.cif`, lo compara y lo
  borra). `--probar` no escribe nada. Imprime `revisados / cifrados / ya cifrados / originales sueltos /
  faltan en el disco` (y el id de los que faltan). Va con el migrador (la app no puede cambiar `archivos.ruta`).
- **Purga a los 30 días**: sigue igual y no necesita la clave; `Disco.Borrar` borra el `.cif` y, si quedó,
  el original al lado.

### Desplegarlo (lo hace Nahuel, en este orden: clave → deploy → comando → verificar)

1. **Clave** (en el VPS, una sola vez; no la generes en otro lado ni la pegues en un chat):
   ```bash
   ssh deploy@187.41.102.190
   cd /opt/caigo && cp -p api.conf api.conf.antes-verificacion
   printf 'CAIGO_CLAVE_VERIFICACION=%s\n' "$(openssl rand -base64 32)" >> api.conf
   chmod 600 api.conf && grep -c CAIGO_CLAVE_VERIFICACION api.conf   # tiene que dar 1
   ```
   Guardá una copia de la clave **fuera del VPS** (en `CREDENCIALES.md` › Caigo): sin ella, los DNI y
   selfies cifrados no se recuperan nunca. No va al repo ni a las copias de la base.
   api y worker leen `/opt/caigo/api.conf` (`env_file` del compose); el deploy los recrea abajo.
2. **Deploy** desde la copia limpia (`~/Trabajo/vimo/.wt/desplegar` en main), con migración (hace pg_dump antes):
   ```bash
   infra/vps/desplegar-api.sh --migrar
   ssh deploy@187.41.102.190 'cd /opt/caigo && docker compose up -d --force-recreate api worker'
   infra/vps/desplegar-web.sh        # el proxy del backoffice que reenvía la IP
   ```
   (`restart` no relee api.conf: por eso el `--force-recreate`.) Comprobar que no diga «sin
   CAIGO_CLAVE_VERIFICACION»: `docker compose logs api --since 5m | grep -i verificacion` (vacío = bien).
3. **Comando**, primero en seco y después de verdad:
   ```bash
   ssh deploy@187.41.102.190 'cd /opt/caigo && docker compose run --rm --no-deps -T api /app/bin/caigoctl cifrar-verificaciones --probar'
   ssh deploy@187.41.102.190 'cd /opt/caigo && docker compose run --rm --no-deps -T api /app/bin/caigoctl cifrar-verificaciones'
   ```
   «faltan en el disco» > 0 no frena: son filas cuyo archivo ya no estaba (se listan los ids para revisar).
4. **Verificar**:
   - Correr el comando otra vez: `cifrados: 0`.
   - En el disco no queda nada sin cifrar: `find /opt/caigo/archivos/selfie /opt/caigo/archivos/dni -type f ! -name '*.cif' | wc -l` → 0.
   - Desde `/equipo` (web) abrir un DNI pendiente: se ve; y queda la fila:
     `docker compose exec -T postgres psql -U postgres caigo -c "SET app.sistema = 'si'; SELECT equipo_id, usuario_id, tipo, ip, visto_en FROM accesos_verificacion ORDER BY visto_en DESC LIMIT 5"`.
   - Subir una selfie desde la app: el archivo nuevo termina en `.cif`.

**Copias de seguridad:** las copias guardan los archivos tal cual están en el disco, así que desde la
migración guardan selfies y DNI **cifrados** (y la clave no está en ellas, sólo en api.conf y en
CREDENCIALES.md). Las copias locales de los 14 días anteriores en el VPS siguen teniendo los originales
sin cifrar hasta que roten: si se quiere cerrar eso ya, borrarlas a mano después de verificar el paso 4.
Las de GitHub sólo llevan bases (no archivos). Para restaurar archivos en otro servidor hace falta la
misma `CAIGO_CLAVE_VERIFICACION`.

## Privacidad AAIP 3.5: vetados, plazos, fotos y legales en las apps (12/11, sin commitear ni desplegar)

Lo de `~/Trabajo/vimo/legales/aaip/pendientes.md` › 1.3 y 3.5 (menos el género y la diversidad: decisión pendiente
de Nahuel, marcada con «PENDIENTE (decisión del dueño, AAIP 1.2)» en `personas.go`, `confianza.go`, `baja.go` y la
política).

- **Vetados** (`internal/plataforma/vetados`, migración `00020_vetados_y_plazos.sql`): al suspender, HMAC del mail y
  del DNI aprobado; el alta y el DNI los consultan. Sin variable nueva: la clave sale de `CAIGO_CLAVE_VERIFICACION`
  por HKDF (si se rota, la lista vuelve a cero). El worker ahora también la lee. Detalle en `docs/api.md`.
- **Plazos** (`internal/dominio/confianza/plazos.go`, en el worker): mensajes, privados, asistencia, «¿Volverías?»,
  reportes de cuentas purgadas, y `intereses` a los 6 meses. Son los de la política: cambian juntos.
- **Fotos sacadas** (`internal/plataforma/archivos/fotos.go`): se borran en el momento y del disco con `archivo.borrar`;
  las sueltas, a partir del día. Ojo: las fotos se sirven con `Cache-Control: public, max-age=31536000, immutable`,
  así que un navegador (o Cloudflare, si tiene una regla de caché para `/v1/archivos`) puede seguir mostrando su copia.
- **Arreglo de la purga:** borraba `archivos` antes que `verificaciones_dni` (FK): con un DNI cargado, la purga fallaba
  entera. Ahora va primero el DNI.
- **Web:** política reescrita (ingreso con mail, prueba de vida real, el código por mail, cookies, Hostinger en Brasil,
  Cloudflare, GitHub, OSM/OpenFreeMap, geocodificadores, el texto en los push, Citas, plazos, cifrado y accesos);
  `/borrar-cuenta` con los plazos; la nota de la selfie web ya no dice «Prueba de vida».
- **Apps:** «Al entrar aceptás los términos y la política de privacidad» en Ingresar y dos filas en Perfil › Tu
  cuenta (iOS y Android a la par). Sin pantallas nuevas (paridad.txt igual).
- **Desplegar:** `infra/vps/desplegar-api.sh --migrar` (pg_dump antes), recrear api y worker, y `desplegar-web.sh`.
  Las apps, en el próximo build.

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
4. ~~Check-in, ¿Repetimos?, verificación y fotos.~~ Hecho: `/v1/archivos` (cuerpo crudo, ?tipo=foto|selfie|dni; fotos públicas, el resto sólo dueño y equipo), `/v1/verificacion` (teléfono con código por mail, simulado sin SMTP con `codigoDePrueba`, selfie, DNI), `/v1/equipo/dni` y `/v1/equipo/reportes` (suspender), `/v1/planes/{id}/qr|checkin|repetimos|en-vivo`, `/v1/publico/vivo/{token}`. `caigoctl equipo user_…` da de alta al equipo.
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

## Plus completo y pagar todo / señar (31/10, en producción c701204)
- Las filas del paywall que antes no hacían nada ahora andan (ver el final de `docs/api.md`):
  «Me encanta» 1 por día gratis y 5 con Plus (quien lo recibe lo ve primero), plan destacado
  (Plus, 1 por semana, va primero en Ahora/Explorar) y filtros finos (verificados, faltan, edad de
  quien organiza, sólo gratis: Plus; sin Plus, 403 `plus`). Migración 00009.
- Comprar entradas con seña: el que compra elige «Pagar todo» o «Señar N %» (`modo`), como el
  Prototipo. Web hecha; Android en la rama `android`.

## 31/10 (tarde): legales, baja, iOS
- Borrar la cuenta: `DELETE /v1/yo` (web `/app/borrar`, Android e iOS en Perfil).
- Términos, Privacidad (Ley 25.326) y botón de arrepentimiento (`/arrepentimiento`, Res. 424/2020;
  pestaña en /equipo, aviso push al equipo). Titular: Nahuel Santillán, CUIT 20-44859234-1.
  Casillas hola@, soporte@, privacidad@ y postmaster@caigo.com.ar (Stalwart de Tu Local Web).
- Fichas de las tiendas: `docs/tiendas.md`. Planes semilla cargados (4, organiza Nahuel, equipo).
- Android 0.2.0 (versionCode 2) con todo lo de Plus, seña y baja.
- iOS en main (`apps/ios`, XcodeGen; CI en macos-15 con Xcode 26.3). Compila, pero nunca corrió:
  falta la pasada visual con Xcode o un iPhone, la App Clip y los productos de App Store Connect.

## Faltantes del handoff (31/10, noche)
Auditoría en docs/auditoria.md y contrato en docs/contrato-faltantes.md. En producción: API (92c1383)
y web (60985da). iOS en main (compila en CI, sin probar en un iPhone). Android en la rama `android`
sin mergear: Nahuel pidió no compilar Android todavía (al mergear corre el CI de Android).
Pendiente: Firebase común `grupo-vimo` para Caigo (lo registra la sesión de Tu
Local Web; el proyecto caigo-5d3b6 queda sin usar), credenciales de producción de la app de MP «Caigo»
(1737058147653670, falta la verificación de Nahuel), Play Console (verificaciones de la cuenta).
Web sin hacer: «Pedir placas» (mailto), menú reportar/bloquear en el chat web, mapa oscuro con
teselas oscuras.

## Apple (31/10, configurado desde el portal)
- App IDs: `ar.com.caigo.app` (App Groups, NFC Tag Reading, Associated Domains, Push, Sign in with
  Apple, IAP) y App Clip `ar.com.caigo.app.Clip` (App Groups, Associated Domains, Push, Sign in with
  Apple). App Group `group.ar.com.caigo.app` en los dos.
- Wallet: Pass Type ID `pass.ar.com.caigo`, certificado «Caigo pases» (vence 30/11/2027). Clave
  generada en el VPS; cert y clave en /opt/caigo/claves/wallet-*.pem (600, deploy) y rutas en api.conf.
- Clerk producción: Native applications › iOS con `ar.com.caigo.app` y `ar.com.caigo.app.Clip`.
- App Store Connect: app «Caigo» (id 6829274265, SKU caigo-ios, español de México). Grupo de
  suscripción «Caigo Plus» con `plus_temporada` (3 meses, US$ 6,99), `plus_mes` (1 mes, US$ 2,99, 1
  semana gratis) y `plus_semana` (1 semana, US$ 0,99). Sólo Argentina: Apple cobra en USD ahí (se
  calculó con el dólar tarjeta del 31/10, $2.028). Avisos del servidor (producción y sandbox) →
  https://api.caigo.com.ar/v1/webhooks/apple; la API trata igual las compras de sandbox (TestFlight).
- Hecho (7/11): experiencias del App Clip y capturas de revisión de las suscripciones. La ficha de la app ya
  está cargada (tiendas/asc/cargar.py).

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

## iOS (rama `ios`, 31/10)
SwiftUI, iOS 17, Observation, en `apps/ios`. El proyecto sale de `project.yml` (`xcodegen generate`; el
.xcodeproj no se versiona). `Nucleo/` es un paquete Swift con lo que no es pantalla (modelos, API,
Clerk en modo nativo, WebSocket, Keychain, lógica de cupo/plata/DNI/mazo, íconos SVG) y se prueba en
la Mac sin Xcode con `apps/ios/probar-nucleo.sh`; `apps/ios/revisar-local.sh` chequea tipos de la app
contra el SDK de macOS (lo propio de iOS va con `#if os(iOS)`). La compilación para el simulador la
hace el job `ios` de CI (macos-15, Xcode 26.3). Clave de Clerk en `Config/Secretos.xcconfig` (no se
versiona; ver `Secretos.ejemplo.xcconfig`). Pantallas: ingreso (Clerk Google/Apple, Apple nativo,
token en DEBUG), alta (datos, teléfono, selfie con Vision, intenciones), verificación y DNI con
PDF417, Ahora (lista, mapa MapKit, Gente/Citas), Explorar con filtros de Plus, detalle, crear,
Mis planes, solicitudes, chat y privados en tiempo real, perfil (con «Borrar mi cuenta»), check-in,
¿Repetimos?, QR, Plus con StoreKit 2, entradas/pases con Apple Wallet, Caigo Tap con Core NFC y push
APNs. Falta: App Clip, productos de StoreKit en App Store Connect, Pass Type ID, y todo lo visual
sin verificar en un iPhone (nunca se corrió en simulador).

## iOS contra el contrato de lo que falta (rama `ios-faltantes`, 31/10)
Avisos nuevos abren su pantalla (`detalle`, `checkin`, `repetimos`, `chat`, `ahora`); PUT /v1/yo/ubicacion al abrir
si ya hay permiso (cada 30 min como mucho); Ajustes de avisos (Perfil › Tu cuenta › Avisos);
`aps-environment` = `$(APS_ENTORNO)` (development en Debug, production en Release). Género en el alta y
en Editar perfil; `falta_genero` abre `HojaGenero` y reintenta (sumarse, entradas, invitaciones).
Organizador: editar (Crear en modo edición), cancelar con confirmación y sacar gente (Solicitudes ›
Ya van). Cancelar mi entrada según la política. Crear con foto, descripción y duración; zona por
geocodificación o del perfil. Verificación: en vivo, contacto de confianza, bloqueos y fila de DNI;
01b se guarda (`Contenedor.somos`) y se usa al sumarse. Ahora sin coordenadas fijas (pide permiso o
usa la ciudad del perfil), header con CLGeocoder, 13a según la hora, `q` en el mapa y en Explorar,
mapa oscuro. Explorar con lat/lng, zona y 8 categorías con fotos CC0 (`apps/ios/FUENTES.md`; faltan
Cita doble y Cultura: hoy muestran el color). Chat con menú reportar/salir/bloquear y «escribiendo»
en privados. Persona con próximos planes. `/v1/version` con `minIos` → pantalla de actualizar.
Nada de esto se vio en un iPhone.

## App Clip de Caigo Tap (rama `ios-clip`, 31/10)
Target `CaigoClip` (`apps/ios/CaigoClip`, bundle `ar.com.caigo.app.Clip`, embebido en la app). Se abre
con `https://caigo.com.ar/t/<placa>` (NFC o QR; `picc_data`/`cmac` de NTAG 424 se mandan tal cual):
ingreso con Apple/Google por Clerk si no hay sesión → `POST /v1/sesion?cliente=ios` → `POST /v1/tap` →
«Adentro» (la misma vista de la app, `Pantallas/Pagos/Adentro.swift`). Si la API dice que no (placa
inactiva, sin plan, falta verificarse), muestra el `detail` tal cual y un `SKOverlay` para bajar la app.
Comparte con la app Tema, Piezas, Contenedor, Ir, Push, el ingreso con Clerk (`IngresoClerk.swift`) y
Nucleo (lista en `project.yml`). Sesión compartida: llavero del grupo `<Team>.ar.com.caigo.app` y,
además, el App Group `group.ar.com.caigo.app` (el Clip deja la sesión ahí; la app la pasa a su llavero
al abrir y la borra), que es el camino que Apple documenta para Clip → app.

Lo que hay que crear en el portal de Apple (Team M6935MSNQB) y App Store Connect:
- App Group `group.ar.com.caigo.app`, habilitado en los dos App IDs.
- App ID `ar.com.caigo.app.Clip` (explícito) con: App Groups, Associated Domains, Sign in with Apple
  (agrupado con el primary `ar.com.caigo.app`). La app `ar.com.caigo.app` suma App Groups (ya tiene
  Associated Domains, Push, NFC Tag Reading y Sign in with Apple).
- Associated Domains: `appclips:caigo.com.ar` en los dos (la app también lleva `applinks:caigo.com.ar`).
  El `apple-app-site-association` ya lista `M6935MSNQB.ar.com.caigo.app.Clip` en `appclips`.
- Perfiles de desarrollo/distribución para los dos bundles (con el App Group).
- En App Store Connect, al subir la primera versión: la experiencia por defecto del App Clip y una
  experiencia avanzada para el prefijo `https://caigo.com.ar/t/` (título, subtítulo, foto 3000×2000,
  acción «Abrir»). Clerk: la conexión de Apple tiene que aceptar también `ar.com.caigo.app.Clip` para
  el Sign in with Apple nativo (si no, cae al ingreso web).

## 1/11 (sesión de Tu Local Web, a pedido de Nahuel)
- Gente: el mazo filtraba por ciudad y el alta de la web no la pide, así que quien se anotaba por la web no
  aparecía para nadie. Ahora entra también quien no tiene ciudad. **Pendiente:** que el alta web pida ciudad
  (y edad, que tampoco pide y hace que los rangos de edad de los planes no se apliquen).
- Detalle del plan (web): el aviso de «falta_dni» ahora sale también junto al botón (antes quedaba en el medio
  de la página y parecía que no pasaba nada).
- /opt/caigo/api.conf había quedado de root (carga de MP): se devolvió a deploy; los deploy fallaban.

- Perfiles completos: `/v1/yo` trae `faltan` (personas.Faltantes: edad, ciudad, zona, bio ≥ 30, 2 fotos, 3 intereses);
  la web (layout de /app → /app/perfil/editar?completar) y el iPhone (Base .completarPerfil) piden completarlo. Android no.
- Tiempo real de los planes (00016): trigger en solicitudes y planes → NOTIFY `{tipo:"plan", plan}` → Hub.avisarPlan al
  organizador y a quienes pidieron sumarse. La web tiene tiempo real por `/app/vivo` (SSE que reenvía el WebSocket de la
  API con la sesión de la cookie; `lib/vivo.ts`): detalle, solicitudes, chat y mensajes. iOS: DetallePlan y Solicitudes.
- SvelteKit 3: toda redirección a MP / links de la API lleva `{ external: true }` (si no, falla en silencio).
