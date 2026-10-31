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
