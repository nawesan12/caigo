<script lang="ts">
	import { onMount } from 'svelte';
	import { invalidateAll } from '$app/navigation';
	import { escucharVivo } from '#lib/vivo.ts';
	import { enhance } from '$app/forms';
	import { PUBLIC_SITIO } from '$app/env/public';
	import Icono from '#lib/ui/Icono.svelte';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import Verificado from '#lib/app-ui/Verificado.svelte';
	import BajateLaApp from '#lib/app-ui/BajateLaApp.svelte';
	import Confirmar from '#lib/app-ui/Confirmar.svelte';
	import Reportar from '#lib/app-ui/Reportar.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { CATEGORIAS, faltanTexto, primerNombre } from '#lib/plan.ts';
	import { cuandoCorto } from '#lib/fechas.ts';
	import { chipAhora, distancia, horario, reputacion, TINTES } from '#lib/app.ts';
	import { desglose, pesos, pesosDeCentavos } from '#lib/precios.ts';

	/*
	 * Detalle del plan (Mockups v5, pantalla 05). Lo que la pantalla no muestra (estados Esperando /
	 * Adentro / organizador, precio de los planes de lugares) sale del Prototipo y del Panel Lugares.
	 */
	let { data, form } = $props();

	const p = $derived(data.plan);

	// En vivo: si cambia el plan o sus solicitudes (aceptan, rechazan, alguien se suma o sale), se recarga.
	onMount(() =>
		escucharVivo((e) => {
			if (e.tipo === 'plan' && e.planId === p.id) invalidateAll();
		})
	);
	const estado = $derived(form && 'estado' in form ? form.estado : p.miEstado);
	const ahoraChip = $derived(chipAhora(p.empiezaEn));
	const conCobro = $derived(!!p.lugar && p.cobro !== 'gratis' && !!p.precioCentavos);
	const d = $derived(
		conCobro ? desglose(p.cobro, (p.precioCentavos ?? 0) / 100, p.senaPct ?? 0) : null
	);
	const segmentos = $derived.by(() => {
		const total = Math.min(p.total, 20);
		const llenos = Math.round((p.somos / p.total) * total);
		return Array.from({ length: total }, (_, i) => i < llenos);
	});
	const vistos = $derived(p.van.slice(0, 4));
	const resto = $derived(Math.max(0, p.somos - vistos.length));
	const requisitos = $derived(
		[
			p.soloVerificados && 'Solo verificados',
			p.ingreso === 'aprobacion' && 'Con aprobación',
			p.ingreso === 'mi_gente' && 'Solo su gente',
			p.genero === 'mujeres' && 'Solo mujeres',
			p.genero === 'diversidades' && 'Solo diversidades',
			p.edadMin && p.edadMax && `De ${p.edadMin} a ${p.edadMax}`
		].filter(Boolean) as string[]
	);
	const organiza = $derived(p.lugar ? p.lugar.nombre : primerNombre(p.organizador.nombre));

	let grupo = $state(false);
	let reportar = $state(false);
	let salir = $state(false);
	let cancelar = $state(false);
	/** Lo que el organizador todavía puede tocar: un plan propio que no empezó ni se canceló. */
	const editable = $derived(
		estado === 'organizador' && !p.cancelado && new Date(p.empiezaEn).getTime() > Date.now()
	);
	/** Los que se sumaron (sin el organizador ni su grupo). */
	const otros = $derived(p.van.filter((v) => !v.organizador).length);
	let cantidad = $state(2);
	let enviando = $state(false);
	/**
	 * Plan «Solo mujeres» / «Solo diversidades»: se confirma acá, sin premarcar, y no se guarda en
	 * ningún lado (AAIP 1.2). Mismo texto que la hoja «Antes de sumarte» de las apps.
	 */
	const confirmar = $derived(
		p.genero === 'mujeres'
			? { campo: 'mujer', texto: 'Soy mujer' }
			: p.genero === 'diversidades'
				? { campo: 'diversidad', texto: 'Soy parte de la diversidad LGBTIQ+' }
				: null
	);
	let confirmado = $state(false);
	/** Con seña se elige: pagar todo (como en el Prototipo, lo primero) o señar. */
	let modo = $state<'entrada' | 'sena'>('entrada');
	const pago = $derived(
		conCobro
			? desglose(
					p.cobro === 'sena' && modo === 'sena' ? 'sena' : 'entrada',
					(p.precioCentavos ?? 0) / 100,
					p.senaPct ?? 0
				)
			: null
	);
	const cta = $derived(
		conCobro && pago
			? `${p.cobro === 'sena' && modo === 'sena' ? 'Señar' : 'Comprar entrada'} · ${pesos(pago.paga * (grupo ? cantidad : 1))}`
			: 'Sumarme'
	);
	const link = $derived(`${PUBLIC_SITIO.replace(/\/+$/, '')}/p/${p.slug}`);

	async function compartir() {
		try {
			if (navigator.share) return await navigator.share({ title: p.titulo, url: link });
			await navigator.clipboard.writeText(link);
			avisar('Link copiado: ' + link.replace(/^https?:\/\//, ''));
		} catch {
			/* cancelado */
		}
	}
</script>

<svelte:head><title>{p.titulo} · Caigo</title></svelte:head>

<div class="scroll">
	<div class="hero" style:background-color={TINTES[p.categoria] ?? '#5B43C9'}>
		{#if p.foto}<img src={p.foto} alt="" />{/if}
		<div class="botones">
			<a class="btn" href="/app" aria-label="Volver"><Icono nombre="atras" tam={22} /></a>
			<button class="btn" onclick={compartir} aria-label="Compartir el plan"
				><svg
					width="20"
					height="20"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2.2"
					stroke-linecap="round"
					stroke-linejoin="round"
					aria-hidden="true"><path d="M12 3v12M7 8l5-5 5 5M5 14v6h14v-6" /></svg
				></button
			>
		</div>
	</div>

	<div class="hoja">
		{#if data.editado}
			<p class="aviso ok" role="status">Listo, guardamos los cambios.</p>
		{/if}
		{#if data.pago}
			<p class="aviso {data.pago}" role="status">
				{data.pago === 'ok'
					? 'Pago aprobado. Ya tenés tu lugar.'
					: data.pago === 'pendiente'
						? 'Tu pago está pendiente. Te avisamos cuando se acredite.'
						: 'El pago no salió. Podés probar de nuevo.'}
			</p>
		{/if}
		<div class="chips">
			<span class="chip-off">{CATEGORIAS[p.categoria] ?? p.categoria}</span>
			{#if ahoraChip}
				<span class="chip-coral">{ahoraChip}</span>
			{:else}
				<span class="chip-off">{cuandoCorto(p.empiezaEn)}</span>
			{/if}
			{#if p.nivel}<span class="chip-off">{p.nivel}</span>{/if}
			{#if p.lugar}<span class="chip-lima">Lugar aliado</span>{/if}
		</div>
		<h1>{p.titulo}</h1>

		<div class="contador">
			<div class="cont-fila">
				<div class="cont-txt">
					<span class="faltan" class:completo={p.faltan <= 0}>{faltanTexto(p.faltan)}</span>
					<span class="somos">Somos {p.somos} de {p.total}</span>
				</div>
				<div class="avs">
					{#each vistos as v (v.id)}
						<Avatar nombre={v.nombre} foto={v.foto} tam={32} borde="2px solid #24212D" />
					{/each}
					{#if resto > 0}<span class="mas">+{resto}</span>{/if}
				</div>
			</div>
			<div class="segs" style:grid-template-columns="repeat({segmentos.length}, 1fr)">
				{#each segmentos as lleno, i (i)}<span class:lleno></span>{/each}
			</div>
		</div>

		<div class="org">
			<Avatar nombre={organiza} foto={p.lugar?.foto || p.organizador.foto} tam={44} />
			<div class="org-txt">
				<span class="org-nombre"
					>Organiza {organiza}{#if p.organizador.verificada}<Verificado />{/if}</span
				>
				<span class="or"
					>{p.lugar ? 'Lugar aliado de Caigo' : reputacion(p.organizador.asistencia)}</span
				>
			</div>
		</div>

		<div class="info">
			<div class="fila">
				<Icono nombre="reloj" tam={20} color="var(--c-acento)" trazo={2} />
				<span class="ft">{horario(p.empiezaEn, p.duracionMin)}</span>
			</div>
			<div class="fila">
				<Icono nombre="pin" tam={20} color="var(--c-acento)" trazo={2} />
				<div class="fcol">
					{#if p.direccion}
						<span class="ft">{p.direccion.texto}</span>
						<span class="fs">{[p.lugarNombre, p.zona].filter(Boolean).join(' · ')}</span>
					{:else}
						<span class="ft"
							>{[p.zona ? `Zona ${p.zona}` : p.lugarNombre, distancia(p.distanciaM)]
								.filter(Boolean)
								.join(' · ')}</span
						>
						<span class="fs">La dirección exacta te llega cuando te aceptan</span>
					{/if}
				</div>
			</div>
			{#if conCobro && d}
				<div class="fila">
					<span class="peso">$</span>
					<div class="fcol">
						<span class="ft">{pesos(d.paga)} por persona</span>
						<span class="fs"
							>{p.cobro === 'sena'
								? `Seña · resto ${pesos(d.resto)} en el\u00a0lugar`
								: 'Entrada con servicio incluido'}</span
						>
					</div>
				</div>
			{:else if p.costoCentavos}
				<div class="fila">
					<span class="peso">$</span><span class="ft"
						>{pesosDeCentavos(p.costoCentavos)} por persona</span
					>
				</div>
			{/if}
			{#if p.beneficio}
				<div class="fila">
					<Icono nombre="regalo" tam={20} color="var(--c-acento)" trazo={2} />
					<span class="ft">Con check-in: {p.beneficio}</span>
				</div>
			{/if}
		</div>

		{#if p.descripcion}<p class="desc">{p.descripcion}</p>{/if}

		{#if requisitos.length}
			<div class="reqs">
				<span class="rl">Requisitos</span>
				{#each requisitos as r (r)}<span class="req">{r}</span>{/each}
			</div>
		{/if}

		{#if form?.codigo === 'falta_dni'}
			<BajateLaApp
				titulo="Verificá tu DNI en la app"
				texto="Este plan es sólo para personas con DNI verificado. El DNI se verifica desde la app."
			/>
		{/if}

		{#if !data.anonimo}
			<div class="acciones">
				{#if editable}
					<div class="fila-acc">
						{#if !p.lugar}<a href="/app/plan/{p.id}/editar">Editar plan</a><span aria-hidden="true"
								>·</span
							>{/if}
						<button onclick={() => (cancelar = true)}>Cancelar el plan</button>
					</div>
				{:else if estado === 'aceptada' && !p.cancelado}
					<button onclick={() => (salir = true)}>Salir del plan</button>
				{/if}
				{#if estado !== 'organizador'}
					<span class="paso-algo"
						>¿Pasó algo? <button onclick={() => (reportar = true)}>Reportar</button></span
					>
				{/if}
			</div>
		{/if}
	</div>
</div>

<div class="barra">
	{#if form?.error}<p class="error" role="alert">
			{form.error}
		</p>{/if}
	{#if p.cancelado}
		<span class="estado esperando">Este plan se canceló</span>
	{:else if estado === 'organizador'}
		<div class="dos">
			<a class="gel gel-violeta grande" href="/app/plan/{p.id}/solicitudes"
				>Solicitudes{p.pendientes ? ` · ${p.pendientes}` : ''}</a
			>
			<a class="gel gel-blanco grande" href="/app/plan/{p.id}/chat">Chat</a>
		</div>
	{:else if estado === 'aceptada'}
		<div class="dos">
			<span class="estado adentro"
				><Icono nombre="check" tam={18} color="#C8F36B" trazo={3.4} />Adentro</span
			>
			<a class="gel gel-lima grande" href="/app/plan/{p.id}/chat">Ir al chat</a>
		</div>
	{:else if estado === 'pendiente'}
		<span class="estado esperando">Esperando…</span>
		<form method="POST" action="?/salir" use:enhance class="cancelar">
			<button>Cancelar el pedido</button>
		</form>
	{:else if p.faltan <= 0}
		<span class="estado esperando">Completo</span>
	{:else}
		<form
			method="POST"
			action="?/sumarme"
			use:enhance={() => {
				enviando = true;
				return async ({ update }) => {
					await update({ reset: false });
					enviando = false;
				};
			}}
		>
			<div class="seg-solo">
				<button type="button" class:on={!grupo} onclick={() => (grupo = false)}>Voy solo</button>
				<button type="button" class:on={grupo} onclick={() => (grupo = true)}>Somos 2+</button>
			</div>
			{#if grupo}
				<div class="cuantos">
					<span>Somos</span>
					<button
						type="button"
						aria-label="Menos"
						onclick={() => (cantidad = Math.max(2, cantidad - 1))}>−</button
					>
					<b aria-live="polite">{cantidad}</b>
					<button
						type="button"
						aria-label="Más"
						onclick={() => (cantidad = Math.min(Math.max(2, p.faltan), 10, cantidad + 1))}>+</button
					>
				</div>
			{/if}
			{#if conCobro && d && p.cobro === 'sena'}
				<div class="modos">
					<button
						type="button"
						class:elegido={modo === 'entrada'}
						onclick={() => (modo = 'entrada')}
					>
						<b>Pagar todo</b><span>Entrada completa&nbsp;ahora</span>
					</button>
					<button type="button" class:elegido={modo === 'sena'} onclick={() => (modo = 'sena')}>
						<b>Señar {p.senaPct}%</b><span
							>Reservás con {pesos(d.sena)}; el resto en el&nbsp;lugar</span
						>
					</button>
				</div>
				<input type="hidden" name="modo" value={modo} />
			{/if}
			{#if p.ingreso === 'aprobacion' && !conCobro}
				<input
					class="mensaje"
					name="mensaje"
					maxlength="200"
					placeholder="Mandale un mensaje (opcional)"
				/>
			{/if}
			{#if confirmar}
				<button
					type="button"
					class="confirma"
					role="switch"
					aria-checked={confirmado}
					onclick={() => (confirmado = !confirmado)}
				>
					<span class="confirma-txt"
						><b>{confirmar.texto}</b><span
							>No lo guardamos: sólo vale para sumarte a este&nbsp;plan.</span
						></span
					>
					<span class="tog" class:on={confirmado}></span>
				</button>
				<input type="hidden" name={confirmar.campo} value={confirmado ? 'si' : ''} />
			{/if}
			<input type="hidden" name="cantidad" value={grupo ? cantidad : 1} />
			<button class="gel gel-lima cta" disabled={enviando || (!!confirmar && !confirmado)}>
				<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"
					><path
						d="M12 4v16M4 12h16"
						stroke="#24212D"
						stroke-width="4"
						stroke-linecap="round"
					/></svg
				>{cta}
			</button>
		</form>
	{/if}
</div>

{#if !data.anonimo}
	<Reportar bind:abierta={reportar} planId={p.id} />
	<Confirmar
		bind:abierta={salir}
		titulo="¿Salís del plan?"
		texto={`Le avisamos a ${organiza} y tu lugar queda libre para otra persona.`}
		boton="Sí, salgo"
		accion="?/salir"
		listo="Saliste del plan."
	/>
	<Confirmar
		bind:abierta={cancelar}
		titulo="¿Cancelás el plan?"
		texto={otros > 0
			? `Les avisamos a ${otros === 1 ? 'la persona que va' : `las ${otros} personas que van`} y a los que pidieron sumarse.`
			: 'Le avisamos a quien haya pedido sumarse. No se puede deshacer.'}
		boton="Sí, cancelar"
		accion="?/cancelar"
	/>
{/if}

<style>
	.scroll {
		padding-bottom: 210px;
	}
	.hero {
		position: relative;
		height: 300px;
		margin-bottom: -30px;
	}
	.hero img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.botones {
		position: absolute;
		top: calc(16px + env(safe-area-inset-top));
		left: 16px;
		right: 16px;
		display: flex;
		justify-content: space-between;
	}
	.btn {
		width: 44px;
		height: 44px;
		border-radius: 16px;
		background: var(--c-btn-hero);
		box-shadow: var(--sh-btn-hero);
		display: grid;
		place-items: center;
		color: var(--carbon);
	}
	.hoja {
		position: relative;
		border-radius: 32px 32px 0 0;
		background: var(--c-fondo);
		padding: 22px 20px 0;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.chips {
		display: flex;
		gap: 6px;
		flex-wrap: wrap;
	}
	.chips span {
		padding: 6px 10px;
		border-radius: 11px;
		font-size: 12px;
		font-weight: 700;
	}
	.chip-off {
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
	}
	.chip-coral {
		color: #24212d;
		background: var(--grad-coral);
		box-shadow: var(--sh-coral);
	}
	.chip-lima {
		color: #24212d;
		background: #c8f36b;
	}
	h1 {
		font:
			700 30px/1.05 'Geist',
			sans-serif;
		letter-spacing: -0.8px;
	}
	.contador {
		padding: 16px;
		border-radius: 24px;
		background: var(--c-contador);
		outline: 1px solid var(--c-contador-borde);
		outline-offset: -1px;
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.cont-fila {
		display: flex;
		justify-content: space-between;
		align-items: flex-end;
		gap: 10px;
	}
	.cont-txt {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.faltan {
		font:
			800 46px/1 'Geist',
			sans-serif;
		color: #ff7868;
		letter-spacing: -1.5px;
		white-space: nowrap;
	}
	.faltan.completo {
		color: #c8f36b;
	}
	.somos {
		font-size: 14px;
		font-weight: 600;
		color: #faf9f6;
	}
	.avs {
		display: flex;
		padding-left: 8px;
	}
	.avs :global(.av) {
		margin-left: -8px;
	}
	.mas {
		width: 32px;
		height: 32px;
		margin-left: -8px;
		border-radius: 50%;
		border: 2px solid #24212d;
		background: #4a4460;
		color: #faf9f6;
		display: grid;
		place-items: center;
		font-size: 11px;
		font-weight: 800;
	}
	.segs {
		display: grid;
		gap: 4px;
	}
	.segs span {
		height: 8px;
		border-radius: 4px;
		border: 1.5px dashed #ff7868;
	}
	.segs span.lleno {
		border: 0;
		background: #c8f36b;
	}
	.org {
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.org-txt {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.org-nombre {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 15px;
		font-weight: 700;
	}
	.or {
		font-size: 13px;
		color: var(--texto-2);
	}
	.info {
		display: flex;
		flex-direction: column;
		border-radius: 20px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-info);
	}
	.fila {
		display: flex;
		gap: 12px;
		align-items: center;
		padding: 12px 14px;
		border-bottom: 1px solid var(--c-linea);
	}
	.fila:last-child {
		border-bottom: 0;
	}
	.fcol {
		flex: 1;
		display: flex;
		flex-direction: column;
	}
	.ft {
		flex: 1;
		font-size: 14px;
		font-weight: 600;
	}
	.fs {
		font-size: 12px;
		color: var(--texto-2);
	}
	.peso {
		width: 20px;
		text-align: center;
		font:
			800 17px 'Geist',
			sans-serif;
		color: #7456f1;
	}
	.desc {
		font-size: 14px;
		line-height: 1.5;
		color: var(--texto-2);
	}
	.reqs {
		display: flex;
		gap: 6px;
		flex-wrap: wrap;
	}
	.rl {
		font-size: 13px;
		font-weight: 700;
		color: var(--texto-2);
		padding: 6px 0;
	}
	.req {
		padding: 6px 10px;
		border-radius: 11px;
		border: 1.5px solid var(--c-borde-chip);
		font-size: 12px;
		font-weight: 700;
	}
	.aviso {
		align-self: center;
		padding: 8px 14px;
		border-radius: 14px;
		font-size: 13px;
		font-weight: 700;
		text-align: center;
	}
	.aviso.ok {
		background: #c8f36b;
	}
	.aviso.pendiente {
		background: var(--lavanda);
		color: var(--violeta-texto);
	}
	.aviso.error {
		background: #ffe1db;
		color: #9a2e1f;
	}
	/* Barra de abajo */
	.barra {
		position: absolute;
		left: 0;
		right: 0;
		bottom: 0;
		z-index: 5;
		padding: 12px 20px calc(30px + env(safe-area-inset-bottom));
		background: var(--c-barra);
		border-top: 1px solid var(--c-borde);
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.modos {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
	.modos button {
		display: flex;
		flex-direction: column;
		gap: 3px;
		padding: 14px;
		border: 0;
		border-radius: 20px;
		text-align: left;
		font: inherit;
		color: var(--carbon);
		cursor: pointer;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plana);
	}
	.modos button.elegido {
		background: var(--lavanda);
		box-shadow:
			inset 0 0 0 2.5px #7456f1,
			0 3px 0 #c9bef5;
	}
	.modos b {
		font-size: 15px;
		font-weight: 800;
	}
	.modos span {
		font-size: 12px;
		font-weight: 600;
		line-height: 1.35;
		color: var(--texto-2);
	}
	.barra form {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.seg-solo {
		display: flex;
		padding: 4px;
		border-radius: 15px;
		background: var(--lavanda);
	}
	.seg-solo button {
		flex: 1;
		text-align: center;
		padding: 9px 0;
		border-radius: 12px;
		font-size: 14px;
		font-weight: 600;
		color: var(--texto-2);
	}
	.seg-solo button.on {
		background: var(--c-seg-on);
		color: var(--carbon);
		font-weight: 700;
		box-shadow: var(--sh-seg-on);
	}
	.cuantos {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 14px;
		font-size: 14px;
		font-weight: 700;
		color: var(--texto-2);
	}
	.cuantos button {
		width: 36px;
		height: 36px;
		border-radius: 12px;
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
		font-size: 20px;
		font-weight: 700;
		color: var(--carbon);
	}
	.cuantos b {
		min-width: 28px;
		text-align: center;
		font:
			800 28px/1 'Geist',
			sans-serif;
		color: var(--carbon);
	}
	.mensaje {
		height: 44px;
		border-radius: 15px;
		border: 0;
		background: var(--c-input-2);
		padding: 0 14px;
		font-size: 15px;
	}
	.confirma {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		padding: 4px 2px;
		text-align: left;
		color: var(--carbon);
	}
	.confirma-txt {
		display: flex;
		flex-direction: column;
	}
	.confirma-txt b {
		font-size: 15px;
		font-weight: 700;
	}
	.confirma-txt span {
		font-size: 12px;
		color: var(--texto-2);
	}
	.tog {
		flex: none;
		position: relative;
		width: 48px;
		height: 28px;
		border-radius: 14px;
		background: #ddd8ea;
		transition: background 0.15s;
	}
	.tog::after {
		content: '';
		position: absolute;
		top: 3px;
		left: 3px;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		background: var(--c-tarjeta);
		transition: left 0.15s;
	}
	.tog.on {
		background: var(--violeta);
	}
	.tog.on::after {
		left: 23px;
	}
	.cta,
	.estado,
	.grande {
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
	}
	.cta {
		width: 100%;
	}
	.dos {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
	.estado {
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		font-weight: 800;
	}
	.esperando {
		color: var(--c-esperando-texto);
		background: var(--c-esperando);
		box-shadow: inset 0 2px 4px rgba(116, 86, 241, 0.18);
	}
	.adentro {
		color: #ffffff;
		background: var(--c-adentro);
	}
	.cancelar {
		align-items: center;
	}
	.cancelar button {
		font-size: 14px;
		font-weight: 700;
		color: var(--violeta-texto);
	}
	.acciones {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 14px;
		padding-top: 4px;
	}
	.fila-acc {
		display: flex;
		gap: 8px;
		align-items: center;
		color: var(--texto-2);
	}
	.acciones a,
	.acciones button {
		font-size: 14px;
		font-weight: 700;
		color: var(--violeta-texto);
		text-decoration: none;
	}
	.paso-algo {
		font-size: 13px;
		font-weight: 600;
		color: var(--texto-2);
	}
	.acciones .paso-algo button {
		font-size: 13px;
	}
	.error {
		padding: 10px 12px;
		border-radius: 14px;
		background: #ffe1db;
		color: #9a2e1f;
		font-size: 13px;
		font-weight: 700;
		text-align: center;
	}
</style>
