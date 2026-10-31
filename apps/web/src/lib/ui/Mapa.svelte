<script lang="ts">
	import { onMount } from 'svelte';
	import { PUBLIC_MAPA_ESTILO } from '$app/env/public';
	import 'maplibre-gl/dist/maplibre-gl.css';

	/**
	 * Mapa para marcar el lugar (MapLibre GL). El estilo sale de PUBLIC_MAPA_ESTILO (en producción
	 * lo sirve el VPS); vacío, usa las teselas raster estándar de OpenStreetMap.
	 */
	let {
		lat = $bindable(0),
		lng = $bindable(0),
		centro
	}: { lat?: number; lng?: number; centro: [number, number] } = $props();

	let caja: HTMLDivElement;
	let mapa: import('maplibre-gl').Map | undefined;
	let marca: import('maplibre-gl').Marker | undefined;
	let lib: typeof import('maplibre-gl') | undefined;

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

	function poner(lo: number, la: number) {
		if (!mapa || !lib) return;
		if (!marca) {
			const el = document.createElement('div');
			el.className = 'pin-caigo';
			marca = new lib.Marker({ element: el, draggable: true, anchor: 'bottom' })
				.setLngLat([lo, la])
				.addTo(mapa);
			marca.on('dragend', () => {
				const p = marca!.getLngLat();
				lat = +p.lat.toFixed(6);
				lng = +p.lng.toFixed(6);
			});
		} else marca.setLngLat([lo, la]);
		lat = +la.toFixed(6);
		lng = +lo.toFixed(6);
	}

	onMount(() => {
		let vivo = true;
		import('maplibre-gl').then((m) => {
			if (!vivo) return;
			lib = m;
			mapa = new m.Map({
				container: caja,
				style: PUBLIC_MAPA_ESTILO || OSM,
				center: lat || lng ? [lng, lat] : [centro[1], centro[0]],
				zoom: lat || lng ? 16 : 13,
				attributionControl: { compact: true }
			});
			mapa.addControl(new m.NavigationControl({ showCompass: false }), 'top-right');
			mapa.on('click', (e) => poner(e.lngLat.lng, e.lngLat.lat));
			if (lat || lng) mapa.on('load', () => poner(lng, lat));
		});
		return () => {
			vivo = false;
			mapa?.remove();
		};
	});

	// Al cambiar de ciudad, el mapa va al centro nuevo (si todavía no se marcó nada).
	$effect(() => {
		const [la, lo] = centro;
		if (mapa && !marca) mapa.flyTo({ center: [lo, la], zoom: 13 });
	});
</script>

<div class="mapa" bind:this={caja} role="application" aria-label="Mapa: tocá dónde está el lugar"></div>

<style>
	.mapa {
		height: 300px;
		border-radius: 22px;
		overflow: hidden;
		background: #f5f2fc;
		box-shadow: var(--sh-input);
	}
	:global(.pin-caigo) {
		width: 30px;
		height: 30px;
		border-radius: 50% 50% 50% 0;
		transform: rotate(-45deg);
		background: linear-gradient(180deg, #ffb2a6 0%, #ff7868 55%, #f05e4c 100%);
		box-shadow:
			inset 0 2px 1px rgba(255, 255, 255, 0.6),
			0 3px 0 #d04a3a;
		border: 3px solid #ffffff;
		cursor: grab;
	}
</style>
