<script lang="ts">
	import { sinViuda } from '#lib/app.ts';

	/*
	 * El vacío de Mis planes (Mockups v5, 13b): dos lugares punteados y el «+» lima, título, texto y
	 * una salida. Las apps lo usan igual para Mensajes y Mi gente (MiosVacio).
	 */
	let { titulo, texto, cta, href }: { titulo: string; texto: string; cta: string; href: string } =
		$props();
</script>

<div class="vacio">
	<div class="burbujas" aria-hidden="true">
		<span class="b1"></span><span class="b1"></span>
		<span class="b2"
			><svg width="34" height="34" viewBox="0 0 24 24"
				><path d="M12 4v16M4 12h16" stroke="#24212D" stroke-width="4" stroke-linecap="round" /></svg
			></span
		>
	</div>
	<h2>{sinViuda(titulo)}</h2>
	<p>{sinViuda(texto)}</p>
	<a class="gel gel-lima cta" {href}>{cta}</a>
</div>

<style>
	.vacio {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 18px;
		text-align: center;
		padding: 56px 8px 0;
	}
	.burbujas {
		display: flex;
		align-items: center;
	}
	.b1 {
		width: 76px;
		height: 76px;
		border-radius: 50%;
		background: var(--lavanda);
		border: 3px dashed #b9acf5;
	}
	.b1 + .b1 {
		margin-left: -18px;
	}
	.b2 {
		width: 84px;
		height: 84px;
		margin-left: -18px;
		border-radius: 50%;
		background: radial-gradient(circle at 32% 26%, #f2ffc8 0%, #c8f36b 40%, #a6d640 100%);
		box-shadow:
			inset 0 -4px 8px rgba(110, 150, 20, 0.3),
			0 12px 22px -8px rgba(140, 190, 40, 0.6);
		display: grid;
		place-items: center;
		border: 4px solid var(--c-fondo);
	}
	h2 {
		font:
			700 30px/1.08 'Geist',
			sans-serif;
		letter-spacing: -0.6px;
		text-wrap: balance;
	}
	p {
		font-size: 15px;
		line-height: 1.5;
		color: var(--texto-2);
		text-wrap: pretty;
	}
	.cta {
		align-self: stretch;
		margin-top: 8px;
		height: 58px;
		border-radius: 20px;
		font-size: 16px;
	}
</style>
