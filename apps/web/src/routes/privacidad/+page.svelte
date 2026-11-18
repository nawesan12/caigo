<script lang="ts">
	import Legal from '#lib/ui/Legal.svelte';
</script>

<!--
	Los plazos de la sección 6 son los del worker (apps/api/internal/dominio/confianza/plazos.go y
	cmd/worker): si cambia uno, cambia el otro.
	AAIP 1.2: ni el género ni la diversidad se guardan (se confirman al sumarse a un plan «Solo
	mujeres» / «Solo diversidades»), del DNI se guarda sólo el resultado, la fecha de nacimiento y la
	huella del número (no el número ni el nombre), y sus fotos y la selfie se borran cuando el equipo
	revisa la verificación, y como máximo a los 30 días (migraciones 00021, 00022 y 00023,
	confianza/fotos_dni.go). Desde el 16/11 el DNI es obligatorio y se aprueba al subirlo.
	Moderación automática (18/11, docs/api.md): a Anthropic va sólo el texto a revisar; la cola del
	equipo (tabla moderaciones, migración 00026) se borra al año de resuelta.
-->

<Legal
	titulo="Política de&nbsp;privacidad"
	ruta="/privacidad"
	vigencia="18 de noviembre de 2026"
	descripcion="Qué datos usa Caigo (teléfono, selfie, ubicación), para qué, con quién los compartimos y cómo pedís verlos, corregirlos o borrarlos cuando quieras."
>
	<p>
		Esta política explica qué datos usa Caigo, para qué, con quién los compartimos y cómo podés
		pedir que los corrijamos o los borremos. Aplica a la app de Caigo para iPhone y Android, a
		caigo.com.ar y al panel de lugares en lugares.caigo.com.ar.
	</p>

	<h2>1. Quién es responsable</h2>
	<p>
		Nahuel Santillán, CUIT 20-44859234-1, con domicilio en Mar del Plata, provincia de Buenos Aires
		(«Caigo»), es responsable de la base de datos de usuarios de Caigo. Para cualquier tema de
		privacidad escribinos a <a href="mailto:privacidad@caigo.com.ar">privacidad@caigo.com.ar</a>.
	</p>

	<h2>2. Qué datos usamos</h2>
	<ul>
		<li>
			<b>Tu cuenta.</b> Entrás con Google, con Apple o con tu mail y una contraseña. Recibimos tu nombre,
			tu mail y, si la tiene, tu foto. La contraseña la guarda Clerk, nuestro proveedor de ingreso: Caigo
			no la ve.
		</li>
		<li>
			<b>Tu perfil.</b> Nombre, edad, ciudad y zona, fotos, bio, trabajo, intereses, lo que buscás (planes,
			gente, citas) y tus respuestas a las preguntas del perfil.
		</li>
		<li>
			<b>Verificación.</b> Tu número de celular y una selfie. El número lo confirmás con un código que
			te mandamos al mail de tu cuenta, no por SMS: eso confirma que lo cargaste vos, no que la línea
			esté a tu nombre. En la app para iPhone y Android, la prueba de vida (que seas una persona y no
			una foto) la hace tu teléfono: detecta tu cara mientras girás la cabeza a un lado y al otro, y saca
			la selfie solo. En la web pasa lo mismo en tu navegador: detecta tu cara mientras girás la cabeza
			y volvés al centro. Ese análisis no sale de tu teléfono ni de tu navegador; a nuestros servidores
			llega sólo la foto.
		</li>
		<li>
			<b>DNI.</b> Para usar Caigo verificás tu DNI: sacás fotos del frente y el dorso y una selfie,
			y la app lee del código del DNI tu número y tu fecha de nacimiento (en la web los escribís
			vos). Del DNI sólo validamos tu edad y que sea tuyo. Guardamos sólo el resultado, tu fecha de
			nacimiento y una huella cifrada del número (una clave que no se puede dar vuelta) para la
			lista de vetados y para que un DNI no se use en dos cuentas.
			<b>No guardamos tu número de DNI, tu nombre ni el sexo que figura en él.</b>
			<b
				>Las fotos del DNI y esa selfie las borramos cuando el equipo revisa la verificación, y como
				máximo a los 30 días.</b
			> Hasta entonces, sólo las ve el equipo que la revisa.
		</li>
		<li>
			<b>Planes «Solo mujeres» y «Solo diversidades».</b> No guardamos tu género, tu orientación ni tu
			identidad de género. Para sumarte a uno de esos planes, confirmás en ese momento que sos mujer o
			que sos parte de la diversidad LGBTIQ+: esa confirmación vale sólo para ese plan y no la guardamos
			en ningún&nbsp;lado.
		</li>
		<li>
			<b>Modo Citas.</b> Si elegís «Algo más romántico — Citas» en lo que buscás, se ve en tu perfil y
			te mostramos personas para conocer. Guardamos a quién le diste «me interesa», «me encanta» o «paso»
			(y lo mismo en el modo Gente con «me prendo»), para no mostrarte dos veces a la misma persona y
			abrir el chat cuando el interés es mutuo. No guardamos qué género buscás.
		</li>
		<li>
			<b>Planes y actividad.</b> Los planes que publicás, a los que te sumás y tu asistencia (check-in),
			las personas que sumás a tu gente o te interesan, los mensajes del chat del plan y los privados,
			los reportes y los bloqueos.
		</li>
		<li>
			<b>Ubicación.</b> Si nos das permiso, tu ubicación aproximada para mostrarte planes cerca, y tu
			ubicación en el momento del check-in para confirmar que llegaste. No seguimos tu ubicación en segundo
			plano. Para mostrar el nombre de una calle o de una zona, la app le pregunta al servicio de mapas
			de tu teléfono (Apple en el iPhone, Google en Android).
		</li>
		<li>
			<b>Pagos.</b> Qué compraste (entradas, señas, Caigo Plus), cuándo y por cuánto. Los datos de tu
			tarjeta los maneja Mercado Pago, Apple o Google: Caigo nunca los ve.
		</li>
		<li>
			<b>Dispositivo.</b> El identificador para mandarte avisos, la versión de la app y la dirección IP
			(la usamos para frenar abusos y la borramos a los 2 días).
		</li>
		<li>
			<b>Contactos de confianza.</b> Hasta 3 (nombre y teléfono o mail), si los cargás. Contale a quien
			cargues. Sólo se usan si activás «¿Todo bien?» en un plan: si a la media hora del inicio no hiciste
			check-in, primero te preguntamos a vos y, si en 10 minutos seguís sin responder, le escribimos por
			mail al contacto que elegiste (si es un teléfono, no mandamos SMS: te avisamos a vos para que le
			escribas). El mail lleva tu primer nombre, el nombre del plan, la hora y un link que muestra el
			lugar, la dirección y el primer nombre de quienes van, hasta 2 horas después del final. Como mucho
			un mail por plan, y quien lo recibe puede darse de baja desde el mismo mail. Compartir tu ubicación
			desde «¿Todo bien?» sale de tu teléfono y no pasa por Caigo.
		</li>
		<li>
			<b>Después del plan.</b> La carita que le ponés a un plan y las fotos que subís de un plan.
		</li>
		<li>
			<b>Logros.</b> Tu nivel, tus insignias, tu racha y si sos «Anfitrión» salen de tus check-ins: no
			guardamos nada aparte. Sí guardamos tu código de «Traé a alguien» y a quién invitaste, los planes
			que ocultaste con «No me interesa» (hasta el fin del día), tu lugar en una lista de espera, a quién
			le mandamos «Esta semana en tu ciudad» y los beneficios por nivel que usaste en un lugar aliado.
		</li>
	</ul>

	<h2>3. Para qué los usamos</h2>
	<ul>
		<li>Para que funcione Caigo: mostrarte planes, sumarte, abrir el chat y avisarte cosas.</li>
		<li>
			Para cuidar a la comunidad: verificar que cada cuenta sea una persona real y mayor de edad,
			que no haya cuentas repetidas, y revisar reportes.
		</li>
		<li>
			Para moderar lo que se publica: los títulos y las descripciones de los planes y los mensajes
			(del chat del plan y los privados) pasan por un filtro automático propio que busca insultos,
			acoso, contenido sexual, estafas o datos de contacto. Si el filtro tiene dudas, manda
			<b>sólo ese texto</b> a Anthropic para que lo clasifique (ver la sección 5). Lo que se marca
			puede no publicarse u ocultarse, y lo revisa una persona del equipo. Si creés que nos
			equivocamos, pedí que lo revisemos escribiendo a
			<a href="mailto:privacidad@caigo.com.ar">privacidad@caigo.com.ar</a>.
		</li>
		<li>Para cobrar las entradas, las señas y Caigo Plus, y llevar la contabilidad.</li>
		<li>Para mejorar Caigo con estadísticas que no identifican a nadie.</li>
	</ul>
	<p>
		No vendemos tus datos ni los usamos para publicidad de terceros. Usamos tu selfie y tu DNI sólo
		para verificarte: no armamos con ellos un patrón biométrico ni los usamos para otra cosa.
	</p>

	<h2>4. Qué ve el resto</h2>
	<ul>
		<li>
			Tu nombre, edad, fotos, zona, intereses, si estás verificado y tu asistencia (cuántas veces
			fuiste a los planes que te sumaste). Tu apellido, tu teléfono y tu DNI no los ve nadie.
		</li>
		<li>
			Quien tiene cuenta pero todavía no verificó su DNI puede mirar planes y perfiles, pero no ve
			tu zona, tu trabajo, a qué planes vas, la dirección de un plan ni el nombre completo de
			quienes van (sólo el primer nombre y la foto).
		</li>
		<li>
			Tu nivel, tu «Anfitrión» y hasta 3 insignias (podés ocultarlas en Ajustes). La de «Local de…»
			(el lugar al que más fuiste en el mes) la ves sólo vos. La racha también es sólo tuya.
		</li>
		<li>
			Si organizás un plan, ves si quien pide sumarse faltó a 2 o más planes en el último mes (no
			cuentan los planes que esa persona reportó). Si faltaste, te avisamos al día siguiente, y
			quien organizó puede marcar que fuiste.
		</li>
		<li>
			Si invitaste a alguien con tu código, ves cuántas personas entraron y cuántas ya fueron a su
			primer plan, no quiénes.
		</li>
		<li>
			Si usás un beneficio por nivel en un lugar aliado, el lugar ve ese día tu primer nombre, tu
			nivel y el beneficio.
		</li>
		<li>
			En un plan, quien organiza ve a quienes piden sumarse. En el link público de un plan se ve el
			nombre y la foto de quien organiza y de quienes van.
		</li>
		<li>
			La dirección exacta de un plan la ven sólo las personas aceptadas, si quien organiza la
			ocultó.
		</li>
		<li>
			Si comprás una entrada en un lugar aliado, el lugar ve tu nombre, tu foto, cuántos van y tu
			código de entrada.
		</li>
		<li>
			Tu carita de un plan no la ve nadie: quien organiza sólo ve el promedio de sus planes, recién
			cuando hay 3 o más, redondeado a medio punto y actualizado una vez por día. Las fotos de un
			plan las ven sólo quienes fueron, con un link que vence a la hora, y las subimos sin la
			ubicación ni los datos del teléfono. Si salís en una foto que no querés, tocá «Salgo yo» y
			deja de verse; si no fuiste y no podés verla, escribinos a
			<a href="mailto:privacidad@caigo.com.ar">privacidad@caigo.com.ar</a>.
		</li>
	</ul>

	<h2>5. Con quién compartimos datos</h2>
	<p>
		Con proveedores que necesitamos para prestar el servicio, sólo lo que hace falta y con
		obligación de cuidarlos:
	</p>
	<ul>
		<li>
			Hostinger, el servidor donde están la base de datos y los archivos (un servidor privado
			virtual en Brasil).
		</li>
		<li>
			Cloudflare (red y protección del sitio): todo lo que va y viene entre tu teléfono o tu
			navegador y nuestro servidor pasa por su red, en Estados Unidos y otros países.
		</li>
		<li>Clerk (ingreso con Google, Apple o mail y contraseña), en Estados Unidos.</li>
		<li>
			Anthropic, PBC (Claude), en Estados Unidos, para clasificar los textos en los que el filtro
			automático tiene dudas. Le llega sólo ese texto: el título y la descripción de un plan o un
			mensaje. <b
				>Nunca le mandamos tu nombre, tus fotos, tu DNI, tu ubicación ni el resto de
				la&nbsp;conversación.</b
			> Anthropic no usa esos textos para entrenar sus modelos (es su servicio comercial) y los guarda
			por un plazo&nbsp;limitado.
		</li>
		<li>Mercado Pago (cobros de entradas, señas y Caigo Plus en la web).</li>
		<li>
			Apple y Google (compras en la app, avisos y pases de Wallet), en Estados Unidos y otros
			países. Los avisos push viajan por sus servidores y llevan el texto que ves en la pantalla: si
			te escriben, el aviso lleva quién te escribió y el mensaje.
		</li>
		<li>
			Apple y Google también convierten coordenadas en nombres de calles o zonas (y al revés) cuando
			creás un plan o buscás planes cerca. Les llega ese punto del mapa, no tu cuenta.
		</li>
		<li>
			OpenStreetMap y OpenFreeMap, que dibujan los mapas de la web y de la app de Android (en el
			iPhone los dibuja Apple). Reciben qué parte del mapa mirás y tu IP, como cualquier sitio que
			abrís.
		</li>
		<li>
			GitHub, en Estados Unidos, donde guardamos las copias de seguridad diarias de la base de
			datos, cifradas antes de salir de nuestro servidor: GitHub no puede leerlas.
		</li>
	</ul>
	<p>
		Algunos de estos proveedores están fuera de la Argentina. En esos casos la transferencia se hace
		con las garantías que pide la Ley 25.326. También podemos compartir datos si lo ordena un juez o
		una autoridad competente.
	</p>

	<h2>6. Cuánto tiempo los guardamos</h2>
	<ul>
		<li>Tu perfil, tu verificación y tu actividad: mientras tengas la cuenta.</li>
		<li>
			Los «me interesa», «me encanta», «me prendo» y «paso»: 6 meses. Si el interés fue mutuo,
			mientras se sigan escribiendo y hasta 6 meses después del último mensaje.
		</li>
		<li>
			Los registros de pagos: 10 años, porque lo pide la ley contable (sin tu perfil, sólo el
			movimiento).
		</li>
		<li>
			Lo que queda después de borrar tu cuenta, ya sin tu nombre ni tus fotos: tus mensajes en el
			chat de un plan, hasta un año después de que el plan terminó; tus mensajes privados, tu
			asistencia a los planes y lo que contestaste en «¿Volverías?», un año; los reportes que
			hiciste o te hicieron, 2 años.
		</li>
		<li>
			Las suspensiones: si suspendemos una cuenta, guardamos una huella cifrada de su mail y de su
			número de DNI (no el dato: una clave que no se puede dar vuelta) para que no vuelva con otra
			cuenta, mientras exista la cuenta y hasta 2 años después de borrarla.
		</li>
		<li>
			El registro de quién del equipo abrió tu selfie o tu DNI: 2 años. Las direcciones IP: 2 días.
			Los códigos de verificación del teléfono: 1 día.
		</li>
		<li>
			Las fotos que sacás de tu perfil o de un plan: se borran en el momento (dejan de verse en su
			link y se borran del servidor).
		</li>
		<li>
			Las fotos del DNI y la selfie de la verificación del DNI: hasta que el equipo revisa la
			verificación, y como máximo 30 días. En ese momento se borran.
		</li>
		<li>
			«¿Todo bien?» (a quién avisar y a qué hora): hasta 24 horas después del final del plan, o
			hasta que hacés check-in. El registro de los mails que mandó (una clave del mail, no la
			dirección): 7 días. Las direcciones que pidieron no recibirlos más: como clave, para siempre.
			Las fotos de un plan: 6 meses desde el plan (las podés borrar antes).
		</li>
		<li>
			«No me interesa»: hasta el fin del día. La lista de espera: hasta un día después del plan.
			«Esta semana en tu ciudad» (a quién se le mandó): 8 semanas. Los beneficios por nivel usados:
			90 días. Los referidos: un año. Si borrás tu cuenta, todo esto se borra con la purga, a los 30
			días.
		</li>
		<li>
			Los textos que marca la moderación automática y quedan para que los revise el equipo: hasta un
			año después de&nbsp;resueltos.
		</li>
	</ul>

	<h2>7. Borrar tu cuenta</h2>
	<p>
		Podés borrar tu cuenta cuando quieras desde Perfil, en la app o en
		<a href="/borrar-cuenta">caigo.com.ar/borrar-cuenta</a>. Al instante la cuenta deja de
		funcionar, se cierran tus sesiones y se cancelan tus planes que no pasaron. A los 30 días se
		borran tu perfil, tus fotos, tu selfie, tu DNI, tu teléfono y tu mail; en ese plazo, si fue un
		error, escribinos a hola@caigo.com.ar y la recuperamos. Lo que tiene que quedar por ley (los
		pagos) queda sin tus datos personales. Si tenés Caigo Plus, cancelalo en la tienda donde lo
		compraste.
	</p>

	<h2>8. Tus derechos</h2>
	<p>
		Podés pedir acceso a tus datos, corregirlos, actualizarlos o borrarlos escribiendo a
		<a href="mailto:privacidad@caigo.com.ar">privacidad@caigo.com.ar</a>. Te respondemos en 10 días
		corridos para el acceso y en 5 días hábiles para corregir o borrar. El acceso es gratuito cada
		seis meses, salvo que tengas un interés legítimo para pedirlo antes.
	</p>
	<p>
		LA AGENCIA DE ACCESO A LA INFORMACIÓN PÚBLICA, en su carácter de Órgano de Control de la Ley N°
		25.326, tiene la atribución de atender las denuncias y reclamos que interpongan quienes resulten
		afectados en sus derechos por incumplimiento de las normas vigentes en materia de protección de
		datos personales.
	</p>

	<h2>9. Seguridad</h2>
	<p>
		Todo viaja cifrado. La selfie y las fotos del DNI se guardan cifradas en el servidor, con una
		clave que no está en la base de datos ni en las copias de seguridad. No son públicas: las ven
		sólo vos y el equipo de Caigo que revisa las verificaciones, y cada vez que alguien del equipo
		abre una queda registrado quién, cuándo y desde dónde. Cada persona accede sólo a sus propios
		datos privados, y eso lo controla la base de datos, no sólo la app.
	</p>

	<h2>10. Menores de edad</h2>
	<p>
		Caigo es sólo para mayores de 18. Si sabés de una cuenta de alguien menor, avisanos y la
		borramos.
	</p>

	<h2>11. Cookies</h2>
	<p>
		caigo.com.ar usa sólo cookies propias y necesarias para que funcione. No usamos cookies de
		publicidad ni de seguimiento de terceros.
	</p>
	<ul>
		<li><b>caigo_sesion:</b> tu sesión, 60 días (se renueva con el uso).</li>
		<li><b>caigo_volver:</b> la página a la que volvés después de ingresar, 15 minutos.</li>
		<li>
			<b>caigo_origen:</b> de dónde llegaste la primera vez (el link de un plan o una campaña, por ejemplo
			utm_source), 30 días. Se guarda una sola vez en tu perfil cuando entrás.
		</li>
		<li>
			<b>caigo_ubic:</b> tu ubicación, redondeada a unos 11 metros, para buscar planes cerca. Dura 6 horas
			y sólo si diste permiso.
		</li>
		<li><b>caigo_tema:</b> si elegiste el modo claro, el oscuro o el del sistema, 1 año.</li>
		<li><b>caigo_bienvenida:</b> que ya viste la bienvenida, 1 año.</li>
		<li><b>caigo_intereses:</b> que ya elegiste qué buscás, 1 año.</li>
		<li>
			<b>caigo_lugar:</b> en el panel de lugares, cuál de tus lugares tenés abierto, 1 año.
		</li>
	</ul>
	<p>
		Mientras ingresás, el navegador guarda un dato de Clerk (caigo-clerk, en el almacenamiento de la
		pestaña) que se borra al terminar o al cerrar la pestaña.
	</p>

	<h2>12. Cambios</h2>
	<p>
		Si la cambiamos, publicamos acá la versión nueva con su fecha. Si el cambio es importante, te
		avisamos antes de que empiece a regir.
	</p>
</Legal>
