<script lang="ts">
	import { onMount } from 'svelte';
	import { enhance } from '$app/forms';
	import { PUBLIC_SITIO } from '$app/env/public';
	import Seo from '#lib/ui/Seo.svelte';
	import { OG_FIJAS, urlOgFija } from '#lib/og.ts';
	import { ldMigas } from '#lib/seo.ts';
	import Icono from '#lib/ui/Icono.svelte';
	import { pesosDeCentavos } from '#lib/precios.ts';

	/*
	 * Caigo Plus por Mercado Pago: el paywall del Prototipo (overlay "paywall") y su pantalla "Ya sos
	 * Plus". Precios, límites y sumadas del día salen de GET /v1/plus; sin sesión, los del handoff
	 * (son los mismos que tiene la API). Sin sumadas, el título y el reloj "Se renuevan en" del
	 * motivo "limit"; con sumadas, el mismo reloj con cuántas usó.
	 */
	let { data, form } = $props();

	/** Precios del handoff (respaldo sin sesión). */
	const RESPALDO: Record<string, number> = { semana: 249900, mes: 599900, temporada: 1399900 };
	/** Semanas que cubre cada plan, para el "por semana". */
	const SEMANAS: Record<string, number> = { semana: 1, mes: 4, temporada: 13 };
	const TEXTOS: Record<string, { l: string; d: string; tag: string }> = {
		semana: { l: 'Semana', d: 'Para probar un finde\u00a0largo', tag: '' },
		mes: { l: 'Mes', d: '7 días gratis para\u00a0arrancar', tag: 'Más elegido' },
		temporada: { l: 'Temporada', d: 'Todo el verano, 3\u00a0meses', tag: '' }
	};
	const centavos = (p: string) =>
		data.plus?.planes.find((x) => x.plan === p)?.centavos ?? RESPALDO[p];
	const porSemana = (p: string) => centavos(p) / SEMANAS[p];
	const precio = (p: string) => pesosDeCentavos(centavos(p));

	const PLANES = $derived(
		(['semana', 'mes', 'temporada'] as const).map((plan) => {
			// El ahorro de la temporada se calcula contra el mes (por semana), no es un número fijo.
			const ahorro =
				plan === 'temporada'
					? Math.round((1 - porSemana('temporada') / porSemana('mes')) * 100)
					: 0;
			return {
				plan,
				...TEXTOS[plan],
				l: data.plus?.planes.find((x) => x.plan === plan)?.nombre || TEXTOS[plan].l,
				per:
					plan === 'semana'
						? 'por semana'
						: `${pesosDeCentavos(Math.round(porSemana(plan) / 100) * 100)} por semana`,
				save: ahorro > 0 ? `−${ahorro}%` : ''
			};
		})
	);

	const estado = $derived(data.plus?.estado);
	const limite = $derived(estado?.limite ?? 3);
	// Con Plus la API da 5 "Me encanta"; sin Plus da el límite gratis (1).
	const encantasGratis = $derived(estado && !estado.activo ? (estado.limiteEncantas ?? 1) : 1);
	const FILAS = $derived([
		['Sumadas en Gente', `${limite} por día`, 'Sin límite'],
		['Ver quién te sumó', '—', '✓'],
		['Me encanta en Citas', `${encantasGratis} por día`, '5 por día'],
		['Tu plan destacado', '—', '1 por semana'],
		['Filtros finos', 'Básicos', 'Todos']
	]);
	const sinSumadas = $derived(!!estado && estado.sumadasHoy >= estado.limite);

	/* Reloj hasta la medianoche de Argentina (reiniciaEn de la API), en vivo. */
	let ahora = $state(Date.now());
	onMount(() => {
		const t = setInterval(() => (ahora = Date.now()), 1000);
		return () => clearInterval(t);
	});
	const reloj = $derived.by(() => {
		const fin = estado ? new Date(estado.reiniciaEn).getTime() : 0;
		const s = Math.max(0, Math.floor((fin - ahora) / 1000));
		return [Math.floor(s / 3600), Math.floor(s / 60) % 60, s % 60]
			.map((n) => String(n).padStart(2, '0'))
			.join(':');
	});

	let elegido = $state('mes');
	let enviando = $state(false);
	const cta = $derived(elegido === 'mes' ? 'Probar 7 días gratis' : `Pagar ${precio(elegido)}`);
	const letra = $derived(
		(elegido === 'mes'
			? `Gratis 7 días, después ${precio('mes')} por mes.`
			: elegido === 'semana'
				? `Pagás ${precio('semana')} hoy.`
				: `Pagás ${precio('temporada')} hoy por 3 meses.`) +
			' Cancelás cuando quieras · Mercado Pago'
	);
	const activo = $derived(!!estado?.activo);

	const vence = $derived(
		estado?.vence
			? new Date(estado.vence).toLocaleDateString('es-AR', {
					day: 'numeric',
					month: 'long',
					timeZone: 'America/Argentina/Buenos_Aires'
				})
			: ''
	);
	const NOMBRE_PLUS: Record<string, string> = {
		semana: 'semanal',
		mes: 'mensual',
		temporada: 'de temporada'
	};
	const nombrePlan = $derived(NOMBRE_PLUS[estado?.plan ?? ''] ?? '');
	/** Cómo se da de baja según dónde se compró (no hay baja desde la web: la lleva cada tienda). */
	const comoCancelar = $derived(
		estado?.origen === 'apple'
			? 'Lo cancelás desde tu iPhone: Ajustes › tu nombre › Suscripciones › Caigo.'
			: estado?.origen === 'google'
				? 'Lo cancelás desde Google Play: Pagos y suscripciones › Suscripciones › Caigo.'
				: estado?.origen === 'mercadopago'
					? 'Lo cancelás desde Mercado Pago: Tu perfil › Suscripciones › Caigo Plus.'
					: ''
	);
	const enlaceCancelar = $derived(
		estado?.origen === 'apple'
			? 'https://apps.apple.com/account/subscriptions'
			: estado?.origen === 'google'
				? 'https://play.google.com/store/account/subscriptions'
				: estado?.origen === 'mercadopago'
					? 'https://www.mercadopago.com.ar/subscriptions'
					: ''
	);
</script>

<Seo
	titulo="Caigo Plus · Caigo"
	descripcion="Con Caigo Plus sumás a toda la gente que quieras, ves quién te sumó, usás todos los filtros y tu plan sale destacado para que se llene más rápido."
	ruta="/plus"
	imagen={urlOgFija('plus')}
	imagenAlt={OG_FIJAS.plus.alt}
	noindex={!!data.conSesion}
	ld={ldMigas(PUBLIC_SITIO.replace(/\/+$/, ''), [
		['Caigo', '/'],
		['Caigo Plus', '/plus']
	])}
/>

<div class="pw">
	{#if activo}
		<div class="listo">
			<div class="pop">
				<img src="/caigo-icon.png" alt="" width="190" height="190" />
				<span class="plus-tag chico">PLUS</span>
			</div>
			<h1 class="ya">Ya sos Plus</h1>
			<p>
				Sumá a toda la gente que quieras. Ya podés ver quién te sumó y tu próximo plan sale
				destacado.
			</p>
			{#if vence}
				<p class="aviso">
					{#if estado?.renueva}
						Tu Plus {nombrePlan} se renueva el&nbsp;{vence}.
					{:else}
						Tu Plus {nombrePlan} vence el {vence} y no se&nbsp;renueva.
					{/if}
				</p>
			{/if}
			<a class="gel gel-lima cta" href="/app">Listo</a>
			{#if estado?.renueva && comoCancelar}
				<p class="cancelar">
					{comoCancelar}
					<a href={enlaceCancelar} target="_blank" rel="noopener noreferrer">Cancelar Plus</a>
				</p>
			{/if}
		</div>
	{:else}
		<div class="scroll">
			<div class="arriba">
				<a class="cerrar" href="/app" aria-label="Cerrar"
					><Icono nombre="cerrar" color="#FFFFFF" trazo={2.6} /></a
				>
			</div>
			<div class="logo">
				<span class="caigo">Caigo</span>
				<span class="plus-tag">PLUS</span>
			</div>
			<div class="encabezado">
				{#if sinSumadas}
					<h1>Te quedaste sin sumadas por&nbsp;hoy</h1>
					<span class="renuevan">Se renuevan en <b>{reloj}</b></span>
				{:else}
					<h1>Más planes. Más gente. Sin&nbsp;límites.</h1>
					<p>Sumá a quien quieras, mirá quién te sumó y destacá tus&nbsp;planes.</p>
					{#if estado}
						<span class="renuevan"
							>{estado.sumadasHoy} de {estado.limite} hoy · se renuevan en <b>{reloj}</b></span
						>
					{/if}
				{/if}
				{#if data.vuelta}
					<p class="aviso">
						Estamos confirmando tu pago con Mercado Pago. Puede tardar unos&nbsp;minutos.
					</p>
				{/if}
				{#if data.error}<p class="aviso">{data.error}</p>{/if}
			</div>

			{#if data.teSumaron.length}
				<a class="teaser" href="/app">
					<span class="caras">
						{#each data.teSumaron.slice(0, 4) as foto, i (i)}
							<span class="cara"
								>{#if foto}<img src={foto} alt="" />{/if}</span
							>
						{/each}
					</span>
					<span class="tt">
						<b
							>{data.teSumaron.length === 1
								? '1 persona te\u00a0sumó'
								: `${data.teSumaron.length} personas te\u00a0sumaron`}</b
						>
						<span>Con Plus ves quiénes son</span>
					</span>
					<span class="candado" aria-hidden="true"
						><svg width="16" height="16" viewBox="0 0 24 24" fill="#FFFFFF"
							><path d="M7 10V7a5 5 0 0 1 10 0v3h1v11H6V10zM9 10h6V7a3 3 0 0 0-6 0z" /></svg
						></span
					>
				</a>
			{/if}

			<div class="tabla">
				<div class="col-plus" aria-hidden="true"></div>
				<div class="fila cab"><span></span><span>GRATIS</span><span class="lima">PLUS</span></div>
				{#each FILAS as [l, g, p], i (l)}
					<div class="fila" class:borde={i > 0}>
						<span class="l">{l}</span>
						<span class="g">{g}</span>
						<span class="p">
							{#if p === '✓'}<Icono
									nombre="check"
									tam={16}
									color="#C8F36B"
									trazo={3.4}
									etiqueta="Sí"
								/>{:else}{p}{/if}
						</span>
					</div>
				{/each}
			</div>

			<h2>Elegí tu plan</h2>
			<form
				id="comprar"
				method="POST"
				use:enhance={() => {
					enviando = true;
					return async ({ update }) => {
						await update({ reset: false });
						enviando = false;
					};
				}}
			>
				<fieldset>
					<legend class="sr">Plan</legend>
					{#each PLANES as p (p.plan)}
						<label class="opcion" class:on={elegido === p.plan}>
							<input type="radio" name="plan" value={p.plan} bind:group={elegido} class="sr" />
							<span class="radio"></span>
							<span class="medio">
								<span class="nombre"
									>{p.l}{#if p.save}<span class="ahorro">{p.save}</span>{/if}</span
								>
								<span class="desc">{p.d}</span>
							</span>
							<span class="der">
								<span class="precio">{precio(p.plan)}</span>
								<span class="desc">{p.per}</span>
							</span>
							{#if p.tag}<span class="tag">{p.tag}</span>{/if}
						</label>
					{/each}
				</fieldset>
			</form>
			<a class="esperar" href="/app">Ahora no</a>
		</div>
		<div class="abajo">
			{#if form?.error}<p class="aviso" role="alert">{form.error}</p>{/if}
			<button class="gel gel-lima cta" form="comprar" disabled={enviando}>
				<img src="/caigo-icon.png" alt="" width="34" height="34" />{data.conSesion
					? cta
					: 'Entrar y ' + cta.toLowerCase()}
			</button>
			<span class="letra">{letra}</span>
		</div>
	{/if}
</div>

<style>
	.pw {
		position: relative;
		min-height: 100vh;
		min-height: 100dvh;
		background: url(/grain.png), radial-gradient(90% 45% at 50% 12%, #3b2f78 0%, #201d2b 70%);
		color: #ffffff;
	}
	.scroll {
		max-width: 430px;
		margin: 0 auto;
		padding: 20px 20px 190px;
		display: flex;
		flex-direction: column;
		gap: 18px;
	}
	.arriba {
		display: flex;
		position: relative;
		z-index: 2;
	}
	.cerrar {
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		background: rgba(255, 255, 255, 0.1);
		box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.15);
	}
	.logo {
		position: relative;
		height: 130px;
		margin: -24px 0 0;
		display: grid;
		place-items: center;
	}
	.caigo {
		font:
			700 80px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #faf9f6;
		animation: tk-up 0.5s ease both;
	}
	.plus-tag {
		position: absolute;
		right: 26px;
		top: 6px;
		padding: 8px 14px;
		border-radius: 14px;
		background: var(--grad-lima);
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.85),
			inset 0 -5px 0 rgba(96, 140, 10, 0.32),
			0 5px 0 #7fa82e,
			0 18px 30px -10px rgba(200, 243, 107, 0.45);
		color: #24212d;
		font:
			800 20px/1 'Geist',
			sans-serif;
		transform: rotate(10deg);
		animation: tk-pop 0.6s 0.2s cubic-bezier(0.3, 1.4, 0.5, 1) both;
	}
	.encabezado {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 10px;
		text-align: center;
	}
	h1 {
		font:
			800 32px/1.02 'Geist',
			sans-serif;
		letter-spacing: -1px;
	}
	.encabezado p {
		font-size: 15px;
		line-height: 1.5;
		color: #d3cee2;
	}
	.aviso {
		padding: 10px 14px;
		border-radius: 14px;
		background: rgba(255, 255, 255, 0.08);
		font-size: 13px !important;
		font-weight: 700;
		line-height: 1.45;
		color: #e4e0ee !important;
		text-align: center;
	}
	.tabla {
		position: relative;
		border-radius: 24px;
		overflow: hidden;
		background: rgba(255, 255, 255, 0.05);
		box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
	}
	.col-plus {
		position: absolute;
		top: 0;
		bottom: 0;
		right: 0;
		width: 29%;
		background: linear-gradient(180deg, rgba(116, 86, 241, 0.45), rgba(116, 86, 241, 0.2));
		box-shadow: inset 1px 0 0 rgba(163, 143, 255, 0.4);
	}
	.fila {
		position: relative;
		display: grid;
		grid-template-columns: 1.5fr 1fr 1fr;
		align-items: center;
	}
	.fila.borde {
		border-top: 1px solid rgba(255, 255, 255, 0.08);
	}
	.fila.cab span {
		padding: 12px 4px 4px;
		text-align: center;
		font-size: 11px;
		font-weight: 800;
		letter-spacing: 0.8px;
		color: #a8a3bd;
	}
	.fila.cab .lima {
		color: #c8f36b;
	}
	.l {
		padding: 12px 0 12px 14px;
		font-size: 13px;
		font-weight: 700;
		color: #faf9f6;
	}
	.g,
	.p {
		padding: 12px 4px;
		text-align: center;
		font-size: 12px;
		font-weight: 600;
		color: #a8a3bd;
	}
	.p {
		font-weight: 800;
		color: #c8f36b;
		display: flex;
		justify-content: center;
	}
	h2 {
		margin-top: 4px;
		font:
			700 20px 'Geist',
			sans-serif;
	}
	fieldset {
		border: 0;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.opcion {
		position: relative;
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 16px;
		border-radius: 22px;
		cursor: pointer;
		background: rgba(255, 255, 255, 0.06);
		box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.14);
		color: #ffffff;
		transition:
			background 0.15s,
			box-shadow 0.15s;
	}
	.opcion.on {
		background: #ffffff;
		color: #24212d;
		box-shadow:
			inset 0 0 0 3px #c8f36b,
			0 4px 0 #94c43a,
			0 18px 30px -12px rgba(200, 243, 107, 0.35);
	}
	.opcion:has(input:focus-visible) {
		outline: 3px solid #b3a3ff;
		outline-offset: 2px;
	}
	.radio {
		flex: none;
		width: 24px;
		height: 24px;
		border-radius: 50%;
		background: #ffffff;
		border: 2px solid rgba(255, 255, 255, 0.35);
	}
	.on .radio {
		border: 7px solid #7456f1;
	}
	.medio {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.nombre {
		display: flex;
		align-items: center;
		gap: 8px;
		font-size: 16px;
		font-weight: 800;
	}
	.ahorro {
		padding: 3px 7px;
		border-radius: 8px;
		background: #c8f36b;
		color: #24212d;
		font-size: 11px;
		font-weight: 800;
	}
	.desc {
		font-size: 12px;
		font-weight: 600;
		color: #c9c4da;
	}
	.on .desc {
		color: #5c586b;
	}
	.der {
		flex: none;
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		gap: 2px;
	}
	.der .desc {
		font-size: 11px;
		font-weight: 700;
	}
	.precio {
		font:
			800 22px/1 'Geist',
			sans-serif;
	}
	.tag {
		position: absolute;
		top: -10px;
		left: 18px;
		padding: 4px 10px;
		border-radius: 9px;
		background: linear-gradient(180deg, #ffb2a6, #ff7868 60%, #f05e4c);
		box-shadow: 0 2px 0 #c9432f;
		color: #24212d;
		font-size: 11px;
		font-weight: 800;
	}
	.esperar {
		align-self: center;
		padding: 8px 14px;
		font-size: 14px;
		font-weight: 700;
		color: #c9c4da;
		text-decoration: none;
	}
	.abajo {
		position: fixed;
		left: 0;
		right: 0;
		bottom: 0;
		padding: 16px 20px 30px;
		background: linear-gradient(180deg, rgba(32, 29, 43, 0), #201d2b 30%);
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 10px;
		z-index: 3;
	}
	.abajo > * {
		width: 100%;
		max-width: 390px;
	}
	.cta {
		height: 64px;
		border-radius: 24px;
		font-size: 18px;
		width: 100%;
	}
	.cta img {
		width: 34px;
		height: 34px;
		margin-left: -6px;
	}
	.letra {
		text-align: center;
		font-size: 12px;
		font-weight: 600;
		line-height: 1.45;
		color: #c9c4da;
	}
	.listo {
		max-width: 430px;
		min-height: 100vh;
		min-height: 100dvh;
		margin: 0 auto;
		padding: 30px;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 18px;
		text-align: center;
	}
	.pop {
		position: relative;
	}
	.pop img {
		width: 190px;
		animation: tk-pop 0.6s cubic-bezier(0.3, 1.4, 0.5, 1) both;
		filter: drop-shadow(0 26px 30px rgba(116, 86, 241, 0.6));
	}
	.plus-tag.chico {
		top: auto;
		right: -14px;
		bottom: 20px;
		padding: 7px 12px;
		border-radius: 13px;
		font-size: 18px;
	}
	.ya {
		font-size: 42px;
		line-height: 1;
		letter-spacing: -1.4px;
	}
	.listo p {
		font-size: 16px;
		line-height: 1.5;
		color: #d3cee2;
	}
	.listo .cta {
		max-width: 390px;
		margin-top: 8px;
	}
	.renuevan {
		display: inline-flex;
		flex-wrap: wrap;
		align-items: center;
		justify-content: center;
		gap: 8px;
		padding: 8px 8px 8px 14px;
		border-radius: 14px;
		background: rgba(255, 255, 255, 0.08);
		font-size: 13px;
		font-weight: 700;
		color: #e4e0ee;
	}
	.renuevan b {
		padding: 5px 9px;
		border-radius: 9px;
		background: #ff7868;
		color: #24212d;
		font:
			800 15px/1 'Geist',
			sans-serif;
		letter-spacing: 0.5px;
		font-variant-numeric: tabular-nums;
	}
	.teaser {
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 14px;
		border-radius: 24px;
		background: linear-gradient(180deg, rgba(116, 86, 241, 0.35), rgba(116, 86, 241, 0.15));
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.15),
			inset 0 0 0 1px rgba(163, 143, 255, 0.35);
		color: #ffffff;
		text-decoration: none;
	}
	.caras {
		flex: none;
		display: flex;
		padding-left: 10px;
	}
	.cara {
		width: 44px;
		height: 44px;
		margin-left: -12px;
		border-radius: 50%;
		overflow: hidden;
		border: 2.5px solid #2e2850;
		background: #3a3452;
	}
	.cara img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		filter: blur(6px);
		transform: scale(1.2);
	}
	.tt {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.tt b {
		font-size: 15px;
		font-weight: 800;
	}
	.tt span {
		font-size: 12px;
		font-weight: 600;
		color: #d3cee2;
	}
	.candado {
		flex: none;
		width: 34px;
		height: 34px;
		border-radius: 12px;
		background: rgba(255, 255, 255, 0.12);
		display: grid;
		place-items: center;
	}
	.cancelar {
		font-size: 13px !important;
		color: #c9c4da !important;
	}
	.cancelar a {
		display: block;
		margin-top: 6px;
		font-weight: 800;
		color: #c8f36b;
	}
</style>
