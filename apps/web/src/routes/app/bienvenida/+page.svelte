<script lang="ts">
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import Icono from '#lib/ui/Icono.svelte';
	import { distancia } from '#lib/app.ts';
	import { faltanTexto } from '#lib/plan.ts';

	/*
	 * Onboarding 01a–01c (Mockups v5): tres slides con Saltar, puntos y "Siguiente"; la última con
	 * "Empezar" y "Ya tengo cuenta". En 01a va un plan real de ahora (si no hay, sin tarjeta). En 01b
	 * el selector cambia la tarjeta de la foto: solo, de a dos (cita doble) o de a cuatro.
	 */
	let { data } = $props();

	let paso = $state(0);
	let grupo = $state<1 | 2 | 4>(2);
	const GRUPOS = [
		{ n: 1, etiqueta: 'Voy solo', chip: 'Previa', caras: ['C'] },
		{ n: 2, etiqueta: 'Somos 2', chip: 'Cita doble', caras: ['C', 'L'] },
		{ n: 4, etiqueta: 'Somos 4', chip: 'Fútbol 5', caras: ['C', 'L', 'J', 'F'] }
	] as const;
	const elegido = $derived(GRUPOS.find((g) => g.n === grupo) ?? GRUPOS[1]);
	const p = $derived(data.plan);
	const ahora = $derived.by(() => {
		if (!p) return '';
		const min = (new Date(p.empiezaEn).getTime() - Date.now()) / 60000;
		const cuando = min <= 5 ? 'Ahora' : min < 60 ? `En ${Math.round(min)} min` : 'Hoy';
		return [cuando, distancia(p.distanciaM)].filter(Boolean).join(' · ');
	});
</script>

<svelte:head><title>Caigo</title></svelte:head>

{#snippet saltar(claro: boolean)}
	<form method="POST" class="saltar-f">
		<button name="a" value="saltar" class="saltar" class:claro>Saltar</button>
	</form>
{/snippet}

{#snippet puntos(oscuro: boolean)}
	<div class="puntos" class:oscuro aria-hidden="true">
		{#each [0, 1, 2] as i (i)}<span class:on={paso === i}></span>{/each}
	</div>
{/snippet}

{#if paso === 0}
	<div class="pant a">
		<img class="foto" src="/fotos/previa.jpg" alt="" />
		<div class="velo"></div>
		<div class="arriba">
			<span class="marca">Caigo</span>
			{@render saltar(false)}
		</div>
		<div class="abajo">
			{#if p}
				<a class="plan" href="/app/plan/{p.id}">
					<div class="caras">
						{#each p.van.slice(0, 3) as v (v.id)}
							<Avatar nombre={v.nombre} foto={v.foto} tam={32} borde="2.5px solid #FFFFFF" />
						{/each}
					</div>
					<div class="pt">
						<span class="pn">{p.titulo}</span>
						<span class="pc"><span class="vivo"></span>{ahora}</span>
					</div>
					<span class="badge-coral pf">{faltanTexto(p.faltan)}</span>
				</a>
			{/if}
			<div class="textos">
				<h1>Planes a los que les falta&nbsp;gente.</h1>
				<p>Una previa, un partido, una mesa de truco. Pasando ahora, cerca&nbsp;tuyo.</p>
			</div>
			<div class="pie">
				{@render puntos(true)}
				<button class="gel gel-lima sig" onclick={() => (paso = 1)}
					>Siguiente<Icono nombre="flecha" tam={22} trazo={2.6} /></button
				>
			</div>
		</div>
	</div>
{:else if paso === 1}
	<div class="pant b">
		<div class="foto-b">
			<img class="foto" src="/fotos/amigos.jpg" alt="" />
			<div class="velo-b"></div>
			<div class="arriba fin">{@render saltar(true)}</div>
			<div class="chip-b">
				<div class="caras">
					{#each elegido.caras as c (c)}
						<Avatar
							nombre={c === 'C' ? 'Cami' : c === 'L' ? 'Lu' : c}
							tam={38}
							borde="2px solid #FFFFFF"
						/>
					{/each}
				</div>
				<span class="mas-b"><Icono nombre="mas" tam={16} color="#C8F36B" trazo={4} /></span>
				<span class="chip-t">{elegido.chip}</span>
			</div>
		</div>
		<div class="hoja">
			<div class="seg-b" role="group" aria-label="Con quién vas">
				{#each GRUPOS as g (g.n)}
					<button aria-pressed={grupo === g.n} onclick={() => (grupo = g.n)}>{g.etiqueta}</button>
				{/each}
			</div>
			<h1>Sumate solo o con&nbsp;amigos.</h1>
			<p>
				Entrás con tu gente en un toque. Así salen las citas dobles y los partidos grupo contra
				grupo.
			</p>
			<div class="pie">
				{@render puntos(false)}
				<button class="gel gel-lima sig" onclick={() => (paso = 2)}
					>Siguiente<Icono nombre="flecha" tam={22} trazo={2.6} /></button
				>
			</div>
		</div>
	</div>
{:else}
	<div class="pant c">
		<div class="collage">
			<div class="marco"><img src="/fotos/grupo.jpg" alt="" /></div>
			<img class="isotipo" src="/caigo-icon.png" alt="" />
			<span class="ya">Ya somos 6</span>
		</div>
		<div class="textos">
			<h1>Lo mejor pasa&nbsp;afuera.</h1>
			<p>Compartís un plan y conocés gente. Sin swipes, sin chats&nbsp;eternos.</p>
		</div>
		<form method="POST" class="pie-c">
			<button class="gel gel-lima empezar" name="a" value="empezar"
				><Icono nombre="mas" tam={18} trazo={4} />Empezar</button
			>
			<button class="cuenta" name="a" value="cuenta">Ya tengo cuenta</button>
		</form>
	</div>
{/if}

<style>
	.pant {
		position: absolute;
		inset: 0;
		z-index: 10;
		display: flex;
		flex-direction: column;
		overflow: hidden;
	}
	.foto {
		position: absolute;
		top: 0;
		left: 0;
		width: 100%;
		object-fit: cover;
	}
	.arriba {
		position: absolute;
		top: calc(16px + env(safe-area-inset-top));
		left: 24px;
		right: 24px;
		display: flex;
		justify-content: space-between;
		align-items: center;
		z-index: 2;
	}
	.arriba.fin {
		justify-content: flex-end;
	}
	.marca {
		font:
			700 30px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: var(--blanco-calido);
	}
	.saltar {
		padding: 8px 14px;
		border-radius: 14px;
		background: rgba(255, 255, 255, 0.18);
		backdrop-filter: blur(10px);
		color: #ffffff;
		font-size: 14px;
		font-weight: 700;
	}
	.saltar.claro {
		background: rgba(255, 255, 255, 0.22);
	}
	.puntos {
		display: flex;
		gap: 6px;
	}
	.puntos span {
		width: 8px;
		height: 8px;
		border-radius: 4px;
		background: #d9d3ee;
		transition: width 0.2s;
	}
	.puntos.oscuro span {
		background: #4a4460;
	}
	.puntos span.on {
		width: 26px;
		background: var(--violeta);
	}
	.puntos.oscuro span.on {
		background: var(--lima);
	}
	.pie {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.sig {
		gap: 10px;
		height: 58px;
		padding: 0 22px 0 26px;
		border-radius: 22px;
		font-size: 17px;
		color: var(--grafito);
	}
	h1 {
		font:
			800 40px/1 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}

	/* 01a */
	.a {
		background: var(--grafito);
	}
	.a .foto {
		height: 66%;
		object-position: 40% 35%;
	}
	.velo {
		position: absolute;
		inset: 0;
		background: linear-gradient(
			180deg,
			rgba(32, 29, 43, 0.55) 0%,
			rgba(32, 29, 43, 0) 18%,
			rgba(32, 29, 43, 0) 46%,
			rgba(32, 29, 43, 0.85) 60%,
			#201d2b 67%
		);
	}
	.abajo {
		position: relative;
		margin-top: auto;
		padding: 0 20px calc(38px + env(safe-area-inset-bottom));
		display: flex;
		flex-direction: column;
		gap: 22px;
	}
	.plan {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 12px 12px 20px;
		border-radius: 24px;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 243, 255, 0.94));
		box-shadow:
			inset 0 1.5px 0 #ffffff,
			0 22px 40px -14px rgba(0, 0, 0, 0.6);
		color: var(--carbon);
		text-decoration: none;
	}
	.caras {
		flex: none;
		display: flex;
		padding-left: 9px;
	}
	.caras :global(.av) {
		margin-left: -9px;
		font-weight: 800;
	}
	.pt {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.pn {
		font-size: 14px;
		font-weight: 800;
		white-space: nowrap;
		overflow: hidden;
		text-overflow: ellipsis;
	}
	.pc {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 12px;
		font-weight: 600;
		color: var(--texto-2);
	}
	.vivo {
		width: 7px;
		height: 7px;
		border-radius: 50%;
		background: var(--coral);
	}
	.pf {
		flex: none;
		padding: 9px 13px;
		border-radius: 14px;
		font-size: 17px;
	}
	.textos {
		display: flex;
		flex-direction: column;
		gap: 12px;
		padding: 0 6px;
	}
	.a h1 {
		color: var(--blanco-calido);
	}
	.a p {
		font-size: 15px;
		line-height: 1.5;
		color: #c9c4da;
		text-wrap: pretty;
	}
	.a .pie {
		padding: 0 6px;
	}

	/* 01b */
	.b {
		background: url(/grain.png), var(--blanco-calido);
	}
	.foto-b {
		position: relative;
		flex: 1 1 auto;
		min-height: 260px;
		margin-bottom: -50px;
	}
	.b .foto {
		height: 100%;
	}
	.velo-b {
		position: absolute;
		top: 0;
		left: 0;
		right: 0;
		height: 140px;
		background: linear-gradient(180deg, rgba(32, 29, 43, 0.5), rgba(32, 29, 43, 0));
	}
	.chip-b {
		position: absolute;
		left: 24px;
		bottom: 90px;
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
	.chip-b .caras {
		padding-left: 8px;
	}
	.chip-b .caras :global(.av) {
		margin-left: -8px;
	}
	.mas-b {
		width: 34px;
		height: 34px;
		border-radius: 12px;
		display: grid;
		place-items: center;
		background: var(--grad-violeta);
		box-shadow: var(--sh-violeta);
	}
	.chip-t {
		font:
			700 17px 'Geist',
			sans-serif;
	}
	.hoja {
		position: relative;
		flex: none;
		border-radius: 36px 36px 0 0;
		background: url(/grain.png), var(--blanco-calido);
		padding: 30px 26px calc(40px + env(safe-area-inset-bottom));
		display: flex;
		flex-direction: column;
		gap: 18px;
	}
	.seg-b {
		display: flex;
		padding: 5px;
		border-radius: 20px;
		background: linear-gradient(180deg, #ede8ff, #f6f3ff);
		box-shadow: inset 0 2px 4px rgba(116, 86, 241, 0.15);
	}
	.seg-b button {
		flex: 1;
		padding: 12px 0;
		border-radius: 16px;
		font-size: 15px;
		font-weight: 600;
		color: var(--texto-2);
	}
	.seg-b button[aria-pressed='true'] {
		background: var(--grad-violeta);
		box-shadow: var(--sh-violeta);
		color: #ffffff;
		font-weight: 800;
	}
	.b h1 {
		text-wrap: pretty;
	}
	.b p {
		font-size: 16px;
		line-height: 1.5;
		color: var(--texto-2);
		text-wrap: pretty;
	}
	.b .pie {
		margin-top: 4px;
	}

	/* 01c */
	.c {
		background:
			url(/grain.png), radial-gradient(120% 70% at 85% 0%, #a38fff 0%, #7456f1 45%, #5a3dd8 100%);
		padding: calc(60px + env(safe-area-inset-top)) 26px calc(34px + env(safe-area-inset-bottom));
		gap: 22px;
	}
	.collage {
		position: relative;
		flex: 1 1 auto;
		min-height: 0;
		display: flex;
		align-items: flex-start;
	}
	.marco {
		width: min(300px, 78%);
		height: min(360px, 100%);
		margin-left: 10px;
		border-radius: 34px;
		overflow: hidden;
		transform: rotate(-4deg);
		box-shadow: 0 30px 60px -20px rgba(20, 10, 80, 0.7);
		border: 6px solid var(--blanco-calido);
	}
	.marco img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.isotipo {
		position: absolute;
		right: -4px;
		bottom: -20px;
		width: 130px;
		height: 130px;
		transform: rotate(12deg);
		filter: drop-shadow(0 22px 22px rgba(20, 10, 80, 0.5));
	}
	.ya {
		position: absolute;
		top: 18px;
		right: 4px;
		padding: 8px 12px;
		border-radius: 14px;
		background: var(--grad-lima);
		box-shadow: var(--sh-lima);
		font:
			700 15px/1 'Geist',
			sans-serif;
		transform: rotate(6deg);
		white-space: nowrap;
	}
	.c .textos {
		padding: 0;
	}
	.c h1 {
		font-size: 46px;
		line-height: 0.98;
		letter-spacing: -1.8px;
		color: #ffffff;
	}
	.c p {
		font-size: 16px;
		line-height: 1.5;
		color: #ffffff;
		text-wrap: pretty;
	}
	.pie-c {
		display: flex;
		flex-direction: column;
		gap: 14px;
		align-items: center;
	}
	.empezar {
		align-self: stretch;
		height: 62px;
		border-radius: 22px;
		gap: 10px;
		font-size: 18px;
	}
	.cuenta {
		font-size: 15px;
		font-weight: 700;
		color: #ffffff;
	}
</style>
