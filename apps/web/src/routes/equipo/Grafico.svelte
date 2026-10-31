<script lang="ts">
	/*
	 * Gráfico simple del backoffice: barras (una o dos series por semana) o línea, con los colores y
	 * las tarjetas del Panel Lugares. Las barras y los rótulos son HTML (se leen igual en el celu);
	 * la línea es un SVG estirado con trazo que no se deforma.
	 */
	type Serie = { nombre: string; valores: number[]; color: 'violeta' | 'lima' | 'coral' };
	let {
		titulo,
		etiquetas,
		series,
		tipo = 'barras',
		formato = (n: number) => String(Math.round(n))
	}: {
		titulo: string;
		etiquetas: string[];
		series: Serie[];
		tipo?: 'barras' | 'linea';
		formato?: (n: number) => string;
	} = $props();

	const COLOR = { violeta: '#7456F1', lima: '#C8F36B', coral: '#FF7868' };
	const max = $derived(Math.max(1, ...series.flatMap((s) => s.valores)));
	const ultimo = $derived(series.map((s) => s.valores[s.valores.length - 1] ?? 0));
	/** Puntos de la línea en un lienzo de 100 × 100 (x al centro de cada columna). */
	const puntos = (v: number[]) =>
		v.map((n, i) => `${((i + 0.5) / v.length) * 100},${100 - (n / max) * 100}`).join(' ');
</script>

<div class="tarjeta grafico">
	<div class="cab">
		<span class="kl">{titulo}</span>
		<div class="leyenda">
			{#each series as s, i (s.nombre)}
				<span class="ley"
					><span class="punto {s.color}"></span>{s.nombre}<b>{formato(ultimo[i])}</b></span
				>
			{/each}
		</div>
	</div>
	<div class="area" style:--n={etiquetas.length}>
		{#if tipo === 'barras'}
			{#each etiquetas as e, i (e)}
				<div class="col">
					<div class="barras">
						{#each series as s (s.nombre)}
							<span
								class="barra {s.color}"
								style:height="{Math.max(2, (s.valores[i] / max) * 100)}%"
								title="{s.nombre}: {formato(s.valores[i])}"
							></span>
						{/each}
					</div>
				</div>
			{/each}
		{:else}
			<svg viewBox="0 0 100 100" preserveAspectRatio="none" aria-hidden="true">
				{#each [25, 50, 75] as y (y)}
					<line x1="0" x2="100" y1={y} y2={y} class="guia" />
				{/each}
				{#each series as s (s.nombre)}
					<polyline points={puntos(s.valores)} stroke={COLOR[s.color]} class="linea" />
				{/each}
			</svg>
			{#each series as s (s.nombre)}
				{#each s.valores as n, i (i)}
					<span
						class="nodo"
						style:left="{((i + 0.5) / s.valores.length) * 100}%"
						style:bottom="calc((100% - 8px) * {n / max})"
						style:background={COLOR[s.color]}
						title="{s.nombre}: {formato(n)}"
					></span>
				{/each}
			{/each}
		{/if}
	</div>
	<div class="ejes" style:--n={etiquetas.length}>
		{#each etiquetas as e (e)}<span>{e}</span>{/each}
	</div>
</div>

<style>
	.grafico {
		display: flex;
		flex-direction: column;
		gap: 14px;
		padding: 20px 22px;
		min-width: 0;
	}
	.cab {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.kl {
		font-size: 13px;
		font-weight: 700;
		color: #5c586b;
	}
	.leyenda {
		display: flex;
		flex-wrap: wrap;
		gap: 6px 14px;
	}
	.ley {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		font-size: 12px;
		font-weight: 700;
		color: #6b6780;
		white-space: nowrap;
	}
	.ley b {
		font-size: 15px;
		font-weight: 800;
		color: #24212d;
	}
	.punto {
		width: 10px;
		height: 10px;
		border-radius: 4px;
	}
	.violeta {
		background: linear-gradient(180deg, #b3a3ff 0%, #8a70ff 30%, #7456f1 62%, #5f41e0 100%);
	}
	.lima {
		background: linear-gradient(180deg, #f0ffc2 0%, #d6fa80 32%, #c8f36b 62%, #abdb4b 100%);
	}
	.coral {
		background: linear-gradient(180deg, #ffb2a6 0%, #ff7868 55%, #f05e4c 100%);
	}
	.area {
		position: relative;
		height: 150px;
		display: grid;
		grid-template-columns: repeat(var(--n), 1fr);
		gap: 6px;
		padding: 8px 0 0;
		border-bottom: 1.5px solid #f0ecff;
	}
	.col {
		display: flex;
		align-items: flex-end;
		justify-content: center;
		min-width: 0;
	}
	.barras {
		height: 100%;
		width: 100%;
		max-width: 44px;
		display: flex;
		align-items: flex-end;
		gap: 3px;
	}
	.barra {
		flex: 1;
		border-radius: 7px 7px 3px 3px;
	}
	.barra.violeta {
		box-shadow: inset 0 2px 1px rgba(255, 255, 255, 0.45);
	}
	.barra.lima {
		box-shadow: inset 0 2px 1px rgba(255, 255, 255, 0.85);
	}
	svg {
		position: absolute;
		inset: 8px 0 0;
		width: 100%;
		height: calc(100% - 8px);
		overflow: visible;
	}
	.guia {
		stroke: #f0ecff;
		stroke-width: 1;
		vector-effect: non-scaling-stroke;
	}
	.linea {
		fill: none;
		stroke-width: 3;
		stroke-linecap: round;
		stroke-linejoin: round;
		vector-effect: non-scaling-stroke;
	}
	.nodo {
		position: absolute;
		width: 10px;
		height: 10px;
		margin: 0 0 -5px -5px;
		border-radius: 50%;
		border: 2px solid #ffffff;
		box-shadow: 0 1px 3px rgba(36, 33, 45, 0.25);
	}
	.ejes {
		display: grid;
		grid-template-columns: repeat(var(--n), 1fr);
		gap: 6px;
		margin-top: -6px;
	}
	.ejes span {
		text-align: center;
		font-size: 11px;
		font-weight: 700;
		color: #6b6780;
		white-space: nowrap;
	}
</style>
