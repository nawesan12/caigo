<script lang="ts">
	import Icono from '#lib/ui/Icono.svelte';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import Verificado from '#lib/app-ui/Verificado.svelte';
	import { CATEGORIAS } from '#lib/plan.ts';
	import { nivelTexto, rachaTexto, siguienteTexto } from '#lib/gestos.ts';
	import type { YoCompleto } from '#lib/tipos.ts';

	/*
	 * Perfil propio (Mockups v5, 11): foto, datos, asistencia, planes, "Le copa", Mi gente y los
	 * planes compartidos. "Sobre mí" y las preguntas salen del perfil de persona del Prototipo. La
	 * rueda lleva a los ajustes (editar perfil, seguridad, avisos, tema).
	 */
	let { data } = $props();

	const yo = $derived(data.yo as YoCompleto);
	const pct = $derived(
		yo.asistencia.de ? Math.round((yo.asistencia.fue / yo.asistencia.de) * 100) : null
	);
	const donde = $derived(
		[[yo.zona, yo.ciudad].filter(Boolean).join(', '), yo.verificada ? 'Verificada' : '']
			.filter(Boolean)
			.join(' · ')
	);
	const prompts = $derived((yo.prompts ?? []).filter((p) => p.p && p.r));
	const restoGente = $derived(Math.max(0, yo.miGente - data.gente.length));
	const logros = $derived(yo.logros);
</script>

<svelte:head><title>Perfil · Caigo</title></svelte:head>

<div class="scroll">
	<a class="ajustes" href="/app/perfil/ajustes" aria-label="Ajustes">
		<svg
			width="20"
			height="20"
			viewBox="0 0 24 24"
			fill="none"
			stroke="currentColor"
			stroke-width="2"
			stroke-linecap="round"
			stroke-linejoin="round"
			aria-hidden="true"
			><circle cx="12" cy="12" r="3" /><path
				d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"
			/></svg
		>
	</a>

	<div class="contenido">
		<div class="quien">
			<a class="foto" href="/app/perfil/editar" aria-label="Editar tus fotos">
				<Avatar nombre={yo.nombre || '?'} foto={yo.fotos[0]} tam={112} />
			</a>
			<div class="nombre">
				<span>{yo.nombre || 'Sin nombre'}{yo.edad ? `, ${yo.edad}` : ''}</span>
				{#if yo.verificada}<Verificado tam={22} />{/if}
			</div>
			{#if donde}<span class="donde">{donde}</span>{/if}
		</div>

		{#if !yo.verificada}
			<!-- Sin DNI, lo único que falta va primero (y lo de ir a planes, después). -->
			<a class="verificar" href="/app/verificar?volver=/app/perfil">
				<span class="v-t">Verificate para sumarte</span>
				<span class="v-s">Con tu DNI, una sola vez. Mirar ya podés.</span>
				<span class="gel gel-lima v-b">Verificarme</span>
			</a>
		{/if}

		<div class="stats">
			<div class="st oscura">
				<span class="sv lima">{pct === null ? '—' : `${pct}%`}</span>
				<span class="sl">Asistencia real</span>
				<span class="sd"
					>{yo.asistencia.de
						? `${yo.asistencia.fue} de ${yo.asistencia.de} con check-in`
						: 'Todavía sin planes'}</span
				>
			</div>
			<div class="st clara">
				<span class="sv violeta">{yo.planes}</span>
				<span class="sl">Planes</span>
				<span class="sd"
					>{yo.organizados} {yo.organizados === 1 ? 'organizado' : 'organizados'}</span
				>
			</div>
		</div>

		{#if logros && yo.verificada}
			<!-- Premios por ir: sobrio, sin puntos. Nivel, racha semanal, insignias y lo que falta. -->
			<div class="caja logros">
				<div class="l-arriba">
					<span class="rot">{nivelTexto(logros)}</span>
					{#if rachaTexto(logros)}<span class="sd">{rachaTexto(logros)}</span>{/if}
				</div>
				{#if logros.insignias.length}
					<ul class="insignias">
						{#each logros.insignias as ins (ins.id)}
							<li>
								<span class="insignia">{ins.nombre}</span><span class="sd">{ins.detalle}</span>
							</li>
						{/each}
					</ul>
				{/if}
				{#if siguienteTexto(logros)}<span class="sd">{siguienteTexto(logros)}</span>{/if}
			</div>
		{/if}

		{#if yo.bio}
			<div class="caja">
				<span class="overline">SOBRE MÍ</span>
				<p class="bio">{yo.bio}</p>
			</div>
		{/if}

		{#each prompts as pr, i (i)}
			<div class="caja">
				<span class="pq">{pr.p}</span>
				<span class="pr">{pr.r}</span>
			</div>
		{/each}

		{#if yo.intereses.length}
			<div class="bloque">
				<span class="rot">Le copa</span>
				<div class="chips">
					{#each yo.intereses as t (t)}<span class="interes">{t}</span>{/each}
				</div>
			</div>
		{/if}

		<a class="gente" href="/app/perfil/gente" aria-label="Mi gente">
			<div class="g-txt">
				<span class="rot">Mi gente · {yo.miGente}</span>
				{#if data.gente.length}
					<div class="caras">
						{#each data.gente as g (g.id)}
							<Avatar nombre={g.nombre} foto={g.fotos?.[0]} tam={34} borde="2px solid #FFFFFF" />
						{/each}
						{#if restoGente > 0}<span class="mas">+{restoGente}</span>{/if}
					</div>
				{:else}
					<span class="sd">Después de un plan, sumá a quien volverías a&nbsp;ver.</span>
				{/if}
			</div>
			<Icono nombre="flecha" tam={20} trazo={2.2} />
		</a>

		{#if yo.verificada}<a class="gente" href="/app/perfil/invitar">
				<div class="g-txt">
					<span class="rot">Traé a alguien</span>
					<span class="sd"
						>Cuando vaya a su primer plan, los dos tienen una semana de&nbsp;Plus.</span
					>
				</div>
				<Icono nombre="flecha" tam={20} trazo={2.2} />
			</a>{/if}

		{#if data.compartidos.length}
			<div class="bloque">
				<span class="rot">Planes compartidos</span>
				<div class="chips">
					{#each data.compartidos as p (p.id)}
						<a class="comp" href="/app/plan/{p.id}"
							>{[CATEGORIAS[p.categoria] ?? p.titulo, p.zona || p.lugarNombre]
								.filter(Boolean)
								.join(' · ')}</a
						>
					{/each}
				</div>
			</div>
		{/if}

		<a class="gel gel-blanco editar" href="/app/perfil/editar">Editar perfil</a>
	</div>
</div>

<style>
	.verificar {
		display: flex;
		flex-direction: column;
		gap: 4px;
		padding: 18px;
		border-radius: 24px;
		background: var(--c-contador);
		outline: 1px solid var(--c-contador-borde);
		outline-offset: -1px;
		color: #faf9f6;
		text-decoration: none;
	}
	.v-t {
		font:
			800 22px/1.15 'Geist',
			sans-serif;
		letter-spacing: -0.6px;
		color: #c8f36b;
	}
	.v-s {
		font-size: 14px;
		font-weight: 600;
		text-wrap: pretty;
	}
	.v-b {
		align-self: flex-start;
		margin-top: 10px;
		padding: 12px 20px;
		border-radius: 16px;
		font-size: 16px;
	}
	.insignias {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.insignias li {
		display: flex;
		align-items: center;
		gap: 10px;
	}
	.ajustes {
		position: absolute;
		top: calc(16px + env(safe-area-inset-top));
		right: 20px;
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
		padding: calc(42px + env(safe-area-inset-top)) 20px var(--bajo-tabbar);
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
	.foto {
		border-radius: 50%;
		text-decoration: none;
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
	.l-arriba {
		display: flex;
		flex-wrap: wrap;
		align-items: baseline;
		justify-content: space-between;
		gap: 4px 12px;
	}
	.logros .sd {
		font-size: 13px;
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
	.gente {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 14px;
		border-radius: 22px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plan);
		color: inherit;
		text-decoration: none;
	}
	.g-txt {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.caras {
		display: flex;
	}
	.caras :global(.av) {
		margin-right: -8px;
		font-size: 12px !important;
		font-weight: 800;
	}
	.mas {
		width: 34px;
		height: 34px;
		border-radius: 50%;
		border: 2px solid var(--c-tarjeta-lisa);
		background: #e4defa;
		color: var(--violeta-texto);
		display: grid;
		place-items: center;
		font-size: 11px;
		font-weight: 800;
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
	.editar {
		height: 54px;
		border-radius: 18px;
		font-size: 16px;
	}
</style>
