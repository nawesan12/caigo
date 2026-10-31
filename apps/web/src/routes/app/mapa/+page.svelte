<script lang="ts">
	import { onMount } from 'svelte';
	import MapaPlanes from '#lib/app-ui/MapaPlanes.svelte';
	import { CATEGORIAS, faltanTexto } from '#lib/plan.ts';
	import { cuandoCorto } from '#lib/fechas.ts';
	import { distancia, TINTES } from '#lib/app.ts';
	import { pedirUbicacion } from '#lib/app-ui/ubicacion.ts';

	/* Ahora · mapa (Mockups v5, pantalla 03), con MapLibre y teselas de OpenStreetMap. */
	let { data } = $props();

	const FILTROS = [
		{ valor: '', etiqueta: 'Próximas 3 h' },
		{ valor: 'deporte', etiqueta: 'Deporte' },
		{ valor: 'previa', etiqueta: 'Previa' },
		{ valor: 'juego', etiqueta: 'Juego' }
	];
	let busqueda = $state('');
	let elegido = $state('');
	let centrar = $state(0);

	const visibles = $derived.by(() => {
		const q = busqueda.trim().toLowerCase();
		if (!q) return data.planes;
		return data.planes.filter((p) =>
			[p.titulo, p.lugarNombre, p.zona].join(' ').toLowerCase().includes(q)
		);
	});
	const plan = $derived(visibles.find((p) => p.id === elegido) ?? visibles[0]);
	const q = (c: string) => (c ? `?categoria=${c}` : '');

	onMount(() => {
		if (!data.ubic) pedirUbicacion();
	});
</script>

<svelte:head><title>Mapa · Caigo</title></svelte:head>

<MapaPlanes
	planes={visibles}
	centro={[data.centro.lat, data.centro.lng]}
	yo={data.centro.real}
	elegido={plan?.id ?? ''}
	bind:centrar
	onelegir={(id: string) => (elegido = id)}
/>

<div class="arriba">
	<div class="fila">
		<label class="buscar">
			<svg
				width="18"
				height="18"
				viewBox="0 0 24 24"
				fill="none"
				stroke="#24212D"
				stroke-width="2.2"
				stroke-linecap="round"
				aria-hidden="true"><circle cx="11" cy="11" r="7" /><path d="M20 20l-4-4" /></svg
			>
			<input
				bind:value={busqueda}
				placeholder="Buscá un plan o un lugar"
				aria-label="Buscar un plan o un lugar"
			/>
		</label>
		<nav class="seg-mapa" aria-label="Vista">
			<a href="/app{q(data.categoria)}">Lista</a>
			<a href="/app/mapa{q(data.categoria)}" aria-current="page">Mapa</a>
		</nav>
	</div>
	<div class="filtros">
		{#each FILTROS as f (f.valor)}
			<a class="filtro" class:on={data.categoria === f.valor} href="/app/mapa{q(f.valor)}"
				>{f.etiqueta}</a
			>
		{/each}
	</div>
</div>

<button
	class="ubicarme"
	aria-label="Ir a mi ubicación"
	onclick={async () => {
		await pedirUbicacion();
		centrar++;
	}}
	><svg
		width="20"
		height="20"
		viewBox="0 0 24 24"
		fill="none"
		stroke="#7456F1"
		stroke-width="2.2"
		stroke-linecap="round"
		stroke-linejoin="round"
		aria-hidden="true"><path d="M3 11l18-8-8 18-2-8z" /></svg
	></button
>

{#if plan}
	<a class="flotante" href="/app/plan/{plan.id}">
		<span class="fimg" style:background-color={TINTES[plan.categoria]}>
			{#if plan.foto}<img src={plan.foto} alt="" />{/if}
		</span>
		<div class="ftxt">
			<div class="fchips">
				<span class="lav">{CATEGORIAS[plan.categoria]}</span>
				<span class="osc">{cuandoCorto(plan.empiezaEn)}</span>
			</div>
			<span class="ft">{plan.titulo}</span>
			<span class="fs"
				>{[plan.lugarNombre || plan.zona, distancia(plan.distanciaM)]
					.filter(Boolean)
					.join(' · ')}</span
			>
			<div class="fpie">
				<span class={plan.faltan > 0 ? 'badge-coral fb' : 'badge-completo fb'}
					>{faltanTexto(plan.faltan)}</span
				>
				<span class="mas" aria-hidden="true"
					><svg width="18" height="18" viewBox="0 0 24 24"
						><path
							d="M12 4v16M4 12h16"
							stroke="#24212D"
							stroke-width="4"
							stroke-linecap="round"
						/></svg
					></span
				>
			</div>
		</div>
	</a>
{:else}
	<div class="flotante vacio">
		<span class="ft">No hay planes cerca. ¿Armás uno?</span>
		<a class="gel gel-lima armar" href="/app/crear">Armá un plan</a>
	</div>
{/if}

<style>
	.arriba {
		position: absolute;
		top: calc(16px + env(safe-area-inset-top));
		left: 16px;
		right: 16px;
		display: flex;
		flex-direction: column;
		gap: 10px;
		z-index: 3;
	}
	.fila {
		display: flex;
		gap: 8px;
	}
	.buscar {
		flex: 1;
		min-width: 0;
		height: 50px;
		border-radius: 18px;
		background: #ffffff;
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 0 16px;
		box-shadow: 0 8px 24px rgba(36, 33, 45, 0.1);
	}
	.buscar input {
		flex: 1;
		min-width: 0;
		border: 0;
		background: transparent;
		font-size: 15px;
	}
	.buscar input::placeholder {
		color: #5c586b;
	}
	.seg-mapa {
		display: flex;
		padding: 4px;
		border-radius: 16px;
		background: #ffffff;
		box-shadow: 0 8px 24px rgba(36, 33, 45, 0.1);
	}
	.seg-mapa a {
		padding: 11px 12px;
		border-radius: 12px;
		font-size: 14px;
		font-weight: 600;
		color: #5c586b;
		text-decoration: none;
	}
	.seg-mapa a[aria-current='page'] {
		background: url(/grain.png), radial-gradient(120% 90% at 0% 0%, #3d3756 0%, #24212d 62%);
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
		color: #faf9f6;
		font-weight: 700;
	}
	.filtros {
		display: flex;
		gap: 8px;
		overflow-x: auto;
		scrollbar-width: none;
	}
	.filtro {
		flex: none;
		padding: 8px 13px;
		border-radius: 13px;
		background: #ffffff;
		font-size: 13px;
		font-weight: 600;
		color: #24212d;
		text-decoration: none;
	}
	.filtro.on {
		background: var(--grad-violeta);
		box-shadow: var(--sh-violeta);
		color: #ffffff;
		font-weight: 700;
	}
	.ubicarme {
		position: absolute;
		right: 16px;
		bottom: calc(262px + env(safe-area-inset-bottom));
		width: 46px;
		height: 46px;
		border-radius: 16px;
		background: #ffffff;
		display: grid;
		place-items: center;
		box-shadow: 0 8px 20px rgba(36, 33, 45, 0.12);
		z-index: 3;
	}
	.flotante {
		position: absolute;
		left: 12px;
		right: 12px;
		bottom: calc(100px + env(safe-area-inset-bottom));
		padding: 12px;
		border-radius: 26px;
		background: #ffffff;
		display: flex;
		gap: 12px;
		align-items: center;
		box-shadow: 0 16px 40px rgba(36, 33, 45, 0.18);
		color: inherit;
		text-decoration: none;
		z-index: 3;
	}
	.flotante.vacio {
		flex-direction: column;
		align-items: stretch;
		text-align: center;
	}
	.armar {
		height: 50px;
		border-radius: 18px;
		font-size: 15px;
	}
	.fimg {
		position: relative;
		flex: none;
		width: 96px;
		height: 120px;
		border-radius: 18px;
		overflow: hidden;
	}
	.fimg img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.ftxt {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 7px;
		min-width: 0;
	}
	.fchips {
		display: flex;
		gap: 6px;
	}
	.fchips span {
		padding: 4px 8px;
		border-radius: 9px;
		font-size: 11px;
		font-weight: 700;
	}
	.lav {
		background: #f0ecff;
	}
	.osc {
		background: #24212d;
		color: #faf9f6;
	}
	.ft {
		font:
			700 18px/1.15 'Geist',
			sans-serif;
	}
	.fs {
		font-size: 12px;
		color: #5c586b;
	}
	.fpie {
		display: flex;
		align-items: center;
		justify-content: space-between;
	}
	.fb {
		padding: 6px 10px;
		border-radius: 11px;
		font-size: 16px;
	}
	.mas {
		width: 40px;
		height: 40px;
		border-radius: 14px;
		display: grid;
		place-items: center;
		background: var(--grad-lima);
		box-shadow: var(--sh-lima);
	}
</style>
