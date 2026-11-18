<script lang="ts">
	import { onMount } from 'svelte';
	import { invalidateAll, replaceState } from '$app/navigation';
	import { page } from '$app/state';
	import { escucharVivo } from '#lib/vivo.ts';
	import { enhance } from '$app/forms';
	import { PUBLIC_SITIO } from '$app/env/public';
	import Icono from '#lib/ui/Icono.svelte';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import Verificado from '#lib/app-ui/Verificado.svelte';
	import Confirmar from '#lib/app-ui/Confirmar.svelte';
	import Reportar from '#lib/app-ui/Reportar.svelte';
	import DiaDelPlan from '#lib/app-ui/DiaDelPlan.svelte';
	import { caritasTexto, enElDia, esVispera, lugarGuardado } from '#lib/dia.ts';
	import { beneficioNivelTexto } from '#lib/gestos.ts';
	import { avisar } from '#lib/toast.svelte.ts';
	import { CATEGORIAS, faltanTexto, fotoDelPlan, lineaConfianza, primerNombre } from '#lib/plan.ts';
	import { cuandoCorto } from '#lib/fechas.ts';
	import {
		chipAhora,
		distancia,
		horario,
		reputacion,
		sinViuda,
		terminado,
		TINTES
	} from '#lib/app.ts';
	import Festejo from '#lib/app-ui/Festejo.svelte';
	import { desglose, pesos, pesosDeCentavos } from '#lib/precios.ts';
	import { avisoPlan } from '#lib/moderacion.ts';

	/*
	 * Detalle del plan (Mockups v5, pantalla 05). Lo que la pantalla no muestra (estados Esperando /
	 * Adentro / organizador, precio de los planes de lugares) sale del Prototipo y del Panel Lugares.
	 */
	let { data, form } = $props();

	const p = $derived(data.plan);
	// Sólo para quien organiza: el resto lo ve igual (revisión) o no lo ve (oculto).
	const moderado = $derived(avisoPlan(p));

	// En vivo: si cambia el plan o sus solicitudes (aceptan, rechazan, alguien se suma o sale), se recarga.
	onMount(() =>
		escucharVivo((e) => {
			if (e.tipo === 'plan' && e.planId === p.id) invalidateAll();
		})
	);
	// Desde la tarjeta de «Hoy» (Ahora, Mis planes): «Llegué» o «¿Todo bien?» ya en marcha. Y al volver
	// de verificarse con ?sumar=1: el «Sumarme» que se había tocado se hace solo si no hay nada que
	// elegir o confirmar (si no, el botón queda a mano).
	let llegarAlAbrir = $state(false);
	let formSumar = $state<HTMLFormElement>();
	onMount(() => {
		const q = page.url.searchParams;
		if (!q.has('todobien') && !q.has('llegue') && !q.has('sumar')) return;
		if (q.has('todobien')) todoBien = true;
		if (q.has('llegue')) llegarAlAbrir = true;
		const sumar = q.has('sumar') && (estado ?? 'ninguno') === 'ninguno' && !!data.yo?.verificada;
		replaceState(page.url.pathname, {});
		if (!sumar || !formSumar) return;
		if (!confirmar && !conCobro && p.faltan > 0) formSumar.requestSubmit();
		else formSumar.querySelector<HTMLButtonElement>('.cta')?.focus();
	});
	// La barra fija avisa su alto: el toast va encima, sin tapar «Adentro» ni «Ir al chat».
	let barra = $state<HTMLElement>();
	$effect(() => {
		if (!barra) return;
		const raiz = document.documentElement;
		const ro = new ResizeObserver(() =>
			raiz.style.setProperty('--alto-barra', `${barra!.offsetHeight}px`)
		);
		ro.observe(barra);
		return () => {
			ro.disconnect();
			raiz.style.removeProperty('--alto-barra');
		};
	});
	// Lo que vuelve el plan manda: si después de pedir (form: «pendiente») te aceptan, el plan se
	// recarga en vivo con «aceptada». La respuesta del form sólo cubre lo que el plan no dice.
	const estado = $derived(p.miEstado || (form && 'estado' in form ? form.estado : undefined));
	const ahoraChip = $derived(chipAhora(p.empiezaEn, new Date(), p.duracionMin));
	const yaPaso = $derived(terminado(p));
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
			p.ingreso === 'aprobacion' && 'Con aprobación',
			p.ingreso === 'mi_gente' && 'Solo su gente',
			p.genero === 'mujeres' && 'Solo mujeres',
			p.genero === 'diversidades' && 'Solo diversidades',
			p.edadMin && p.edadMax && `De ${p.edadMin} a ${p.edadMax}`
		].filter(Boolean) as string[]
	);
	const organiza = $derived(p.lugar ? p.lugar.nombre : primerNombre(p.organizador.nombre));
	/** La línea de confianza del detalle (quien organiza ya tiene su fila): «Todos verificados». */
	const seguro = $derived(lineaConfianza(p, { detalle: true }));
	const caritas = $derived(p.lugar ? '' : caritasTexto(p.organizador.caritas));
	const rep = $derived(p.lugar ? 'Lugar aliado de Caigo' : reputacion(p.organizador.asistencia));
	const adentro = $derived(estado === 'aceptada' || estado === 'organizador');
	/** Hoy: cuenta regresiva, Llegué y «¿Todo bien?» (desde 3 h antes hasta el final). */
	const hoy = $derived(!data.anonimo && adentro && enElDia(p));
	/**
	 * La víspera: «¿Seguís yendo?» (no a quien organiza ni a quien ya confirmó). Sólo en planes gratis:
	 * con una entrada paga no se libera el lugar con un toque (se cancela la entrada, con su reembolso).
	 */
	const vispera = $derived(
		!data.anonimo &&
			estado === 'aceptada' &&
			!p.cancelado &&
			(p.cobro ?? 'gratis') === 'gratis' &&
			esVispera(p) &&
			!p.mio?.confirme
	);
	/** Después: caritas, fotos y su gente. */
	const despues = $derived(!data.anonimo && adentro && yaPaso && !p.cancelado);
	/** Lista de espera: el plan está completo (o se llenó al tocar «Sumarme»). */
	const espera = $derived(p.mio?.espera);
	/** «Es tuyo hasta las 21:40»; vacío si el aviso ya venció. */
	let reloj = $state(new Date());
	onMount(() => {
		const t = setInterval(() => (reloj = new Date()), 30_000);
		return () => clearInterval(t);
	});
	const guardado = $derived(
		espera?.estado === 'avisado' ? lugarGuardado(espera.venceEn, reloj) : ''
	);
	const completo = $derived(p.faltan <= 0 || form?.codigo === 'completo');

	let grupo = $state(false);
	let reportar = $state(false);
	let todoBien = $state(false);
	let salir = $state(false);
	let cancelar = $state(false);
	/** Lo que el organizador todavía puede tocar: un plan propio que no empezó ni se canceló. */
	const editable = $derived(
		estado === 'organizador' && !p.cancelado && new Date(p.empiezaEn).getTime() > Date.now()
	);
	/** Los que se sumaron (sin el organizador ni su grupo). */
	const otros = $derived(p.van.filter((v) => !v.organizador).length);
	let cantidad = $state(2);
	/** «¡Estás adentro!»: cuando el form de sumarse vuelve con «aceptada». */
	let festejo = $state(false);
	$effect(() => {
		if (form && 'estado' in form && form.estado === 'aceptada') festejo = true;
	});
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
		{#if fotoDelPlan(p)}<img src={fotoDelPlan(p)} alt="" />{/if}
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
		{#if moderado}
			<p
				class="aviso"
				class:pendiente={!moderado.oculto}
				class:error={moderado.oculto}
				role="status"
			>
				{moderado.texto}
			</p>
		{/if}
		<div class="chips">
			<span class="chip-off">{CATEGORIAS[p.categoria] ?? p.categoria}</span>
			{#if ahoraChip && ahoraChip !== 'Ya pasó' && !hoy}
				<span class="chip-coral">{ahoraChip}</span>
			{:else if ahoraChip === 'Ya pasó'}
				<span class="chip-off">Ya pasó</span>
			{:else}
				<span class="chip-off">{cuandoCorto(p.empiezaEn)}</span>
			{/if}
			{#if p.nivel}<span class="chip-off">{p.nivel}</span>{/if}
			{#if p.lugar}<span class="chip-lima">Lugar aliado</span>{/if}
		</div>
		<h1>{p.titulo}</h1>

		{#if hoy}
			<DiaDelPlan
				plan={p}
				todoBien={data.todoBien}
				yoId={data.yo?.id}
				bind:hoja={todoBien}
				{llegarAlAbrir}
			/>
		{/if}
		{#if vispera}
			<div class="vispera">
				<span class="v-t">{cuandoCorto(p.empiezaEn)}. ¿Seguís&nbsp;yendo?</span>
				<div class="dos">
					<form
						method="POST"
						action="?/confirmo"
						use:enhance={() =>
							async ({ result, update }) => {
								if (result.type === 'success') avisar('¡Genial! Te esperan.');
								await update({ reset: false });
							}}
					>
						<button class="gel gel-lima v-b">Voy</button>
					</form>
					<button class="gel gel-blanco v-b" onclick={() => (salir = true)}>Me bajo</button>
				</div>
			</div>
		{/if}
		{#if despues}
			<div class="despues">
				<a class="fila-d" href="/app/plan/{p.id}/repetimos">
					<Icono
						nombre={p.mio?.carita ? 'check' : 'carita4'}
						tam={20}
						color="var(--c-acento)"
						trazo={p.mio?.carita ? 3 : 2}
					/>
					<span class="ft"
						><b>{estado === 'organizador' ? '¿Repetimos?' : '¿Cómo estuvo?'}</b><span
							>{p.mio?.carita ? 'Ya nos contaste. Gracias.' : 'Una carita y tu gente'}</span
						></span
					>
					<Icono nombre="flecha" tam={18} color="#9A95AD" trazo={2.6} />
				</a>
				<a class="fila-d" href="/app/plan/{p.id}/fotos">
					<Icono nombre="imagen" tam={20} color="var(--c-acento)" trazo={2} />
					<span class="ft"
						><b>Fotos del plan{p.mio?.fotos ? ` · ${p.mio.fotos}` : ''}</b><span
							>Sólo las ven quienes fueron</span
						></span
					>
					<Icono nombre="flecha" tam={18} color="#9A95AD" trazo={2.6} />
				</a>
			</div>
		{/if}

		<div class="contador">
			<div class="cont-fila">
				<div class="cont-txt">
					<span class="faltan" class:completo={p.faltan <= 0 || yaPaso || p.cancelado}
						>{p.cancelado
							? 'Se canceló'
							: yaPaso
								? `Fueron ${p.somos}`
								: faltanTexto(p.faltan)}</span
					>
					{#if !yaPaso && !p.cancelado}<span class="somos">Somos {p.somos} de {p.total}</span>{/if}
				</div>
				<div class="avs">
					{#each vistos as v (v.id)}
						<Avatar nombre={v.nombre} foto={v.foto} tam={32} borde="2px solid #24212D" />
					{/each}
					{#if resto > 0}<span class="mas">+{resto}</span>{/if}
				</div>
			</div>
			{#if !yaPaso && !p.cancelado}
				<div class="segs" style:grid-template-columns="repeat({segmentos.length}, 1fr)">
					{#each segmentos as lleno, i (i)}<span class:lleno></span>{/each}
				</div>
			{/if}
		</div>

		<div class="org">
			<Avatar nombre={organiza} foto={p.lugar?.foto || p.organizador.foto} tam={44} />
			<div class="org-txt">
				<span class="org-nombre"
					>Organiza {organiza}{#if p.organizador.verificada}<Verificado
						/>{/if}{#if !p.lugar && p.organizador.anfitrion}<span class="insignia">Anfitrión</span
						>{/if}</span
				>
				{#if rep || caritas}<span class="or"
						>{[rep, caritas].filter(Boolean).join('\u00a0· ').replace(/ /g, '\u00a0')}</span
					>{/if}
			</div>
		</div>
		{#if seguro.length}
			<p class="seguro">
				{#if p.confianza?.todosVerificados}<Verificado tam={14} />{/if}<span
					>{seguro.join('\u00a0· ')}</span
				>
			</p>
		{/if}

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
			{#if p.lugar?.beneficioNivel}
				<div class="fila">
					<Icono nombre="regalo" tam={20} color="var(--c-acento)" trazo={2} />
					<span class="ft"
						>{sinViuda(beneficioNivelTexto(p.lugar.beneficioNivel, data.yo?.logros ?? null))}</span
					>
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

		{#if !data.anonimo}
			<div class="acciones">
				{#if editable}
					<div class="fila-acc">
						{#if !p.lugar}<a href="/app/plan/{p.id}/editar">Editar plan</a><span aria-hidden="true"
								>·</span
							>{/if}
						<button onclick={() => (cancelar = true)}>Cancelar el plan</button>
					</div>
				{:else if estado === 'aceptada' && !p.cancelado && !yaPaso}
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

<div class="barra" bind:this={barra}>
	{#if form?.error}<p class="error" role="alert">
			{form.error}
		</p>{/if}
	{#if form?.codigo === 'con_entrada'}
		<form
			method="POST"
			action="?/cancelarEntrada"
			class="cancelar"
			use:enhance={() => {
				enviando = true;
				return async ({ result, update }) => {
					if (result.type === 'success') avisar('Cancelaste tu entrada y saliste del plan.');
					await update({ reset: false });
					enviando = false;
				};
			}}
		>
			<button disabled={enviando}>Cancelar mi entrada</button>
		</form>
	{/if}
	{#if p.cancelado}
		<span class="estado esperando">Este plan se canceló</span>
	{:else if yaPaso && (estado === 'organizador' || estado === 'aceptada')}
		<!-- Ya pasó: lo que queda es el cierre (¿Cómo estuvo? / ¿Repetimos?) y las fotos. -->
		<div class="dos">
			<a class="gel gel-lima grande" href="/app/plan/{p.id}/repetimos"
				>{estado === 'organizador' ? '¿Repetimos?' : '¿Cómo estuvo?'}</a
			>
			{#if estado === 'organizador'}
				<a class="gel gel-blanco grande" href="/app/plan/{p.id}/solicitudes">Quiénes fueron</a>
			{:else}
				<a class="gel gel-blanco grande" href="/app/plan/{p.id}/fotos">Fotos</a>
			{/if}
		</div>
	{:else if yaPaso}
		<span class="estado esperando">Ya pasó</span>
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
		<form
			method="POST"
			action="?/salir"
			class="cancelar"
			use:enhance={() => {
				enviando = true;
				return async ({ update }) => {
					await update({ reset: false });
					enviando = false;
				};
			}}
		>
			<button disabled={enviando}>Cancelar el pedido</button>
		</form>
	{:else if espera}
		{#if espera.estado === 'avisado' && !guardado}
			<p class="libre" role="status">Se venció tu lugar. Si se libera otro, te avisamos.</p>
		{:else if espera.estado === 'avisado'}
			<p class="libre" role="status">Se liberó un lugar. {guardado.replace(/ /g, '\u00a0')}.</p>
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
				{#if confirmar}<input type="hidden" name={confirmar.campo} value="si" />{/if}
				<button class="gel gel-lima cta" disabled={enviando}>Entrar al plan</button>
			</form>
		{:else}
			<span class="estado esperando">En la lista · {espera.posicion}°</span>
			<form method="POST" action="?/dejarEspera" class="cancelar" use:enhance>
				<button>Salir de la lista</button>
			</form>
		{/if}
	{:else if completo && !yaPaso && !conCobro}
		<form
			method="POST"
			action="?/espera"
			use:enhance={() => {
				enviando = true;
				return async ({ result, update }) => {
					if (result.type === 'success') avisar('Te anotamos. Si se libera un lugar, te avisamos.');
					await update({ reset: false });
					enviando = false;
				};
			}}
		>
			{#if confirmar}
				<button
					type="button"
					class="confirma"
					role="switch"
					aria-checked={confirmado}
					onclick={() => (confirmado = !confirmado)}
				>
					<span class="confirma-txt"><b>{confirmar.texto}</b></span>
					<span class="tog" class:on={confirmado}></span>
				</button>
				<input type="hidden" name={confirmar.campo} value={confirmado ? 'si' : ''} />
			{/if}
			<span class="estado esperando">Completo</span>
			<button class="gel gel-violeta cta" disabled={enviando || (!!confirmar && !confirmado)}
				>Anotame en la lista</button
			>
		</form>
	{:else if p.faltan <= 0}
		<span class="estado esperando">Completo</span>
	{:else}
		<form
			method="POST"
			action="?/sumarme"
			bind:this={formSumar}
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

{#if !data.anonimo && data.yo}
	<Festejo
		bind:abierto={festejo}
		plan={p}
		yo={{ id: data.yo.id, nombre: data.yo.nombre, foto: data.yo.fotos?.[0] }}
	/>
{/if}
{#if !data.anonimo}
	{#if hoy}
		<DiaDelPlan
			parte="hoja"
			plan={p}
			todoBien={data.todoBien}
			bind:hoja={todoBien}
			onReportar={() => (reportar = true)}
		/>
	{/if}
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
		overflow-wrap: anywhere;
		text-wrap: balance;
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
		flex-wrap: wrap;
		align-items: center;
		gap: 6px;
		font-size: 15px;
		font-weight: 700;
	}
	.or {
		font-size: 13px;
		color: var(--texto-2);
	}
	.seguro {
		display: flex;
		align-items: flex-start;
		gap: 6px;
		margin-top: -6px;
		font-size: 13px;
		font-weight: 600;
		line-height: 1.35;
		color: var(--texto-2);
	}
	.seguro :global(svg) {
		margin-top: 1px;
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
		overflow-wrap: anywhere;
		text-wrap: pretty;
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
	.vispera,
	.despues {
		display: flex;
		flex-direction: column;
		border-radius: 22px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plana);
	}
	.vispera {
		gap: 12px;
		padding: 16px;
	}
	.v-t {
		font:
			700 18px/1.2 'Geist',
			sans-serif;
		text-wrap: balance;
	}
	.v-b {
		height: 50px;
		border-radius: 18px;
		font-size: 16px;
		width: 100%;
	}
	.vispera form {
		display: contents;
	}
	.fila-d {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 14px 16px;
		text-decoration: none;
		color: inherit;
		border-bottom: 1px solid var(--c-linea);
	}
	.fila-d:last-child {
		border-bottom: 0;
	}
	.fila-d .ft {
		display: flex;
		flex-direction: column;
	}
	.fila-d .ft b {
		font-size: 14px;
		font-weight: 800;
	}
	.fila-d .ft span {
		font-size: 12px;
		font-weight: 400;
		color: var(--texto-2);
	}
	.libre {
		padding: 10px 12px;
		border-radius: 14px;
		background: #c8f36b;
		color: #24212d;
		font-size: 14px;
		font-weight: 700;
		text-align: center;
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
