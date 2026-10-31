<script lang="ts" module>
	const COLORES: [string, string][] = [
		['#7456F1', '#FFFFFF'],
		['#C8F36B', '#24212D'],
		['#FF7868', '#24212D'],
		['#F0ECFF', '#5B43C9'],
		['#201D2B', '#FFFFFF']
	];
	/** Un color fijo por nombre, para que la misma persona tenga siempre el mismo. */
	export function colorDe(nombre: string): [string, string] {
		let h = 0;
		for (const c of nombre) h = (h * 31 + c.charCodeAt(0)) >>> 0;
		return COLORES[h % COLORES.length];
	}
</script>

<script lang="ts">
	/** Avatar redondo del handoff: foto, o la inicial sobre un color con brillo. */
	let {
		nombre,
		foto,
		tam = 44,
		borde = ''
	}: { nombre: string; foto?: string; tam?: number; borde?: string } = $props();
	const [bg, fg] = $derived(colorDe(nombre || '?'));
</script>

<span
	class="av"
	style:width="{tam}px"
	style:height="{tam}px"
	style:background-color={bg}
	style:color={fg}
	style:font-size="{Math.round(tam * 0.4)}px"
	style:border={borde || undefined}
>
	{#if foto}<img src={foto} alt="" loading="lazy" />{:else}{(nombre?.[0] ?? '·').toUpperCase()}{/if}
</span>

<style>
	.av {
		flex: none;
		border-radius: 50%;
		display: grid;
		place-items: center;
		overflow: hidden;
		font-family: 'Geist', sans-serif;
		font-weight: 700;
		background-image: radial-gradient(
			circle at 30% 25%,
			rgba(255, 255, 255, 0.6),
			rgba(255, 255, 255, 0) 42%
		);
	}
	img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
</style>
