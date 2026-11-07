# Caigo — plan de construcción

## Contexto
Caigo es la 4ta app de Grupo Vimo: una app social para sumarse a planes a los que les falta gente,
o completar los propios ("Hacé planes. Conocé gente."). Arranca en Mar del Plata y Villa Gesell. El handoff está en
`~/Trabajo/vimo/Tuki Mockups v2 Updates.zip`: README con tokens y pantallas, brief, mockups v5,
prototipo, panel de lugares, pases NFC/Wallet y deck. Hoy caigo.com.ar es sólo un placeholder. El objetivo es construir el
producto entero y dejarlo en el VPS, conviviendo con Tu Local Web, Corredor y TengoLibre.

## Decisiones de Nahuel (31/10)
- Alcance: **todo el handoff** (MVP + modos Gente/Citas, Plus, DNI, panel de lugares, entradas/señas, Caigo Tap, Wallet). El deck no se implementa.
- UI fiel al handoff, **Geist** en todo; los mockups v5 mandan y el prototipo aporta los flujos extra.
- Orden: **API Go → web SvelteKit → iOS (Swift, iOS 17+) → Android (Kotlin + Compose, minSdk 26)**.
- Repo **nawesan12/caigo**, público, monorepo en `~/Trabajo/vimo/caigo`, todo a main.
- Identidad: **Clerk** (la app única de Grupo Vimo). Teléfono con código por **mail** (7/11: sin proveedor de SMS, va a la casilla verificada de Clerk por el SMTP propio; sin SMTP, simulado con código de prueba).
- Prueba de vida y DNI **en el dispositivo** (Vision / ML Kit), DNI frente/dorso + lectura del PDF417, revisión manual en el backoffice.
- Mapa: **MapKit** en iOS; **MapLibre + teselas Protomaps** (recorte de la Costa Atlántica, servidas desde el VPS) en Android y web.
- Plus: **StoreKit 2 + Play Billing + Mercado Pago (web)**, con el estado unificado en la API.
- Entradas/señas: **Mercado Pago marketplace** con una app nueva de MP para Caigo, OAuth por lugar y el cargo por servicio como comisión.
- Dominios: `caigo.com.ar` (landing, `/p/<slug>` SSR, `/equipo` backoffice), `api.caigo.com.ar`, `lugares.caigo.com.ar`.
- Los lugares se registran solos y el equipo los aprueba. Por ahora el único moderador es Nahuel (rol de equipo, alta con `caigoctl`).
- Caigo Tap: **QR + NFC NDEF con URL firmada (HMAC)**; el verificador SUN de NTAG 424 queda listo para cuando haya placas.
- Base vacía en producción + `caigoctl semilla` para cargar planes del equipo. Fotos CC0 (Openverse/StockSnap) para la landing.
- Cuentas externas (Pass Type ID, Google Wallet issuer, Firebase/FCM, app de MP, productos de StoreKit/Play): **yo dejo la lista exacta y las crea Nahuel**. No se crea nada en ninguna cuenta sin preguntar.

## Acordado con las otras sesiones (31/10)
- Registro común del VPS: `/opt/VPS.md` (lo armó Tu Local Web). Caigo actualiza su fila al subir.
- Puertos de Caigo: **api 127.0.0.1:4300, web 127.0.0.1:3500**. Los demás están tomados: 3100, 3200/4000, 3300/4100, 3400/4200 y 8080.
- **Postgres propio** en `/opt/caigo` (mem_limit 256m, shared_buffers 64MB), api 128m y web ~192m. Hoy se usa ~1 GB de 3,9.
- Caddy: sólo archivos propios en `/etc/caddy/sitios/caigo*.caddy` con `import solo_cloudflare`, cert de origen `/etc/caddy/certs/caigo.*`, y `caddy validate` + vuelta atrás. No se tocan tiendas.caddy, el ask global ni 00-cloudflare.caddy.
- APNs: copia propia del .p8 en `/opt/caigo/apns` (fuera del repo), Key ZXZDZ763W6, topic `ar.com.caigo.app`.
- vimo-copia: Tu Local Web ya sumó `otra_app caigo caigo-postgres-1 api.conf compose.yml pg.secret archivos apns`. El compose se tiene que llamar `caigo`, el servicio `postgres` y el superusuario `postgres`.
- La sesión backend-migration-typescript-to-go (maderera) queda afuera.

## Estructura del repo
```
caigo/
  apps/api      Go: cmd/api, cmd/worker, cmd/caigoctl, internal/{plataforma,dominio,integraciones}, db/migraciones
  apps/web      SvelteKit + adapter-node: landing, /p/[slug], panel lugares (host lugares.), /equipo
  apps/ios      Xcode SwiftUI (iOS 17, Observation), App Clip para Caigo Tap
  apps/android  Gradle Kotlin + Compose, Instant App/deep link para Caigo Tap
  packages/openapi   contrato único → clientes generados (TS, Swift, Kotlin)
  infra/vps     compose.yml, api.conf.example, caigo*.caddy
  docs/         estado.md, decisiones.md, handoff/ (el zip descomprimido)
```

## Qué se reutiliza
- Esqueleto de la API: `~/Trabajo/vimo/tengolibre/apps/api`, que es un recorte de mostrador-v2: stdlib mux, pgx, goose embebido, RLS con `set_config` (`db.Pool.EnNegocio`), roles sin BYPASSRLS (`ctl bootstrap`), problem+json, `/salud/vivo` y `/salud/listo`, y el test que prohíbe pgxpool fuera de `plataforma/db`.
- De `~/Trabajo/vimo/mostrador-v2/apps/api/internal/plataforma`: `clerk`, `limite`, `idempotencia`, `outbox`, `cifrado`, `auditoria` y `correo`. También el cliente APNs (HTTP/2 + JWT ES256, docs/decisiones.md §40).
- `integraciones/mercadopago` de mostrador-v2: OAuth marketplace, preferencias y webhooks con HMAC.
- Infra: `tengolibre/infra/vps/compose.yml` (alpine + binario montado, deploy con `*.nuevo` + `mv`).

## Etapas (en orden, cada una termina en main y verificada)
1. **Base**: repo, docs/handoff, OpenAPI inicial, API calcada del boilerplate y Clerk (verificación de JWT, `usuarios` sincronizados por webhook). Compose local.
2. **Dominio núcleo (API)**: perfiles livianos, intereses e intenciones, planes (campos completos del brief, recurrencia, geo con PostGIS o índice por bbox), "Ahora" (próximas 3 h por distancia) y Explorar (agenda y filtros). Además: sumarse solo o en bloque, aprobación, cupo `f = total − (sumados + extra)`, dirección oculta hasta la aceptación, mi gente, bloquear y reportar.
3. **Tiempo real y avisos**: chat del plan por WebSocket (archivado a los N días), privados sólo entre "mi gente", push APNs + FCM desde el worker con outbox.
4. **Confianza**: check-in (geofence + QR/NFC firmado), reputación por asistencia, "¿Repetimos?", verificación DNI (subida de imágenes al disco del VPS, cola de revisión), SMS simulado.
5. **Gente/Citas y Plus**: mazo de personas, "me prendo/me interesa", límite de 3 por día, "Te sumaron" bloqueado sin Plus, suscripciones (validación de StoreKit 2 por App Store Server API, Play Developer API y MP preapproval).
6. **Lugares**: alta y aprobación, planes con entrada o seña, checkout MP marketplace, códigos `TK-xxxx`, beneficios por check-in, pase de socio, cobros, placas Caigo Tap (verificador SUN 424). Pases Apple Wallet (firma PKCS#7 en Go) y Google Wallet (JWT).
7. **Web**: landing en caigo.com.ar (reemplaza el placeholder), link público `/p/<slug>` SSR con sumarse desde la web, panel `lugares.caigo.com.ar` y backoffice `/equipo` (reportes, DNI, lugares).
8. **VPS**: `/opt/caigo`, DNS en Cloudflare, cert de origen, Caddy, `/opt/VPS.md` y vimo-copia. Después, la ficha de Caigo en grupovimo.com.ar (se coordina con quien lleve la web).
9. **iOS**: las 25 pantallas de v5 + los flujos del prototipo, modo oscuro, MapKit, Vision liveness, lectura de DNI, StoreKit, PassKit, App Clip y NFC (Core NFC), push.
10. **Android**: lo mismo en Compose: MapLibre, ML Kit, Play Billing, Google Wallet, NFC y FCM.

## Lo que necesito de Nahuel (te lo paso como lista el día que toque)
- Clerk: activar teléfono cuando haya SMS real y la instancia de producción para caigo.com.ar.
- Apple: Pass Type ID `pass.ar.com.caigo`, productos de suscripción en App Store Connect, App Clip ID, clave de API de ASC y Xcode instalado.
- Google: Play Console, proyecto Firebase (FCM), emisor de Google Wallet y productos de Play Billing.
- Mercado Pago: app nueva "Caigo" (marketplace).

## Verificación
- API: `go vet`, `go test ./...` (incluye RLS: un usuario no ve la dirección de un plan al que no lo aceptaron; cupo en bloque; límite de Plus) y e2e con curl sobre el compose local.
- Web: `bun run check`, unitarios y capturas propias contra los .dc.html a 1440 y 375 px (fiel al handoff, sin renglones viudos).
- iOS/Android: builds en simulador/emulador y capturas contra Mockups v5 a 390×844.
- VPS: `caddy validate`, `curl https://api.caigo.com.ar/salud/listo`, la web y `/p/<slug>` desde el teléfono, y `docker stats` dentro de los topes.
