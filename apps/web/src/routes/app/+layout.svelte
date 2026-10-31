<script lang="ts">
	import { page } from '$app/state';
	import TabBar from '#lib/app-ui/TabBar.svelte';

	/*
	 * Caigo desde la web: las pantallas de Mockups v5 a 390 px. En el celular ocupan toda la pantalla;
	 * en la compu van centradas dentro del marco de teléfono del handoff (radio 48, borde #15131C).
	 */
	let { children } = $props();

	const TABS: Record<string, 'ahora' | 'explorar' | 'mis' | 'perfil'> = {
		'/app': 'ahora',
		'/app/mapa': 'ahora',
		'/app/explorar': 'explorar',
		'/app/mis': 'mis',
		'/app/perfil': 'perfil'
	};
	const activa = $derived(TABS[page.route.id ?? ''] ?? '');
</script>

<svelte:head>
	<meta name="robots" content="noindex" />
	<meta name="theme-color" content="#FAF9F6" />
</svelte:head>

<div class="lienzo">
	<a class="marca" href="/" aria-label="Caigo, inicio">Caigo</a>
	<div class="tel">
		{@render children()}
		{#if activa}<TabBar {activa} />{/if}
	</div>
</div>

<style>
	.lienzo {
		min-height: 100vh;
		min-height: 100dvh;
		background: #faf9f6;
	}
	.marca {
		display: none;
	}
	.tel {
		position: relative;
		height: 100vh;
		height: 100dvh;
		overflow: hidden;
		background:
			url(/grain.png),
			radial-gradient(90% 38% at 100% 0%, #ece5ff 0%, rgba(236, 229, 255, 0) 70%),
			#faf9f6;
		/* Las pantallas son position:absolute dentro del teléfono, como en el handoff. */
		isolation: isolate;
	}
	/* Cada pantalla scrollea adentro del teléfono. */
	.tel :global(.scroll) {
		position: absolute;
		inset: 0;
		overflow-y: auto;
		overscroll-behavior: contain;
		-webkit-overflow-scrolling: touch;
	}
	@media (min-width: 700px) {
		.lienzo {
			display: grid;
			place-items: center;
			padding: 32px 24px;
			background: #e9e6df;
		}
		.marca {
			display: block;
			position: fixed;
			top: 24px;
			left: 32px;
			font:
				700 30px/1 'Geist',
				sans-serif;
			letter-spacing: -0.045em;
			color: #201d2b;
			text-decoration: none;
		}
		.tel {
			width: 390px;
			height: min(844px, calc(100dvh - 64px));
			border-radius: 48px;
			box-shadow:
				0 0 0 10px #15131c,
				0 40px 80px rgba(36, 33, 45, 0.25);
		}
	}
</style>
