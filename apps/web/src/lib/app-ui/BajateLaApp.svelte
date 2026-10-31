<script lang="ts">
	import Tiendas from '#lib/ui/Tiendas.svelte';

	/**
	 * Lo que todavía es sólo de la app (Gente y Citas, pases de Wallet, Plus, check-in, DNI): la
	 * tarjeta de plan del handoff con el isotipo y las insignias de las tiendas.
	 */
	let { titulo = 'Bajate la app', texto }: { titulo?: string; texto: string } = $props();
</script>

<div class="bajate">
	<div class="cab">
		<img src="/caigo-icon.png" alt="" width="56" height="56" />
		<div>
			<span class="t">{titulo}</span>
			<span class="s">{texto}</span>
		</div>
	</div>
	<Tiendas chico />
</div>

<style>
	.bajate {
		display: flex;
		flex-direction: column;
		gap: 16px;
		padding: 16px;
		border-radius: 24px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plan);
	}
	.cab {
		display: flex;
		align-items: center;
		gap: 12px;
	}
	img {
		flex: none;
		width: 56px;
		height: 56px;
		filter: drop-shadow(0 8px 10px rgba(116, 86, 241, 0.3));
	}
	.cab div {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.t {
		font:
			700 18px/1.15 'Geist',
			sans-serif;
	}
	.s {
		font-size: 13px;
		line-height: 1.4;
		color: var(--texto-2);
		text-wrap: pretty;
	}
</style>
