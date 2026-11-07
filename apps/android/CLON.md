# Caigo Android: reglas del clon (léelas enteras antes de escribir)

Objetivo (Nahuel): Caigo para Android **idéntica** al iPhone, completa, sin nada que falte. Molde y trampas:
`~/Trabajo/vimo/tengolibre/apps/android/GUIA-CLON-IOS.md` (§3, §5 y §8).

- **Fuente de verdad: `apps/ios`** (SwiftUI), no el handoff ni la web. Lo que hace el iPhone lo hace Android, y si el
  iPhone hace algo raro, Android hace lo mismo. Iguales:
  - los textos, **letra por letra**, incluidos los de accesibilidad y los placeholders;
  - los endpoints `/v1/…`, con su método, query y cuerpo;
  - los estados: cargando, vacío, error y sin conexión;
  - los avisos, las hojas, los menús, las confirmaciones, las animaciones y los hápticos;
  - los tamaños, los colores, los pesos de letra, los espaciados y los radios.
- **Un `.kt` por cada `.swift`**, con el mismo nombre, en la carpeta equivalente en minúscula y el paquete
  `ar.com.caigo.app.<carpeta>`. Por ejemplo, `Caigo/Pantallas/Gente/Recibidos.swift` va a
  `app/src/main/java/ar/com/caigo/app/pantallas/gente/Recibidos.kt`. Las demás carpetas:
  - `Caigo/App` → `app`;
  - `Nucleo/Sources/Nucleo` → `nucleo`;
  - `Caigo/Piezas` → `piezas`;
  - `Caigo/Tema` → `tema`.
- **Mismos nombres que en Swift:** cada `struct X: View` es un `@Composable fun X(...)`; los modelos y los helpers
  llevan el mismo nombre que en Swift.
- **Arquitectura de Android, que se queda como está:**
  - `Contenedor` + `LocalApp`;
  - `Ir` sobre NavController, en `app/Navegacion.kt`;
  - `Gel` y `Gels`, la paleta `LocalPaleta` y `T(...)` para el texto;
  - `Icono` + `I.*`;
  - `Cta` y `Pantalla`.

  Las firmas que ya existen no se cambian; si hace falta algo, se suma.
- **Plataforma:**
  - Android entra sólo con Google: sin «Seguir con Apple».
  - «Face ID» pasa a «tu huella o tu cara», e «iPhone» a «teléfono» o «celular».
  - Plus se cobra con Play Billing (`app/Facturacion.kt`), con los mismos ids que StoreKit.
  - Las placas Caigo Tap se leen con NFC y App Links.
  - El App Clip (`CaigoClip`) no tiene par: Android no tiene App Clips.
  - Wallet pasa a «Guardar en Google Wallet» o a la entrada en la app, según lo que ya haga Android.
  - MapKit pasa a MapLibre.
- **No se compila en la Mac** (nunca `gradlew` ni emulador). Compila GitHub.
- **No toques archivos de otro agente.** Si necesitás algo compartido que no existe:
  - escribilo en tu archivo como `private`, con un comentario `// PARA EL NÚCLEO: …`;
  - anotalo en tu informe.
