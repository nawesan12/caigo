<script lang="ts">
	import { onMount, tick } from 'svelte';
	import { page } from '$app/state';
	import Seo from '#lib/ui/Seo.svelte';
	import Pantalla from '#lib/ui/PantallaOscura.svelte';
	import { completar, ErrorClerk } from '#lib/clerk.ts';

	/*
	 * Vuelta de Google o Apple (por Clerk). Termina el ingreso en el navegador y postea el token de
	 * sesión de Clerk al servidor, que lo canjea en Go por la sesión de Caigo.
	 */
	let token = $state('');
	let error = $state('');
	let form: HTMLFormElement;

	onMount(async () => {
		try {
			token = await completar(page.url.searchParams.get('rotating_token_nonce') ?? '');
		} catch (e) {
			error = e instanceof ErrorClerk ? e.codigo.replace(/[^a-z_]/g, '').slice(0, 40) : '';
			error ||= 'clerk_no_responde';
		}
		await tick();
		form.requestSubmit();
	});
</script>

<Seo titulo="Entrando · Caigo" noindex />

<Pantalla>
	<div class="centro" role="status" aria-live="polite">
		<img src="/caigo-icon.png" alt="" width="132" height="132" />
		<h1>Entrando a Caigo</h1>
		<p>Un segundo, estamos terminando de validar tu&nbsp;ingreso.</p>
	</div>
	<form method="POST" bind:this={form} hidden>
		<input type="hidden" name="token" value={token} />
		<input type="hidden" name="error" value={error} />
	</form>
</Pantalla>

<style>
	.centro {
		min-height: 80vh;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 16px;
		text-align: center;
	}
	img {
		width: 132px;
		height: 132px;
		filter: drop-shadow(0 30px 34px rgba(0, 0, 0, 0.5));
		animation: tk-pop 0.6s cubic-bezier(0.3, 1.4, 0.5, 1) both;
	}
	h1 {
		font:
			800 32px/1.05 'Geist',
			sans-serif;
		letter-spacing: -1px;
	}
	p {
		max-width: 300px;
		font-size: 15px;
		line-height: 1.5;
		color: #c9c4da;
	}
</style>
