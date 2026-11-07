# Fichas de las tiendas

Textos listos para pegar en Play Console y App Store Connect cuando se creen las apps (ver
`docs/cuentas-externas.md`). Salen del brief (`docs/handoff/reference/brief-producto-v1.0.md`) y de
la landing. Los límites de caracteres están al lado de cada campo.

## Comunes

- **Nombre** (30): Caigo
- **Categoría:** Social (Play: Social · App Store: Redes sociales; secundaria: Estilo de vida)
- **Edad:** 18+. Hay contenido generado por personas, chat y un modo Citas. En Play, cuestionario
  IARC: interacción entre usuarios sí, ubicación compartida sí (aproximada, la dirección exacta sólo
  al aceptado), compras dentro de la app sí. App Store: 17+ («Citas y encuentros» + «Contenido
  generado por usuarios»).
- **Sitio:** https://caigo.com.ar · **Privacidad:** https://caigo.com.ar/privacidad ·
  **Términos:** https://caigo.com.ar/terminos (hoy son borradores: revisarlos antes de publicar).
- **Contacto:** el mail de soporte que elija Nahuel.

## Play Store

**Descripción corta** (80):
> Sumate a planes a los que les falta gente, o completá los tuyos. Hacé planes.

**Descripción completa** (4000):

> ¿Te falta uno para el fútbol 5? ¿Son tres para el truco? ¿Querés sumar otro grupo a la previa?
> Caigo es la app para sumarte a planes a los que les falta gente, o completar los tuyos.
>
> No gira alrededor de perfiles ni de chatear sin fin: gira alrededor del plan. Ves qué pasa cerca,
> cuántos faltan, y en pocos toques estás ahí.
>
> AHORA
> Los planes de las próximas horas cerca tuyo, en lista o en el mapa. Fútbol, pádel, truco, previas,
> salidas, citas dobles y más.
>
> EXPLORAR
> La agenda de la semana por día y por tipo de plan. Encontrá algo para hoy o para el finde.
>
> ARMÁ TU PLAN EN TRES PASOS
> Qué hacen, cuándo y dónde, y cuántos faltan. Elegí si entra cualquiera o si aprobás vos, si es
> sólo para tu gente o para todos, y si la dirección se ve recién cuando aceptás a alguien.
>
> SOLO O CON TU GRUPO
> Sumate solo o con tus amigos: «Somos 2+» cuenta a todo tu grupo en el cupo.
>
> CHAT DEL PLAN
> Cuando entrás, se abre el chat con los que van. Coordinan ahí y listo.
>
> GENTE Y CITAS
> Conocé gente que también tiene ganas de salir. En Gente sumás amigos para tus planes; en Citas,
> si el interés es mutuo, se abre el chat.
>
> CONFIANZA
> Todos verifican su teléfono y una selfie antes de sumarse. Podés verificar tu DNI para planes que
> lo piden. Ves la asistencia de cada persona, y podés bloquear y reportar.
>
> LUGARES ALIADOS
> Bares, cafés y canchas publican planes con entrada o seña. Pagás con Mercado Pago, guardás tu
> entrada en Google Wallet y hacés check-in con Caigo Tap.
>
> CAIGO PLUS
> Sumadas ilimitadas, ver quién te sumó, más «me encanta» en Citas, un plan destacado por semana y
> todos los filtros.
>
> Arrancamos en Mar del Plata y Villa Gesell. Hacé planes. Conocé gente.

**Seguridad de los datos** (formulario de Play):
| Dato | Se recolecta | Para qué | Se comparte |
|---|---|---|---|
| Nombre, edad, fotos, bio | Sí | Perfil | No |
| Teléfono | Sí | Verificación | No |
| Selfie y fotos del DNI | Sí | Verificación (revisión manual) | No |
| Ubicación aproximada | Sí (en la app) | Planes cerca | No |
| Mensajes | Sí | Chat del plan y privados | No |
| Compras | Sí | Plus y entradas | Mercado Pago / Google Play procesan el pago |
| Identificadores del dispositivo | Sí | Avisos (FCM) | No |
Cifrado en tránsito: sí. Borrar la cuenta: sí, desde Perfil en la app y en la web.
**Link para borrar la cuenta** (lo pide Play): https://caigo.com.ar/app/borrar (pide ingresar y borra todo: `DELETE /v1/yo`).

## App Store

- **Subtítulo** (30): Hacé planes. Conocé gente.
- **Palabras clave** (100): `planes,salir,amigos,fútbol,truco,previa,juntadas,gente,citas,Mar del Plata,Gesell`
- **Texto promocional** (170):
  > ¿Te falta uno para el fútbol? ¿Son tres para el truco? Sumate a planes a los que les falta
  > gente cerca tuyo, o completá los tuyos.
- **Descripción:** la misma de Play, cambiando «Google Wallet» por «Apple Wallet».
- **Notas para la revisión:** la app pide ingresar con Apple o Google y verificar teléfono y selfie.
  Hay que dejarles una cuenta de prueba (el código del teléfono llega al mail de esa cuenta). Explicar que
  el modo Citas se puede reportar y bloquear, y que se modera a mano desde caigo.com.ar/equipo.
- **Etiquetas de privacidad:** las mismas filas que la tabla de Play («Datos vinculados a vos»).

## Capturas

Play pide al menos 2 (mejor 4–8) de 1080×1920 o más; App Store, de 6,9" (1320×2868) y 6,5".
Propuesta, en orden: Ahora con el mapa, detalle de un plan («Faltan 3»), Explorar, crear plan,
chat del plan, Gente/Citas, Caigo Plus. Se sacan del emulador / simulador con los planes semilla.
