# Contrato de lo que falta (31/10)

Sale de la auditoría contra el handoff. La API lo implementa en la rama `api-faltantes`; web, Android
e iOS programan contra este contrato en sus ramas. Todo en JSON, errores en problem+json con `code`.
Si al implementarlo algo tiene que cambiar, se cambia acá primero.

## 1. Género y «Solo mujeres / Solo diversidades»
- `GET /v1/yo` suma `genero` (`""` | `mujer` | `varon` | `no_binario`) y `diversidad` (bool: «me
  identifico como parte de la diversidad»). `PATCH /v1/yo` acepta los dos. Son autodeclarados, no se
  muestran a nadie.
- Sumarse (`POST /v1/planes/{id}/sumarme`), comprar entradas y aceptar invitaciones a un plan con
  `genero: mujeres` piden `genero == mujer`; con `genero: diversidades` piden `diversidad == true` o
  `genero == no_binario`. Errores 422: `falta_genero` (no lo cargó: la app lo pregunta y reintenta),
  `solo_mujeres`, `solo_diversidades`.
- El mazo de Citas no cambia.

## 2. Editar, cancelar y sacar gente
- `PATCH /v1/planes/{id}` (organizador; plan que no empezó): cualquier campo de la creación
  (`titulo`, `descripcion`, `empiezaEn`, `duracionMin`, `total`, `ingreso`, `visibilidad`,
  `soloVerificados`, `ocultarDireccion`, `edadMin`, `edadMax`, `genero`, `nivel`, `costoCentavos`,
  `foto`, `zona`, `lugarNombre`, `direccion`, `lat`, `lng`). `total` no puede quedar por debajo de
  los que ya van (409 `cupo_menor`). Devuelve el Plan. Si cambian fecha, hora o lugar, aviso
  `plan.cambiado` a los aceptados.
- Planes de lugares: `PATCH /v1/lugares/{lugar}/planes/{plan}` con los campos del panel (sin precio ni
  cobro si ya hay entradas pagas: 409 `con_entradas`).
- `DELETE /v1/planes/{id}` (existe) y `DELETE /v1/lugares/{lugar}/planes/{plan}` (nuevo): cancelan,
  avisan `plan.cancelado` a todos los aceptados y pendientes, y devuelven el 100 % (cargo incluido) de
  las entradas pagas por Mercado Pago.
- `DELETE /v1/planes/{id}/personas/{usuarioId}` (organizador): saca a alguien aceptado (no a una
  entrada paga: 409 `con_entrada`). Aviso `solicitud.sacada`.
- `DELETE /v1/entradas/{id}` (quien compró): cancela su entrada según la política del plan:
  `flex` → devuelve todo hasta 24 h antes; `mod` → hasta 72 h antes todo, después nada de la seña;
  `strict` → 409 `sin_reembolso`. El cargo por servicio no se devuelve cuando cancela la persona.
  Responde `{ reembolsoCentavos }`.

## 3. Avisos push nuevos (worker)
Datos del push: `{"plan": id, "pantalla": "detalle" | "checkin" | "repetimos" | "chat"}`.
- `plan.recordatorio`: 2 h antes («Hoy 21:30: Truco en Güemes. ¿Vas?») y al abrir el check-in
  (30 min antes, pantalla `checkin`).
- `plan.cancelado`, `plan.cambiado`, `solicitud.rechazada`, `solicitud.sacada`.
- `plan.repetimos`: al organizador, 1 h después del final (pantalla `repetimos`).
- `plan.nuevo_de_tu_gente`: a quienes tienen al organizador en su gente, al publicar (máx. 3 por
  persona por día).
- `plan.cerca`: a quien tenga ubicación reciente (< 6 h) a menos de 5 km, intereses en común y no
  silenció avisos, cuando se publica un plan que empieza en < 3 h. Máx. 1 por persona por día.
- `PUT /v1/yo/ubicacion` `{lat, lng}`: la app la manda al abrir (redondeada a ~1 km; se borra a las
  24 h). `PATCH /v1/yo` acepta `avisos` `{planesCerca: bool, tuGente: bool}` (por defecto true).

## 4. Seguridad y bloqueos
- `GET /v1/bloqueos` → `{ bloqueos: [{id, nombre, foto}] }`; `DELETE /v1/bloqueos/{id}` desbloquea.
- `contactoConfianza` en `PATCH /v1/yo` ya existe (texto: teléfono o mail).

## 5. Ahora / Explorar / personas
- `q` (texto, ≥ 2 letras: título, zona, lugar) y `zona` en `/v1/planes/ahora|explorar` y sus
  versiones públicas. Básicos (no Plus).
- `GET /v1/publico/zonas?ciudad=Mar+del+Plata` → `{ zonas: ["Güemes", …] }` (las de planes de los
  próximos 30 días + las fijas de cada ciudad).
- `GET /v1/personas/{id}` suma `proximos` (sus próximos planes que quien mira puede ver; mismo formato
  que en el mazo).
- `GET /v1/version` suma `minAndroid` y `minIos` (enteros, versionCode / build); si la app es más
  vieja, pide actualizar.

## 6. Lugares
- Check-in de planes gratis en la puerta: `POST /v1/lugares/{id}/planes/{plan}/checkin/{usuarioId}`
  (miembro del lugar; aceptado en ese plan). `POST /v1/lugares/{id}/validar` además acepta el QR de
  check-in que muestra la app (el mismo token que `/v1/planes/{id}/qr`).
- `PUT /v1/lugares/{id}` (existe) acepta `foto`.

## 7. Backoffice (`/v1/equipo/...`, sólo equipo)
- `GET usuarios?q=` → id, nombre, foto, ciudad, creado, verificada, dni, suspendido, reportes.
  `POST usuarios/{id}/suspender {motivo}` y `POST usuarios/{id}/reactivar`.
- `GET planes?q=&cual=proximos|reportados` y `POST planes/{id}/cancelar {motivo}` (como el
  organizador: avisa y devuelve).
- `GET lugares?estado=` (todos) y `POST lugares/{id}/suspender|reactivar`.
- `GET metricas` → por semana (últimas 8): planes publicados, completados (llegaron al cupo y
  empezaron), asistencia (check-ins / aceptados), horas promedio hasta completar, repetición a 30
  días, organizadores activos, altas por `origen`.
- Planes semilla desde la web: `POST planes` con el cuerpo de `POST /v1/planes` + `organizadorId`.

## 8. Crecimiento
- `POST /v1/publico/espera {email, ciudad}` (lista de espera; tope por IP).
- `PATCH /v1/yo` acepta `origen` una sola vez (ej. `p:<slug>`, `utm:instagram`); la web lo guarda en
  una cookie al llegar con `?ref=` o `?utm_source=` y lo manda después del ingreso.

## 9. Recurrencia
- El worker crea la próxima fecha de un plan `recurrente` 6 días antes, aunque nadie conteste
  «¿Repetimos?» (mismo plan, mismo organizador, sin sumados). «¿Repetimos? No» corta la serie.

## 10. Tiempo real
- `escribiendo` también en privados: el cliente manda `{"tipo":"escribiendo","privado":"<id>"}` por el
  WebSocket y el otro recibe `{"tipo":"escribiendo","privado":"<id de quien escribe>"}`.
