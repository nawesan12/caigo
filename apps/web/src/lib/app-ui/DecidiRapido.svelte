<script lang="ts">
	import { goto } from '$app/navigation';
	import TarjetaPlan from '#lib/ui/TarjetaPlan.svelte';
	import Deslizable from './Deslizable.svelte';
	import { CATEGORIAS, fotoDelPlan } from '#lib/plan.ts';
	import { cuandoCorto } from '#lib/fechas.ts';
	import { distancia, TINTES } from '#lib/app.ts';
	import type { GestosLista } from './gestos-lista.svelte.ts';
	import type { Plan } from '#lib/tipos.ts';

	/*
	 * «Decidí rápido» (A4 del informe del 18/11): opcional, sólo con 8 planes o más por decidir. Un
	 * plan por vez con «Paso», «Ver más» y «Caigo» abajo (y los mismos gestos). La lista de Ahora no
	 * cambia: esto se abre encima y se cierra.
	 */
	let {
		abierto = $bindable(false),
		planes,
		g
	}: { abierto?: boolean; planes: Plan[]; g: GestosLista } = $props();

	let i = $state(0);
	let sumados = $state(0);
	let ocupado = $state(false);
	const actual = $derived(planes[i]);
	const fin = $derived(i >= planes.length);

	$effect(() => {
		if (abierto) {
			i = 0;
			sumados = 0;
		}
	});

	async function caigo() {
		if (!actual || ocupado) return;
		ocupado = true;
		if (await g.caigo(actual)) sumados++;
		ocupado = false;
		i++;
	}
	function paso() {
		if (!actual) return;
		// «Paso» es «No me interesa»: no vuelve a salir hoy. Sin «Deshacer» en el mazo.
		g.noMeInteresa(actual);
		g.deshacer = null;
		i++;
	}
	const donde = (p: Plan) =>
		[p.lugarNombre || (p.zona ? `Zona ${p.zona}` : p.ciudad), distancia(p.distanciaM)]
			.filter(Boolean)
			.join(' · ');
</script>

{#if abierto}
	<div class="mazo" role="dialog" aria-modal="true" aria-labelledby="decidi-t">
		<div class="arriba">
			<h2 id="decidi-t">Decidí rápido</h2>
			{#if !fin}<span class="cuenta">{i + 1} de {planes.length}</span>{/if}
			<button class="cerrar" type="button" aria-label="Cerrar" onclick={() => (abierto = false)}
				><svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true"
					><path
						d="M6 6l12 12M18 6L6 18"
						stroke="currentColor"
						stroke-width="2.6"
						stroke-linecap="round"
					/></svg
				></button
			>
		</div>

		{#if fin}
			<div class="listo">
				<h3>{sumados ? `Listo: te sumaste a ${sumados}.` : 'Listo.'}</h3>
				{#if sumados}<p>Te avisamos cuando te&nbsp;acepten.</p>{/if}
				<button class="gel gel-lima grande" type="button" onclick={() => (abierto = false)}
					>Volver a la lista</button
				>
			</div>
		{:else if actual}
			{#key actual.id}
				<div class="carta">
					<Deslizable titulo={actual.titulo} onderecha={caigo} onizquierda={paso}>
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
							alta
						/>
					</Deslizable>
				</div>
			{/key}
			<div class="botones">
				<button class="gel gel-blanco" type="button" onclick={paso} disabled={ocupado}>Paso</button>
				<button
					class="gel gel-blanco"
					type="button"
					onclick={() => goto(`/app/plan/${actual.id}`)}
					disabled={ocupado}>Ver más</button
				>
				<button class="gel gel-lima" type="button" onclick={caigo} disabled={ocupado}>Caigo</button>
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
		flex: 1;
		display: flex;
		flex-direction: column;
		justify-content: center;
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
	.listo p {
		font-size: 15px;
		color: var(--texto-2);
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
