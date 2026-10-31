<script lang="ts">
	import { onMount } from 'svelte';
	import { PUBLIC_SITIO } from '$app/env/public';
	import Confirmar from '#lib/app-ui/Confirmar.svelte';
	import Reportar from '#lib/app-ui/Reportar.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { agrupar, badgeMio, TINTES } from '#lib/app.ts';
	import { partes, cuandoCorto } from '#lib/fechas.ts';
	import type { Plan } from '#lib/tipos.ts';

	/*
	 * Mis planes (Mockups v5, 10); vacío, 13b "Todavía no te sumaste a nada". En los que organizo:
	 * compartir, editar y cancelar; en el historial, ¿Repetimos? (09b) y Reportar.
	 */
	let { data } = $props();

	let aCancelar = $state<Plan | null>(null);
	let cancelarAbierta = $state(false);
	let aReportar = $state('');
	let reportarAbierta = $state(false);
	const yaEmpezo = (p: Plan) => new Date(p.empiezaEn).getTime() <= Date.now();
	const otrosDe = (p: Plan) => p.van.filter((v) => !v.organizador).length;
	onMount(() => {
		if (data.cancelado) avisar('Cancelaste el plan. Ya les avisamos.');
	});

	const grupos = $derived(agrupar(data.planes, data.cual === 'historial'));
	const TABS = $derived([
		{ k: 'organizo', l: `Organizo${data.cuentas.organizo ? ` · ${data.cuentas.organizo}` : ''}` },
		{ k: 'me_sume', l: `Me sumé${data.cuentas.me_sume ? ` · ${data.cuentas.me_sume}` : ''}` },
		{ k: 'historial', l: 'Historial' }
	]);
	const linea = (p: Plan, grupo: string) => {
		const hora = grupo === 'HOY' ? partes(p.empiezaEn).hora : cuandoCorto(p.empiezaEn);
		const extra = p.recurrente ? 'se repite cada semana' : p.lugarNombre || p.zona;
		return [hora, extra].filter(Boolean).join(' · ');
	};
	async function compartir(p: Plan) {
		const link = `${PUBLIC_SITIO.replace(/\/+$/, '')}/p/${p.slug}`;
		try {
			if (navigator.share) return await navigator.share({ title: p.titulo, url: link });
			await navigator.clipboard.writeText(link);
			avisar('Link copiado.');
		} catch {
			/* cancelado */
		}
	}
</script>

<svelte:head><title>Mis planes · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<h1>Mis planes</h1>
		<nav class="seg3" aria-label="Qué planes ver">
			{#each TABS as t (t.k)}
				<a href="/app/mis?cual={t.k}" aria-current={data.cual === t.k ? 'page' : undefined}>{t.l}</a
				>
			{/each}
		</nav>

		{#each grupos as g (g.titulo)}
			<span class="overline-app">{g.titulo}</span>
			{#each g.planes as p (p.id)}
				{@const b =
					data.cual === 'historial'
						? { texto: p.cancelado ? 'Se canceló' : `Fueron ${p.somos}`, completo: false }
						: badgeMio(p)}
				<div class="tarjeta">
					<a class="cuerpo" href="/app/plan/{p.id}">
						<span class="img" style:background-color={TINTES[p.categoria]}>
							{#if p.foto}<img src={p.foto} alt="" loading="lazy" />{/if}
						</span>
						<div class="txt">
							<span class="t">{p.titulo}</span>
							<span class="s">{linea(p, g.titulo)}</span>
							<div class="pie">
								<span
									class="b"
									class:completo={b.completo}
									class:coral={!b.completo && g.titulo === 'HOY'}>{b.texto}</span
								>
								{#if p.miEstado === 'pendiente'}<span class="estado">Esperando…</span>{/if}
							</div>
						</div>
					</a>
					{#if data.cual === 'historial'}
						<div class="acciones">
							<a href="/app/plan/{p.id}/repetimos"
								>{p.miEstado === 'organizador' ? '¿Repetimos?' : '¿Volverías?'}</a
							>
							<span aria-hidden="true">·</span>
							<button
								onclick={() => {
									aReportar = p.id;
									reportarAbierta = true;
								}}>Reportar</button
							>
						</div>
					{:else if p.miEstado === 'organizador'}
						<div class="acciones">
							<button onclick={() => compartir(p)}>Compartir link</button>
							{#if !yaEmpezo(p) && !p.lugar}
								<span aria-hidden="true">·</span>
								<a href="/app/plan/{p.id}/editar">Editar</a>
								<span aria-hidden="true">·</span>
								<button
									onclick={() => {
										aCancelar = p;
										cancelarAbierta = true;
									}}>Cancelar</button
								>
							{/if}
						</div>
						{#if p.pendientes}
							<a class="pedidos" href="/app/plan/{p.id}/solicitudes">
								<span
									>{p.pendientes === 1
										? '1 persona quiere sumarse'
										: `${p.pendientes} personas quieren sumarse`}</span
								>
								<svg
									width="18"
									height="18"
									viewBox="0 0 24 24"
									fill="none"
									stroke="#C8F36B"
									stroke-width="2.6"
									stroke-linecap="round"
									stroke-linejoin="round"
									aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" /></svg
								>
							</a>
						{/if}
					{/if}
				</div>
			{/each}
		{:else}
			<div class="vacio">
				<div class="burbujas" aria-hidden="true">
					<span class="b1"></span><span class="b1"></span>
					<span class="b2"
						><svg width="34" height="34" viewBox="0 0 24 24"
							><path
								d="M12 4v16M4 12h16"
								stroke="#24212D"
								stroke-width="4"
								stroke-linecap="round"
							/></svg
						></span
					>
				</div>
				{#if data.cual === 'organizo'}
					<h2>Todavía no armaste ningún plan</h2>
					<p>Armá uno en menos de 30 segundos y que se sume la&nbsp;gente.</p>
					<a class="gel gel-lima cta" href="/app/crear">Armá un plan</a>
				{:else if data.cual === 'me_sume'}
					<h2>Todavía no te sumaste a nada</h2>
					<p>Hay una mesa de truco a la que le falta uno. Y sabemos que te la&nbsp;sabés.</p>
					<a class="gel gel-lima cta" href="/app">Ver planes cerca</a>
				{:else}
					<h2>Todavía no hay historial</h2>
					<p>Acá quedan los planes a los que fuiste.</p>
					<a class="gel gel-lima cta" href="/app">Ver planes cerca</a>
				{/if}
			</div>
		{/each}
	</div>
</div>

<Reportar bind:abierta={reportarAbierta} planId={aReportar} />
{#if aCancelar}
	{@const otros = otrosDe(aCancelar)}
	<Confirmar
		bind:abierta={cancelarAbierta}
		titulo="¿Cancelás «{aCancelar.titulo}»?"
		texto={otros > 0
			? `Les avisamos a ${otros === 1 ? 'la persona que va' : `las ${otros} personas que van`} y a los que pidieron sumarse.`
			: 'Le avisamos a quien haya pedido sumarse. No se puede deshacer.'}
		boton="Sí, cancelar"
		accion="?/cancelar"
		campos={{ id: aCancelar.id }}
		listo="Cancelaste el plan. Ya les avisamos."
	/>
{/if}

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px 120px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	h1 {
		font:
			800 40px/1 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}
	.seg3 {
		display: flex;
		padding: 4px;
		border-radius: 16px;
		background: var(--lavanda);
	}
	.seg3 a {
		flex: 1;
		text-align: center;
		padding: 10px 0;
		border-radius: 12px;
		font-size: 14px;
		font-weight: 600;
		color: var(--texto-2);
		text-decoration: none;
		white-space: nowrap;
	}
	.seg3 a[aria-current='page'] {
		background: var(--c-seg-on);
		color: var(--carbon);
		font-weight: 700;
		box-shadow: var(--sh-seg-on);
	}
	.overline-app {
		font-size: 13px;
		font-weight: 800;
		letter-spacing: 1px;
		color: var(--texto-2);
	}
	.tarjeta {
		border-radius: 24px;
		overflow: hidden;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plan);
	}
	.cuerpo {
		display: flex;
		gap: 12px;
		padding: 14px;
		color: inherit;
		text-decoration: none;
	}
	.img {
		flex: none;
		width: 72px;
		height: 72px;
		border-radius: 18px;
		overflow: hidden;
	}
	.img img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.txt {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 4px;
		min-width: 0;
	}
	.t {
		font:
			700 18px/1.15 'Geist',
			sans-serif;
		text-wrap: balance;
	}
	.s {
		font-size: 12px;
		color: var(--texto-2);
	}
	.pie {
		display: flex;
		gap: 8px;
		align-items: center;
	}
	.b {
		white-space: nowrap;
		padding: 5px 10px;
		border-radius: 10px;
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
		font:
			700 15px/1 'Geist',
			sans-serif;
	}
	.b.coral {
		color: #24212d;
		background: var(--grad-coral);
		box-shadow: var(--sh-coral);
	}
	.b.completo {
		color: #24212d;
		background: var(--grad-lima);
		box-shadow: var(--sh-lima);
	}
	.estado {
		font-size: 12px;
		font-weight: 700;
		color: var(--violeta-texto);
	}
	.pedidos {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 12px 14px;
		background: #7456f1;
		color: #ffffff;
		font-size: 14px;
		font-weight: 700;
		text-decoration: none;
	}
	.pedidos:hover {
		color: #ffffff;
	}
	/* Las acciones de cada plan, con el mismo texto que tenía «Compartir link». */
	.acciones {
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 0 14px 14px 98px;
		margin-top: -8px;
		font-size: 12px;
		color: var(--texto-2);
	}
	.acciones a,
	.acciones button {
		font-size: 12px;
		font-weight: 700;
		color: var(--violeta-texto);
		text-decoration: none;
	}
	.vacio {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 18px;
		text-align: center;
		padding: 56px 8px 0;
	}
	.burbujas {
		display: flex;
		align-items: center;
	}
	.b1 {
		width: 76px;
		height: 76px;
		border-radius: 50%;
		background: var(--lavanda);
		border: 3px dashed #b9acf5;
	}
	.b1 + .b1 {
		margin-left: -18px;
	}
	.b2 {
		width: 84px;
		height: 84px;
		margin-left: -18px;
		border-radius: 50%;
		background: radial-gradient(circle at 32% 26%, #f2ffc8 0%, #c8f36b 40%, #a6d640 100%);
		box-shadow:
			inset 0 -4px 8px rgba(110, 150, 20, 0.3),
			0 12px 22px -8px rgba(140, 190, 40, 0.6);
		display: grid;
		place-items: center;
		border: 4px solid #faf9f6;
	}
	.vacio h2 {
		font:
			700 30px/1.08 'Geist',
			sans-serif;
		letter-spacing: -0.6px;
	}
	.vacio p {
		font-size: 15px;
		line-height: 1.5;
		color: var(--texto-2);
	}
	.cta {
		align-self: stretch;
		margin-top: 8px;
		height: 58px;
		border-radius: 20px;
		font-size: 16px;
	}
</style>
