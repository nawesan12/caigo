<script lang="ts">
	import { page } from '$app/state';
	import { PUBLIC_SITIO } from '$app/env/public';
	import Icono, { type NombreIcono } from '#lib/ui/Icono.svelte';
	import { iniciales } from '#lib/plan.ts';

	/*
	 * Chrome del panel: la barra lateral de `Caigo Panel Lugares.dc.html` (marca, lugar, "Publicar
	 * plan", navegación y "Ver la app como usuario →"). Debajo de 900 px pasa arriba, con la
	 * navegación en una fila que se desliza, para que el panel se pueda usar desde el teléfono.
	 */
	let { data, children } = $props();

	const NAV: { l: string; href: string; icono: NombreIcono }[] = [
		{ l: 'Planes', href: '/', icono: 'calendario' },
		{ l: 'Check-in de hoy', href: '/checkin', icono: 'escanear' },
		{ l: 'Caigo Tap', href: '/tap', icono: 'tap' },
		{ l: 'Cobros', href: '/cobros', icono: 'cobros' }
	];
	const ruta = $derived(page.url.pathname);
	const activo = (href: string) =>
		href === '/' ? ruta === '/' || ruta.startsWith('/plan/') : ruta.startsWith(href);
	const conChrome = $derived(data.lugar?.estado === 'aprobado' && ruta !== '/registro');
	let selector: HTMLFormElement | undefined = $state();
</script>

<svelte:head>
	<meta name="robots" content="noindex" />
	<meta name="theme-color" content="#201D2B" />
</svelte:head>

<div class="panel" class:solo={!conChrome}>
	{#if conChrome && data.lugar}
		<aside>
			<div class="marca">
				<a href="/" class="caigo">Caigo</a>
				<span class="para">PARA LUGARES</span>
			</div>
			<div class="lugar">
				<span class="avatar">{iniciales(data.lugar.nombre)}</span>
				<div class="lugar-txt">
					<span class="nombre">{data.lugar.nombre}</span>
					<span class="sub">Lugar aliado{data.lugar.zona ? ` · ${data.lugar.zona}` : ''}</span>
				</div>
				{#if data.lugares.length > 1}
					<Icono nombre="abajo" tam={16} color="#C9C4DA" trazo={2.4} />
					<form method="POST" action="/lugar" bind:this={selector} class="selector">
						<label class="sr" for="lugar-sel">Cambiar de lugar</label>
						<select
							id="lugar-sel"
							name="id"
							value={data.lugar.id}
							onchange={() => selector?.requestSubmit()}
						>
							{#each data.lugares as l (l.id)}
								<option value={l.id}
									>{l.nombre}{l.estado !== 'aprobado' ? ' (pendiente)' : ''}</option
								>
							{/each}
							<option value="nuevo">+ Sumar otro lugar</option>
						</select>
					</form>
				{/if}
			</div>
			<a class="gel gel-lima publicar" href="/nuevo">
				<svg width="16" height="16" viewBox="0 0 24 24" aria-hidden="true"
					><path
						d="M12 4v16M4 12h16"
						stroke="#24212D"
						stroke-width="3.6"
						stroke-linecap="round"
					/></svg
				>Publicar plan
			</a>
			<nav aria-label="Secciones del panel">
				{#each NAV as n (n.href)}
					<a
						href={n.href}
						class:on={activo(n.href)}
						aria-current={activo(n.href) ? 'page' : undefined}
					>
						<Icono nombre={n.icono} tam={20} />{n.l}
					</a>
				{/each}
			</nav>
			<div class="abajo">
				<a class="ver" href={PUBLIC_SITIO}>Ver la app como usuario →</a>
				<form method="POST" action="/salir">
					<button class="salir">Salir</button>
				</form>
			</div>
		</aside>
	{/if}
	<main>
		{@render children()}
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
		color: #24212d;
	}
	.panel.solo {
		grid-template-columns: minmax(0, 1fr);
	}
	aside {
		position: sticky;
		top: 0;
		height: 100vh;
		overflow-y: auto;
		padding: 28px 18px;
		display: flex;
		flex-direction: column;
		gap: 26px;
		background: url(/grain.png), radial-gradient(120% 60% at 0% 0%, #3b2f78 0%, #201d2b 70%);
		color: #faf9f6;
	}
	.marca {
		flex: none;
		display: flex;
		flex-direction: column;
		gap: 6px;
		padding: 0 8px;
	}
	.caigo {
		display: block;
		font:
			700 40px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #faf9f6;
		white-space: nowrap;
		text-decoration: none;
	}
	.para {
		font-size: 12px;
		font-weight: 800;
		letter-spacing: 1.5px;
		color: #c8f36b;
	}
	.lugar {
		position: relative;
		flex: none;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px;
		border-radius: 20px;
		background: rgba(255, 255, 255, 0.08);
		box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12);
	}
	.avatar {
		flex: none;
		width: 42px;
		height: 42px;
		border-radius: 14px;
		display: grid;
		place-items: center;
		background: linear-gradient(180deg, #ffb2a6, #ff7868 60%, #f05e4c);
		color: #24212d;
		font:
			800 16px/1 'Geist',
			sans-serif;
	}
	.lugar-txt {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.nombre {
		font-size: 14px;
		font-weight: 800;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.sub {
		font-size: 12px;
		font-weight: 600;
		color: #c9c4da;
	}
	.selector select {
		position: absolute;
		inset: 0;
		width: 100%;
		opacity: 0;
		cursor: pointer;
	}
	.publicar {
		flex: none;
		height: 52px;
		border-radius: 18px;
		gap: 8px;
		font-size: 15px;
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.85),
			inset 0 -4px 0 rgba(96, 140, 10, 0.3),
			0 4px 0 #7fa82e;
	}
	nav {
		flex: none;
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
		white-space: nowrap;
	}
	nav a:hover {
		color: #ffffff;
	}
	nav a.on {
		background: rgba(255, 255, 255, 0.14);
		color: #ffffff;
		box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.15);
	}
	.abajo {
		flex: none;
		margin-top: auto;
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.ver {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 12px 14px;
		border-radius: 16px;
		font-size: 13px;
		font-weight: 700;
		color: #c9c4da;
		text-decoration: none;
		background: rgba(255, 255, 255, 0.05);
	}
	.ver:hover {
		color: #ffffff;
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
	}
	.solo main {
		padding: 0;
	}

	/* Teléfono y tablet: la barra pasa arriba. */
	@media (max-width: 900px) {
		.panel {
			grid-template-columns: minmax(0, 1fr);
		}
		aside {
			position: static;
			height: auto;
			overflow: visible;
			padding: 18px 16px 14px;
			display: grid;
			grid-template-columns: auto minmax(0, 1fr);
			gap: 14px;
			align-items: center;
		}
		.marca {
			padding: 0;
			gap: 4px;
		}
		.caigo {
			font-size: 30px;
		}
		.para {
			font-size: 10.5px;
		}
		.lugar {
			padding: 8px;
			gap: 10px;
			border-radius: 16px;
		}
		.avatar {
			width: 34px;
			height: 34px;
			border-radius: 12px;
			font-size: 13px;
		}
		.publicar {
			grid-column: 1 / -1;
			height: 50px;
		}
		nav {
			grid-column: 1 / -1;
			display: grid;
			grid-template-columns: 1fr 1fr;
			gap: 4px;
		}
		nav a {
			padding: 10px 12px;
			font-size: 14px;
			gap: 10px;
		}
		.abajo {
			grid-column: 1 / -1;
			flex-direction: row;
			justify-content: space-between;
			align-items: center;
			margin-top: 0;
		}
		.ver {
			padding: 8px 12px;
			font-size: 12px;
		}
		main {
			padding: 22px 16px 80px;
		}
	}
</style>
