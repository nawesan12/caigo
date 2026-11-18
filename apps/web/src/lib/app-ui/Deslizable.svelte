<script lang="ts">
	import { onMount, type Snippet } from 'svelte';

	/**
	 * La tarjeta de la lista con gestos de un pulgar (A1 del informe del 18/11): a la derecha «Caigo»
	 * (fondo lima), a la izquierda «No me interesa» (fondo grafito). La lista no cambia: el gesto es
	 * un atajo de los botones. Para teclado y lectores de pantalla, dos botones que aparecen al
	 * enfocarlos hacen lo mismo. Con «reducir movimiento», sin animación de vuelo.
	 */
	let {
		children,
		titulo,
		onderecha,
		onizquierda,
		pista = false
	}: {
		children: Snippet;
		titulo: string;
		/** null: el plan no admite «Caigo» (ya estás, está completo). */
		onderecha: (() => void) | null;
		onizquierda: () => void;
		/**
		 * La primera vez (una sola, en este navegador), la tarjeta se corre unos 40 px a cada lado y deja
		 * ver los dos fondos: así se descubre el gesto sin un tour ni texto.
		 */
		pista?: boolean;
	} = $props();

	const PISTA = 'caigo-pista-gestos';
	onMount(() => {
		if (!pista || quieto()) return;
		try {
			if (localStorage.getItem(PISTA)) return;
			localStorage.setItem(PISTA, '1');
		} catch {
			return;
		}
		const ts = [
			setTimeout(() => (dx = onderecha ? 44 : -44), 700),
			setTimeout(() => (dx = -44), 1300),
			setTimeout(() => (dx = 0), 1900)
		];
		return () => ts.forEach(clearTimeout);
	});

	let dx = $state(0);
	let arrastrando = $state(false);
	let saliendo = $state<'' | 'izq'>('');
	let ancho = 1;
	let x0 = 0;
	let y0 = 0;
	let eje: '' | 'h' | 'v' = '';
	let movido = false;
	let paso = false;
	const umbral = () => ancho * 0.35;

	const quieto = () =>
		typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;

	function bajar(e: PointerEvent) {
		if (e.pointerType === 'mouse' && e.button !== 0) return;
		ancho = (e.currentTarget as HTMLElement).offsetWidth || 1;
		x0 = e.clientX;
		y0 = e.clientY;
		eje = '';
		movido = false;
		paso = false;
		arrastrando = true;
	}

	function mover(e: PointerEvent) {
		if (!arrastrando) return;
		const mx = e.clientX - x0;
		const my = e.clientY - y0;
		if (!eje) {
			if (Math.abs(mx) < 8 && Math.abs(my) < 8) return;
			eje = Math.abs(mx) > Math.abs(my) ? 'h' : 'v';
			if (eje === 'h') (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
		}
		if (eje !== 'h') return;
		movido = true;
		// A la derecha sólo si hay «Caigo»; si no, se resiste.
		dx = mx > 0 && !onderecha ? mx * 0.15 : mx;
		const pasa = Math.abs(dx) >= umbral();
		if (pasa !== paso) {
			paso = pasa;
			if (pasa) navigator.vibrate?.(10);
		}
	}

	function soltar() {
		if (!arrastrando) return;
		arrastrando = false;
		const d = dx;
		if (eje === 'h' && d <= -umbral()) {
			if (quieto()) {
				dx = 0;
				onizquierda();
				return;
			}
			saliendo = 'izq';
			dx = -ancho * 1.2;
			setTimeout(() => {
				onizquierda();
				saliendo = '';
				dx = 0;
			}, 200);
			return;
		}
		dx = 0;
		if (eje === 'h' && d >= umbral() && onderecha) onderecha();
	}

	/** Si hubo arrastre, el toque no abre el plan. */
	function click(e: MouseEvent) {
		if (movido) {
			e.preventDefault();
			e.stopPropagation();
			movido = false;
		}
	}
</script>

<div class="deslizable" class:derecha={dx > 0} class:izquierda={dx < 0}>
	<div class="fondo" aria-hidden="true">
		<span class="caigo" class:listo={dx >= umbral()}>
			<svg width="18" height="18" viewBox="0 0 24 24"
				><path d="M12 4v16M4 12h16" stroke="#24212D" stroke-width="4" stroke-linecap="round" /></svg
			>Sumarme
		</span>
		<span class="no" class:listo={-dx >= umbral()}>No me interesa</span>
	</div>
	<div
		class="frente"
		class:suelto={!arrastrando}
		class:saliendo={saliendo === 'izq'}
		style:transform={dx ? `translateX(${dx}px) rotate(${dx / 60}deg)` : undefined}
		onpointerdown={bajar}
		onpointermove={mover}
		onpointerup={soltar}
		onpointercancel={soltar}
		onclickcapture={click}
		ondragstart={(e) => e.preventDefault()}
		role="presentation"
	>
		{@render children()}
	</div>
	<div class="acciones">
		{#if onderecha}<button type="button" aria-label="Sumarme a {titulo}" onclick={onderecha}
				>Sumarme</button
			>{/if}
		<button type="button" aria-label="No me interesa {titulo}" onclick={onizquierda}
			>No me interesa</button
		>
	</div>
</div>

<style>
	.deslizable {
		position: relative;
	}
	.fondo {
		position: absolute;
		inset: 0;
		border-radius: 24px;
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0 24px;
		opacity: 0;
		font-size: 17px;
		font-weight: 800;
	}
	.derecha .fondo {
		opacity: 1;
		background: var(--grad-lima);
		color: var(--carbon);
	}
	.izquierda .fondo {
		opacity: 1;
		background: var(--grad-oscuro);
		color: var(--blanco-calido);
	}
	.fondo span {
		display: inline-flex;
		align-items: center;
		gap: 6px;
		transition: transform 0.15s ease;
	}
	.fondo .listo {
		transform: scale(1.08);
	}
	.derecha .no,
	.izquierda .caigo {
		visibility: hidden;
	}
	.frente {
		position: relative;
		touch-action: pan-y;
		user-select: none;
		-webkit-user-select: none;
	}
	.frente.suelto {
		transition: transform 0.22s cubic-bezier(0.2, 0.8, 0.2, 1);
	}
	.frente.saliendo {
		opacity: 0;
		transition:
			transform 0.2s ease-in,
			opacity 0.2s ease-in;
	}
	.acciones button {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip-path: inset(50%);
		white-space: nowrap;
	}
	.acciones button:focus-visible {
		position: static;
		width: auto;
		height: auto;
		clip-path: none;
		margin-top: 8px;
		margin-right: 8px;
		padding: 8px 12px;
		border-radius: 12px;
		background: var(--c-gel-blanco);
		box-shadow: var(--sh-gel-blanco);
		font-size: 14px;
		font-weight: 700;
	}
	@media (prefers-reduced-motion: reduce) {
		.frente,
		.frente.suelto,
		.frente.saliendo,
		.fondo span {
			transition: none;
		}
	}
</style>
