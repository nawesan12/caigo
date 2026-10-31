<script lang="ts">
	import type { Snippet } from 'svelte';

	/** Estado vacío de Mockups v5 (13a–13c): isotipo girado, título, texto y siempre una salida. */
	let {
		titulo,
		texto = '',
		children
	}: { titulo: string; texto?: string; children?: Snippet } = $props();
</script>

<div class="vacio">
	<img src="/caigo-icon.png" alt="" width="170" height="170" />
	<h2>{titulo}</h2>
	{#if texto}<p>{texto}</p>{/if}
	{#if children}<div class="salidas">{@render children()}</div>{/if}
</div>

<style>
	.vacio {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 16px;
		text-align: center;
		max-width: 360px;
		margin: 0 auto;
	}
	img {
		width: 170px;
		height: 170px;
		transform: rotate(-10deg);
		filter: drop-shadow(0 22px 24px rgba(116, 86, 241, 0.35));
	}
	h2 {
		font:
			700 30px/1.08 'Geist',
			sans-serif;
		letter-spacing: -0.6px;
	}
	p {
		font-size: 15px;
		line-height: 1.5;
		color: #5c586b;
	}
	.salidas {
		align-self: stretch;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 12px;
		margin-top: 6px;
	}
	.salidas :global(.gel) {
		align-self: stretch;
		height: 58px;
		border-radius: 20px;
		font-size: 16px;
	}
	.salidas :global(.link) {
		font-size: 14px;
		font-weight: 700;
		color: #5b43c9;
		text-decoration: none;
	}
</style>
