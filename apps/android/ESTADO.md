# Caigo para Android: estado y cómo retomar

Objetivo (Nahuel): Android **idéntica** al iPhone (`apps/ios`), completa. Nada sale a las tiendas hasta que esté.
Reglas del clon: `CLON.md`; método de la repasada: `PASADA.md`.

## Cómo se trabaja
- Compila sólo GitHub, en el espejo público `grupovimo/caigo-android` (`.github/workflows/android.yml`, con los
  secretos `CAIGO_*`): `~/Trabajo/vimo/caigo-android/publicar.sh "mensaje"` corre `herramientas/paridad.sh`, copia
  `apps/android`, commitea en los dos repos y empuja. Sube los artifacts `galeria` (JPG de 540 px) y, a mano
  (Run workflow), `play` con el paquete firmado. El CI de nawesan12/caigo también compila y prueba, sin galería.
- `apps/pruebas/respuestas.json`: la API simulada, compartida con iOS.
  - Android la usa en `PantallasTest` (78 pantallas × claro/oscuro), por el gancho `Api.interceptorPrueba`.
  - iOS la usa en `CaigoTests/PantallasTests.swift`, por `Contenedor.prueba`.
  - `ParidadTest` exige un `.kt` por cada `.swift` de `Caigo/Pantallas`.

## Hecho (7/11)
- **Carpetas iguales a las del iPhone**: `app`, `nucleo`, `pantallas/<área>`, `piezas` y `tema`. Un `.kt` por `.swift`.
  Se sumaron las 7 pantallas que faltaban: Ajustes, Recibidos, GenteComun, IngresoCamara, IngresoClerk, Confirmar
  y Adentro.
- **Repasada a fondo** con 5 agentes que leyeron enteros los `.swift` y los `.kt`: unas 350 diferencias corregidas en
  textos, endpoints, estados, animaciones y hápticos. Además se sacaron funciones que el iPhone no tiene: los filtros
  de Plus en Ahora, «Distancia» en Explorar, «Sacar de mi gente», la paginación del chat y el selector de tema.
- No apareció ningún bug del iPhone.

## Distinto a propósito (plataforma)
- **Cuenta y pagos:** sólo Google para entrar, Plus con Play Billing y «Guardar en Google Wallet».
- **Caigo Tap:** se lee con NFC en primer plano y con App Links. El App Clip no tiene par.
- **Mapas:** MapLibre y Geocoder en lugar de MapKit.
- **Prueba de vida:** usa la probabilidad de ojos abiertos de ML Kit en lugar de los puntos de Vision.
- **Desenfoque de Recibidos:** sólo existe desde Android 12.

## Sin probar en un teléfono
- Ingreso con Clerk.
- Push FCM.
- NFC.
- Cámaras: DNI, selfie y QR.
- Play Billing.
- Mapas.

En las capturas no salen las fotos de la red (Coil en Robolectric); en la app sí.

## Repaso visual (7/11), a revisar por Nahuel
- Galería: https://claude.ai/artifact/EjWR71aqgtGqHvGGRHBWZX
- Arreglado: chips ilegibles en oscuro, la barra de pestañas transparente, el QR vacío, renglones que arrancaban con «·» y «1 sumada».
- También en el iPhone (Crear, MazoGente, `sinViudas`, barra opaca); TestFlight subido.
- Pregunta abierta: en Ahora oscuro el handoff pone el logo en vez de la campana, así que no se llega a Recibidos. ¿Campana también en oscuro?
- Para retomar: Nahuel mira la galería y hace un repaso profundo de todo. Lo que marque se arregla en Android y,
  si también pasa en el iPhone, en los dos lados, con TestFlight después.

## Falta
1. **Cuenta de Play** (necesita que Nahuel verifique un Android y su teléfono). Hay que armar:
   - la app;
   - las suscripciones `plus_semana`, `plus_mes` y `plus_temporada`;
   - RTDN;
   - la huella de Play en `assetlinks.json`.

   Después, la prueba interna.
2. La revisión de Nahuel con la galería: https://claude.ai/artifact/EjWR71aqgtGqHvGGRHBWZX
