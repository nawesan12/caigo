<script lang="ts">
	import Legal from '#lib/ui/Legal.svelte';
</script>

<!--
	Los plazos de la sección 6 son los del worker (apps/api/internal/dominio/confianza/plazos.go y
	cmd/worker): si cambia uno, cambia el otro.
	AAIP 1.2: ni el género ni la diversidad se guardan (se confirman al sumarse a un plan «Solo
	mujeres» / «Solo diversidades»), del DNI se guarda sólo el resultado, la fecha de nacimiento y la
	huella del número (no el número ni el nombre), y sus fotos y la selfie se borran al resolver la
	verificación (migraciones 00021, 00022 y 00023, confianza/fotos_dni.go).
-->

<Legal
	titulo="Política de&nbsp;privacidad"
	ruta="/privacidad"
	vigencia="13 de noviembre de 2026"
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
			la selfie solo. Ese análisis no sale del teléfono; a nuestros servidores llega sólo la foto. En
			la web no hay prueba de vida automática: sacás la selfie con la cámara o subís una foto, y el equipo
			de Caigo la puede revisar.
		</li>
		<li>
			<b>DNI, si elegís verificarlo.</b> Sacás fotos del frente y el dorso y una selfie, y la app
			lee del código del DNI tu número y tu fecha de nacimiento. Del DNI sólo validamos tu edad y
			que sea tuyo. Guardamos sólo el resultado, tu fecha de nacimiento y una huella cifrada del
			número (una clave que no se puede dar vuelta) para la lista de vetados y para que un DNI no se
			use en dos cuentas.
			<b>No guardamos tu número de DNI, tu nombre ni el sexo que figura en él.</b>
			<b
				>Las fotos del DNI y esa selfie las borramos apenas la verificación queda resuelta, aprobada
				o rechazada.</b
			> Mientras está pendiente, sólo las ve el equipo que la revisa.
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
			<b>Contacto de confianza.</b> Si usás «Compartir mi plan en vivo», el contacto que elegís.
		</li>
	</ul>

	<h2>3. Para qué los usamos</h2>
	<ul>
		<li>Para que funcione Caigo: mostrarte planes, sumarte, abrir el chat y avisarte cosas.</li>
		<li>
			Para cuidar a la comunidad: verificar que cada cuenta sea una persona real y mayor de edad,
			que no haya cuentas repetidas, y revisar reportes.
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
			Las fotos del DNI y la selfie de la verificación del DNI: hasta que la verificación queda
			resuelta. En ese momento se borran.
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
