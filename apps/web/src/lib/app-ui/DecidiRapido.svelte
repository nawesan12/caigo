<script lang="ts" module>
	/** El mazo que se dejó para «Ver más» (sessionStorage): al volver a Ahora, sigue donde estaba. */
	export const MAZO = 'caigo-mazo';
	export type MazoGuardado = { ids: string[]; i: number; sumados: number };

	export function leerMazo(): MazoGuardado | null {
		try {
			const m = JSON.parse(sessionStorage.getItem(MAZO) ?? 'null') as MazoGuardado | null;
			sessionStorage.removeItem(MAZO);
			return m && Array.isArray(m.ids) ? m : null;
		} catch {
			return null;
		}
	}
</script>

<script lang="ts">
	import { tick } from 'svelte';
	import { goto } from '$app/navigation';
	import TarjetaPlan from '#lib/ui/TarjetaPlan.svelte';
	import Icono from '#lib/ui/Icono.svelte';
	import Deslizable from './Deslizable.svelte';
	import { CATEGORIAS, fotoDelPlan, lineaConfianza } from '#lib/plan.ts';
	import { cuandoCorto } from '#lib/fechas.ts';
	import { distancia, TINTES } from '#lib/app.ts';
	import type { GestosLista } from './gestos-lista.svelte.ts';
	import type { Plan } from '#lib/tipos.ts';

	/*
	 * «Decidí rápido» (A4 del informe del 18/11): opcional, sólo con 8 planes o más que se suman con un
	 * toque (paraDecidir). Un plan por vez con «Paso», «Ver más» y «Sumarme» abajo (y los mismos
	 * gestos). Sin festejos en el medio: la carta muestra «Adentro» y el resumen va al final. «Paso»
	 * sólo pasa (ocultar por hoy, con «Deshacer», es el gesto de la lista). «Ver más» guarda el mazo
	 * y, al volver, sigue donde estaba.
	 */
	let {
		abierto = $bindable(false),
		planes,
		g,
		desde = 0,
		yaSumados = 0
	}: {
		abierto?: boolean;
		planes: Plan[];
		g: GestosLista;
		/** Para retomar un mazo guardado. */
		desde?: number;
		yaSumados?: number;
	} = $props();

	let i = $state(0);
	let sumados = $state(0);
	let ocupado = $state(false);
	let adentro = $state(false);
	let caja = $state<HTMLElement>();
	let titulo = $state<HTMLElement>();
	let antes: HTMLElement | null = null;
	const actual = $derived(planes[i]);
	const fin = $derived(i >= planes.length);

	$effect(() => {
		if (abierto) {
			i = desde;
			sumados = yaSumados;
			antes = document.activeElement as HTMLElement | null;
			tick().then(() => titulo?.focus());
			return () => antes?.focus?.();
		}
	});

	async function sumarme() {
		if (!actual || ocupado) return;
		ocupado = true;
		const r = await g.caigo(actual, false);
		if (r) {
			sumados++;
			adentro = true;
			navigator.vibrate?.(15);
			await new Promise((ok) => setTimeout(ok, 550));
			adentro = false;
		}
		ocupado = false;
		i++;
	}
	function paso() {
		if (actual && !ocupado) i++;
	}
	function verMas() {
		if (!actual) return;
		try {
			sessionStorage.setItem(
				MAZO,
				JSON.stringify({ ids: planes.map((p) => p.id), i, sumados } satisfies MazoGuardado)
			);
		} catch {
			/* sin almacenamiento: vuelve a la lista */
		}
		goto(`/app/plan/${actual.id}`);
	}

	/** El foco queda adentro del mazo (es un diálogo modal). */
	function tecla(e: KeyboardEvent) {
		if (!abierto) return;
		if (e.key === 'Escape') {
			abierto = false;
			return;
		}
		if (e.key !== 'Tab' || !caja) return;
		const f = [...caja.querySelectorAll<HTMLElement>('button:not([disabled]), a[href]')];
		if (!f.length) return;
		const [primero, ultimo] = [f[0], f[f.length - 1]];
		if (e.shiftKey && document.activeElement === primero) {
			e.preventDefault();
			ultimo.focus();
		} else if (!e.shiftKey && document.activeElement === ultimo) {
			e.preventDefault();
			primero.focus();
		}
	}
	const donde = (p: Plan) =>
		[p.lugarNombre || (p.zona ? `Zona ${p.zona}` : p.ciudad), distancia(p.distanciaM)]
			.filter(Boolean)
			.join(' · ');
</script>

<svelte:window onkeydown={tecla} />

{#if abierto}
	<div class="mazo" role="dialog" aria-modal="true" aria-labelledby="decidi-t" bind:this={caja}>
		<div class="arriba">
			<h2 id="decidi-t" tabindex="-1" bind:this={titulo}>Decidí rápido</h2>
			{#if !fin}<span class="cuenta">{i + 1} de {planes.length}</span>{/if}
			<button class="cerrar" type="button" aria-label="Cerrar" onclick={() => (abierto = false)}
				><Icono nombre="cerrar" tam={20} trazo={2.6} /></button
			>
		</div>

		{#if fin}
			<div class="listo" role="status">
				<h3>{sumados ? `Listo: te sumaste a\u00a0${sumados}.` : 'Listo.'}</h3>
				<button class="gel gel-lima grande" type="button" onclick={() => (abierto = false)}
					>Volver a la lista</button
				>
			</div>
		{:else if actual}
			{#key actual.id}
				<div class="carta">
					<Deslizable titulo={actual.titulo} onderecha={sumarme} onizquierda={paso}>
						<TarjetaPlan
							foto={fotoDelPlan(actual) || undefined}
							tinte={TINTES[actual.categoria] ?? '#5B43C9'}
							categoria={CATEGORIAS[actual.categoria] ?? actual.categoria}
							cuando={cuandoCorto(actual.empiezaEn)}
							titulo={actual.titulo}
							donde={donde(actual)}
							faltan={actual.faltan}
							somos={actual.somos}
							total={actual.total}
							personas={actual.van.map((p) => ({ nombre: p.nombre, foto: p.foto }))}
							confianza={lineaConfianza(actual)}
							lugarAliado={!!actual.lugar}
							alta
						/>
					</Deslizable>
					{#if adentro}
						<span class="adentro" role="status"
							><Icono nombre="check" tam={22} color="#24212D" trazo={3.4} />Adentro</span
						>
					{/if}
				</div>
			{/key}
			<div class="botones">
				<button class="gel gel-blanco" type="button" onclick={paso} disabled={ocupado}>Paso</button>
				<button class="gel gel-blanco" type="button" onclick={verMas} disabled={ocupado}
					>Ver más</button
				>
				<button class="gel gel-lima" type="button" onclick={sumarme} disabled={ocupado}
					>Sumarme</button
				>
			</div>
		{/if}
	</div>
{/if}

<style>
	.mazo {
		position: absolute;
		inset: 0;
		z-index: 45;
		display: flex;
		flex-direction: column;
		gap: 18px;
		padding: calc(20px + env(safe-area-inset-top)) 20px calc(28px + env(safe-area-inset-bottom));
		background: var(--c-fondo);
		animation: aparece 0.2s ease both;
	}
	.arriba {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	h2 {
		font:
			800 28px/1 'Geist',
			sans-serif;
		letter-spacing: -0.8px;
	}
	.cuenta {
		font-size: 14px;
		font-weight: 600;
		color: var(--texto-2);
	}
	.cerrar {
		margin-left: auto;
		flex: none;
		width: 44px;
		height: 44px;
		display: grid;
		place-items: center;
		border-radius: 50%;
		background: var(--c-gel-blanco);
		box-shadow: var(--sh-gel-blanco);
		color: var(--carbon);
	}
	.carta {
		position: relative;
		flex: 1;
		display: flex;
		flex-direction: column;
		justify-content: center;
	}
	.adentro {
		position: absolute;
		top: 50%;
		left: 50%;
		transform: translate(-50%, -50%);
		display: inline-flex;
		align-items: center;
		gap: 8px;
		padding: 14px 22px;
		border-radius: 20px;
		background: var(--grad-lima);
		box-shadow: var(--sh-gel-lima, 0 12px 26px -10px rgba(36, 33, 45, 0.45));
		color: var(--carbon);
		font-size: 20px;
		font-weight: 800;
		animation: aparece 0.18s ease both;
		pointer-events: none;
	}
	h2:focus {
		outline: none;
	}
	.botones {
		display: grid;
		grid-template-columns: 1fr 1fr 1.3fr;
		gap: 10px;
	}
	.botones .gel {
		padding: 16px 8px;
		border-radius: 20px;
		font-size: 16px;
	}
	.listo {
		flex: 1;
		display: flex;
		flex-direction: column;
		justify-content: center;
		align-items: center;
		gap: 12px;
		text-align: center;
	}
	.listo h3 {
		font:
			800 30px/1.1 'Geist',
			sans-serif;
		letter-spacing: -0.8px;
		text-wrap: balance;
	}
	.grande {
		margin-top: 12px;
		padding: 16px 28px;
		border-radius: 20px;
		font-size: 17px;
	}
	@keyframes aparece {
		from {
			opacity: 0;
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.mazo {
			animation: none;
		}
	}
</style>
