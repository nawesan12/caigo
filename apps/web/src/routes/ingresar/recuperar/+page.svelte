<script lang="ts">
	import { tick, untrack } from 'svelte';
	import { PUBLIC_CLERK_PUBLICABLE } from '$app/env/public';
	import Seo from '#lib/ui/Seo.svelte';
	import Pantalla from '#lib/ui/PantallaOscura.svelte';
	import Requisitos from '#lib/ui/Requisitos.svelte';
	import {
		confirmarCodigo,
		ErrorClerk,
		fapiDe,
		guardarClave,
		mensajeClerk,
		pedirRecuperar,
		requisitosClave,
		type FlujoMail,
		type PasoMail
	} from '#lib/clerk.ts';

	/*
	 * Recuperar la contraseña con Clerk (calcado de Tengo Libre): un código por mail
	 * (reset_password_email_code), con el código se elige la contraseña nueva, se cierran las otras
	 * sesiones de Clerk y se entra. Si el mail no tiene cuenta, la pantalla dice lo mismo (no se revela
	 * quién está registrado). Desde un navegador nuevo, Clerk puede pedir otro código para confirmarlo.
	 * No está en el handoff: el splash (00) con los campos y botones del ingreso.
	 */
	let { data } = $props();

	const fapi = $derived(fapiDe(PUBLIC_CLERK_PUBLICABLE));
	let email = $state(untrack(() => data.email));
	let codigo = $state('');
	let clave = $state('');
	let paso = $state<'mail' | 'codigo' | 'nueva' | 'confiar'>('mail');
	let enviado = $state(false);
	let enviando = $state(false);
	let error = $state('');
	let flujo: FlujoMail | null = null;
	let token = $state('');
	let form: HTMLFormElement;
	const req = $derived(requisitosClave(clave));

	async function hacer(fn: () => Promise<void>) {
		enviando = true;
		error = '';
		try {
			await fn();
		} catch (x) {
			error =
				x instanceof ErrorClerk ? mensajeClerk(x.codigo) : 'No pudimos conectar. Probá de nuevo.';
		} finally {
			if (!token) enviando = false;
		}
	}

	async function seguir(sig: PasoMail) {
		if (sig.paso === 'listo') {
			token = sig.token;
			await tick();
			form.requestSubmit();
		} else if (sig.paso === 'clave_nueva') paso = 'nueva';
		else if (sig.paso === 'codigo') {
			codigo = '';
			paso = 'confiar';
		}
	}

	function enviar(e: SubmitEvent) {
		e.preventDefault();
		const f = fapi;
		if (!f || enviando) return;
		if (paso === 'mail')
			void hacer(async () => {
				flujo = await pedirRecuperar(f, email.trim());
				enviado = true;
				if (flujo) paso = 'codigo';
			});
		else if ((paso === 'codigo' || paso === 'confiar') && flujo)
			void hacer(async () => seguir(await confirmarCodigo(flujo!, codigo, paso === 'codigo')));
		else if (paso === 'nueva' && flujo) {
			if (!req.largo || !req.comun)
				return void (error = 'La contraseña todavía no cumple los requisitos.');
			void hacer(async () => seguir(await guardarClave(flujo!, clave)));
		}
	}
</script>

<Seo
	titulo="{paso === 'nueva' ? 'Contraseña nueva' : 'Recuperar la contraseña'} · Caigo"
	ruta="/ingresar/recuperar"
	noindex
/>

<Pantalla>
	<div class="cuerpo">
		<img src="/caigo-icon.png" alt="" class="icono" width="88" height="88" />
		{#if paso === 'nueva'}
			<h1>Elegí una contraseña&nbsp;nueva</h1>
		{:else}
			<h1>¿Te olvidaste la&nbsp;contraseña?</h1>
			<p class="bajada">
				Si entrás con Google o Apple, no necesitás contraseña: usá ese botón. Si usás tu mail, te
				mandamos un código para crear una&nbsp;nueva.
			</p>
		{/if}
		<form onsubmit={enviar}>
			{#if paso === 'mail'}
				<label class="sr" for="email">Tu mail</label>
				<input
					id="email"
					class="input"
					type="email"
					autocomplete="email"
					inputmode="email"
					required
					maxlength="200"
					placeholder="tu@mail.com"
					bind:value={email}
				/>
				<button class="gel gel-lima boton" disabled={enviando || !fapi}
					>{enviando ? 'Mandando…' : 'Mandarme el código'}</button
				>
			{:else if paso === 'codigo' || paso === 'confiar'}
				<label class="etiqueta" for="codigo">
					{paso === 'confiar'
						? `Para confirmar este dispositivo, escribí el código nuevo que te llegó a ${email}`
						: `Código que te llegó a ${email}`}
				</label>
				<input
					id="codigo"
					class="input codigo"
					inputmode="numeric"
					autocomplete="one-time-code"
					maxlength="6"
					required
					bind:value={codigo}
				/>
				<button class="gel gel-lima boton" disabled={enviando}
					>{enviando ? 'Revisando…' : 'Seguir'}</button
				>
			{:else}
				<label class="sr" for="clave">Contraseña nueva</label>
				<input
					id="clave"
					class="input"
					type="password"
					autocomplete="new-password"
					required
					bind:value={clave}
				/>
				<Requisitos {req} />
				<button class="gel gel-lima boton" disabled={enviando}
					>{enviando ? 'Guardando…' : 'Guardar y entrar'}</button
				>
				<p class="chico">Cerramos las otras sesiones abiertas por&nbsp;seguridad.</p>
			{/if}
			{#if error}<p class="aviso" role="alert">{error}</p>{/if}
		</form>
		{#if enviado && paso === 'codigo'}
			<p class="nota" role="status">
				Listo: si ese mail tiene cuenta, te llega un código en un minuto. Revisá también&nbsp;spam.
			</p>
		{:else if enviado && paso === 'mail' && !error}
			<p class="nota" role="status">
				Si ese mail tiene una cuenta con contraseña, te llega un código en un minuto. Si entrás con
				Google o Apple, usá ese&nbsp;botón.
			</p>
		{/if}
		<a class="volver" href="/ingresar">Volver a ingresar</a>
	</div>
</Pantalla>

<form method="POST" action="/sso-callback" bind:this={form} hidden>
	<input type="hidden" name="token" value={token} />
</form>

<style>
	.cuerpo {
		display: flex;
		flex-direction: column;
		gap: 16px;
		padding-top: 24px;
	}
	.icono {
		width: 88px;
		height: 88px;
		filter: drop-shadow(0 20px 24px rgba(0, 0, 0, 0.5));
	}
	h1 {
		font:
			800 32px/1.05 'Geist',
			sans-serif;
		letter-spacing: -1px;
	}
	.bajada {
		font-size: 15px;
		line-height: 1.5;
		color: #c9c4da;
	}
	form {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.etiqueta {
		font-size: 14px;
		font-weight: 700;
		line-height: 1.4;
		overflow-wrap: anywhere;
	}
	.codigo {
		font-size: 22px;
		letter-spacing: 0.3em;
		text-align: center;
	}
	.boton {
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
		font-weight: 800;
		width: 100%;
	}
	.chico,
	.nota {
		font-size: 13px;
		line-height: 1.5;
		color: #c9c4da;
	}
	.nota {
		padding: 14px;
		border-radius: 16px;
		background: rgba(255, 255, 255, 0.08);
	}
	.aviso {
		font-size: 14px;
		font-weight: 600;
		line-height: 1.5;
		color: #ffd9d3;
	}
	.volver {
		align-self: center;
		min-height: 44px;
		display: grid;
		place-items: center;
		font-size: 14px;
		font-weight: 700;
		color: #c8f36b;
		text-decoration: none;
	}
</style>
