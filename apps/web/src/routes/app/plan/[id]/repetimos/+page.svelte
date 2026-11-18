<script lang="ts">
	import { untrack } from 'svelte';
	import { SvelteSet } from 'svelte/reactivity';
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import Reportar from '#lib/app-ui/Reportar.svelte';
	import Icono from '#lib/ui/Icono.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { cuandoCorto, diasHasta, partes } from '#lib/fechas.ts';
	import { primerNombre } from '#lib/plan.ts';
	import { CARITAS } from '#lib/dia.ts';

	/*
	 * 09b ¿Repetimos? (Mockups v5). Quien organizó elige «Sí, el <día>» o «Esta vez no»; los que
	 * fueron sólo contestan «¿Volverías a salir con…?» (privado: los «sí» pasan a su gente) y cierran
	 * con «Listo». Abajo, «¿Pasó algo? Reportar».
	 */
	let { data, form } = $props();

	const p = $derived(data.plan);
	const organizo = $derived(p.miEstado === 'organizador');
	const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
	const cuando = $derived.by(() => {
		const n = diasHasta(p.empiezaEn);
		const d = partes(p.empiezaEn);
		if (n === 0) return 'hoy';
		if (n === -1) return d.hora >= '19:00' ? 'anoche' : 'ayer';
		if (n > -7) return `el ${DIAS[d.semana]}`;
		return cuandoCorto(p.empiezaEn);
	});
	const otraVez = $derived(`Sí, el ${DIAS[partes(p.empiezaEn).semana]}`);
	const gente = $derived(p.van.filter((v) => v.id !== data.yo.id));
	const si = new SvelteSet<string>(
		untrack(() => data.plan.van.filter((v) => v.enMiGente).map((v) => v.id))
	);
	let enviando = $state(false);
	let reportar = $state(false);
	let carita = $state(untrack(() => data.plan.mio?.carita ?? 0));
	/** Con quiénes quedó mutuo al responder: ya pueden hablar por privado. */
	let mutuos = $state<{ id: string; nombre: string; foto?: string }[]>([]);
	let planNuevo = $state('');
	const fotos = $derived(p.mio?.fotos ?? 0);

	function alternar(id: string) {
		if (si.has(id)) si.delete(id);
		else si.add(id);
	}
	const detalle = (v: (typeof gente)[number]) =>
		v.enMiGente ? 'Ya es de tu gente' : v.organizador ? 'Organizó el plan' : 'Primer plan juntos';
</script>

<svelte:head><title>¿Repetimos? · Caigo</title></svelte:head>

<form
	method="POST"
	class="scroll pantalla"
	use:enhance={() => {
		enviando = true;
		return async ({ result, update }) => {
			enviando = false;
			if (result.type === 'success') {
				const nuevo = result.data?.nuevo as { id: string } | null;
				const m = (result.data?.mutuos as typeof mutuos) ?? [];
				if (m.length) {
					// «¡Es mutuo!» reemplaza el formulario (ya se mandó) y queda arriba, a la vista. Con un
					// plan repetido, primero los mutuos y un link al plan nuevo.
					mutuos = m;
					planNuevo = nuevo?.id ?? '';
					document.querySelector('.pantalla')?.scrollTo({ top: 0 });
					return;
				}
				avisar(
					nuevo
						? 'Listo, se repite. Ya está publicado.'
						: si.size
							? 'Listo. Los sumamos a tu gente.'
							: 'Gracias por contarnos.'
				);
				await goto(nuevo ? `/app/plan/${nuevo.id}` : '/app/mis?cual=historial');
				return;
			}
			await update({ reset: false });
		};
	}}
>
	{#each [...si] as id (id)}<input type="hidden" name="si" value={id} />{/each}
	{#if carita}<input type="hidden" name="carita" value={carita} />{/if}
	<div class="arriba">
		<a class="atras" href="/app/mis?cual=historial" aria-label="Volver"
			><Icono nombre="atras" tam={22} /></a
		>
		<span class="etiqueta">{p.titulo} · {cuando}</span>
		<h1>{organizo ? '¿Repetimos?' : '¿Y, qué tal?'}</h1>
		{#if organizo}
			<div class="botones">
				<button
					class="gel gel-lima si-btn"
					name="repetir"
					value="si"
					disabled={enviando || mutuos.length > 0}>{otraVez}</button
				>
				<button class="no-btn" name="repetir" value="no" disabled={enviando || mutuos.length > 0}
					>Esta vez no</button
				>
			</div>
		{/if}
	</div>

	<div class="hoja">
		{#if mutuos.length}
			<div class="mutuo" role="status">
				{#each mutuos as m (m.id)}
					<div class="persona">
						<Avatar nombre={m.nombre} foto={m.foto} tam={44} />
						<div class="pc">
							<span class="n">¡Es mutuo con {primerNombre(m.nombre)}!</span>
							<span class="d">Ya pueden hablar</span>
						</div>
						<a class="gel gel-lima escribir" href="/app/mensajes/{m.id}">Escribir</a>
					</div>
				{/each}
				{#if planNuevo}<a class="gel gel-lima escribir" href="/app/plan/{planNuevo}"
						>Ver el plan nuevo</a
					>{/if}
				<a class="link-volver" href="/app/mis?cual=historial">Listo</a>
			</div>
		{:else}
			{#if !organizo}
				<div class="cab">
					<h2>¿Cómo estuvo?</h2>
					<p>Nadie ve tu carita: suma al promedio de quien organizó.</p>
				</div>
				<div class="caritas" role="radiogroup" aria-label="¿Cómo estuvo?">
					{#each CARITAS as c (c.valor)}
						<button
							type="button"
							role="radio"
							aria-checked={carita === c.valor}
							aria-label={c.texto}
							class:on={carita === c.valor}
							onclick={() => (carita = c.valor)}
							><Icono
								nombre={`carita${c.valor}` as 'carita1'}
								tam={30}
								trazo={2}
								color={carita === c.valor ? '#24212D' : 'currentColor'}
							/></button
						>
					{/each}
				</div>
			{/if}
			<a class="fotos" href="/app/plan/{p.id}/fotos">
				<Icono nombre="imagen" tam={20} color="var(--c-acento)" trazo={2} />
				<span class="pc"
					><span class="n">Fotos del plan{fotos ? ` · ${fotos}` : ''}</span><span class="d"
						>{fotos ? 'Sólo las ven quienes fueron' : 'Subí la primera'}</span
					></span
				>
				<Icono nombre="flecha" tam={18} color="#9A95AD" trazo={2.6} />
			</a>
			<div class="cab">
				<h2>¿Volverías a salir con…?</h2>
				<p>Los que sumes a tu gente ven primero tus próximos planes. Nadie ve tus respuestas.</p>
			</div>
			{#each gente as v (v.id)}
				<div class="persona">
					<Avatar nombre={v.nombre} foto={v.foto} tam={44} />
					<div class="pc">
						<span class="n">{primerNombre(v.nombre)}</span>
						<span class="d">{detalle(v)}</span>
					</div>
					{#if si.has(v.id)}
						<button
							type="button"
							class="tu-gente"
							aria-pressed="true"
							onclick={() => alternar(v.id)}
							><Icono nombre="check" tam={14} color="#24212D" trazo={3} />Tu gente</button
						>
					{:else}
						<button
							type="button"
							class="sumar"
							aria-pressed="false"
							aria-label="Sumar a {primerNombre(v.nombre)} a tu gente"
							onclick={() => alternar(v.id)}
							><svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"
								><path
									d="M12 4v16M4 12h16"
									stroke="#7456F1"
									stroke-width="3.6"
									stroke-linecap="round"
								/></svg
							></button
						>
					{/if}
				</div>
			{:else}
				<p class="vacio">Esta vez no fue nadie más.</p>
			{/each}
			{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}
			{#if !organizo}
				<button class="gel gel-lima listo" name="repetir" value="no" disabled={enviando}
					>{enviando ? 'Guardando…' : 'Listo'}</button
				>
			{/if}
		{/if}
		<span class="paso-algo"
			>¿Pasó algo? <button type="button" onclick={() => (reportar = true)}>Reportar</button></span
		>
	</div>
</form>

<Reportar bind:abierta={reportar} planId={p.id} />

<style>
	.pantalla {
		display: flex;
		flex-direction: column;
		background:
			url(/grain.png),
			radial-gradient(120% 70% at 85% 0%, #a38fff 0%, var(--violeta) 45%, #5a3dd8 100%);
	}
	.arriba {
		padding: calc(16px + env(safe-area-inset-top)) 24px 26px;
		display: flex;
		flex-direction: column;
		gap: 18px;
	}
	.atras {
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		color: var(--carbon);
		background: var(--c-gel-blanco);
		box-shadow:
			inset 0 2px 0 #ffffff,
			0 3px 0 #dcd5ee;
		margin-left: -4px;
	}
	.etiqueta {
		align-self: flex-start;
		padding: 6px 11px;
		border-radius: 11px;
		background: var(--violeta-texto);
		color: #ffffff;
		font-size: 13px;
		font-weight: 700;
	}
	h1 {
		font:
			800 52px/0.95 'Geist',
			sans-serif;
		color: #ffffff;
		letter-spacing: -2px;
	}
	.botones {
		display: flex;
		gap: 10px;
	}
	.si-btn,
	.no-btn {
		height: 54px;
		border-radius: 18px;
		font-size: 16px;
	}
	.si-btn {
		flex: 1.4;
		font-weight: 800;
	}
	.no-btn {
		flex: 1;
		border: 2px solid #a897f7;
		color: #ffffff;
		font-weight: 700;
	}
	.hoja {
		flex: 1;
		border-radius: 32px 32px 0 0;
		background: var(--c-fondo);
		padding: 24px 20px calc(14px + env(safe-area-inset-bottom));
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.cab {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	h2 {
		font:
			700 22px 'Geist',
			sans-serif;
	}
	.cab p {
		font-size: 13px;
		line-height: 1.45;
		color: var(--texto-2);
	}
	.persona {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px;
		border-radius: 20px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plan);
	}
	.pc {
		flex: 1;
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.n {
		font-size: 15px;
		font-weight: 700;
	}
	.d {
		font-size: 12px;
		color: var(--texto-2);
	}
	.tu-gente {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 0 12px;
		height: 40px;
		border-radius: 14px;
		background: var(--grad-lima);
		box-shadow: var(--sh-lima);
		font-size: 13px;
		font-weight: 800;
		color: var(--carbon);
	}
	.sumar {
		width: 40px;
		height: 40px;
		border-radius: 14px;
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
		display: grid;
		place-items: center;
	}
	.caritas {
		display: grid;
		grid-template-columns: repeat(5, 1fr);
		gap: 8px;
	}
	.caritas button {
		height: 56px;
		border-radius: 18px;
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
		font-size: 26px;
		display: grid;
		place-items: center;
	}
	.caritas button.on {
		background: var(--c-chip-on);
		box-shadow: var(--sh-chip-on);
	}
	.fotos {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 14px 16px;
		border-radius: 20px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plan);
		color: inherit;
		text-decoration: none;
	}
	.mutuo {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.escribir {
		height: 40px;
		padding: 0 14px;
		border-radius: 14px;
		font-size: 14px;
		text-decoration: none;
	}
	.link-volver {
		align-self: center;
		font-size: 14px;
		font-weight: 700;
		color: var(--violeta-texto);
	}
	.vacio {
		font-size: 14px;
		color: var(--texto-2);
	}
	.listo {
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
		margin-top: 6px;
	}
	.paso-algo {
		align-self: center;
		margin-top: auto;
		padding: 14px 0;
		font-size: 13px;
		font-weight: 600;
		color: var(--texto-2);
	}
	.paso-algo button {
		font-size: 13px;
		font-weight: 700;
		color: var(--violeta-texto);
	}
	.error {
		padding: 12px 14px;
		border-radius: 16px;
		background: #ffe1db;
		color: #9a2e1f;
		font-size: 14px;
		font-weight: 700;
	}
</style>
