# Caigo: revisión del 12/11 antes de subir la beta a Play y TestFlight

Fue una revisión de solo lectura: no toqué código ni corrí compilaciones en la Mac. Lo que sigue sale de leer `apps/android`, `apps/ios` y `apps/api`, más los runs de `grupovimo/caigo-android` y `nawesan12/caigo`.

## El bloqueante: `:app:buildReleasePreBundle` (run 43244255286, 12/11 15:06)

El error exacto del log (`gh run view 43244255286 -R grupovimo/caigo-android --log-failed`):

```
> Task :app:minifyReleaseWithR8
> Task :app:buildReleasePreBundle FAILED
Execution failed for task ':app:buildReleasePreBundle'.
> Multiple shrunk-resources files found in directory '.../shrunk_resources_proto_format/release/minifyReleaseWithR8':
  [shrunk-resources-proto-format-arm64-v8a-release.ap_, shrunk-resources-proto-format-x86_64-release.ap_]
```

**Causa:** en `app/build.gradle.kts` (líneas 79-86) está `splits { abi { isEnable = true; include("arm64-v8a", "x86_64") } }` y, a la vez, `isShrinkResources = true` en release. Con los splits prendidos, R8 genera un `.ap_` reducido por cada ABI. `bundleRelease` espera uno solo, así que el App Bundle no se puede armar. R8 pasó bien: el problema está solo en el empaquetado.

**Arreglo** (alcanza con uno de los dos):

1. Apagar los splits cuando se arma el bundle y dejarlos para los APK de prueba:
   ```kotlin
   splits {
       abi {
           isEnable = gradle.startParameter.taskNames.none { it.contains("bundle", ignoreCase = true) }
           reset()
           include("arm64-v8a", "x86_64")
           isUniversalApk = false
       }
   }
   ```
2. Sacar el bloque `splits` entero. Play ya parte por ABI desde el AAB, y para probar en un teléfono se puede usar `bundletool build-apks --connected-device`.

La opción 1 cambia menos cosas. Después de aplicarla, hay que correr `gh workflow run Android -R grupovimo/caigo-android` y bajar el artifact `play`.

Las otras fallas de `workflow_dispatch` del 11/11 (43107613041, 43104444627 y 43102111734) fueron de infraestructura: «The job was not acquired by Runner». No eran del código.

## Hallazgos

| Prioridad | Plataforma | Archivo:línea | Qué pasa | Cómo arreglarlo |
|---|---|---|---|---|
| bloquea | Android | `app/build.gradle.kts:79-86` | Los splits por ABI con `isShrinkResources` rompen `bundleRelease` (el error de arriba). No hay AAB para subir. | Splits apagados en el bundle (opción 1 de arriba) y relanzar el workflow. |
| bloquea | Play Console | (fuera del código) | Falta crear las suscripciones `plus_semana`, `plus_mes` y `plus_temporada` y el perfil de pagos, y configurar RTDN (Pub/Sub a `https://api.caigo.com.ar/v1/webhooks/google?clave=…`). Sin productos, Plus muestra «Caigo Plus todavía no está disponible en Google Play». La cuenta es **personal y nueva**, así que Play exige una prueba cerrada con 12 testers durante 14 días antes de pedir producción. | Subir primero el AAB a prueba interna (sin un AAB subido Play no deja crear suscripciones). Después crear los productos con esos ids exactos, RTDN y `CAIGO_GOOGLE_PLAY_CUENTA` en la API. |
| bloquea (App Links) | Android / web | `infra/vps/well-known/assetlinks.json` y `apps/pruebas/enlaces.json` («huellas») | Hoy están la huella de subida (57:21…) y la de debug (A2:25…), pero falta la de **Play App Signing**. Las apps que instala Play quedan firmadas con esa clave, así que `autoVerify` falla y los links de caigo.com.ar abren el navegador. | Copiar el SHA-256 de Play Console › Integridad de la app › Firma, sumarlo en `enlaces.json`, correr `generar.py` y `desplegar-web.sh`. |
| grave | Android | `pantallas/planes/Ahora.kt:123` y `pantallas/mios/Checkin.kt:149` | Se pide `ACCESS_FINE_LOCATION` sola. En Android 12+ (targetSdk 36), pedir FINE sin COARSE hace que el sistema ignore el pedido («must be requested with ACCESS_COARSE_LOCATION»), así que el cartel no aparece. Ahora cae siempre en «por ciudad» y el check-in por ubicación no llega a `llegue()`. Además, si la persona elige «aproximada», `rememberPermiso` mira solo FINE y lo toma como negado. | Pedir las dos juntas con `RequestMultiplePermissions(arrayOf(FINE, COARSE))` y tomar como concedido si viene cualquiera de las dos (como ya hace `Contenedor.tienePermisoUbicacion`). |
| grave | API + Android | `api/internal/dominio/plus/compras.go:209-222` y `pantallas/pagos/Plus.kt:154-168` | El acknowledge de la compra lo hace solo `POST /v1/plus/google`. Si la app se cierra o pierde la red entre la compra y ese POST, nadie reconoce la compra: la notificación de Google (`AvisoGoogle`) guarda el estado pero no llama a `Reconocer`, y la app no busca compras pendientes al abrir. Google la devuelve sola a los 3 días. | En `AvisoGoogle`, si `g.Reconoce`, llamar a `t.Play.Reconocer`. En Android, al abrir Plus o la app, pasar `play.compras()` con `!isAcknowledged` por `plusGoogle`. |
| grave | iOS | TestFlight run 43102057409 (sha 2eaeb96) | El último TestFlight es **anterior** a 1d14d6c («Links a la app»: `Contenedor.swift`, `Navegacion.swift` y el AASA nuevo). Lo que está en TestFlight no abre `/app/...` como la web nueva espera. | `gh workflow run testflight -R nawesan12/caigo` desde main (6e08b86, CI en verde). |
| menor | iOS CI | run 43097477735 | Hay una corrida de `ci` en cola hace más de 20 h (macOS arm64 sin cupo). Ocupa la cola. | Cancelarla: `gh run cancel 43097477735 -R nawesan12/caigo`. |
| menor | CI Android | `.github/workflows/android.yml` (paso «Paquete para Play») | Si falta `CAIGO_FIRMA_JKS`, el AAB sale **sin firmar** y el run queda en verde. Play lo rechaza. | En el paso del bundle, cortar con `exit 1` si no hay `caigo.firma.archivo` en `local.properties`. |
| menor | Android | `app/build.gradle.kts:25-26` | `versionCode = 2` es fijo y a mano. Cada subida a Play necesita un número nuevo, y si se repite Play rechaza el AAB. | Tomarlo de `-PversionCode=${{ github.run_number }}`, como hace iOS con `CURRENT_PROJECT_VERSION`. |
| menor | Android | `AndroidManifest.xml` (MainActivity, `screenOrientation="portrait"`) | Con targetSdk 36, Android 16 ignora la orientación fija en pantallas grandes (≥600 dp). La app va a girar en tablets y plegables, y Play lo marca en el informe previo al lanzamiento. | Revisar Ahora, Crear y Chat en horizontal en una tablet de la prueba previa, o aceptar el aviso por ahora. |
| menor | iOS | `apps/ios/project.yml` (sin `PrivacyInfo.xcprivacy`) | La app usa `UserDefaults` (Contenedor, Intro, Entradas, Clerk y Llavero) y no tiene manifiesto de privacidad. App Store pide el motivo declarado (CA92.1); en el Gmail de Nahuel no encontré el aviso ITMS-91053 (busqué con las palabras clave del aviso), pero puede trabar la revisión para producción. | Agregar `PrivacyInfo.xcprivacy` con `NSPrivacyAccessedAPICategoryUserDefaults` › `CA92.1` (y uno para el Clip). |
| menor | Web | `https://caigo.com.ar/app/borrar` | Responde 303 y manda al ingreso. Play pide una URL de «eliminar cuenta» que explique los pasos sin tener que entrar. | Una página pública (por ejemplo `/borrar-cuenta`) con los pasos y el link a `/app/borrar`. |
| menor | Android | `nucleo/Clerk.kt:20` y comentarios | Hay restos de «Apple» y `oauth_apple` en comentarios y en la API del cliente de Clerk. En pantalla no aparece nada de «iPhone», «Face ID», «App Store» ni «Apple», y no hay TODO, FIXME ni lorem. | Nada urgente. |

Revisé y no encontré problemas en:
- **Permisos:** no hay ubicación en segundo plano, SMS, alarmas exactas ni foreground service.
- **Red:** cleartext apagado (`red.xml`), y el release no es debuggable.
- **Manifiesto y queries:** `allowBackup=false`, `queries` solo para Custom Tabs, targetSdk y compileSdk en 36 y Billing 8.0.
- **Modo debug:** el ingreso con token está tras `BuildConfig.DEBUG`.
- **Contratos:** coinciden con la API (`planes`, `gente`, `personas`, `mensajes`, `privados`, `conversaciones`, `solicitudes`, `codigo`, `nuevo`, `link`, `adentro`, `estado`/`planes` de Plus, `entradas`/`socios` de pases y Wallet `{link}`). Además, el JSON se lee con `ignoreUnknownKeys` y `coerceInputValues`, y los modelos de respuesta tienen todos sus valores por defecto.
- **Lector de DNI:** el PDF417 no tiene índices fuera de rango.
- **Paridad:** 36 `.kt` por 36 `.swift` en Pantallas, y `ParidadTest` lo controla.
- **Flujos:** el alta pide datos, teléfono (el código llega por mail), selfie, intenciones y perfil completo, igual que en iOS. Push: FCM armado a mano con los secretos del CI, permiso de avisos pedido una vez y token registrado en `PUT /v1/dispositivos`.

## Datos para Play Console

**Anuncios:** no tiene SDK de anuncios. Declarar «No contiene anuncios».

**Compras dentro de la app:** sí. Son las suscripciones Caigo Plus por Play Billing (`plus_semana` $2.499, `plus_mes` $5.999 con 7 días gratis según la oferta que se cargue y `plus_temporada` $13.999, en ARS). Las entradas y señas de los planes de lugares se pagan con **Mercado Pago en la web** (Custom Tab). Son bienes y servicios físicos (eventos), así que entran en la excepción de Play Billing; conviene aclararlo en la declaración de pagos si Play lo pregunta.

**Acceso a la app:** requiere cuenta. Para que entre un revisor:
1. «Seguir con Google»: el revisor usa su cuenta de Google o una que le pasemos. No hay usuario y clave propios ni modo demo.
2. Nombre y fecha de nacimiento (18+).
3. Teléfono: el código de verificación llega **al mail de la cuenta de Google**, no por SMS.
4. Selfie con prueba de vida (cámara frontal, parpadeo).
5. Intenciones y perfil mínimo (bio, fotos e intereses).

Recomendación: crear una cuenta de Google de prueba (por ejemplo `revision.caigo@…`) sin verificación en dos pasos, pasarle el usuario y la clave a Play en «Acceso a la app» y explicarle los pasos 3 y 4. El DNI es opcional: no hace falta para usar la app.

**Seguridad de los datos (lo que se recolecta).** Todo se transmite cifrado (HTTPS), se puede pedir la eliminación (en la app: Ajustes › Borrar cuenta, `DELETE /v1/yo`; y en la web) y nada se usa para publicidad.

| Tipo de dato (Play) | Qué es en Caigo | Recolecta | Comparte | Opcional | Para qué |
|---|---|---|---|---|---|
| Info personal › Nombre | nombre del perfil; nombre y apellido del DNI | sí | no | no (el DNI sí es opcional) | funcionalidad, cuenta |
| Info personal › Correo | el de Google (vía Clerk), para el código del teléfono | sí | no | no | cuenta, comunicaciones |
| Info personal › IDs de usuario | id de Caigo y de Clerk | sí | no | no | cuenta |
| Info personal › Teléfono | teléfono verificado | sí | no | no | seguridad / prevención de fraude |
| Info personal › Otra info | fecha de nacimiento y edad, género, número de DNI, sexo y trámite (DNI opcional), intereses e intenciones (planes o citas), bio | sí | no | género, DNI y bio parciales | funcionalidad, seguridad |
| Info personal › Orientación sexual | no se pide; «citas» es una intención, no una orientación (revisarlo con el formulario a mano) | — | — | — | — |
| Ubicación › Aproximada | redondeada a ~1 km (`PUT /v1/yo/ubicacion`, se borra a las 24 h) | sí | no | sí | funcionalidad (planes cerca, avisos) |
| Ubicación › Precisa | lat y lng en Ahora y Explorar y en el check-in («llegué»); lugar del plan al crearlo | sí | no | sí | funcionalidad |
| Fotos y videos › Fotos | fotos de perfil y del plan; selfie de verificación; frente y dorso del DNI | sí | no | DNI opcional | funcionalidad, seguridad |
| Mensajes › Otros mensajes en la app | chat del plan y mensajes privados | sí | no | sí | funcionalidad |
| Actividad › Interacciones y otro contenido generado | planes creados, solicitudes, reacciones de Gente y Citas, reportes, bloqueos, opiniones de «¿repetimos?» | sí | no | sí | funcionalidad, seguridad |
| Info financiera › Historial de compras | suscripción Plus (token de Google) y entradas compradas (el pago lo procesa Mercado Pago, no la app) | sí | no | sí | funcionalidad |
| IDs del dispositivo | token de FCM para los avisos | sí | no | sí | funcionalidad (push) |
| Fallos y diagnóstico | no hay Crashlytics ni analítica (`firebase_analytics_collection_deactivated`) | no | — | — | — |

Proveedores que procesan datos (no cuentan como «compartir» si son proveedores de servicio): Clerk (ingreso), Google Firebase (push), Google Play (compras), Mercado Pago (pagos de entradas, en su web) y Google Wallet (pases).

**Clasificación de contenido:** interacción entre usuarios (chat), ubicación compartida con otros (el lugar del plan y el check-in), función de citas y solo para mayores de 18 (validado en la app y en la API). Tiene reportar y bloquear, que son los requisitos de UGC.

**Política de privacidad:** https://caigo.com.ar/privacidad (200). **Términos:** https://caigo.com.ar/terminos (200).

## Lo que no pude verificar

- **El AAB release en un teléfono:** nunca se armó, así que R8 en ejecución no se probó (serialización, Coil, Tink y MapLibre). Hay que probar la versión de prueba interna en un teléfono real apenas exista.
- **Páginas de 16 KB:** maplibre 11.13.5, camerax `libimage_processing_util_jni` y `libsurface_util_jni`, `libdatastore_shared_counter` y `androidx.graphics.path`. Mirarlo en Play Console › App Bundle explorer o con `bundletool` cuando haya AAB.
- **Configuración de producción:** no la miré (`CAIGO_GOOGLE_PLAY_CUENTA`, `CAIGO_GOOGLE_WALLET_EMISOR`, `CAIGO_FCM_CUENTA` y `CAIGO_MIN_ANDROID`), por la regla de no tocar producción.
- **Lo que ESTADO.md marca como no probado en teléfono:** ingreso con Clerk en Android, push FCM, NFC, cámaras (DNI, selfie y QR), Play Billing y mapas.
- **Si App Store Connect avisó de la falta del manifiesto de privacidad:** el mail puede haber ido a otra casilla.
- **Contra la API, solo muestreé las rutas principales.** No comparé campo por campo todos los modelos (`Plan`, `Perfil`, `Tarjeta`, `EntradaMia`).
