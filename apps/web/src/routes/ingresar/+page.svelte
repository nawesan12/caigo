<script lang="ts">
	import { PUBLIC_CLERK_PUBLICABLE } from '$app/env/public';
	import { enhance } from '$app/forms';
	import Seo from '#lib/ui/Seo.svelte';
	import Pantalla from '#lib/ui/PantallaOscura.svelte';
	import { fapiDe, iniciar, ErrorClerk, type ProveedorClerk } from '#lib/clerk.ts';

	/*
	 * Ingreso con Google o Apple (Clerk, modo nativo). No está en el handoff: se arma con el splash
	 * (00) y los botones del onboarding (01c "Empezar" / "Ya tengo cuenta").
	 */
	let { data, form } = $props();

	const fapi = $derived(fapiDe(PUBLIC_CLERK_PUBLICABLE));
	let yendo = $state<ProveedorClerk | null>(null);
	let error = $state('');

	async function entrar(p: ProveedorClerk) {
		if (!fapi || yendo) return;
		error = '';
		yendo = p;
		try {
			location.href = await iniciar(fapi, p, `${location.origin}/sso-callback`);
		} catch (e) {
			yendo = null;
			error =
				e instanceof ErrorClerk && e.codigo !== 'clerk_no_responde'
					? 'No pudimos arrancar el ingreso. Probá de nuevo.'
					: 'No pudimos hablar con el servicio de ingreso. Probá en un rato.';
		}
	}
</script>

<Seo titulo="Entrar · Caigo" ruta="/ingresar" noindex />

<Pantalla>
	<div class="centro">
		<img src="/caigo-icon.png" alt="" class="icono" width="132" height="132" />
		<span class="marca">Caigo</span>
		<h1>Hacé planes. Conocé&nbsp;gente.</h1>
		<p>Entrá con tu cuenta de Google o de Apple para sumarte a&nbsp;planes.</p>
	</div>

	<div class="acciones">
		{#if fapi}
			<button
				class="gel gel-lima boton"
				onclick={() => entrar('google')}
				disabled={!!yendo}
				aria-busy={yendo === 'google'}
			>
				{yendo === 'google' ? 'Abriendo Google…' : 'Seguir con Google'}
			</button>
			<button
				class="boton vidrio"
				onclick={() => entrar('apple')}
				disabled={!!yendo}
				aria-busy={yendo === 'apple'}
			>
				{yendo === 'apple' ? 'Abriendo Apple…' : 'Seguir con Apple'}
			</button>
		{:else}
			<p class="aviso">El ingreso todavía no está configurado en este&nbsp;sitio.</p>
		{/if}
		{#if error || data.error}
			<p class="aviso" role="alert">{error || data.error}</p>
		{/if}

		{#if data.dev}
			<form method="POST" action="?/prueba" use:enhance class="prueba">
				<label class="sr" for="token">Token de prueba</label>
				<input id="token" name="token" class="input" placeholder="cs_… (sólo en desarrollo)" />
				<button class="boton vidrio chico">Entrar con un token de prueba</button>
				{#if form?.prueba}<p class="aviso">{form.prueba}</p>{/if}
			</form>
		{/if}
		<p class="legal">
			Al entrar aceptás los <a href="/terminos">términos</a> y la
			<a href="/privacidad">política de&nbsp;privacidad</a>.
		</p>
	</div>
</Pantalla>

<style>
	.centro {
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 18px;
		text-align: center;
		padding: 24px 0;
	}
	.icono {
		width: 132px;
		height: 132px;
		filter: drop-shadow(0 30px 34px rgba(0, 0, 0, 0.5));
	}
	.marca {
		font:
			700 46px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #faf9f6;
	}
	h1 {
		font:
			700 20px 'Geist',
			sans-serif;
		color: #ffffff;
		letter-spacing: -0.2px;
	}
	.centro p {
		max-width: 320px;
		font-size: 15px;
		line-height: 1.5;
		color: #c9c4da;
	}
	.acciones {
		display: flex;
		flex-direction: column;
		gap: 12px;
		width: 100%;
		max-width: 400px;
		margin: 0 auto;
	}
	.boton {
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
		font-weight: 800;
		width: 100%;
	}
	.vidrio {
		display: flex;
		align-items: center;
		justify-content: center;
		color: #ffffff;
		background: rgba(255, 255, 255, 0.12);
		box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.18);
	}
	.vidrio:disabled {
		opacity: 0.6;
	}
	.chico {
		height: 50px;
		font-size: 15px;
	}
	.aviso {
		font-size: 14px;
		font-weight: 600;
		line-height: 1.5;
		text-align: center;
		color: #ffd9d3;
	}
	.prueba {
		display: flex;
		flex-direction: column;
		gap: 10px;
		margin-top: 8px;
		padding-top: 16px;
		border-top: 1px solid rgba(255, 255, 255, 0.1);
	}
	.legal {
		margin-top: 6px;
		font-size: 12px;
		font-weight: 600;
		line-height: 1.45;
		text-align: center;
		color: #a8a3bd;
	}
	.legal a {
		color: #c9c4da;
	}
</style>
