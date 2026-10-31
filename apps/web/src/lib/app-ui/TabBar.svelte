<script lang="ts">
	import { D } from '#lib/ui/Icono.svelte';

	/** La tab bar flotante de Mockups v5: Ahora · Explorar · [Crear] · Mis planes · Perfil. */
	let { activa }: { activa: 'ahora' | 'explorar' | 'mis' | 'perfil' | '' } = $props();

	const TABS = [
		{ k: 'ahora', l: 'Ahora', href: '/app', d: D.reloj },
		{ k: 'explorar', l: 'Explorar', href: '/app/explorar', d: D.lupa },
		{ k: 'crear', l: 'Crear', href: '/app/crear', d: '' },
		{ k: 'mis', l: 'Mis planes', href: '/app/mis', d: D.calendario },
		{ k: 'perfil', l: 'Perfil', href: '/app/perfil', d: D.usuario }
	] as const;
</script>

<nav class="tabbar" aria-label="Secciones">
	{#each TABS as t (t.k)}
		{#if t.k === 'crear'}
			<a class="tab crear" href={t.href} aria-label="Crear un plan">
				<img src="/caigo-icon.png" alt="" width="68" height="68" />
			</a>
		{:else}
			<a
				class="tab"
				class:on={activa === t.k}
				href={t.href}
				aria-current={activa === t.k ? 'page' : undefined}
			>
				<svg
					width="24"
					height="24"
					viewBox="0 0 24 24"
					fill="none"
					stroke="currentColor"
					stroke-width="2.2"
					stroke-linecap="round"
					stroke-linejoin="round"
					aria-hidden="true"><path d={t.d} /></svg
				>
				<span>{t.l}</span>
			</a>
		{/if}
	{/each}
</nav>

<style>
	.tabbar {
		position: absolute;
		left: 18px;
		right: 18px;
		bottom: calc(24px + env(safe-area-inset-bottom));
		height: 70px;
		border-radius: 35px;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(244, 240, 255, 0.94));
		backdrop-filter: blur(16px);
		-webkit-backdrop-filter: blur(16px);
		box-shadow:
			inset 0 1.5px 0 #ffffff,
			inset 0 -2px 0 rgba(116, 86, 241, 0.08),
			0 22px 40px -14px rgba(60, 40, 140, 0.45),
			0 0 0 1px rgba(116, 86, 241, 0.07);
		display: grid;
		grid-template-columns: repeat(5, 1fr);
		padding: 0 6px;
		z-index: 8;
	}
	.tab {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 2px;
		margin: 8px 3px;
		border-radius: 28px;
		color: #8a8699;
		text-decoration: none;
		-webkit-tap-highlight-color: transparent;
	}
	.tab span {
		font-size: 10.5px;
		font-weight: 500;
		white-space: nowrap;
	}
	.tab:hover {
		color: #7456f1;
	}
	.tab.on {
		color: #7456f1;
		background: linear-gradient(180deg, #f6f3ff, #e6deff);
		box-shadow:
			inset 0 1.5px 0 #ffffff,
			inset 0 -2px 0 rgba(116, 86, 241, 0.14),
			0 4px 10px -4px rgba(116, 86, 241, 0.35);
	}
	.tab.on span {
		font-weight: 800;
	}
	.crear img {
		width: 68px;
		height: 68px;
		margin-top: -30px;
		object-fit: contain;
		filter: drop-shadow(0 10px 12px rgba(116, 86, 241, 0.45));
		transition: transform 0.1s ease;
	}
	.crear:active img {
		transform: translateY(3px);
	}
</style>
