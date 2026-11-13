# Rutas de la API

Generado desde el código (`apps/api/internal/*/http.go`). Base: `https://api.caigo.com.ar`.
Sesión: `Authorization: Bearer cs_…` (de `POST /v1/sesion`). Errores: problem+json `{title, detail, code, issues[]}`.
«Pública» = sin la sesión de Caigo; varias se autentican solas: `/v1/sesion` (JWT de Clerk), `/v1/tiempo-real` (token en ?token=), `/v1/wallet/...` (ApplePass), webhooks (firma o clave), `/v1/archivos/{id}` (selfie y DNI piden sesión del dueño o del equipo; van cifrados en el disco, se sirven con `no-store`, sin `CAIGO_CLAVE_VERIFICACION` responden 503 `sin_clave_verificacion`, y cada vez que el equipo abre uno ajeno queda en `accesos_verificacion`).

| Método | Ruta | Acceso | Paquete |
|---|---|---|---|
| GET | `/salud/listo` | pública | plataforma/servidor |
| GET | `/salud/vivo` | pública | plataforma/servidor |
| POST | `/v1/archivos` | sesión | dominio/confianza |
| GET | `/v1/archivos/{id}` | pública | dominio/confianza |
| GET | `/v1/bloqueos` | sesión | dominio/personas |
| DELETE | `/v1/bloqueos/{id}` | sesión | dominio/personas |
| PUT | `/v1/bloqueos/{id}` | sesión | dominio/personas |
| DELETE | `/v1/cuenta` | sesión | dominio/confianza |
| PUT | `/v1/dispositivos` | sesión | dominio/avisos |
| DELETE | `/v1/dispositivos/{token}` | sesión | dominio/avisos |
| DELETE | `/v1/entradas/{id}` | sesión | dominio/lugares |
| GET | `/v1/equipo/arrepentimientos` | equipo | dominio/confianza |
| POST | `/v1/equipo/arrepentimientos/{id}` | equipo | dominio/confianza |
| GET | `/v1/equipo/dni` | equipo | dominio/confianza |
| GET | `/v1/equipo/pedidos-placas` | equipo | dominio/lugares |
| POST | `/v1/equipo/pedidos-placas/{id}/atendido` | equipo | dominio/lugares |
| POST | `/v1/equipo/dni/{id}` | equipo | dominio/confianza |
| GET | `/v1/equipo/lugares` | equipo | dominio/lugares |
| POST | `/v1/equipo/lugares/{id}` | equipo | dominio/lugares |
| POST | `/v1/equipo/lugares/{id}/reactivar` | equipo | dominio/lugares |
| POST | `/v1/equipo/lugares/{id}/suspender` | equipo | dominio/lugares |
| GET | `/v1/equipo/metricas` | equipo | dominio/equipo |
| GET | `/v1/equipo/planes` | equipo | dominio/equipo |
| POST | `/v1/equipo/planes` | equipo | dominio/equipo |
| POST | `/v1/equipo/planes/{id}/cancelar` | equipo | dominio/equipo |
| GET | `/v1/equipo/reportes` | equipo | dominio/confianza |
| POST | `/v1/equipo/reportes/{id}` | equipo | dominio/confianza |
| GET | `/v1/equipo/usuarios` | equipo | dominio/equipo |
| POST | `/v1/equipo/usuarios/{id}/reactivar` | equipo | dominio/equipo |
| POST | `/v1/equipo/usuarios/{id}/suspender` | equipo | dominio/equipo |
| GET | `/v1/gente` | sesión | dominio/personas |
| GET | `/v1/gente/mazo` | sesión | dominio/gente |
| GET | `/v1/gente/recibidos` | sesión | dominio/gente |
| DELETE | `/v1/gente/recibidos/{id}` | sesión | dominio/gente |
| DELETE | `/v1/gente/{id}` | sesión | dominio/personas |
| PUT | `/v1/gente/{id}` | sesión | dominio/personas |
| POST | `/v1/gente/{id}/reaccion` | sesión | dominio/gente |
| GET | `/v1/invitaciones` | sesión | dominio/gente |
| POST | `/v1/invitaciones/{planId}` | sesión | dominio/gente |
| GET | `/v1/lugares` | sesión | dominio/lugares |
| POST | `/v1/lugares` | sesión | dominio/lugares |
| GET | `/v1/lugares/{id}` | sesión | dominio/lugares |
| PUT | `/v1/lugares/{id}` | sesión | dominio/lugares |
| GET | `/v1/lugares/{id}/cobros` | sesión | dominio/lugares |
| DELETE | `/v1/lugares/{id}/mercadopago` | sesión | dominio/lugares |
| POST | `/v1/lugares/{id}/mercadopago` | sesión | dominio/lugares |
| GET | `/v1/lugares/{id}/placas` | sesión | dominio/lugares |
| POST | `/v1/lugares/{id}/placas` | sesión | dominio/lugares |
| DELETE | `/v1/lugares/{id}/placas/{placa}` | sesión | dominio/lugares |
| POST | `/v1/lugares/{id}/placas/pedido` | sesión | dominio/lugares |
| GET | `/v1/lugares/{id}/placas/pedidos` | sesión | dominio/lugares |
| GET | `/v1/lugares/{id}/planes` | sesión | dominio/lugares |
| POST | `/v1/lugares/{id}/planes` | sesión | dominio/lugares |
| DELETE | `/v1/lugares/{id}/planes/{plan}` | sesión | dominio/lugares |
| GET | `/v1/lugares/{id}/planes/{plan}` | sesión | dominio/lugares |
| PATCH | `/v1/lugares/{id}/planes/{plan}` | sesión | dominio/lugares |
| GET | `/v1/lugares/{id}/planes/{plan}/asistentes` | sesión | dominio/lugares |
| POST | `/v1/lugares/{id}/planes/{plan}/checkin/{usuarioId}` | sesión | dominio/lugares |
| POST | `/v1/lugares/{id}/validar` | sesión | dominio/lugares |
| POST | `/v1/mercadopago/vuelta` | sesión | dominio/lugares |
| GET | `/v1/pases` | sesión | dominio/lugares |
| GET | `/v1/pases/entradas/{id}/{wallet}` | sesión | dominio/wallet |
| GET | `/v1/pases/socios/{lugar}/{wallet}` | sesión | dominio/wallet |
| GET | `/v1/personas/{id}` | sesión | dominio/personas |
| POST | `/v1/planes` | sesión | dominio/planes |
| GET | `/v1/planes/ahora` | sesión | dominio/planes |
| GET | `/v1/planes/explorar` | sesión | dominio/planes |
| GET | `/v1/planes/mios` | sesión | dominio/planes |
| DELETE | `/v1/planes/{id}` | sesión | dominio/planes |
| GET | `/v1/planes/{id}` | sesión | dominio/planes |
| PATCH | `/v1/planes/{id}` | sesión | dominio/planes |
| POST | `/v1/planes/{id}/checkin` | sesión | dominio/planes |
| POST | `/v1/planes/{id}/destacar` | sesión | dominio/planes |
| POST | `/v1/planes/{id}/en-vivo` | sesión | dominio/planes |
| POST | `/v1/planes/{id}/entradas` | sesión | dominio/lugares |
| POST | `/v1/planes/{id}/invitar` | sesión | dominio/gente |
| GET | `/v1/planes/{id}/mensajes` | sesión | dominio/chat |
| POST | `/v1/planes/{id}/mensajes` | sesión | dominio/chat |
| DELETE | `/v1/planes/{id}/personas/{usuarioId}` | sesión | dominio/planes |
| GET | `/v1/planes/{id}/qr` | sesión | dominio/planes |
| POST | `/v1/planes/{id}/repetimos` | sesión | dominio/planes |
| GET | `/v1/planes/{id}/solicitudes` | sesión | dominio/planes |
| DELETE | `/v1/planes/{id}/sumarme` | sesión | dominio/planes |
| POST | `/v1/planes/{id}/sumarme` | sesión | dominio/planes |
| GET | `/v1/plus` | sesión | dominio/plus |
| POST | `/v1/plus/apple` | sesión | dominio/plus |
| POST | `/v1/plus/google` | sesión | dominio/plus |
| POST | `/v1/plus/mercadopago` | sesión | dominio/plus |
| GET | `/v1/privados` | sesión | dominio/chat |
| GET | `/v1/privados/{id}` | sesión | dominio/chat |
| POST | `/v1/privados/{id}` | sesión | dominio/chat |
| POST | `/v1/publico/arrepentimiento` | pública | dominio/confianza |
| POST | `/v1/publico/espera` | pública | dominio/equipo |
| GET | `/v1/publico/planes/ahora` | pública | dominio/planes |
| GET | `/v1/publico/planes/explorar` | pública | dominio/planes |
| GET | `/v1/publico/planes/{slug}` | pública | dominio/planes |
| GET | `/v1/publico/vivo/{token}` | pública | dominio/planes |
| GET | `/v1/publico/zonas` | pública | dominio/planes |
| POST | `/v1/reportes` | sesión | dominio/personas |
| DELETE | `/v1/sesion` | pública | plataforma/sesion |
| POST | `/v1/sesion` | pública | plataforma/sesion |
| POST | `/v1/solicitudes/{id}/{accion}` | sesión | dominio/planes |
| POST | `/v1/tap` | sesión | dominio/lugares |
| GET | `/v1/tiempo-real` | pública | dominio/chat |
| GET | `/v1/verificacion` | sesión | dominio/confianza |
| POST | `/v1/verificacion/dni` | sesión | dominio/confianza |
| POST | `/v1/verificacion/selfie` | sesión | dominio/confianza |
| POST | `/v1/verificacion/telefono` | sesión | dominio/confianza |
| POST | `/v1/verificacion/telefono/confirmar` | sesión | dominio/confianza |
| GET | `/v1/version` | pública | plataforma/servidor |
| GET | `/v1/wallet/v1/devices/{disp}/registrations/{tipo}` | pública | dominio/wallet |
| DELETE | `/v1/wallet/v1/devices/{disp}/registrations/{tipo}/{serie}` | pública | dominio/wallet |
| POST | `/v1/wallet/v1/devices/{disp}/registrations/{tipo}/{serie}` | pública | dominio/wallet |
| POST | `/v1/wallet/v1/log` | pública | dominio/wallet |
| GET | `/v1/wallet/v1/passes/{tipo}/{serie}` | pública | dominio/wallet |
| POST | `/v1/webhooks/apple` | pública | dominio/plus |
| POST | `/v1/webhooks/google` | pública | dominio/plus |
| POST | `/v1/webhooks/mercadopago` | pública | integraciones/mercadopago |
| DELETE | `/v1/yo` | sesión | dominio/confianza |
| GET | `/v1/yo` | sesión | dominio/personas |
| PATCH | `/v1/yo` | sesión | dominio/personas |
| PUT | `/v1/yo/ubicacion` | sesión | dominio/personas |

## Entradas con la API de Orders de Mercado Pago (7/11)
- `POST /v1/planes/{id}/entradas` responde igual que antes (`{entradaId, link, totalCentavos, cargoCentavos,
  saldoCentavos}`); `link` ahora es el `checkout_url` de una orden de Checkout Pro (`POST /v1/orders`, `type
  online`, `processing_mode manual`, con el token del lugar, `marketplace_fee` = cargo, vence a los 30 min:
  `PT30M`, idempotencia `orden:<referencia>`), en vez del `init_point` de una preferencia. Las apps no cambian.
- `POST /v1/webhooks/mercadopago` (misma firma `x-signature`) reparte por `type`: `order` (evento «Order
  (Mercado Pago)») → outbox `mp.orden` → el worker lee `GET /v1/orders/{id}` y aplica la transacción que
  cuenta (`processed` paga, `refunded`/`charged_back` devuelve, `canceled`/`expired` anula; `failed` deja la
  compra esperando); `payment` → `mp.pago` sigue para las entradas creadas con preferencias (y concilia por
  `external_reference` también los pagos de las órdenes); `subscription_preapproval` es Plus (preapproval, sin
  cambios: Orders no cubre suscripciones).
- Devoluciones: entrada con `mp_orden` → `POST /v1/orders/{id}/refund` (total sin cuerpo; parcial con
  `{transactions: [{id, amount}]}`); entradas viejas → `POST /v1/payments/{id}/refunds`.
- Un «approved» sólo paga una entrada `pendiente`: una ya devuelta o cancelada no se reabre con un aviso viejo.
- Migración 00017: `entradas.mp_orden`, `entradas.mp_transaccion`.

## Caigo Plus: lo que suma (31/10)
- `GET /v1/plus` → `estado` suma `encantasHoy`, `limiteEncantas` (1 sin Plus, 5 con Plus) y
  `destacarDesde` (vacío: puede destacar ya, si tiene Plus).
- «Me encanta» en Citas (`accion: encanta`): pasado el límite, 409 `limite_encanta`. Quien lo recibe
  ve primero a quien se lo dio, en el mazo y en «Les interesás».
- `POST /v1/planes/{id}/destacar` (Plus, uno por semana, plan propio que no pasó): el plan sale con
  `destacado: true` y va primero en Ahora y Explorar. Sin Plus: 403 `plus`; ya usado: 409 `destacado_usado`.
- Filtros finos de Ahora/Explorar (Plus): `verificados=1`, `faltan=N`, `edadDesde`,
  `edadHasta` (edad de quien organiza) y `gratis=1`. Sin Plus: 403 `plus`. Los básicos (`categoria`,
  `ciudad`, `desde`, `hasta`, `lat`, `lng`, `radio`) son para todos.
- `POST /v1/planes/{id}/entradas` acepta `modo`: `entrada` (pagar todo) o `sena` (sólo si el plan
  tiene seña). Vacío: lo que diga el plan.

## Borrar la cuenta (31/10, en dos tiempos desde el 12/11)
`DELETE /v1/cuenta` (204, con sesión; `DELETE /v1/yo` es el mismo, el de antes). Cuerpo opcional
`{"confirmarEmail": "…"}` (la web): si viene y no es el mail de la cuenta en Clerk, 422 `email_distinto`.
`GET /v1/yo` trae `email` (el de Clerk, copiado al entrar) para que las apps confirmen escribiéndolo.
**En el momento:** se cierran todas sus sesiones, se borran sus
dispositivos de push, su ubicación y los códigos del teléfono, y la cuenta queda desactivada (`baja_en`).
Sus planes futuros (no los de lugares) se cancelan como si los cancelara: aviso `plan.cancelado` con el
motivo «La persona que organizaba se dio de baja» y reembolso entero de las entradas pagas; sus propias
entradas pagas de planes futuros se le devuelven (el precio, sin el cargo) y sus solicitudes en planes
futuros se cancelan. Desde ahí, `POST /v1/sesion` (y cualquier pedido con el JWT de Clerk) responde
**409** `code: "cuenta_borrada"`, `detail`: «Esta cuenta se borró. Si fue un error, escribinos a
hola@caigo.com.ar dentro de los 30 días y la recuperamos.» (mostrarlo tal cual). Con el token `cs_…` viejo: 401.

**A los 30 días** (el worker, cada hora): se borran sus fotos, selfie y DNI (archivos y verificación),
teléfono, nacimiento, género, contacto de confianza, bio, intereses, Mi gente, los «me interesa» e
invitaciones de Gente/Citas y sus bloqueos; la fila queda con nombre «Cuenta borrada» (así firman sus
mensajes en los chats de los demás) y el clerk_id se libera: si vuelve a entrar, arranca una cuenta nueva.
Los pagos y el historial de planes quedan atados al id sin datos. En los 30 días se recupera a mano:
`caigoctl recuperar-cuenta <mail|user_…>`. La cuenta de Clerk no se toca (es de todo Grupo Vimo). Plus
hay que cancelarlo en la tienda. Página pública de los pasos (la URL de Play): `https://caigo.com.ar/borrar-cuenta`.

**Después de la purga** (`confianza.Plazos`, el worker cada hora): los mensajes del chat de un plan (firmados
«Cuenta borrada») se borran al año de que terminó el plan (o de la purga, lo último); los privados, la asistencia
(`solicitudes`) y «¿Volverías?», al año de la purga; los reportes que hizo o le hicieron, a los 2 años. El texto de
sus solicitudes se vacía en la purga. Los `intereses` de todos duran 6 meses (un match, mientras se escriban y 6
meses después del último privado).

**Vetados (12/11):** suspender a alguien (`POST /v1/equipo/usuarios/{id}/suspender` o resolver un reporte con
`suspender`) anota en `vetados` un HMAC-SHA256 de su mail y de los DNI que se le aprobaron (clave derivada con HKDF
de `CAIGO_CLAVE_VERIFICACION`, propósito `caigo:vetados:v1`; nada en claro). Mientras exista la cuenta quedan atados a
ella (reactivar los borra); la purga los suelta y vencen a los 2 años. Un alta con un mail vetado (normalizado:
minúsculas, sin `+algo`, y sin puntos en Gmail) responde **403** como una suspendida; un `POST /v1/verificacion/dni`
con un número vetado, **422** `code: "dni_vetado"`. Se puede suspender una cuenta dada de baja que todavía no se purgó.
Sin la clave, la purga no purga una suspendida que no tenga huellas (espera).

**Fotos que se sacan:** al sacar una foto del perfil (`PATCH /v1/yo` `fotos`), de un plan o de un lugar, si no la usa
otro perfil, plan o lugar, se borra de `archivos` en el momento (`GET /v1/archivos/{id}` da 404) y el worker la borra
del disco (evento `archivo.borrar`). Las fotos sueltas con más de un día (subidas que nunca se usaron) las barre el worker.

**Compras de Google:** el aviso de Google (`POST /v1/webhooks/google`, RTDN) además confirma
(acknowledge) la compra si Google todavía lo espera, está vigente y trae el id de la persona: no depende
de que la app llegue a mandar `POST /v1/plus/google`.

## Lo que faltaba contra el handoff (31/10, rama `api-faltantes`)
Implementa `docs/contrato-faltantes.md` (migración 00014). Lo que hay que saber al programar contra esto:

**Perfil.** `GET /v1/yo` suma `genero` (`""`|`mujer`|`varon`|`no_binario`) y `avisos`
`{planesCerca, tuGente}` (todo privado: no sale en `/v1/personas/{id}`). `PATCH /v1/yo` acepta esos dos
(en `avisos` se puede mandar uno solo; `diversidad`, de las versiones viejas, se acepta y se ignora) y `origen` (`[A-Za-z0-9:_./-]`, hasta 80; sólo la primera vez, las
siguientes se ignoran sin error). `PUT /v1/yo/ubicacion {lat, lng}` → 204 (se guarda redondeada a 0,01°
y se borra a las 24 h). `GET /v1/personas/{id}` suma `proximos` (hasta 5: `{id, titulo, categoria,
empiezaEn, foto}`). `GET /v1/bloqueos` → `{bloqueos: [{id, nombre, foto}]}`; `DELETE /v1/bloqueos/{id}`.

**Género.** Sumarse, comprar entradas y aceptar una invitación a un plan `mujeres`: 422 `falta_genero` |
`solo_mujeres`. A un plan `diversidades`: el pedido (`/sumarme`, `/entradas`, `/v1/invitaciones/{planId}`)
lleva `diversidad: true`, que la persona confirma en ese momento; sin eso, 422 `confirmar_diversidad`. No
se guarda en ningún lado (AAIP 1.2, migración 00021). Del DNI sólo se valida la edad: el `sexo` que
mandan las versiones viejas se descarta.

**Editar y cancelar.** `PATCH /v1/planes/{id}`: el cuerpo de `POST /v1/planes` (parcial) + `total`;
`categoria`, `ciudad`, `recurrente`, `somos` y `faltan` se ignoran. Errores: 404 (no es tuyo), 409
`de_un_lugar` | `cancelado` | `empezo` | `cupo_menor`. Si cambia la hora, la dirección, el lugar o la
zona: aviso `plan.cambiado`. `PATCH /v1/lugares/{id}/planes/{plan}`: los campos de `POST
/v1/lugares/{id}/planes` (parcial; `recurrente` no se cambia), 409 `con_entradas` (precio/cobro/seña con
entradas pagas), `cupo_menor`, `empezo`; devuelve el `PlanDelLugar`. `DELETE /v1/planes/{id}` y `DELETE
/v1/lugares/{id}/planes/{plan}` → 204: aviso `plan.cancelado` a aceptados y pendientes, las compras sin
pagar se anulan y cada entrada pagada se devuelve entera por Mercado Pago (evento `entrada.reembolsar`,
lo hace el worker). `DELETE /v1/planes/{id}/personas/{usuarioId}` → 204 (queda `rechazada`: no puede
volver a pedir), 409 `con_entrada` | `es_tuyo`. Rechazar una solicitud ahora avisa
(`solicitud.rechazada`).

**Entradas.** `DELETE /v1/entradas/{id}` → `{reembolsoCentavos}`. `flex`: lo del lugar si faltan ≥ 24 h;
`mod`: si faltan ≥ 72 h; después de eso se cancela igual (libera el lugar) con reembolso 0. `strict`: 409
`sin_reembolso`. Otros: 409 `no_pagada` | `cancelado` | `paso`. El cargo por servicio no se devuelve.
Con reembolso, la entrada queda `reembolsada` (con `reembolso_centavos`); sin, `cancelada`.

**Check-in en el lugar.** `GET /v1/planes/{id}/qr` a alguien aceptado le devuelve **su** código (al
organizador, el de siempre). `POST /v1/lugares/{id}/validar` acepta ese código además de `TK-xxxx`, y
`POST /v1/lugares/{id}/planes/{plan}/checkin/{usuarioId}` marca la llegada a mano; los dos responden la
`Validacion` (`repetida: true` si ya había llegado). Los asistentes suman `usuarioId`.

**Buscar.** `q` (≥ 2 letras; título, zona, lugar o nombre del lugar, sin tildes) y `zona` en
`/v1/planes/ahora|explorar` y `/v1/publico/planes/ahora|explorar`. `GET /v1/publico/zonas?ciudad=` →
`{zonas: [...]}`. `GET /v1/version` → `{version, minAndroid, minIos}` (`CAIGO_MIN_ANDROID`,
`CAIGO_MIN_IOS`; 0 = cualquiera).

**Avisos push nuevos** (datos `{"plan": id, "pantalla": "detalle"|"checkin"|"repetimos"|"chat"}`):
`plan.recordatorio` 2 h antes (a los aceptados que ya estaban; no al organizador) y al abrir el check-in
(30 min antes, a los aceptados sin check-in, pantalla `checkin`); `plan.repetimos` al organizador 1 h
después del final (no en planes de lugares); `plan.cancelado`, `plan.cambiado`, `solicitud.rechazada` y
`solicitud.sacada` (pantalla `ahora`); al publicar (`plan.publicado`): «X armó un plan» a quienes tienen al
organizador en su gente (máx. 3 por día) y «Cerca tuyo» a quienes mandaron ubicación hace < 6 h a < 5 km,
con algún interés igual a uno del organizador o a la categoría, si el plan empieza en < 3 h (máx. 1 por
día). Los dos respetan bloqueos, `avisos` del perfil y quién puede ver el plan. El worker corre los
programados cada minuto (`avisos_enviados` los hace idempotentes) y la recurrencia.

**Recurrencia.** El worker crea la próxima fecha de cada serie (`recurrente`) cuando falta ≤ 6 días: mismo
plan, sólo el grupo del organizador (los de lugares, con el mismo cobro). «¿Repetimos? No» del
organizador corta la serie (`recurrente = false` desde ese plan); cancelar el último plan también. «Sí»
dos veces no duplica: devuelve el de la semana siguiente si ya existe.

**Backoffice** (`/v1/equipo/...`, 403 si no es del equipo). `GET usuarios?q=` → `{usuarios: [{id, nombre,
foto, ciudad, creado, verificada (selfie), dni, suspendido, reportes}]}`; `POST usuarios/{id}/suspender
{motivo}` | `reactivar` → 204. `GET planes?q=&cual=proximos|reportados` → `{planes: [{id, slug, titulo,
empiezaEn, ciudad, zona, organizador{id,nombre}, lugar, somos, total, cancelado, reportes}]}`; `POST
planes/{id}/cancelar {motivo}` → 204; `POST planes` (cuerpo de `POST /v1/planes` + `organizadorId`) → 201
con el Plan. `GET lugares?estado=` (**sin estado: todos**, los pendientes primero; antes devolvía sólo
pendientes); `POST lugares/{id}/suspender {motivo}` (aprobado → suspendido) | `reactivar`. `GET metricas`
→ `{semanas: [{desde, publicados, completados, asistencia, horasHastaCompletar, repeticion30d,
organizadoresActivos, altas, altasPorOrigen}]}`: 8 semanas (lunes, hora de Argentina), la actual primero;
las proporciones van de 0 a 1 y son `null` si no hubo datos.

**Lista de espera.** `POST /v1/publico/espera {email, ciudad}` → 204 (un mail repetido también; 5 por
hora por IP).

**Tiempo real.** `{"tipo":"escribiendo","privado":"<id del otro>"}` por el WebSocket; el otro recibe
`{"tipo":"escribiendo","privado":"<id de quien escribe>","quien":{id,nombre}}` (sólo si pueden hablar).

## Pedidos de placas Caigo Tap (31/10)
- `POST /v1/lugares/{id}/placas/pedido` `{cantidad 1-20, tipo: "comun"|"ntag424", nota ≤500}` → 201 con el
  pedido. Miembro del lugar y lugar aprobado (409 `sin_aprobar`); hasta 3 pendientes a la vez (409
  `demasiados`); tope de 10 por día. Encola `placas.pedido` → push a todo el equipo.
- `GET /v1/lugares/{id}/placas/pedidos` → `{pedidos: [{id, cantidad, tipo, nota, creadoEn, atendidoEn?, respuesta?}]}`
  (los 20 últimos).
- `GET /v1/equipo/pedidos-placas` → pendientes primero y atendidos de los últimos 30 días, con
  `lugarId, lugarNombre, lugarCiudad, lugarDireccion, lugarTelefono, lugarInstagram, pidioNombre`.
- `POST /v1/equipo/pedidos-placas/{id}/atendido` `{nota}` → 204 (la nota le llega al lugar como `respuesta`);
  404 si no existe o ya se atendió.

## Código del teléfono por mail (7/11)
No hay proveedor de SMS: el código de 6 dígitos de `POST /v1/verificacion/telefono {telefono}` llega
por mail a la casilla verificada de la cuenta en Clerk (la principal si está verificada; si no, la
primera verificada). Se lee con la Backend API de Clerk por el `clerk_id` de la sesión.
- Con SMTP (`CAIGO_SMTP`): `200 {"via":"mail","destino":"n•••@gmail.com"}` (casilla enmascarada).
- Sin SMTP (desarrollo, tests): simulado como antes, `200 {"codigoDePrueba":"123456"}`.
- Sin mail verificado: `422` problem+json `code: sin_mail` («Tu cuenta no tiene un mail verificado…»).
- Si el mail no sale: `500` y el código se borra (se puede pedir otro sin esperar los 30 s).
- El código vence en 10 minutos y acepta 5 intentos (`/confirmar`, igual que antes).

Variables: `CAIGO_SMTP` (host:puerto; 465 con TLS, otro con STARTTLS), `CAIGO_SMTP_USUARIO`,
`CAIGO_SMTP_CLAVE`, `CAIGO_CORREO_DE` (por defecto `Caigo <hola@caigo.com.ar>`). Sin usuario no
autentica (Mailpit en desarrollo: `CAIGO_SMTP=127.0.0.1:1025`). Fuera de desarrollo sin SMTP
completo, la API arranca igual y avisa en el log.
