# Propuestas de Caigo (16/11/2026)

Análisis crítico de Caigo contra Chicle y Timeleft, propuestas de producto y la landing nueva. **Nada de esto está desplegado.** La landing de producción sigue siendo `apps/web`.

| Archivo | Qué es | Artifact |
|---|---|---|
| `informe.html` | Informe crítico, cuatro caminos y la recomendación (lugares + la mesa de los jueves + lanzar en Gesell en enero). **La estrategia todavía no está aprobada.** | https://claude.ai/artifact/HbX44qqfy3QsmT6gZcBNwQ |
| `landing.html` | **Landing A, la elegida por Nahuel.** Nocturna, «Falta uno.». | https://claude.ai/artifact/VjG2vzGbAMBJUwbyPgfgPU |
| `cartelera.html` | Landing B, «Cartelera» (risografía con tiritas para arrancar). Alternativa, no elegida. | https://claude.ai/artifact/PHkhwuGqAe8AUHY3TFeE3D |
| `capturas/` | Timeleft, Chicle y la caigo.com.ar actual, a 390 y 1440 px. | |
| `img/` | Imágenes web de las tres páginas, más miniaturas (`t-*`) y pantallas del handoff (`m-*`). | |

Las fotos originales, los mockups y todas las piezas para redes están en `~/Trabajo/vimo/marketing/caigo/recursos/` (índice en su `README.md`; galería en https://claude.ai/artifact/CFgnTKTU9ZZLhYMhufBVrJ). `visual/` y `mockups/` quedan fuera de git porque son copias de esos originales.

## Qué hace la landing A

- **Héroe.** Foto de fútbol 5 con el titular «Falta uno.» y una tarjeta de plan («Somos 9 de 10») que abre el detalle.
- **«Esta noche en <ciudad>».** Lista de planes con foto y «Falta N». Por ahora **sólo Argentina, con 15 ciudades por región**. La ciudad se detecta por la zona horaria de la provincia, y hay un botón «Usar mi ubicación» que elige la más cercana por GPS. En producción: ciudad por IP del proxy primero, después GPS.
- **Detalle del plan y lead.** «Sumarme» pide crear la cuenta (Google, Apple o nombre + celular/mail, con términos aceptados) y termina en «¡Listo! Pediste sumarte», con el aviso de DNI y selfie antes de ir. Cada registro es un lead con el plan que lo trajo. En el Artifact es una demostración; en producción va por Clerk + API.
- **Resto de la página.** Cómo funciona, mockups reales de la app (pantallas del handoff v5), la mesa de los jueves (**todavía no existe en el producto**), «Todos verificados», temporada (sólo en ciudades de la costa), lugares, preguntas frecuentes y cierre.

## Para pasarla a `apps/web` (cuando Nahuel lo pida)

- La lista de planes sale de la API (planes con lugar libre en las próximas horas, por ciudad), con un estado vacío diseñado para cuando no haya nada.
- El registro va por Clerk. Guardar la intención de sumarse (plan, ciudad, origen y UTM) para medir los leads.
- Las fotos son generadas con ChatGPT: reemplazarlas por fotos reales cuando haya planes.
- Sacar «Próximamente» y la lista de espera de la landing actual.
- No desplegar sin el ok de Nahuel.
