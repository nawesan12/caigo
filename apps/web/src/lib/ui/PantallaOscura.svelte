<script lang="ts">
	import type { Snippet } from 'svelte';

	/** El fondo del splash (00) de Mockups v5: grafito con grano y las dos manchas lima y coral. */
	let { children, ancho = 440 }: { children: Snippet; ancho?: number } = $props();
</script>

<div class="pantalla">
	<span class="mancha lima" aria-hidden="true"></span>
	<span class="mancha coral" aria-hidden="true"></span>
	<div class="contenido" style:max-width="{ancho}px">
		{@render children()}
	</div>
</div>

<style>
	.pantalla {
		position: relative;
		min-height: 100vh;
		min-height: 100dvh;
		overflow: hidden;
		display: flex;
		justify-content: center;
		background:
			url(/grain.png),
			radial-gradient(70% 45% at 50% 42%, #3a3450 0%, #262233 50%, #17151f 100%);
		color: #faf9f6;
	}
	.mancha {
		position: absolute;
		border-radius: 50%;
		pointer-events: none;
	}
	.lima {
		top: 120px;
		left: -60px;
		width: 180px;
		height: 180px;
		background: radial-gradient(circle at 35% 30%, #f2ffc8, #c8f36b 45%, #9ccb3a);
		opacity: 0.5;
		filter: blur(22px);
	}
	.coral {
		bottom: 150px;
		right: -50px;
		width: 150px;
		height: 150px;
		background: radial-gradient(circle at 35% 30%, #ffc4ba, #ff7868 50%, #e0503f);
		opacity: 0.45;
		filter: blur(24px);
	}
	.contenido {
		position: relative;
		width: 100%;
		display: flex;
		flex-direction: column;
		padding: 40px 20px 40px;
	}
</style>
