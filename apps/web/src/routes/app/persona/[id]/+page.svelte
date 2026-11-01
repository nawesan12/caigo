<script lang="ts">
	import Icono from '#lib/ui/Icono.svelte';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import Confirmar from '#lib/app-ui/Confirmar.svelte';
	import Opciones from '#lib/app-ui/Opciones.svelte';
	import Reportar from '#lib/app-ui/Reportar.svelte';
	import Verificado from '#lib/app-ui/Verificado.svelte';
	import { cuandoCorto } from '#lib/fechas.ts';
	import { primerNombre } from '#lib/plan.ts';

	/*
	 * Perfil de otra persona: las piezas del perfil propio (Mockups v5, 11) con lo que la API deja
	 * ver, «Sus próximos planes» y, como en las apps, «Mandar mensaje» y «Reportar o bloquear a Lu»
	 * abajo de todo.
	 */
	let { data } = $props();

	const p = $derived(data.persona);
	const n = $derived(primerNombre(p.nombre));
	const pct = $derived(
		p.asistencia.de ? Math.round((p.asistencia.fue / p.asistencia.de) * 100) : null
	);
	const donde = $derived(
		[[p.zona, p.ciudad].filter(Boolean).join(', '), p.verificada ? 'Verificada' : '']
			.filter(Boolean)
			.join(' · ')
	);
	const prompts = $derived((p.prompts ?? []).filter((x) => x.p && x.r));
	const proximos = $derived(p.proximos ?? []);
	let menu = $state(false);
	let reportar = $state(false);
	let bloquear = $state(false);
</script>

<svelte:head><title>{p.nombre} · Caigo</title></svelte:head>

<div class="scroll">
	<a class="volver" href={data.volver} aria-label="Volver"><Icono nombre="atras" tam={22} /></a>

	<div class="contenido">
		<div class="quien">
			<Avatar nombre={p.nombre || '?'} foto={p.fotos?.[0]} tam={112} />
			<div class="nombre">
				<span>{p.nombre}{p.edad ? `, ${p.edad}` : ''}</span>
				{#if p.verificada}<Verificado tam={22} />{/if}
			</div>
			{#if donde}<span class="donde">{donde}</span>{/if}
			{#if p.compartidos}
				<span class="donde"
					>{p.compartidos === 1
						? 'Compartieron 1 plan'
						: `Compartieron ${p.compartidos} planes`}</span
				>
			{/if}
		</div>

		<div class="stats">
			<div class="st oscura">
				<span class="sv lima">{pct === null ? '—' : `${pct}%`}</span>
				<span class="sl">Asistencia real</span>
				<span class="sd"
					>{p.asistencia.de
						? `${p.asistencia.fue} de ${p.asistencia.de} con check-in`
						: 'Todavía sin planes'}</span
				>
			</div>
			<div class="st clara">
				<span class="sv violeta">{p.planes}</span>
				<span class="sl">Planes</span>
				<span class="sd">{p.organizados} {p.organizados === 1 ? 'organizado' : 'organizados'}</span>
			</div>
		</div>

		{#if p.bio}
			<div class="caja">
				<span class="overline">SOBRE MÍ</span>
				<p class="bio">{p.bio}</p>
			</div>
		{/if}

		{#each prompts as pr, i (i)}
			<div class="caja">
				<span class="pq">{pr.p}</span>
				<span class="pr">{pr.r}</span>
			</div>
		{/each}

		{#if p.intereses?.length}
			<div class="bloque">
				<span class="rot">Le copa</span>
				<div class="chips">
					{#each p.intereses as t (t)}<span class="interes">{t}</span>{/each}
				</div>
			</div>
		{/if}

		{#if proximos.length}
			<div class="bloque">
				<span class="rot">Sus próximos planes</span>
				<div class="chips">
					{#each proximos as pl (pl.id)}
						<a class="comp" href="/app/plan/{pl.id}">{pl.titulo} · {cuandoCorto(pl.empiezaEn)}</a>
					{/each}
				</div>
			</div>
		{/if}

		<a class="gel gel-lima cta" href="/app/mensajes/{p.id}"
			><Icono nombre="enviar" tam={18} trazo={2.4} />Mandar mensaje</a
		>
		<button class="reportar" onclick={() => (menu = true)}>Reportar o bloquear a {n}</button>
	</div>
</div>

<Opciones
	bind:abierta={menu}
	opciones={[
		{ texto: `Reportar a ${n}`, peligro: true, accion: () => (reportar = true) },
		{ texto: `Bloquear a ${n}`, peligro: true, accion: () => (bloquear = true) }
	]}
/>
<Reportar bind:abierta={reportar} usuarioId={p.id} titulo="Reportar a {n}" />
<Confirmar
	bind:abierta={bloquear}
	titulo="¿Bloqueás a {n}?"
	texto={`${n} no va a ver tus planes ni tu perfil, y no te va a poder escribir. Lo podés deshacer en Ajustes.`}
	boton="Sí, bloquear"
	accion="?/bloquear"
	campos={{ nombre: p.nombre }}
/>

<style>
	.volver {
		position: absolute;
		top: calc(16px + env(safe-area-inset-top));
		left: 20px;
		z-index: 2;
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		color: var(--carbon);
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
	}
	.contenido {
		padding: calc(42px + env(safe-area-inset-top)) 20px 48px;
		display: flex;
		flex-direction: column;
		gap: 18px;
	}
	.quien {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 10px;
		text-align: center;
	}
	.nombre {
		display: flex;
		align-items: center;
		gap: 8px;
		font:
			700 28px/1 'Geist',
			sans-serif;
	}
	.donde {
		font-size: 14px;
		color: var(--texto-2);
	}
	.quien .donde + .donde {
		margin-top: -6px;
	}
	.stats {
		display: grid;
		grid-template-columns: 1.2fr 1fr;
		gap: 10px;
	}
	.st {
		padding: 16px;
		border-radius: 22px;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.oscura {
		background: var(--grad-oscuro);
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
	}
	.clara {
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
	}
	.sv {
		font:
			800 40px/1 'Geist',
			sans-serif;
	}
	.lima {
		color: var(--lima);
	}
	.violeta {
		color: var(--violeta-texto);
	}
	.sl {
		font-size: 13px;
		font-weight: 600;
	}
	.oscura .sl {
		color: var(--blanco-calido);
	}
	.sd {
		font-size: 12px;
		color: var(--texto-2);
	}
	.oscura .sd {
		color: #b9b4cc;
	}
	.caja {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding: 18px;
		border-radius: 24px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plana);
	}
	.bio {
		font-size: 15px;
		line-height: 1.5;
		text-wrap: pretty;
	}
	.pq {
		font-size: 13px;
		font-weight: 700;
		color: var(--texto-2);
	}
	.pr {
		font:
			700 22px/1.2 'Geist',
			sans-serif;
		letter-spacing: -0.4px;
		text-wrap: pretty;
	}
	.bloque {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.rot {
		font-size: 15px;
		font-weight: 700;
	}
	.chips {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.interes {
		padding: 8px 12px;
		border-radius: 13px;
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
		font-size: 13px;
		font-weight: 600;
	}
	.comp {
		padding: 10px 12px;
		border-radius: 16px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plan);
		font-size: 13px;
		font-weight: 600;
		color: inherit;
		text-decoration: none;
	}
	.cta {
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
		gap: 10px;
	}
	.reportar {
		align-self: center;
		padding: 6px 10px;
		font-size: 13px;
		font-weight: 700;
		color: var(--texto-2);
	}
</style>
