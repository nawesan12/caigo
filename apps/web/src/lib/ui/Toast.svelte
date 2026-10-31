<script lang="ts">
	import { toast } from '#lib/toast.svelte.ts';
</script>

<div class="zona" role="status" aria-live="polite">
	{#if toast.texto}
		<div class="toast">{toast.texto}</div>
	{/if}
</div>

<style>
	.zona {
		position: fixed;
		left: 50%;
		bottom: 30px;
		z-index: 50;
		transform: translateX(-50%);
		width: max-content;
		max-width: calc(100vw - 32px);
		pointer-events: none;
	}
	.toast {
		padding: 14px 20px;
		border-radius: 18px;
		background: #24212d;
		color: #faf9f6;
		font-size: 14px;
		font-weight: 700;
		line-height: 1.4;
		text-align: center;
		text-wrap: pretty;
		box-shadow: 0 18px 30px -12px rgba(0, 0, 0, 0.5);
		animation: tk-up 0.25s ease both;
	}
</style>
