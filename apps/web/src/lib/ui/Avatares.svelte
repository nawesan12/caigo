<script lang="ts" module>
	/** Colores de los avatares del handoff (violeta, lima, coral…), en ronda. */
	const COLORES: [string, string][] = [
		['#7456F1', '#FFFFFF'],
		['#C8F36B', '#24212D'],
		['#FF7868', '#24212D']
	];
	export type Avatar = { nombre: string; foto?: string };
</script>

<script lang="ts">
	/** Avatares apilados con "+N" (tarjeta de plan de Mockups v5, pantalla 02). */
	let {
		personas,
		total = personas.length,
		tam = 30,
		max = 3
	}: { personas: Avatar[]; total?: number; tam?: number; max?: number } = $props();

	const vistos = $derived(personas.slice(0, max));
	const resto = $derived(Math.max(0, total - vistos.length));
</script>

<div class="pila" style:--t="{tam}px">
	{#each vistos as p, i (i)}
		{@const [bg, fg] = COLORES[i % COLORES.length]}
		<span class="av" style:background-color={bg} style:color={fg} title={p.nombre}>
			{#if p.foto}
				<img src={p.foto} alt="" loading="lazy" />
			{:else}
				{(p.nombre[0] ?? '·').toUpperCase()}
			{/if}
		</span>
	{/each}
	{#if resto > 0}
		<span class="av mas">+{resto}</span>
	{/if}
</div>

<style>
	.pila {
		display: flex;
		flex: none;
	}
	.av {
		width: var(--t);
		height: var(--t);
		margin-right: -8px;
		border-radius: 50%;
		border: 2px solid #ffffff;
		display: grid;
		place-items: center;
		overflow: hidden;
		font-size: 12px;
		font-weight: 800;
		background-image: radial-gradient(
			circle at 30% 25%,
			rgba(255, 255, 255, 0.6),
			rgba(255, 255, 255, 0) 42%
		);
	}
	.av img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.mas {
		background-color: #e4defa;
		color: #5b43c9;
	}
</style>
