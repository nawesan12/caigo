# Auditoría contra el handoff (31/10)

Lista de verificación por cliente (archivo:línea). Lo que falta se implementa según docs/contrato-faltantes.md.

# Checklist del handoff de Caigo (repo ~/Trabajo/vimo/caigo, handoff en docs/handoff/)

Para cada ítem, decir en el cliente auditado: HECHO (llama a la API real) / A MEDIAS (qué falta) /
FALSO (texto fijo o datos de ejemplo que deberían venir de la API) / BOTÓN MUERTO (sin acción) / NO ESTÁ.
Citar archivo:línea. Prototipo = handlers del <script> de "Caigo Prototipo.dc.html" (copia en
/private/tmp/claude-501/-Users-nahuelsantillan-Trabajo-vimo/66f68005-f8b2-4ff7-af07-3393d28b2272/scratchpad/proto.js).

## App (Mockups v5: pantallas 00–14b)
- 00 Splash; 01a–01c onboarding 3 slides (Saltar, dots, selector Voy solo/Somos 2/Somos 4, "Ya tengo cuenta")
- 01d Teléfono: AR +54, código SMS, "Reenviar en 0:24" (cuenta regresiva + reenviar real), aviso 18+, CTA Seguir
- 01e Selfie con prueba de vida: anillo de progreso, instrucción "Girá la cabeza", chips Buena luz/Cara centrada/Sin lentes
- Prototipo onb: intenciones (Sumarme/Conocer gente/Completar mis planes/Algo más→Citas), intereses, editIntents/restartOnb
- 02 Ahora lista: ubicación real en header ("Güemes, Mar del Plata"), segmented Lista/Mapa, chips categoría, "Próximas 3 horas · N planes cerca" (N real), tarjeta con foto, hora, Faltan N, avatares +N, distancia
- 03 Ahora mapa: pines con contador, buscador "Buscá un plan o un lugar" (¿filtra de verdad?), tarjeta flotante
- Modos Ahora: Planes/Gente/Citas; mazo arrastrable (>100px, tinte, tap foto izq/der, tap 42% inferior abre perfil); like/pass/superLike; toast "¡Es mutuo…!"
- Perfil de persona (pp): fotos con barras, stats, prompts, próximos planes, invitar a un plan (ppInvite), reportar (ppReport), bloquear, ppAccept/ppDecline (Te invitan)
- "Te sumaron / Te invitan" (likes overlay): lista con blur sin Plus, lkUnlock → paywall
- 04 Explorar: tiles de categoría con foto, sección recurrentes ("Todos los sábados…"), agenda por día ("Viernes 30 · Ver los 9"), filtros (categoría, fecha, zona, cupo, solo verificados)
- 05 Detalle: hero, chips (categoría/cuando/nivel), contador Faltan N / Somos N de M con barra segmentada, avatares, organizador con reputación ("Fue a 23 de 24 · 96%"), cuándo, dónde (zona; dirección exacta al aceptar), costo, requisitos, CTA Sumarme + "Somos 2+", estados pending/in, Solo verificados → verificación DNI y vuelta, compartir, reportar plan, salir del plan
- Venue: botón "Entrada", checkout (cantidad coPlus/coMinus, Pagar todo / Señar 30%, cargo por servicio), "Agregar a Wallet", beneficio
- 06a–06c Crear: categorías, Ahora/Hoy/Otro día + horas, lugar + "Cambiar" (selector de lugar/mapa real), "Se repite cada semana", steppers Somos/Faltan + preview badge, edad, Cualquiera/Solo mujeres/Solo diversidades, nivel, Libre/Con aprobación/Solo mi gente, Público/Mi gente/Por link, Solo verificados, Ocultar dirección, costo, foto del plan; defaults (Hoy 21:30, Somos 3/Falta 1…); título vacío → "{categoría} en Güemes"
- Publicado: copiar link caigo.com.ar/p/<slug>, compartir
- 07 Solicitudes: resumen con contador, "N pedidos", persona o grupo ("somos 2"), asistencia, mensaje, Ahora no / Aceptar / Aceptar a los 2
- 08 Chat: tarjeta fijada con dirección y "check-in desde HH:MM", mensaje de sistema lima ("Plan completo. A disfrutar."), burbujas, "Lu está escribiendo…" (indicador de escritura real), input, Enter envía; privados
- 09a Check-in: "Estás en <lugar>", Llegué (geo), "Compartir mi plan en vivo"; QR (showQR / qrScan)
- 09b ¿Repetimos?: Sí, el jueves / Esta vez no; ¿Volverías a salir con…? (privado) → mi gente; ¿Pasó algo? Reportar
- 10 Mis planes: Organizo · N / Me sumé · N / Historial (contadores reales); grupos HOY / ESTA SEMANA; "N personas quieren sumarse"; "Compartir link"; editar/cancelar plan
- 11 Perfil: avatar, nombre/edad/zona/Verificada, tarjeta "96% Asistencia real (23 de 24)", "31 Planes (12 organizados)", "Le copa" (intereses), "Mi gente · 18", planes compartidos; editar perfil (fotos, bio, prompts, intereses); ajustes; cerrar sesión; borrar cuenta
- 12 Link público web
- 13a–13c Estados vacíos con acción ("No hay planes cerca. ¿Armás uno?", "Todavía no te sumaste a nada", "Nadie pidió sumarse todavía")
- 14a–14b Modo oscuro (Ahora y Detalle)
- Plus (paywall): 3 sumadas/día con contador hasta medianoche en vivo, Semana/Mes/Temporada, compra real; perfil → "Ya sos Caigo Plus"
- Verificación DNI: frente → dorso → prueba de vida → verificado
- Caigo Tap (tap / tapdone): "Apoyá el celu en la placa", "Adentro" con beneficio, saldo, +1 visita
- Mis pases (Pases NFC 1f): entradas y pases de socio, + Wallet
- Push: registro de token, abrir la pantalla correcta al tocar el aviso
- Deep links: caigo.com.ar/p/<slug> y /t/<placa> abren la app

## Panel Lugares (web, routes/panel)
- Header "Caigo / PARA LUGARES", lugar (nombre · Lugar aliado · zona), "Publicar plan", "Ver la app como usuario →"
- Tus planes: próximos/pasados, KPIs (k.l/k.v/k.d), "N de M confirmados", badge, plata
- Nuevo plan wizard: nombre, categoría, descripción, foto de portada, día/hora/duración, cupo total, cómo se cobra (gratis/entrada/seña %), precio, qué incluye, desglose LA PERSONA PAGA / VOS RECIBÍS, si alguien cancela (política), beneficio por check-in, quién puede sumarse, preview en vivo "ASÍ LO VE LA GENTE EN CAIGO", publicado con Copiar link / Ver el plan
- Quiénes vienen + Check-in: validar código TK-xxxx, "Escanear QR con la cámara", beneficio, "Cobrá el resto en el lugar"
- Caigo Tap: placas, "+ Activar placa", "Probar", "Pedir placas"
- Pase de socio: visitas para el beneficio, beneficio, preview Wallet, toggle
- Cobros: MP conectado, tabla PLAN/ENTRADAS/COBRADO/ESTADO (¿datos reales o "Datos de ejemplo."?)
- Registro de lugar nuevo y estado pendiente de aprobación; varios lugares/miembros

## Hallazgos por área (resumen del auditor)

**Ingreso / verificación**
- 01b «Voy solo / Somos 2 / Somos 4» decorativo (iOS Intro.swift:142, Android Intro.kt:216).
- Web /app sin onboarding 01a–01c ni intereses al alta (Prototipo `onb`/`obNext`).
- «Herramientas para salir tranqui» (en vivo, contacto de confianza, bloqueos) sin acción: iOS Verificacion.swift:97-139 (y fila DNI :83-89), Android Verificacion.kt:121-151. Falta GET/DELETE bloqueos (contrato §4).
- /p no manda a verificarse ante `falta_selfie` (web p/[slug]/+page.server.ts:29-58).

**Ahora / mapa**
- Header de ubicación sin geocodificación inversa; iOS usa coordenadas fijas de MdP sin permiso (iOS Ahora.swift:45,79; Android Ahora.kt:120; web app/+page.svelte:12).
- Vacío 13a dice siempre «Domingo a la siesta…» (iOS Ahora.swift:218, Android Ahora.kt:242).
- Buscador del mapa filtra local: usar `q` (contrato §5).
- Modo oscuro: falta en web /app; mapa iOS/Android con buscador/chips claros (MapaAhora.swift:68-100, Mapa.kt:157).
- Chips del mapa web sin «Cita doble» y «Salida» (app/mapa/+page.svelte:12). Android Ahora no manda filtros finos (Ahora.kt:99).

**Explorar**
- Tiles con fotos de pexels enlazadas (iOS Explorar.swift:4-12, Android Explorar.kt:74-83) y sólo 5 de 8 categorías.
- Filtro por zona (contrato §5). Web sólo «Solo verificados»: faltan fecha/zona/cupo (app/explorar/+page.svelte:116).
- iOS Explorar no manda lat/lng (Explorar.swift:114); Android nunca manda `radio`.

**Detalle**
- Género no validado (contrato §1). Android beneficio `desbloqueado=false` fijo (Detalle.kt:208).
- Web /app: no se puede salir de un plan aceptado (app/plan/[id]/+page.svelte:232), ni reportar, ni cancelar como organizador.

**Crear plan**
- Falta foto, descripción y duración en las tres apps (iOS Crear.swift:130-148, Android Crear.kt:205, web app/crear).
- Android «Cambiar» lugar sin buscador (Crear.kt:525-563) y ciudad deducida de la latitud (Crear.kt:180). iOS zona por defecto «Güemes» (Crear.swift:133,188).

**Mis planes / solicitudes**
- Editar plan (contrato §2) en API, apps y panel. Cancelar sin aviso ni reembolso. Sacar aceptados. Android no confirma al cancelar (Detalle.kt:348).
- Web: historial sin ¿Repetimos?/¿Volverías?/Reportar; Mis planes sin cancelar/editar.

**Chat**
- Sin menú reportar/bloquear/salir (iOS Chat.swift:153, Android Chat.kt:131). «Escribiendo» en privados (contrato §10). Android no pagina historial (`antes`, Chat.kt:93). Web: sondeo sin escribiendo; tarjeta fijada sin «check-in desde HH:MM».

**Gente / Citas / Perfil**
- Próximos planes en el perfil de una persona (contrato §5; iOS Persona.swift:161, Android). Android sin «Sacar de Mi gente».
- «Planes compartidos» falta en Android (Perfil.kt:218-280) y web. Web editar perfil sin fotos, bio, prompts (app/perfil/+page.svelte:72). Sin ajustes de avisos (contrato §3) ni selector de tema.

**Plus**
- Límites fijos: usar `limite`/`limiteEncantas` (Android Plus.kt:347, web plus/+page.svelte:44). Web /plus: precios por semana y «−22 %» fijos (:28-39), sin contador ni cuenta regresiva, «Ya sos Plus» sin vencimiento ni cancelar, botones que van a «/» en vez de /app.

**Pases / Tap / Panel de lugares**
- «Probar» placa sólo abre /t; sin QR imprimible (panel/tap/+page.svelte:156). «Pedir placas» es mailto (:182).
- Panel: editar/cancelar planes, check-in de planes gratis (contrato §6), editar datos y foto del lugar, estados de cobros, «Copiar link» muerto (nuevo/+page.svelte:133), «Ver la app como usuario» va a la landing.

**Backoffice**: usuarios, planes, lugares, métricas y semilla (contrato §7).

**Push**: avisos nuevos (contrato §3). Android no pide POST_NOTIFICATIONS (13+) ni borra el dispositivo al salir.

**Landing**: planes reales en «¿Quién se prende?» (+page.svelte:14-52), lista de espera y origen (contrato §8), «Abrir en la app» con esquema propio en /p y /t (usar universal link o tienda).

**Varios**: clientes no usan `/v1/version`; iOS `aps-environment: development` fijo (project.yml:39) → production en Release.
