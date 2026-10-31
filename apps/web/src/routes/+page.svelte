<script lang="ts">
	import { enhance } from '$app/forms';
	import { PUBLIC_PANEL, PUBLIC_SITIO } from '$app/env/public';
	import { OG_FIJAS, urlOgFija } from '#lib/og.ts';
	import { ldSitio } from '#lib/seo.ts';
	import Seo from '#lib/ui/Seo.svelte';
	import Pie from '#lib/ui/Pie.svelte';
	import TarjetaPlan from '#lib/ui/TarjetaPlan.svelte';
	import Tiendas from '#lib/ui/Tiendas.svelte';

	/*
	 * Landing de Caigo. No está diseñada en el handoff: se arma sólo con piezas de Mockups v5 — el
	 * splash (00), los tres slides del onboarding (01a–01c) con sus textos, las tarjetas de "Ahora"
	 * (02) con los planes de ejemplo del handoff, y el gradiente violeta de "Tu plan ya está en
	 * Caigo" del panel. Fotos CC0 de StockSnap (static/fotos/CREDITOS.md).
	 * "¿Quién se prende?" muestra los planes que están pasando (+page.server.ts); si no hay, los de
	 * ejemplo. La lista de espera se arma con el contador oscuro y el selector de 01b.
	 */
	let { data, form } = $props();
	const EJEMPLOS = [
		{
			foto: '/fotos/playa.jpg',
			tinte: '#3B6B57',
			categoria: 'Deporte',
			cuando: 'Hoy 20:30',
			titulo: 'Fútbol 5 en Punta Mogotes',
			donde: 'Cancha Los Pinos · 1,2 km',
			faltan: 2,
			somos: 8,
			total: 10,
			personas: [{ nombre: 'M' }, { nombre: 'J' }, { nombre: 'L' }]
		},
		{
			foto: '/fotos/truco.jpg',
			tinte: '#5B43C9',
			categoria: 'Juego',
			cuando: 'Hoy 21:30',
			titulo: 'Truco en un bar de Güemes',
			donde: 'Bar La Cumbre · 800 m',
			faltan: 1,
			somos: 3,
			total: 4,
			personas: [{ nombre: 'C' }, { nombre: 'L' }, { nombre: 'N' }]
		},
		{
			foto: '/fotos/previa.jpg',
			tinte: '#3A3452',
			categoria: 'Previa',
			cuando: 'Hoy 23:00',
			titulo: 'Previa en La Perla, buscamos otro grupo',
			donde: 'La Perla · 2,4 km',
			faltan: 4,
			somos: 5,
			total: 9,
			personas: [{ nombre: 'G' }, { nombre: 'F' }, { nombre: 'R' }]
		}
	];

	const reales = $derived(data.ahora.length > 0);
	let ciudad = $state('Mar del Plata');
	let enviando = $state(false);
	const espera = $derived(form?.espera);
</script>

<Seo
	titulo="Caigo · Hacé planes. Conocé gente."
	descripcion="Sumate a planes a los que les falta gente en Mar del Plata y Villa Gesell: un fútbol 5 al que le falta uno, un truco de a cuatro o una previa que busca grupo."
	ruta="/"
	imagen={urlOgFija('inicio')}
	imagenAlt={OG_FIJAS.inicio.alt}
	ld={ldSitio(PUBLIC_SITIO.replace(/\/+$/, ''))}
/>

<!-- 00 · Splash -->
<header class="hero">
	<span class="mancha lima" aria-hidden="true"></span>
	<span class="mancha coral" aria-hidden="true"></span>
	<nav class="barra">
		<a class="logo" href="/" aria-label="Caigo, inicio">Caigo</a>
		<a class="vidrio-chico" href={PUBLIC_PANEL}>Para lugares</a>
	</nav>
	<div class="hero-centro">
		<img src="/caigo-icon.png" alt="" class="icono" width="188" height="188" fetchpriority="high" />
		<h1 class="marca-grande">Caigo</h1>
		<p class="lema">Hacé planes. Conocé gente.</p>
		<p class="bajada">
			Sumate a planes a los que les falta gente, o completá los tuyos. En Mar del Plata y
			Villa&nbsp;Gesell.
		</p>
		<div class="acciones" id="app">
			<a class="gel gel-lima usar" href="/app">Usar Caigo desde la web</a>
			<Tiendas tono="oscuro" />
		</div>
	</div>
	<!-- La tarjeta flotante de 01a. -->
	<div class="flotante" aria-hidden="true">
		<div class="av3">
			<span style="background-color:#7456F1;color:#fff">N</span>
			<span style="background-color:#FF7868">L</span>
			<span style="background-color:#C8F36B">C</span>
		</div>
		<div class="flotante-txt">
			<b>Previa en La Perla</b>
			<span><i></i>Ahora · 900 m</span>
		</div>
		<span class="badge-coral flotante-badge">Falta 1</span>
	</div>
</header>

<main>
	<!-- 01a–01c · Cómo funciona -->
	<section class="seccion" aria-labelledby="como">
		<div class="ancho">
			<div class="cabeza">
				<h2 id="como" class="titulo-seccion">Cómo funciona</h2>
			</div>
			<ol class="pasos">
				<li class="paso paso-a">
					<div class="paso-foto">
						<img
							src="/fotos/previa.jpg"
							alt=""
							width="960"
							height="640"
							loading="lazy"
							decoding="async"
						/>
						<div class="velo-a"></div>
						<div class="mini">
							<div class="av3 av3-chico">
								<span style="background-color:#7456F1;color:#fff">N</span>
								<span style="background-color:#FF7868">L</span>
								<span style="background-color:#C8F36B">C</span>
							</div>
							<div class="flotante-txt">
								<b>Previa en La Perla</b>
								<span><i></i>Ahora · 900 m</span>
							</div>
							<span class="badge-coral mini-badge">Falta 1</span>
						</div>
					</div>
					<div class="paso-txt">
						<span class="num lima-txt">01</span>
						<h3>Planes a los que les falta gente.</h3>
						<p>Una previa, un partido, una mesa de truco. Pasando ahora, cerca&nbsp;tuyo.</p>
					</div>
				</li>
				<li class="paso paso-b">
					<div class="paso-foto">
						<img
							src="/fotos/amigos.jpg"
							alt=""
							width="960"
							height="677"
							loading="lazy"
							decoding="async"
						/>
						<div class="cita">
							<div class="av3 av3-medio">
								<span style="background-color:#7456F1;color:#fff">C</span>
								<span style="background-color:#FF7868">L</span>
							</div>
							<span class="mas-violeta" aria-hidden="true"
								><svg width="16" height="16" viewBox="0 0 24 24"
									><path
										d="M12 4v16M4 12h16"
										stroke="#C8F36B"
										stroke-width="4"
										stroke-linecap="round"
									/></svg
								></span
							>
							<b>Cita doble</b>
						</div>
					</div>
					<div class="hoja">
						<div class="selector" aria-hidden="true">
							<span>Voy solo</span><span class="on">Somos 2</span><span>Somos 4</span>
						</div>
						<span class="num">02</span>
						<h3>Sumate solo o con amigos.</h3>
						<p>
							Entrás con tu gente en un toque. Así salen las citas dobles y los partidos grupo
							contra&nbsp;grupo.
						</p>
					</div>
				</li>
				<li class="paso paso-c">
					<div class="polaroid">
						<div class="marco">
							<img
								src="/fotos/grupo.jpg"
								alt=""
								width="960"
								height="640"
								loading="lazy"
								decoding="async"
							/>
						</div>
						<span class="somos6">Ya somos 6</span>
						<img
							class="icono-c"
							src="/caigo-icon.png"
							alt=""
							width="308"
							height="308"
							loading="lazy"
							decoding="async"
						/>
					</div>
					<div class="paso-txt">
						<span class="num lima-txt">03</span>
						<h3>Lo mejor pasa afuera.</h3>
						<p>Compartís un plan y conocés gente. Sin swipes, sin chats&nbsp;eternos.</p>
					</div>
				</li>
			</ol>
		</div>
	</section>

	<!-- 02 · Ahora -->
	<section class="seccion ahora" aria-labelledby="ahora">
		<div class="ancho">
			<div class="cabeza">
				<span class="overline"
					>{reales ? 'PASANDO AHORA EN CAIGO' : 'ASÍ SE VEN LOS PLANES EN CAIGO'}</span
				>
				<h2 id="ahora" class="titulo-seccion">¿Quién se prende?</h2>
			</div>
			<div class="grilla">
				{#if reales}
					{#each data.ahora as p (p.id)}
						<TarjetaPlan {...p} href="/app/plan/{p.id}" enlace="/app/plan/{p.id}" />
					{/each}
				{:else}
					{#each EJEMPLOS as e (e.titulo)}
						<TarjetaPlan {...e} href="/app" />
					{/each}
				{/if}
			</div>
		</div>
	</section>

	<!-- Lista de espera de la app: el contador oscuro del detalle (05) y el selector de 01b. -->
	<section class="seccion espera-sec" aria-labelledby="espera">
		<div class="ancho">
			<div class="espera">
				<div class="espera-txt">
					<span class="overline lima-txt">LISTA DE ESPERA</span>
					<h2 id="espera">Te avisamos cuando esté la app.</h2>
					<p>
						Mientras tanto, Caigo anda desde la web. Dejanos tu mail y te escribimos una sola vez,
						cuando esté en las&nbsp;tiendas.
					</p>
				</div>
				{#if espera && 'listo' in espera}
					<p class="espera-listo" role="status">
						Listo. Te escribimos a {espera.email} cuando la app esté&nbsp;lista.
					</p>
				{:else}
					<form
						method="POST"
						action="?/espera"
						class="espera-form"
						use:enhance={() => {
							enviando = true;
							return async ({ update }) => {
								await update({ reset: false });
								enviando = false;
							};
						}}
					>
						<label class="sr" for="espera-mail">Tu mail</label>
						<input
							id="espera-mail"
							class="espera-mail"
							type="email"
							name="email"
							required
							maxlength="200"
							autocomplete="email"
							placeholder="tu@mail.com"
							value={espera?.email ?? ''}
						/>
						<fieldset class="selector espera-ciudad">
							<legend class="sr">Tu ciudad</legend>
							{#each data.ciudades as c (c)}
								<label class:on={ciudad === c}
									><input
										type="radio"
										name="ciudad"
										value={c}
										bind:group={ciudad}
										class="sr"
									/>{c === 'Otra ciudad' ? 'Otra' : c}</label
								>
							{/each}
						</fieldset>
						{#if espera && 'error' in espera}<p class="espera-error" role="alert">
								{espera.error}
							</p>{/if}
						<button class="gel gel-lima cta espera-cta" disabled={enviando}>Avisame</button>
					</form>
				{/if}
			</div>
		</div>
	</section>

	<!-- Para lugares: el violeta de "Tu plan ya está en Caigo" (Panel Lugares). -->
	<section class="seccion" aria-labelledby="lugares">
		<div class="ancho">
			<div class="lugares">
				<div class="lugares-txt">
					<span class="overline lima-txt">PARA LUGARES</span>
					<h2 id="lugares">¿Tenés un bar, un café o una&nbsp;cancha?</h2>
					<p>
						Publicá planes con entrada o seña, hacé el check-in en la puerta con Caigo Tap y cobrá
						con Mercado&nbsp;Pago.
					</p>
					<p class="nota">
						Publicar es gratis. Caigo cobra un cargo por servicio a quien compra, nunca
						al&nbsp;lugar.
					</p>
					<a class="gel gel-lima cta" href={PUBLIC_PANEL}>
						Ir al panel de lugares
						<svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true"
							><path
								d="M5 12h14M13 6l6 6-6 6"
								stroke="#24212D"
								stroke-width="2.6"
								stroke-linecap="round"
								stroke-linejoin="round"
							/></svg
						>
					</a>
				</div>
				<div class="lugares-foto">
					<img
						src="/fotos/lugar.jpg"
						alt=""
						width="960"
						height="720"
						loading="lazy"
						decoding="async"
					/>
				</div>
			</div>
		</div>
	</section>
</main>

<Pie />

<style>
	/* ——— Hero: splash ——— */
	.hero {
		position: relative;
		overflow: hidden;
		min-height: min(100svh, 900px);
		display: flex;
		flex-direction: column;
		background:
			url(/grain.png), radial-gradient(70% 45% at 50% 42%, #3a3450 0%, #262233 50%, #17151f 100%);
		color: #faf9f6;
		padding: 0 20px 120px;
	}
	.mancha {
		position: absolute;
		border-radius: 50%;
		pointer-events: none;
	}
	.lima {
		top: 120px;
		left: -60px;
		width: 180px;
		height: 180px;
		background: radial-gradient(circle at 35% 30%, #f2ffc8, #c8f36b 45%, #9ccb3a);
		opacity: 0.5;
		filter: blur(22px);
	}
	.coral {
		bottom: 150px;
		right: -50px;
		width: 150px;
		height: 150px;
		background: radial-gradient(circle at 35% 30%, #ffc4ba, #ff7868 50%, #e0503f);
		opacity: 0.45;
		filter: blur(24px);
	}
	.barra {
		position: relative;
		width: 100%;
		max-width: 1180px;
		margin: 0 auto;
		padding-top: 20px;
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.logo {
		font:
			700 30px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #faf9f6;
		text-decoration: none;
	}
	.logo:hover {
		color: #faf9f6;
	}
	.vidrio-chico {
		padding: 8px 14px;
		border-radius: 14px;
		background: rgba(255, 255, 255, 0.18);
		backdrop-filter: blur(10px);
		color: #ffffff;
		font-size: 14px;
		font-weight: 700;
		text-decoration: none;
	}
	.vidrio-chico:hover {
		color: #ffffff;
		background: rgba(255, 255, 255, 0.26);
	}
	.hero-centro {
		position: relative;
		flex: 1;
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 22px;
		padding: 40px 0 20px;
		text-align: center;
	}
	.icono {
		width: clamp(150px, 22vw, 188px);
		height: auto;
		filter: drop-shadow(0 30px 34px rgba(0, 0, 0, 0.5));
		animation: tk-pop 0.6s cubic-bezier(0.3, 1.4, 0.5, 1) both;
	}
	.marca-grande {
		font:
			700 clamp(46px, 9vw, 120px) / 1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #faf9f6;
	}
	.lema {
		font:
			700 20px 'Geist',
			sans-serif;
		color: #ffffff;
		letter-spacing: -0.2px;
	}
	.bajada {
		max-width: 420px;
		font-size: 15px;
		line-height: 1.5;
		color: #c9c4da;
	}
	.acciones {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 22px;
		margin-top: 6px;
		width: 100%;
	}
	.usar {
		width: min(100%, 340px);
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
	}
	/* Tarjeta flotante de 01a */
	.flotante {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 36px;
		margin: 0 auto;
		width: min(350px, calc(100% - 40px));
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 12px 12px 20px;
		border-radius: 24px;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 243, 255, 0.94));
		box-shadow:
			inset 0 1.5px 0 #ffffff,
			0 22px 40px -14px rgba(0, 0, 0, 0.6);
		color: #24212d;
		animation: tk-up 0.5s 0.2s ease both;
	}
	.av3 {
		flex: none;
		display: flex;
	}
	.av3 span {
		width: 32px;
		height: 32px;
		margin-left: -9px;
		border-radius: 50%;
		border: 2.5px solid #ffffff;
		display: grid;
		place-items: center;
		font-size: 12px;
		font-weight: 800;
		color: #24212d;
		background-image: radial-gradient(
			circle at 30% 25%,
			rgba(255, 255, 255, 0.6),
			rgba(255, 255, 255, 0) 42%
		);
	}
	.flotante-txt {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 1px;
		min-width: 0;
		text-align: left;
	}
	.flotante-txt b {
		font-size: 14px;
		font-weight: 800;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.flotante-txt span {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 12px;
		font-weight: 600;
		color: #5c586b;
	}
	.flotante-txt i {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: #ff7868;
	}
	.flotante-badge {
		flex: none;
		padding: 9px 13px;
		border-radius: 14px;
		font-size: 17px;
	}

	/* ——— Secciones ——— */
	main {
		background:
			url(/grain.png), radial-gradient(90% 38% at 100% 0%, #ece5ff 0%, rgba(236, 229, 255, 0) 70%),
			#faf9f6;
	}
	.seccion {
		padding: 64px 20px;
	}
	.ancho {
		max-width: 1180px;
		margin: 0 auto;
	}
	.cabeza {
		display: flex;
		flex-direction: column;
		gap: 8px;
		margin-bottom: 28px;
	}
	.titulo-seccion {
		font:
			800 40px/1 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}
	.num {
		font:
			800 44px/1 'Geist',
			sans-serif;
		color: #7456f1;
	}
	.lima-txt {
		color: #c8f36b;
	}

	/* ——— Pasos (01a–01c) ——— */
	.pasos {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
		gap: 20px;
	}
	.paso {
		position: relative;
		display: flex;
		flex-direction: column;
		min-height: 600px;
		border-radius: 36px;
		overflow: hidden;
		box-shadow: 0 40px 80px -40px rgba(36, 33, 45, 0.45);
	}
	.paso h3 {
		font:
			800 40px/1 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}
	.paso p {
		font-size: 16px;
		line-height: 1.5;
	}
	.paso-txt {
		display: flex;
		flex-direction: column;
		gap: 12px;
		padding: 0 26px 32px;
	}
	/* 01a */
	.paso-a {
		background: #201d2b;
		color: #faf9f6;
	}
	.paso-a .paso-foto {
		position: relative;
		height: 340px;
		flex: none;
	}
	.paso-foto img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.velo-a {
		position: absolute;
		inset: 0;
		background: linear-gradient(
			180deg,
			rgba(32, 29, 43, 0.55) 0%,
			rgba(32, 29, 43, 0) 18%,
			rgba(32, 29, 43, 0) 50%,
			rgba(32, 29, 43, 0.85) 85%,
			#201d2b 100%
		);
	}
	.mini {
		position: absolute;
		left: 20px;
		right: 20px;
		bottom: 18px;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 12px 12px 20px;
		border-radius: 24px;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 243, 255, 0.94));
		box-shadow:
			inset 0 1.5px 0 #ffffff,
			0 22px 40px -14px rgba(0, 0, 0, 0.6);
		color: #24212d;
	}
	.mini-badge {
		flex: none;
		padding: 9px 13px;
		border-radius: 14px;
		font-size: 17px;
	}
	/* En el celular la tarjeta queda más angosta que en el handoff: se achica el aire para que el
	   título entre entero. */
	@media (max-width: 480px) {
		.mini {
			left: 14px;
			right: 14px;
			gap: 8px;
			padding: 10px 10px 10px 14px;
		}
		.mini-badge {
			padding: 7px 10px;
			font-size: 14px;
		}
	}
	.paso-a .paso-txt {
		margin-top: auto;
		padding-top: 24px;
	}
	.paso-a p {
		color: #c9c4da;
		font-size: 15px;
	}
	/* 01b */
	.paso-b {
		background: url(/grain.png), #faf9f6;
	}
	.paso-b .paso-foto {
		position: relative;
		height: 300px;
		flex: none;
	}
	.cita {
		position: absolute;
		left: 24px;
		bottom: 64px;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 10px 16px 10px 18px;
		border-radius: 22px;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(248, 246, 255, 0.9));
		box-shadow:
			inset 0 1.5px 0 #ffffff,
			0 20px 40px -14px rgba(20, 10, 60, 0.55);
	}
	.cita b {
		font:
			700 17px 'Geist',
			sans-serif;
	}
	.av3-medio span {
		width: 38px;
		height: 38px;
		margin-left: -8px;
		border-width: 2px;
		font-size: 15px;
	}
	.av3-chico span {
		width: 32px;
		height: 32px;
	}
	.mas-violeta {
		width: 34px;
		height: 34px;
		border-radius: 12px;
		display: grid;
		place-items: center;
		background: var(--grad-violeta);
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.45),
			inset 0 -5px 0 rgba(40, 20, 140, 0.35),
			0 4px 0 #4a2fc2,
			0 16px 26px -10px rgba(116, 86, 241, 0.75);
	}
	.hoja {
		position: relative;
		margin-top: -40px;
		flex: 1;
		border-radius: 36px 36px 0 0;
		background: url(/grain.png), #faf9f6;
		padding: 30px 26px 32px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.selector {
		display: flex;
		padding: 5px;
		border-radius: 20px;
		background: linear-gradient(180deg, #ede8ff, #f6f3ff);
		box-shadow: inset 0 2px 4px rgba(116, 86, 241, 0.15);
	}
	.selector span,
	.selector label {
		flex: 1;
		text-align: center;
		padding: 12px 0;
		font-size: 15px;
		font-weight: 600;
		color: #5c586b;
		white-space: nowrap;
	}
	.selector .on {
		border-radius: 16px;
		background: var(--grad-violeta);
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.45),
			inset 0 -5px 0 rgba(40, 20, 140, 0.35),
			0 4px 0 #4a2fc2,
			0 16px 26px -10px rgba(116, 86, 241, 0.75);
		color: #ffffff;
		font-weight: 800;
	}
	.paso-b p {
		color: #5c586b;
	}
	/* 01c */
	.paso-c {
		background:
			url(/grain.png), radial-gradient(120% 70% at 85% 0%, #a38fff 0%, #7456f1 45%, #5a3dd8 100%);
		color: #ffffff;
	}
	.polaroid {
		position: relative;
		height: 360px;
		flex: none;
	}
	.marco {
		position: absolute;
		top: 34px;
		left: 36px;
		right: 54px;
		height: 280px;
		border-radius: 34px;
		overflow: hidden;
		transform: rotate(-4deg);
		box-shadow: 0 30px 60px -20px rgba(20, 10, 80, 0.7);
		border: 6px solid #faf9f6;
	}
	.marco img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.somos6 {
		position: absolute;
		top: 48px;
		right: 30px;
		padding: 8px 12px;
		border-radius: 14px;
		background: var(--grad-lima);
		box-shadow: var(--sh-lima);
		color: #24212d;
		font:
			700 15px/1 'Geist',
			sans-serif;
		transform: rotate(6deg);
	}
	.icono-c {
		position: absolute;
		top: 240px;
		right: 22px;
		width: 110px;
		height: 110px;
		transform: rotate(12deg);
		filter: drop-shadow(0 22px 22px rgba(20, 10, 80, 0.5));
	}
	.paso-c h3 {
		font-size: 46px;
		line-height: 0.98;
		letter-spacing: -1.8px;
	}
	.paso-c .paso-txt {
		margin-top: auto;
	}

	/* ——— Ahora ——— */
	.grilla {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
		gap: 16px;
	}

	/* ——— Lista de espera ——— */
	.espera-sec {
		padding-top: 0;
	}
	.espera {
		display: grid;
		grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
		gap: 32px;
		align-items: center;
		padding: 40px;
		border-radius: 36px;
		background: var(--grad-oscuro);
		color: #faf9f6;
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 40px 80px -40px rgba(36, 33, 45, 0.55);
	}
	.espera-txt {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.espera h2 {
		font:
			800 40px/1.02 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}
	.espera-txt p {
		font-size: 16px;
		line-height: 1.5;
		color: #d3cee2;
	}
	.espera-form {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.espera-mail {
		height: 56px;
		border: 0;
		border-radius: 18px;
		padding: 0 18px;
		background: #faf9f6;
		box-shadow: var(--sh-input);
		font: inherit;
		font-size: 16px;
		color: #24212d;
	}
	.espera-mail:focus-visible {
		outline: 3px solid #b3a3ff;
		outline-offset: 2px;
	}
	.espera-ciudad {
		margin: 0;
		border: 0;
	}
	.espera-ciudad label {
		flex: 1 1 auto;
		padding: 12px 10px;
		font-size: 14px;
		cursor: pointer;
		text-align: center;
	}
	.espera-ciudad label:has(input:focus-visible) {
		outline: 3px solid #b3a3ff;
		outline-offset: 2px;
	}
	.espera-cta {
		margin-top: 4px;
		justify-content: center;
	}
	.espera-error {
		padding: 10px 12px;
		border-radius: 14px;
		background: #ffe1db;
		color: #9a2e1f;
		font-size: 13px;
		font-weight: 700;
		text-align: center;
	}
	.espera-listo {
		padding: 18px 20px;
		border-radius: 20px;
		background: #c8f36b;
		color: #24212d;
		font-size: 16px;
		font-weight: 700;
		line-height: 1.45;
	}

	/* ——— Para lugares ——— */
	.lugares {
		display: grid;
		grid-template-columns: minmax(0, 1.1fr) minmax(0, 1fr);
		gap: 32px;
		align-items: center;
		padding: 40px;
		border-radius: 36px;
		background:
			url(/grain.png), radial-gradient(120% 70% at 85% 0%, #a38fff 0%, #7456f1 45%, #5a3dd8 100%);
		color: #ffffff;
		box-shadow: 0 40px 80px -40px rgba(60, 40, 140, 0.6);
	}
	.lugares-txt {
		display: flex;
		flex-direction: column;
		gap: 14px;
		align-items: flex-start;
	}
	.lugares h2 {
		font:
			800 40px/1.02 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}
	.lugares p {
		font-size: 16px;
		line-height: 1.5;
	}
	.lugares .nota {
		font-size: 13px;
		font-weight: 600;
		color: #e4ddff;
	}
	.cta {
		margin-top: 8px;
		height: 60px;
		padding: 0 22px 0 26px;
		border-radius: 22px;
		font-size: 17px;
	}
	.lugares-foto {
		height: 100%;
		min-height: 300px;
		border-radius: 28px;
		overflow: hidden;
		border: 6px solid #faf9f6;
		transform: rotate(2deg);
		box-shadow: 0 30px 60px -20px rgba(20, 10, 80, 0.7);
	}
	.lugares-foto img {
		width: 100%;
		height: 100%;
		object-fit: cover;
		display: block;
	}

	@media (max-width: 760px) {
		.seccion {
			padding: 48px 16px;
		}
		.lugares,
		.espera {
			grid-template-columns: 1fr;
			padding: 28px 22px;
		}
		.lugares h2,
		.espera h2,
		.titulo-seccion {
			font-size: 34px;
		}
		.lugares-foto {
			height: 240px;
			min-height: 0;
		}
		.paso {
			min-height: 560px;
		}
		.grilla,
		.pasos {
			grid-template-columns: minmax(0, 1fr);
		}
	}
</style>
