<script lang="ts">
	import { page } from '$app/state';
	import Vacio from '#lib/ui/Vacio.svelte';
	import { CATEGORIAS, faltanTexto } from '#lib/plan.ts';
	import { diasParaElegir, partes } from '#lib/fechas.ts';
	import { TINTES } from '#lib/app.ts';
	import type { Plan } from '#lib/tipos.ts';

	/* Explorar (Mockups v5, pantalla 04). Fotos de las categorías: CC0 de static/fotos. */
	let { data } = $props();

	const TILES = [
		{ valor: 'deporte', foto: '/fotos/playa.jpg' },
		{ valor: 'juego', foto: '/fotos/truco.jpg' },
		{ valor: 'previa', foto: '/fotos/previa.jpg' },
		{ valor: 'salida', foto: '/fotos/grupo.jpg' },
		{ valor: 'cita_doble', foto: '/fotos/amigos.jpg' },
		{ valor: 'gastronomia', foto: '/fotos/lugar.jpg' }
	];
	const LARGO = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
	const CORTO = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];

	let busqueda = $state('');
	let filtros = $state(false);

	const diaDe = (f: string) => {
		const p = partes(f);
		return `${p.anio}-${String(p.mes + 1).padStart(2, '0')}-${String(p.dia).padStart(2, '0')}`;
	};
	const dias = $derived(
		diasParaElegir(new Date(), 6).map((d, i) => {
			const p = partes(d.valor + 'T12:00:00-03:00');
			return { ...d, corto: i === 0 ? 'Hoy' : CORTO[p.semana], num: p.dia, largo: i === 0 ? 'Hoy' : `${LARGO[p.semana]} ${p.dia}` };
		})
	);
	const filtrados = $derived.by(() => {
		const q = busqueda.trim().toLowerCase();
		if (!q) return data.planes;
		return data.planes.filter((p) =>
			[p.titulo, p.lugarNombre, p.zona, CATEGORIAS[p.categoria]].join(' ').toLowerCase().includes(q)
		);
	});
	const conPlanes = $derived(new Set(filtrados.map((p) => diaDe(p.empiezaEn))));
	const elegido = $derived(dias.find((d) => d.valor === data.dia) ?? dias[0]);
	const delDia = $derived(filtrados.filter((p) => diaDe(p.empiezaEn) === elegido.valor));
	const recurrente = $derived(filtrados.find((p) => p.recurrente));

	function url(cambios: Record<string, string>) {
		const q = new URLSearchParams(page.url.search);
		for (const [k, v] of Object.entries(cambios)) {
			if (v) q.set(k, v);
			else q.delete(k);
		}
		const s = q.toString();
		return '/app/explorar' + (s ? '?' + s : '');
	}
	const repite = (p: Plan) => {
		const x = partes(p.empiezaEn);
		const plural = ['domingos', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábados'];
		return `Todos los ${plural[x.semana]} · ${x.hora}`;
	};
</script>

<svelte:head><title>Explorar · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<div class="cab">
			<h1>Explorar</h1>
			<div class="buscar">
				<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#7456F1" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" /></svg>
				<input bind:value={busqueda} placeholder="¿Qué tenés ganas de hacer?" aria-label="Buscar planes" />
				<button class="filtrar" onclick={() => (filtros = !filtros)} aria-label="Filtros" aria-expanded={filtros}>
					<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="#FFFFFF" stroke-width="2.4" stroke-linecap="round" aria-hidden="true"><path d="M4 7h10M18 7h2M4 17h4M12 17h8" /><circle cx="16" cy="7" r="2" /><circle cx="10" cy="17" r="2" /></svg>
				</button>
			</div>
			{#if filtros}
				<div class="chips">
					<a class="chip" class:sel={data.verificados} href={url({ verificados: data.verificados ? '' : '1' })} data-sveltekit-noscroll>Solo verificados</a>
				</div>
			{/if}
		</div>

		<div class="tiles">
			{#each TILES as t (t.valor)}
				<a
					class="tile"
					class:on={data.categoria === t.valor}
					href={url({ categoria: data.categoria === t.valor ? '' : t.valor })}
					data-sveltekit-noscroll
					aria-current={data.categoria === t.valor ? 'true' : undefined}
				>
					<img src={t.foto} alt="" loading="lazy" />
					<span>{CATEGORIAS[t.valor]}</span>
				</a>
			{/each}
		</div>

		<div class="dias">
			{#each dias as d (d.valor)}
				<a
					class="dia"
					class:on={d.valor === elegido.valor}
					href={url({ dia: d.valor })}
					data-sveltekit-noscroll
					aria-current={d.valor === elegido.valor ? 'date' : undefined}
				>
					<span class="dc">{d.corto}</span>
					<span class="dn">{d.num}</span>
					<span class="punto" class:hay={conPlanes.has(d.valor)}></span>
				</a>
			{/each}
		</div>

		{#if recurrente}
			<a class="recurrente" href="/app/plan/{recurrente.id}" style:background-color={TINTES[recurrente.categoria]}>
				{#if recurrente.foto}<img src={recurrente.foto} alt="" />{/if}
				<div class="velo"></div>
				<div class="rec-in">
					<span class="frec">{repite(recurrente)}</span>
					<div class="rec-pie">
						<div class="rec-txt">
							<span class="rt">{recurrente.titulo}</span>
							<span class="rs">{[recurrente.lugarNombre || recurrente.zona, recurrente.soloVerificados && 'verificados'].filter(Boolean).join(' · ')}</span>
						</div>
						<span class={recurrente.faltan > 0 ? 'badge-coral rb' : 'badge-completo rb'}>{faltanTexto(recurrente.faltan)}</span>
					</div>
				</div>
			</a>
		{/if}

		<div class="sec">
			<h2>{elegido.largo}</h2>
			<span>{delDia.length} {delDia.length === 1 ? 'plan' : 'planes'}</span>
		</div>

		{#each delDia as g (g.id)}
			{@const x = partes(g.empiezaEn)}
			<a class="fila" href="/app/plan/{g.id}">
				<span class="fimg" style:background-color={TINTES[g.categoria]}>
					{#if g.foto}<img src={g.foto} alt="" loading="lazy" />{/if}
				</span>
				<div class="ftxt">
					<span class="fh">{x.hora} · {(CATEGORIAS[g.categoria] ?? '').toUpperCase()}</span>
					<span class="ft">{g.titulo}</span>
					<span class="fs">{[g.lugarNombre || (g.zona && `Zona ${g.zona}`), g.soloVerificados && 'Verificados'].filter(Boolean).join(' · ')}</span>
				</div>
				<span class="fb" class:coral={g.faltan === 1}>{faltanTexto(g.faltan)}</span>
			</a>
		{:else}
			<Vacio titulo="Ese día no hay nada todavía" texto="Armá un plan y que se sume la gente.">
				<a class="gel gel-lima" href="/app/crear">Armá un plan</a>
			</Vacio>
		{/each}
	</div>
</div>

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px 120px;
		display: flex;
		flex-direction: column;
		gap: 22px;
	}
	.cab {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	h1 {
		font:
			800 40px/1 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}
	.buscar {
		display: flex;
		align-items: center;
		gap: 12px;
		height: 60px;
		padding: 0 8px 0 18px;
		border-radius: 22px;
		background: #ffffff;
		box-shadow:
			inset 0 1.5px 0 #ffffff,
			0 2px 0 #e8e3f4,
			0 16px 30px -12px rgba(80, 60, 160, 0.3);
	}
	.buscar input {
		flex: 1;
		min-width: 0;
		border: 0;
		background: transparent;
		font-size: 16px;
		font-weight: 500;
	}
	.buscar input::placeholder {
		color: #8a8699;
	}
	.filtrar {
		flex: none;
		width: 46px;
		height: 46px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		background: var(--grad-violeta);
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.45),
			inset 0 -5px 0 rgba(40, 20, 140, 0.35),
			0 4px 0 #4a2fc2,
			0 16px 26px -10px rgba(116, 86, 241, 0.75);
	}
	.chips {
		display: flex;
		gap: 8px;
	}
	.chips .chip {
		text-decoration: none;
		padding: 9px 13px;
		border-radius: 13px;
	}
	.tiles {
		display: flex;
		gap: 10px;
		overflow-x: auto;
		margin: -4px -20px;
		padding: 4px 20px 12px;
		scrollbar-width: none;
	}
	.tiles::-webkit-scrollbar {
		display: none;
	}
	.tile {
		position: relative;
		flex: none;
		width: 96px;
		height: 112px;
		border-radius: 24px;
		overflow: hidden;
		box-shadow: 0 12px 22px -10px rgba(36, 33, 45, 0.45);
	}
	.tile.on {
		outline: 3px solid #7456f1;
		outline-offset: 2px;
	}
	.tile img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.tile::after {
		content: '';
		position: absolute;
		inset: 0;
		background: linear-gradient(180deg, rgba(32, 29, 43, 0) 40%, rgba(32, 29, 43, 0.85));
	}
	.tile span {
		position: absolute;
		left: 10px;
		bottom: 10px;
		z-index: 1;
		color: #ffffff;
		font:
			700 15px 'Geist',
			sans-serif;
	}
	.dias {
		display: grid;
		grid-template-columns: repeat(6, 1fr);
		gap: 6px;
	}
	.dia {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 3px;
		padding: 10px 0;
		border-radius: 18px;
		background: linear-gradient(180deg, #ffffff, #f6f3ff);
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 2px 0 #e8e3f4;
		color: #24212d;
		text-decoration: none;
	}
	.dia.on {
		background: linear-gradient(180deg, #9a84ff, #7456f1 55%, #6243e6);
		box-shadow:
			inset 0 2px 1px rgba(255, 255, 255, 0.4),
			inset 0 -3px 0 rgba(40, 20, 140, 0.3),
			0 10px 18px -6px rgba(116, 86, 241, 0.6);
		color: #ffffff;
	}
	.dc {
		font-size: 12px;
		font-weight: 700;
	}
	.dn {
		font:
			700 22px/1.1 'Geist',
			sans-serif;
	}
	.punto {
		width: 5px;
		height: 5px;
		border-radius: 50%;
		background: transparent;
	}
	.punto.hay {
		background: #ff7868;
	}
	.dia.on .punto.hay {
		background: #c8f36b;
	}
	.recurrente {
		position: relative;
		display: block;
		height: 168px;
		border-radius: 28px;
		overflow: hidden;
		box-shadow: 0 18px 32px -14px rgba(36, 33, 45, 0.5);
		text-decoration: none;
	}
	.recurrente img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.velo {
		position: absolute;
		inset: 0;
		background: linear-gradient(90deg, rgba(32, 29, 43, 0.88) 0%, rgba(32, 29, 43, 0.2) 75%);
	}
	.rec-in {
		position: absolute;
		inset: 16px 18px;
		display: flex;
		flex-direction: column;
		justify-content: space-between;
	}
	.frec {
		align-self: flex-start;
		padding: 6px 10px;
		border-radius: 11px;
		background: rgba(255, 255, 255, 0.18);
		backdrop-filter: blur(8px);
		color: #ffffff;
		font-size: 12px;
		font-weight: 800;
	}
	.rec-pie {
		display: flex;
		justify-content: space-between;
		align-items: flex-end;
		gap: 10px;
	}
	.rec-txt {
		display: flex;
		flex-direction: column;
		gap: 4px;
		min-width: 0;
	}
	.rt {
		font:
			700 24px/1.05 'Geist',
			sans-serif;
		color: #ffffff;
	}
	.rs {
		font-size: 13px;
		color: #dad6e6;
	}
	.rb {
		padding: 8px 12px;
		border-radius: 13px;
		font-size: 17px;
	}
	.sec {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
	}
	h2 {
		font:
			700 22px 'Geist',
			sans-serif;
	}
	.sec span {
		font-size: 13px;
		font-weight: 700;
		color: #7456f1;
	}
	.fila {
		display: grid;
		grid-template-columns: 64px minmax(0, 1fr) auto;
		gap: 14px;
		align-items: center;
		padding: 10px 14px 10px 10px;
		border-radius: 24px;
		background: #ffffff;
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 2px 0 #e8e3f4,
			0 12px 24px -12px rgba(80, 60, 160, 0.22);
		color: inherit;
		text-decoration: none;
		margin-top: -8px;
	}
	.fimg {
		width: 64px;
		height: 64px;
		border-radius: 18px;
		overflow: hidden;
	}
	.fimg img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.ftxt {
		display: flex;
		flex-direction: column;
		gap: 4px;
		min-width: 0;
	}
	.fh {
		font-size: 12px;
		font-weight: 800;
		color: #7456f1;
	}
	.ft {
		font-size: 15px;
		font-weight: 700;
		line-height: 1.25;
	}
	.fs {
		font-size: 12px;
		color: #5c586b;
	}
	.fb {
		white-space: nowrap;
		padding: 7px 10px;
		border-radius: 12px;
		background: linear-gradient(180deg, #f8f6ff, #eae4ff);
		font:
			700 15px/1 'Geist',
			sans-serif;
	}
	.fb.coral {
		background: linear-gradient(180deg, #ffa597, #ff7868 50%, #f2604f);
	}
</style>
