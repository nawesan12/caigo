<script lang="ts">
	/** «Ocultaste Truco en Güemes · Deshacer», 5 s abajo (después de «No me interesa»). */
	let { texto, ondeshacer }: { texto: string; ondeshacer: () => void } = $props();
</script>

<div class="zona" role="status" aria-live="polite">
	{#if texto}
		<div class="barra">
			<span class="t">{texto}</span>
			<button type="button" onclick={ondeshacer}>Deshacer</button>
		</div>
	{/if}
</div>

<style>
	.zona {
		position: absolute;
		left: 16px;
		right: 16px;
		bottom: calc(108px + env(safe-area-inset-bottom));
		z-index: 50;
		display: flex;
		justify-content: center;
		pointer-events: none;
	}
	.barra {
		pointer-events: auto;
		display: flex;
		align-items: center;
		gap: 14px;
		max-width: 100%;
		padding: 10px 10px 10px 18px;
		border-radius: 18px;
		background: #24212d;
		color: #faf9f6;
		font-size: 14px;
		font-weight: 700;
		box-shadow: 0 18px 30px -12px rgba(0, 0, 0, 0.5);
		animation: sube 0.2s ease both;
	}
	.t {
		min-width: 0;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	button {
		flex: none;
		padding: 8px 12px;
		border-radius: 12px;
		background: rgba(200, 243, 107, 0.16);
		color: #c8f36b;
		font-size: 14px;
		font-weight: 800;
	}
	@keyframes sube {
		from {
			opacity: 0;
			transform: translateY(10px);
		}
	}
	@media (prefers-reduced-motion: reduce) {
		.barra {
			animation: none;
		}
	}
</style>
