<script lang="ts">
	import { PUBLIC_CLERK_PUBLICABLE } from '$app/env/public';
	import { enhance } from '$app/forms';
	import Seo from '#lib/ui/Seo.svelte';
	import { OG_FIJAS, urlOgFija } from '#lib/og.ts';
	import Pantalla from '#lib/ui/PantallaOscura.svelte';
	import Icono from '#lib/ui/Icono.svelte';
	import { tick } from 'svelte';
	import Requisitos from '#lib/ui/Requisitos.svelte';
	import {
		confirmarCodigo,
		empezarConMail,
		entrarConClave,
		ErrorClerk,
		fapiDe,
		guardarClave,
		iniciar,
		mensajeClerk,
		requisitosClave,
		type FlujoMail,
		type PasoMail,
		type ProveedorClerk
	} from '#lib/clerk.ts';

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

	/*
	 * Con mail y contraseña (calcado de Tengo Libre, lib/clerk.ts): contraseña o código, y si es la
	 * primera vez se crea la cuenta. Desde un navegador nuevo Clerk puede pedir confirmarlo con un
	 * código por mail. Al final el token de Clerk va a /sso-callback, que lo canjea por la sesión de
	 * Caigo (igual que con Google o Apple).
	 */
	let paso = $state<'mail' | PasoMail['paso']>('mail');
	let email = $state('');
	let clave = $state('');
	let codigo = $state('');
	let alta = $state(false);
	let confiar = $state(false);
	let enviando = $state(false);
	let tokenMail = $state('');
	let flujo: FlujoMail | null = null;
	let formToken: HTMLFormElement;
	const req = $derived(requisitosClave(clave));

	async function hacer(fn: () => Promise<PasoMail>) {
		enviando = true;
		error = '';
		try {
			const sig = await fn();
			if (sig.paso === 'listo') {
				tokenMail = sig.token;
				await tick();
				formToken.requestSubmit();
				return;
			}
			alta = 'alta' in sig ? sig.alta : false;
			confiar = sig.paso === 'codigo' && !!sig.confiar;
			if (sig.paso === 'codigo') codigo = '';
			paso = sig.paso;
		} catch (x) {
			error =
				x instanceof ErrorClerk ? mensajeClerk(x.codigo) : 'No pudimos conectar. Probá de nuevo.';
		} finally {
			if (!tokenMail) enviando = false;
		}
	}

	function seguirConMail(e: SubmitEvent) {
		e.preventDefault();
		const f = fapi;
		if (!f || enviando) return;
		if (paso === 'mail')
			void hacer(async () => {
				const r = await empezarConMail(f, email.trim());
				flujo = r.flujo;
				return r.sigue;
			});
		else if (paso === 'clave' && flujo) void hacer(() => entrarConClave(flujo!, clave));
		else if (paso === 'codigo' && flujo) void hacer(() => confirmarCodigo(flujo!, codigo));
		else if (paso === 'clave_nueva' && flujo) {
			if (!req.largo || !req.comun)
				return void (error = 'La contraseña todavía no cumple los requisitos.');
			void hacer(() => guardarClave(flujo!, clave));
		}
	}

	function otroMail() {
		paso = 'mail';
		clave = codigo = error = '';
		flujo = null;
	}
</script>

{#if data.lugares}
	<!-- En lugares.caigo.com.ar ésta es la entrada (la raíz pide sesión y trae acá): se indexa. -->
	<Seo
		titulo="Publicá planes y llená tu lugar · Caigo para lugares"
		descripcion="Caigo para lugares: publicá planes con entrada o seña, cobrá con Mercado Pago y hacé el check-in con Caigo Tap. Llená tu bar, café o cancha en la costa."
		ruta="/ingresar"
		lugares
		imagen={urlOgFija('lugares')}
		imagenAlt={OG_FIJAS.lugares.alt}
	/>
{:else}
	<Seo titulo="Entrar · Caigo" ruta="/ingresar" noindex />
{/if}

<Pantalla>
	<div class="centro">
		<img src="/caigo-icon.png" alt="" class="icono" width="132" height="132" />
		<span class="marca">Caigo</span>
		<h1>Hacé planes. Conocé&nbsp;gente.</h1>
		{#if data.lugares}
			<p>Entrá con Google, Apple o tu mail para publicar los planes de tu&nbsp;lugar.</p>
		{:else}
			<p>Entrá con Google, Apple o tu mail para sumarte a&nbsp;planes.</p>
		{/if}
	</div>

	<div class="acciones">
		{#if fapi}
			{#if paso === 'mail'}
				<button
					class="gel gel-lima boton"
					onclick={() => entrar('google')}
					disabled={!!yendo || enviando}
					aria-busy={yendo === 'google'}
				>
					{yendo === 'google' ? 'Abriendo Google…' : 'Seguir con Google'}
				</button>
				<button
					class="boton vidrio"
					onclick={() => entrar('apple')}
					disabled={!!yendo || enviando}
					aria-busy={yendo === 'apple'}
				>
					{yendo === 'apple' ? 'Abriendo Apple…' : 'Seguir con Apple'}
				</button>
			{/if}
			<form class="con-mail" onsubmit={seguirConMail}>
				{#if paso === 'mail'}
					<span class="separador">o con tu mail</span>
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
					<button class="boton vidrio" disabled={enviando || !!yendo}
						>{enviando ? 'Un segundo…' : 'Seguir con mail'}</button
					>
				{:else}
					<button type="button" class="volver" onclick={otroMail}>
						<Icono nombre="atras" tam={18} /><span>{email}</span>
					</button>
					{#if paso === 'clave'}
						<label class="etiqueta" for="clave">Tu contraseña</label>
						<input
							id="clave"
							class="input"
							type="password"
							autocomplete="current-password"
							required
							bind:value={clave}
						/>
						<button class="gel gel-lima boton" disabled={enviando}
							>{enviando ? 'Entrando…' : 'Entrar'}</button
						>
						<a class="olvide" href="/ingresar/recuperar?email={encodeURIComponent(email)}"
							>¿Te olvidaste la contraseña?</a
						>
					{:else if paso === 'codigo'}
						<p class="explica">
							{#if confiar}
								Es la primera vez que entrás desde este dispositivo. Te mandamos un código a <b
									>{email}</b
								> para&nbsp;confirmarlo.
							{:else if alta}
								Te mandamos un código a <b>{email}</b>. Con él creamos tu&nbsp;cuenta.
							{:else}
								Te mandamos un código a <b>{email}</b>. Escribilo para&nbsp;entrar.
							{/if}
						</p>
						<label class="etiqueta" for="codigo">Código</label>
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
							>{enviando ? 'Revisando…' : alta ? 'Crear mi cuenta' : 'Entrar'}</button
						>
					{:else if paso === 'clave_nueva'}
						<p class="explica">Elegí una contraseña para entrar con tu&nbsp;mail.</p>
						<label class="etiqueta" for="nueva">Contraseña</label>
						<input
							id="nueva"
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
					{/if}
				{/if}
			</form>
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

<!-- El token de Clerk del ingreso con mail termina igual que el de Google o Apple. -->
<form method="POST" action="/sso-callback" bind:this={formToken} hidden>
	<input type="hidden" name="token" value={tokenMail} />
</form>

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
	.con-mail {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.separador {
		display: flex;
		align-items: center;
		gap: 12px;
		font-size: 13px;
		font-weight: 600;
		color: #a8a3bd;
	}
	.separador::before,
	.separador::after {
		content: '';
		flex: 1;
		height: 1px;
		background: rgba(255, 255, 255, 0.15);
	}
	.volver {
		align-self: flex-start;
		display: flex;
		align-items: center;
		gap: 6px;
		min-height: 44px;
		max-width: 100%;
		padding: 0;
		border: 0;
		background: none;
		font: inherit;
		font-size: 14px;
		font-weight: 700;
		color: #c9c4da;
		cursor: pointer;
	}
	.volver span {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.etiqueta {
		font-size: 14px;
		font-weight: 700;
		color: #faf9f6;
	}
	.explica {
		font-size: 14px;
		line-height: 1.5;
		color: #c9c4da;
		overflow-wrap: anywhere;
	}
	.explica b {
		color: #faf9f6;
	}
	.codigo {
		font-size: 22px;
		letter-spacing: 0.3em;
		text-align: center;
	}
	.olvide {
		align-self: center;
		min-height: 44px;
		display: grid;
		place-items: center;
		font-size: 14px;
		font-weight: 700;
		color: #c8f36b;
		text-decoration: none;
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
