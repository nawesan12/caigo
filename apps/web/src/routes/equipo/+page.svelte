<script lang="ts">
	import { enhance } from '$app/forms';
	import Icono, { type NombreIcono } from '#lib/ui/Icono.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { partes } from '#lib/fechas.ts';
	import type { SubmitFunction } from '$app/forms';

	/*
	 * Backoffice del equipo. No está en el handoff: se arma con la barra lateral, las tarjetas, los
	 * chips y los botones gel del Panel Lugares.
	 */
	let { data } = $props();

	const TABS: { k: string; l: string; icono: NombreIcono; n: number }[] = $derived([
		{ k: 'lugares', l: 'Lugares pendientes', icono: 'pin', n: data.lugares.length },
		{ k: 'dni', l: 'DNI', icono: 'usuario', n: data.dni.length },
		{ k: 'reportes', l: 'Reportes', icono: 'escudo', n: data.reportes.length }
	]);
	const MOTIVOS: Record<string, string> = {
		acoso: 'Acoso',
		falso: 'Perfil falso',
		no_vino: 'No vino',
		inapropiado: 'Inapropiado',
		inseguro: 'Inseguro',
		otro: 'Otro'
	};
	const fecha = (f: string) => {
		const p = partes(f);
		return `${p.dia}/${p.mes + 1} ${p.hora}`;
	};
	const resultado: SubmitFunction =
		() =>
		async ({ result, update }) => {
			if (result.type === 'success') avisar(String(result.data?.ok ?? 'Listo.'));
			else if (result.type === 'failure') avisar(String(result.data?.error ?? 'No se pudo.'));
			await update();
		};
</script>

<svelte:head>
	<title>Equipo · Caigo</title>
	<meta name="robots" content="noindex" />
</svelte:head>

<div class="panel">
	<aside>
		<div class="marca">
			<a href="/" class="caigo">Caigo</a>
			<span class="para">EQUIPO</span>
		</div>
		<nav aria-label="Secciones">
			{#each TABS as t (t.k)}
				<a
					href="?tab={t.k}"
					class:on={data.tab === t.k}
					aria-current={data.tab === t.k ? 'page' : undefined}
				>
					<Icono nombre={t.icono} />{t.l}
					{#if t.n}<span class="n">{t.n}</span>{/if}
				</a>
			{/each}
		</nav>
		<form method="POST" action="/salir" class="abajo"><button class="salir">Salir</button></form>
	</aside>

	<main>
		{#if data.tab === 'lugares'}
			<h1>Lugares pendientes</h1>
			<div class="lista">
				{#each data.lugares as l (l.id)}
					<form method="POST" action="?/lugar" class="tarjeta-plana item" use:enhance={resultado}>
						<input type="hidden" name="id" value={l.id} />
						<div class="cab">
							<div>
								<span class="t">{l.nombre}</span>
								<span class="s">{[l.rubro, l.ciudad, l.zona].filter(Boolean).join(' · ')}</span>
							</div>
							<span class="chip-dato">{fecha(l.creadoEn)}</span>
						</div>
						<div class="datos">
							<span><b>Dirección:</b> {l.direccion}</span>
							{#if l.instagram}<span><b>Instagram:</b> @{l.instagram}</span>{/if}
							{#if l.telefono}<span><b>Teléfono:</b> {l.telefono}</span>{/if}
							<a
								href="https://www.openstreetmap.org/?mlat={l.lat}&mlon={l.lng}#map=18/{l.lat}/{l.lng}"
								target="_blank"
								rel="noopener noreferrer">Ver en el mapa</a
							>
						</div>
						<input
							class="input"
							name="motivo"
							maxlength="200"
							placeholder="Motivo (si lo rechazás)"
						/>
						<div class="acc">
							<button class="gel gel-lima b" name="decision" value="aprobar">Aprobar</button>
							<button class="b rechazar" name="decision" value="rechazar">Rechazar</button>
						</div>
					</form>
				{:else}
					<p class="vacia tarjeta-plana">No hay lugares esperando revisión.</p>
				{/each}
			</div>
		{:else if data.tab === 'dni'}
			<h1>DNI</h1>
			<div class="lista">
				{#each data.dni as d (d.id)}
					<form method="POST" action="?/dni" class="tarjeta-plana item" use:enhance={resultado}>
						<input type="hidden" name="id" value={d.id} />
						<div class="cab">
							<div>
								<span class="t">{d.nombrePerfil}{d.edadPerfil ? `, ${d.edadPerfil}` : ''}</span>
								<span class="s">Perfil · {fecha(d.creadaEn)}</span>
							</div>
							{#if d.repetido}<span class="chip-dato alerta">DNI usado en otra cuenta</span>{/if}
						</div>
						<div class="datos">
							<span><b>DNI:</b> {d.numero}</span>
							<span><b>Apellido y nombres:</b> {d.apellido}, {d.nombres}</span>
							<span><b>Sexo:</b> {d.sexo} · <b>Nacimiento:</b> {d.nacimiento.slice(0, 10)}</span>
						</div>
						<div class="fotos">
							{#each [['Frente', d.frenteId], ['Dorso', d.dorsoId], ['Selfie', d.selfieId], ['Selfie de ingreso', d.selfieIngresoId]] as [l, f] (l)}
								{#if f}
									<a href="/archivo/{f}" target="_blank" rel="noopener" class="foto">
										<img src="/archivo/{f}" alt={l} loading="lazy" />
										<span>{l}</span>
									</a>
								{/if}
							{/each}
						</div>
						<input
							class="input"
							name="motivo"
							maxlength="200"
							placeholder="Motivo (si lo rechazás)"
						/>
						<div class="acc">
							<button class="gel gel-lima b" name="decision" value="aprobar">Aprobar</button>
							<button class="b rechazar" name="decision" value="rechazar">Rechazar</button>
						</div>
					</form>
				{:else}
					<p class="vacia tarjeta-plana">No hay DNI para revisar.</p>
				{/each}
			</div>
		{:else}
			<h1>Reportes</h1>
			<div class="lista">
				{#each data.reportes as r (r.id)}
					<form method="POST" action="?/reporte" class="tarjeta-plana item" use:enhance={resultado}>
						<input type="hidden" name="id" value={r.id} />
						<div class="cab">
							<div>
								<span class="t">{MOTIVOS[r.motivo] ?? r.motivo}</span>
								<span class="s"
									>{r.deNombre} reportó a {r.sobreNombre || 'un plan'}{r.planTitulo
										? ` · ${r.planTitulo}`
										: ''}</span
								>
							</div>
							<span class="chip-dato" class:alerta={r.sobreReportes > 1}
								>{r.sobreReportes} {r.sobreReportes === 1 ? 'reporte' : 'reportes'}</span
							>
						</div>
						{#if r.detalle}<p class="detalle">{r.detalle}</p>{/if}
						<input
							class="input"
							name="resolucion"
							maxlength="300"
							placeholder="Qué se hizo (queda registrado)"
						/>
						<div class="acc">
							<button class="gel gel-violeta b" name="decision" value="resolver">Resolver</button>
							{#if r.sobreId}
								<button class="b rechazar" name="decision" value="suspender"
									>Suspender a {r.sobreNombre}</button
								>
							{/if}
							<button class="b gris" name="decision" value="descartar">Descartar</button>
						</div>
					</form>
				{:else}
					<p class="vacia tarjeta-plana">No hay reportes abiertos.</p>
				{/each}
			</div>
		{/if}
	</main>
</div>

<style>
	.panel {
		min-height: 100vh;
		display: grid;
		grid-template-columns: 248px minmax(0, 1fr);
		background:
			url(/grain.png), radial-gradient(50% 50% at 100% 0%, #e3d9ff 0%, rgba(227, 217, 255, 0) 70%),
			#f3f0e9;
	}
	aside {
		position: sticky;
		top: 0;
		height: 100vh;
		padding: 28px 18px;
		display: flex;
		flex-direction: column;
		gap: 26px;
		background: url(/grain.png), radial-gradient(120% 60% at 0% 0%, #3b2f78 0%, #201d2b 70%);
		color: #faf9f6;
	}
	.marca {
		display: flex;
		flex-direction: column;
		gap: 6px;
		padding: 0 8px;
	}
	.caigo {
		font:
			700 40px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #faf9f6;
		text-decoration: none;
	}
	.para {
		font-size: 12px;
		font-weight: 800;
		letter-spacing: 1.5px;
		color: #c8f36b;
	}
	nav {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	nav a {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 14px;
		border-radius: 16px;
		font-size: 15px;
		font-weight: 700;
		color: #c9c4da;
		text-decoration: none;
	}
	nav a.on {
		background: rgba(255, 255, 255, 0.14);
		color: #ffffff;
		box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.15);
	}
	.n {
		margin-left: auto;
		padding: 3px 8px;
		border-radius: 9px;
		background: #ff7868;
		color: #24212d;
		font-size: 12px;
		font-weight: 800;
	}
	.abajo {
		margin-top: auto;
	}
	.salir {
		padding: 6px 14px;
		font-size: 13px;
		font-weight: 700;
		color: #a8a3bd;
	}
	main {
		min-width: 0;
		padding: 34px 44px 90px;
		max-width: 960px;
	}
	h1 {
		font:
			800 44px/1 'Geist',
			sans-serif;
		letter-spacing: -1.6px;
		margin-bottom: 24px;
	}
	.lista {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.item {
		display: flex;
		flex-direction: column;
		gap: 14px;
		padding: 20px 22px;
		border-radius: 24px;
	}
	.cab {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: 12px;
	}
	.cab > div {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.t {
		font:
			700 20px/1.15 'Geist',
			sans-serif;
	}
	.s {
		font-size: 13px;
		font-weight: 600;
		color: #5c586b;
	}
	.chip-dato {
		flex: none;
		padding: 6px 11px;
		border-radius: 11px;
		background: #f0ecff;
		font-size: 12px;
		font-weight: 800;
	}
	.chip-dato.alerta {
		background: #ffe1db;
		color: #9a2e1f;
	}
	.datos {
		display: flex;
		flex-direction: column;
		gap: 4px;
		font-size: 14px;
		color: #5c586b;
	}
	.datos b {
		color: #24212d;
	}
	.datos a {
		font-weight: 700;
	}
	.detalle {
		padding: 12px 14px;
		border-radius: 16px;
		background: #f5f2fc;
		font-size: 14px;
		line-height: 1.5;
	}
	.fotos {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
		gap: 10px;
	}
	.foto {
		display: flex;
		flex-direction: column;
		gap: 6px;
		font-size: 12px;
		font-weight: 800;
		color: #5c586b;
		text-decoration: none;
	}
	.foto img {
		width: 100%;
		aspect-ratio: 4 / 3;
		object-fit: cover;
		border-radius: 16px;
		background: #e4defa;
	}
	.acc {
		display: flex;
		gap: 10px;
		flex-wrap: wrap;
	}
	.b {
		height: 48px;
		padding: 0 20px;
		border-radius: 16px;
		font-size: 15px;
		font-weight: 800;
	}
	.rechazar {
		background: #ffe1db;
		color: #9a2e1f;
	}
	.gris {
		background: #f0ecff;
		color: #5b43c9;
	}
	.vacia {
		padding: 28px 22px;
		border-radius: 24px;
		font-size: 15px;
		font-weight: 600;
		color: #5c586b;
	}
	@media (max-width: 900px) {
		.panel {
			grid-template-columns: minmax(0, 1fr);
		}
		aside {
			position: static;
			height: auto;
			padding: 18px 16px;
			gap: 14px;
		}
		.caigo {
			font-size: 30px;
		}
		nav a {
			padding: 10px 12px;
		}
		.abajo {
			display: none;
		}
		main {
			padding: 22px 16px 60px;
		}
		h1 {
			font-size: 34px;
		}
		.item {
			padding: 18px 16px;
		}
		.cab {
			flex-wrap: wrap;
		}
	}
</style>
