# Cuentas externas que faltan

Todo el código ya está listo: cada pieza se prende sola cuando su dato aparece en
`/opt/caigo/api.conf` (y en `local.properties` en Android). Sin esos datos, esa parte queda apagada
sin romper nada.

## Mercado Pago — app nueva "Caigo" (entradas, señas y Plus en la web)
1. developers.mercadopago.com → Tus integraciones → Crear aplicación «Caigo», tipo marketplace,
   con Checkout Pro y Suscripciones.
2. URL de redirección del OAuth: `https://lugares.caigo.com.ar/mercadopago/vuelta`.
3. Webhooks: `https://api.caigo.com.ar/v1/webhooks/mercadopago`, eventos «Pagos» y «Planes y
   suscripciones». Copiar la clave secreta de la firma.
4. Pasar a `api.conf`: `CAIGO_MP_APP_ID`, `CAIGO_MP_SECRETO` (client secret), `CAIGO_MP_TOKEN`
   (access token de producción de la cuenta que cobra Plus), `CAIGO_MP_SECRETO_WEBHOOK`.

## Apple (cuenta M6935MSNQB) — hecho el 31/10
App IDs, App Clip, App Group, Pass Type ID con su certificado (ya en el VPS), app en App Store
Connect con las suscripciones y los avisos del servidor. Detalle en `docs/estado.md` › «Apple».
Falta (pide un build subido): experiencias del App Clip y capturas de revisión de las suscripciones.

## Google
- **Play Console**: app `ar.com.caigo.app`. Suscripciones con los mismos ids: `plus_semana`,
  `plus_mes` (con oferta de 7 días gratis), `plus_temporada`.
- **Cuenta de servicio** con acceso a la Play Developer API (Play Console → Usuarios y permisos →
  invitar el mail de la cuenta, permiso «Ver datos financieros» y «Administrar pedidos y
  suscripciones»). El JSON va a `/opt/caigo/claves/google.json` (600) y en `api.conf`:
  `CAIGO_GOOGLE_PLAY_CUENTA=/datos/claves/google.json`.
- **Real-time developer notifications**: tema de Pub/Sub con suscripción push a
  `https://api.caigo.com.ar/v1/webhooks/google?clave=<CAIGO_GOOGLE_CLAVE_WEBHOOK>` (la clave ya está
  generada en `api.conf`).
- **Firebase** (push de Android): proyecto (puede ser el mismo «Grupo Vimo»), app Android
  `ar.com.caigo.app`. El `google-services.json` va a la app (local.properties) y una cuenta de
  servicio con «Firebase Cloud Messaging API Admin» a `/opt/caigo/claves/fcm.json` →
  `CAIGO_FCM_CUENTA=/datos/claves/fcm.json`.
- **Google Wallet**: consola de Google Pay & Wallet → pedir acceso de emisor (gratis). El Issuer ID va
  a `CAIGO_GOOGLE_WALLET_EMISOR`; la cuenta de servicio de Play tiene que tener rol en el emisor.
- **Ingreso con Google en Android**: no hace falta cliente OAuth Android mientras el ingreso vaya por
  la Frontend API de Clerk con Custom Tabs (usa el cliente web que ya existe).

## Placas Caigo Tap
- Placas comunes: cualquier etiqueta NFC NTAG213/215 con la URL que da el panel. Ya funcionan.
- NTAG 424 DNA: el panel genera una clave por placa («claveSun», se muestra una vez); se programa el
  chip con SDM (UID + contador reflejados en `picc_data`, `cmac` sobre entrada vacía, misma clave para
  las dos cosas). A definir con el proveedor de las placas.

Después de cargar cada dato: `ssh deploy@187.41.102.190 'cd /opt/caigo && docker compose restart api worker'`.
