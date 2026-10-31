<script lang="ts">
	import Avatares, { type Avatar } from './Avatares.svelte';
	import { faltanTexto } from '#lib/plan.ts';

	/** La tarjeta de plan de "Ahora · lista" (Mockups v5, pantalla 02). */
	let {
		foto,
		tinte = '#5B43C9',
		categoria,
		cuando,
		titulo,
		donde,
		faltan,
		somos,
		total,
		personas,
		href,
		accion = 'Sumarme'
	}: {
		foto?: string;
		tinte?: string;
		categoria: string;
		cuando: string;
		titulo: string;
		donde: string;
		faltan: number;
		somos: number;
		total: number;
		personas: Avatar[];
		href?: string;
		accion?: string;
	} = $props();
</script>

<article class="tarjeta-plan">
	<div class="foto" style:background-color={tinte}>
		{#if foto}<img src={foto} alt="" loading="lazy" />{/if}
		<div class="chips">
			<span class="cat">{categoria}</span>
			<span class="cuando">{cuando}</span>
		</div>
		<span class={faltan > 0 ? 'badge-coral badge' : 'badge-completo badge'}>{faltanTexto(faltan)}</span>
	</div>
	<div class="cuerpo">
		<div class="txt">
			<h3>{titulo}</h3>
			<span class="donde">{donde}</span>
		</div>
		<div class="pie">
			<div class="quienes">
				<Avatares {personas} total={somos} />
				<span class="sub">Somos {somos} de {total}</span>
			</div>
			{#if href}
				<a class="gel gel-lima sumarme" {href}>
					<svg width="14" height="14" viewBox="0 0 24 24" aria-hidden="true"
						><path d="M12 4v16M4 12h16" stroke="#24212D" stroke-width="4" stroke-linecap="round" /></svg
					>{accion}</a
				>
			{/if}
		</div>
	</div>
</article>

<style>
	.tarjeta-plan {
		background: #ffffff;
		border-radius: 24px;
		overflow: hidden;
		box-shadow: var(--sh-tarjeta-plan);
	}
	.foto {
		position: relative;
		height: 150px;
	}
	.foto img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.chips {
		position: absolute;
		top: 12px;
		left: 12px;
		display: flex;
		gap: 6px;
	}
	.chips span {
		padding: 6px 10px;
		border-radius: 11px;
		font-size: 12px;
		font-weight: 700;
	}
	.cat {
		background: #faf9f6;
		color: #24212d;
	}
	.cuando {
		background: var(--grad-oscuro);
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
		color: #faf9f6;
	}
	.badge {
		position: absolute;
		right: 12px;
		bottom: 12px;
		padding: 7px 12px;
		border-radius: 13px;
		font-size: 18px;
	}
	.cuerpo {
		padding: 14px 16px 16px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.txt {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	h3 {
		font:
			700 20px/1.15 'Geist',
			sans-serif;
		letter-spacing: -0.3px;
	}
	.donde {
		font-size: 13px;
		color: var(--texto-2);
	}
	.pie {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 10px;
	}
	.quienes {
		display: flex;
		align-items: center;
		gap: 10px;
		min-width: 0;
	}
	.sub {
		margin-left: 6px;
		font-size: 13px;
		font-weight: 600;
		color: var(--texto-2);
		white-space: nowrap;
	}
	.sumarme {
		flex: none;
		gap: 6px;
		padding: 9px 14px;
		border-radius: 14px;
		font-size: 14px;
	}
</style>
