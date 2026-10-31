<script lang="ts">
	import { onMount } from 'svelte';
	import { PUBLIC_MAPA_ESTILO } from '$app/env/public';
	import 'maplibre-gl/dist/maplibre-gl.css';
	import { faltanTexto } from '#lib/plan.ts';
	import { ESTILO_OSCURO, seguirTema } from './tema-mapa.ts';
	import type { Plan } from '#lib/tipos.ts';

	/**
	 * El mapa de "Ahora" con los pines del handoff: la burbuja con el contador (coral, lima si está
	 * completo, violeta la elegida) y el punto violeta de quien mira. En modo oscuro va con las
	 * teselas oscuras y cambia en vivo si cambia el tema (los pines son del DOM: quedan).
	 */
	let {
		planes,
		centro,
		yo,
		elegido = '',
		centrar = $bindable(0),
		onelegir
	}: {
		planes: Plan[];
		centro: [number, number];
		yo: boolean;
		elegido?: string;
		centrar?: number;
		onelegir: (id: string) => void;
	} = $props();

	let caja: HTMLDivElement;
	let mapa: import('maplibre-gl').Map | undefined;
	let lib: typeof import('maplibre-gl') | undefined;
	let marcas: import('maplibre-gl').Marker[] = [];
	let oscuro = $state(false);

	const OSM = {
		version: 8 as const,
		sources: {
			osm: {
				type: 'raster' as const,
				tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
				tileSize: 256,
				maxzoom: 19,
				attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
			}
		},
		layers: [{ id: 'osm', type: 'raster' as const, source: 'osm' }]
	};

	function pintar() {
		if (!mapa || !lib) return;
		for (const m of marcas) m.remove();
		marcas = [];
		for (const p of planes) {
			if (p.lat == null || p.lng == null) continue;
			const el = document.createElement('button');
			el.type = 'button';
			el.className = 'pin-plan' + (p.id === elegido ? ' sel' : p.faltan <= 0 ? ' completo' : '');
			el.innerHTML = `<span></span><i></i>`;
			el.firstElementChild!.textContent = faltanTexto(p.faltan);
			el.setAttribute('aria-label', `${p.titulo}: ${faltanTexto(p.faltan)}`);
			el.addEventListener('click', (e) => {
				e.stopPropagation();
				onelegir(p.id);
			});
			marcas.push(
				new lib.Marker({ element: el, anchor: 'bottom' }).setLngLat([p.lng, p.lat]).addTo(mapa)
			);
		}
		if (yo) {
			const el = document.createElement('div');
			el.className = 'pin-yo';
			el.innerHTML = '<span></span>';
			marcas.push(new lib.Marker({ element: el }).setLngLat([centro[1], centro[0]]).addTo(mapa));
		}
	}

	const estilo = (o: boolean) => (o ? ESTILO_OSCURO : PUBLIC_MAPA_ESTILO || OSM);

	onMount(() => {
		let vivo = true;
		const [inicial, dejar] = seguirTema(caja, (o) => {
			oscuro = o;
			mapa?.setStyle(estilo(o));
		});
		oscuro = inicial;
		import('maplibre-gl').then((m) => {
			if (!vivo) return;
			lib = m;
			mapa = new m.Map({
				container: caja,
				style: estilo(oscuro),
				center: [centro[1], centro[0]],
				zoom: 13,
				attributionControl: { compact: true }
			});
			mapa.on('load', pintar);
		});
		return () => {
			vivo = false;
			dejar();
			mapa?.remove();
		};
	});

	$effect(() => {
		void planes;
		void elegido;
		pintar();
	});
	$effect(() => {
		if (centrar && mapa) mapa.flyTo({ center: [centro[1], centro[0]], zoom: 14 });
	});
</script>

<div class="mapa" class:oscuro bind:this={caja}></div>

<style>
	.mapa {
		position: absolute;
		inset: 0;
		background: #f2efe8;
	}
	.mapa.oscuro {
		background: #201d2b;
	}
	.mapa :global(.maplibregl-ctrl-bottom-right) {
		bottom: 96px;
	}
	:global(.pin-plan) {
		display: flex;
		flex-direction: column;
		align-items: center;
		background: none;
		border: 0;
		padding: 0;
		cursor: pointer;
	}
	:global(.pin-plan span) {
		padding: 7px 11px;
		border-radius: 13px;
		background: linear-gradient(180deg, #ffa597, #ff7868 50%, #f2604f);
		color: var(--carbon);
		font:
			700 15px/1 'Geist',
			sans-serif;
		white-space: nowrap;
		box-shadow: 0 6px 14px rgba(36, 33, 45, 0.18);
		border: 2.5px solid #ffffff;
	}
	:global(.pin-plan i) {
		width: 10px;
		height: 10px;
		margin-top: -6px;
		transform: rotate(45deg);
		background: #f2604f;
		border-right: 2.5px solid #ffffff;
		border-bottom: 2.5px solid #ffffff;
	}
	:global(.pin-plan.completo span),
	:global(.pin-plan.completo i) {
		background: linear-gradient(180deg, #e2ff9c, #c8f36b 50%, #b2e04f);
	}
	:global(.pin-plan.sel span),
	:global(.pin-plan.sel i) {
		background: linear-gradient(180deg, #9a84ff, #7456f1 55%, #6243e6);
		color: #ffffff;
	}
	:global(.pin-plan.sel) {
		z-index: 2;
	}
	:global(.pin-yo) {
		width: 56px;
		height: 56px;
		border-radius: 50%;
		background: rgba(116, 86, 241, 0.18);
		display: grid;
		place-items: center;
	}
	:global(.pin-yo span) {
		width: 18px;
		height: 18px;
		border-radius: 50%;
		background: #7456f1;
		border: 3px solid #ffffff;
		box-shadow: 0 2px 6px rgba(36, 33, 45, 0.3);
	}
</style>
