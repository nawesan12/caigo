<script lang="ts">
	import { enhance } from '$app/forms';
	import Legal from '#lib/ui/Legal.svelte';

	/*
	 * «Borrar mi cuenta» en la web: la URL que se carga en Google Play (Seguridad de los datos) y que
	 * se puede leer sin entrar. No está en el handoff: es una página legal con los campos del
	 * arrepentimiento y el botón de peligro de la casa.
	 */
	let { data, form } = $props();
	let enviando = $state(false);
</script>

<Legal
	titulo="Borrar tu cuenta de&nbsp;Caigo"
	ruta="/borrar-cuenta"
	descripcion="Cómo borrar tu cuenta de Caigo desde la app o la web: qué se borra en el momento, qué se guarda y por cuánto (30 días), y cómo recuperarla si fue un error."
>
	{#if data.listo}
		<h2>Listo, borramos tu cuenta</h2>
		<p>
			Ya no podés entrar con ella y tus datos personales se borran del todo en 30 días. Si fue un
			error, escribinos a <a href="mailto:hola@caigo.com.ar">hola@caigo.com.ar</a> dentro de esos 30 días
			y la&nbsp;recuperamos.
		</p>
	{:else}
		<p>
			Caigo es la app de Grupo Vimo para sumarse a planes en Mar del Plata y Villa Gesell. Podés
			borrar tu cuenta cuando quieras, desde la app o desde esta página, sin escribirle
			a&nbsp;nadie.
		</p>

		<h2>Desde la app</h2>
		<ul>
			<li>Abrí Caigo y entrá a <b>Perfil</b>.</li>
			<li>Al final, tocá <b>Borrar mi cuenta</b>.</li>
			<li>Escribí el mail de tu cuenta para confirmar y tocá <b>Borrar mi&nbsp;cuenta</b>.</li>
		</ul>

		<h2>Desde la web</h2>
		{#if data.conSesion}
			<p>Escribí el mail con el que entrás a Caigo para confirmar. No se puede&nbsp;deshacer.</p>
			<form
				method="POST"
				use:enhance={() => {
					enviando = true;
					return async ({ update }) => {
						await update({ reset: false });
						enviando = false;
					};
				}}
			>
				<label>
					<span>Tu mail</span>
					<input
						class="input"
						name="email"
						type="email"
						required
						maxlength="200"
						autocomplete="email"
						placeholder="tu@mail.com"
						value={form?.email ?? ''}
					/>
				</label>
				{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}
				<button class="borrar" disabled={enviando}
					>{enviando ? 'Borrando…' : 'Borrar mi cuenta'}</button
				>
			</form>
		{:else}
			<p>
				<a href="/ingresar?volver=%2Fborrar-cuenta">Entrá a caigo.com.ar</a> con la misma cuenta que usás
				en la app (Google, Apple o tu mail) y volvés a esta página con el botón para&nbsp;borrarla.
			</p>
		{/if}

		<h2>Qué se borra</h2>
		<p>
			<b>En el momento:</b> tu cuenta deja de funcionar, se cierran todas tus sesiones, dejás de recibir
			avisos, se borra tu ubicación y salís de los planes que no pasaron. Los planes que organizabas se
			cancelan y avisamos a quienes estaban anotados; las entradas pagas de esos planes se devuelven.
		</p>
		<p>
			<b>A los 30 días:</b> se borran tu nombre, tus fotos, tu selfie y la verificación de identidad (DNI),
			tu teléfono, tu mail, tu fecha de nacimiento, tu bio, tus intereses, tu gente y tus «me interesa».
			Tus mensajes en los chats de los demás quedan firmados por «Cuenta borrada», sin tu nombre ni tu&nbsp;foto.
		</p>

		<h2>Qué se guarda</h2>
		<p>
			Los registros de pagos, sin tus datos personales, porque la ley nos pide conservar las
			operaciones (10 años). Sin tu nombre ni tus fotos, tus mensajes en el chat de un plan quedan hasta
			un año después de que el plan terminó; tus privados, tu asistencia a los planes y lo que
			contestaste en «¿Volverías?», un año; los reportes, 2 años. Si la cuenta estaba suspendida,
			guardamos sólo una huella cifrada de tu mail y de tu DNI por 2 años, para que no vuelva con otra
			cuenta. Todo está en la <a href="/privacidad">política de&nbsp;privacidad</a>.
		</p>
		<p>
			Durante los 30 días tus datos quedan guardados sin que nadie los vea, por si fue un error:
			escribinos a <a href="mailto:hola@caigo.com.ar">hola@caigo.com.ar</a> y recuperamos tu cuenta. Pasado
			ese plazo, si volvés a entrar, empezás una cuenta&nbsp;nueva (salvo que estuviera&nbsp;suspendida).
		</p>

		<h2>Caigo Plus</h2>
		<p>
			Borrar la cuenta no cancela la suscripción. Cancelala en Google Play, en la App Store o en
			Mercado Pago, donde la hayas&nbsp;comprado.
		</p>
	{/if}
</Legal>

<style>
	form {
		display: flex;
		flex-direction: column;
		gap: 14px;
		margin-top: 12px;
	}
	label {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	label span {
		font-size: 13px;
		font-weight: 800;
		color: #5c586b;
	}
	.input {
		width: 100%;
		padding: 14px 16px;
		border: 0;
		border-radius: 16px;
		font: inherit;
		font-size: 15px;
		color: #24212d;
		background: #f6f3ff;
		box-shadow: inset 0 0 0 1.5px #e2dcf0;
	}
	.borrar {
		height: 56px;
		border: 0;
		border-radius: 20px;
		font: inherit;
		font-size: 17px;
		font-weight: 800;
		color: #ffffff;
		cursor: pointer;
		background: linear-gradient(180deg, #ffb2a6 0%, #ff7868 40%, #f05e4c 100%);
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.45),
			0 4px 0 #d04a3a;
	}
	.borrar:disabled {
		opacity: 0.6;
	}
	.error {
		margin: 0;
		font-size: 14px;
		font-weight: 700;
		color: #d04a3a;
	}
</style>
