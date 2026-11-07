# Repasada de fidelidad: Caigo Android contra iPhone

Nahuel quiere Android **IDÉNTICA** al iPhone, completa, sin nada que falte, y la repasada tiene que ser **a fondo**.
- iPhone: `~/Trabajo/vimo/caigo/apps/ios`, la fuente de verdad.
- Android: `~/Trabajo/vimo/caigo/apps/android`.
- Reglas: leé `apps/android/CLON.md` entero.
- Molde y trampas: `~/Trabajo/vimo/tengolibre/apps/android/GUIA-CLON-IOS.md`, §3, §5 y §8.

El Android ya existe, armado desde el handoff en paralelo con el iPhone. Sus carpetas ya copian las del iPhone. Ahora
tiene que quedar igual al iPhone, archivo por archivo.

## Método, por cada par
1. Leé el `.swift` COMPLETO con Read: todas las líneas, en partes si es largo. No uses grep para «darte una idea».
2. Leé el `.kt` COMPLETO.
3. Si el `.kt` no existe:
   - buscá dónde está hoy esa pantalla en Android: suele estar dentro de otro `.kt` de tu área;
   - pasala a su propio archivo, con el mismo nombre;
   - si no existe en ningún lado, escribila.
4. Recorré el `.swift` de arriba abajo y buscá cada pieza en el `.kt`:
   - cada `View`, `if`/`else`/`switch` y `ForEach`;
   - cada texto, incluidos los de accesibilidad y los placeholders;
   - cada `Button` o `Menu` con su acción y cada `.sheet`/`.alert`/`.confirmationDialog`;
   - cada `.task`/`.onChange`/`.refreshable`, cada llamada a la API (método, ruta, query, cuerpo) y cada aviso;
   - cada estado (cargando, vacío, error, sin conexión) y cada condición;
   - los tamaños, colores, pesos de letra, espaciados, radios, animaciones y hápticos.
5. Lo que falte o difiera, corregilo en el `.kt` con Kotlin que compile. Leé las firmas reales de `tema/`, `piezas/`,
   `nucleo/` y `app/` antes de usarlas.
6. Bugs típicos de un clon que hay que buscar:
   - `nonEmpty` de Swift (recorta espacios) traducido como `isEmpty`;
   - `.capitalized` mal traducido;
   - un `Locale` que no es es-AR;
   - un `try?` que en Kotlin tira y cierra la app;
   - un `.task(id:)` sin la clave correcta;
   - un `Deshacer` que no restaura.
7. Si encontrás un **bug del iPhone** (algo que está mal en los dos), corregilo en los dos y anotalo.

## Límites
- Tocá SÓLO los archivos de tu área. Si algo compartido de `tema/`, `piezas/`, `nucleo/` o `app/` está mal o falta,
  NO lo toques (salvo que sea tu área): anotalo en el informe como «PARA EL NÚCLEO», con la firma que necesitás.
- Si una pantalla nueva necesita ruta en `app/Navegacion.kt`, no la toques: anotá en el informe la ruta y la llamada.
- NO compiles (nada de gradlew), NO hagas commit ni push. El integrador compila.
- Diferencias aceptadas: sólo las de plataforma de `CLON.md`.

## Informe (menos de 350 palabras)
- Por archivo, cuántas cosas corregiste, con 1 o 2 ejemplos.
- Los `.kt` nuevos o separados.
- La lista exacta de lo que quedó distinto y por qué.
- Lo «PARA EL NÚCLEO» y las rutas de navegación que hacen falta.
- Los bugs del iPhone que corregiste.
