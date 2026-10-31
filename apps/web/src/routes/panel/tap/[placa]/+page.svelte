<script lang="ts">
	import Icono from '#lib/ui/Icono.svelte';

	/*
	 * Placa de Caigo Tap para imprimir (Pases NFC, 3a "Placa de puerta · 10 × 10 cm" y 3b "Calco de
	 * barra"), con el QR de la placa para quien no tiene NFC. Al imprimir sale sólo la placa, a
	 * 10 × 10 cm (o 7 cm la de barra).
	 */
	let { data } = $props();

	const barra = $derived(data.placa.uso === 'barra');
</script>

<svelte:head><title>Placa {data.placa.nombre} · Caigo para lugares</title></svelte:head>

<div class="vista">
	<div class="cabeza">
		<div class="titulos">
			<div class="titulo-fila">
				<a class="atras" href="/tap" aria-label="Volver a Caigo Tap"
					><Icono nombre="atras" color="#24212D" trazo={2.6} /></a
				>
				<h1>{data.placa.nombre}</h1>
			</div>
			<p>Imprimila y pegala donde la gente la vea. El QR sirve para los celus sin&nbsp;NFC.</p>
		</div>
		<button type="button" class="gel gel-violeta imprimir" onclick={() => print()}>Imprimir</button>
	</div>

	<div class="hoja">
		<div class="placa" class:barra>
			<span class="brillo" aria-hidden="true"></span>
			<span class="caigo">Caigo</span>
			<svg
				class="qr"
				viewBox="0 0 {data.qr.lado} {data.qr.lado}"
				shape-rendering="crispEdges"
				role="img"
				aria-label="QR de la placa"
				><rect width="100%" height="100%" fill="#FFFFFF" /><path
					d={data.qr.d}
					fill="#24212D"
				/></svg
			>
			<div class="pie">
				<span class="t">Apoyá el celu o escaneá</span>
				<span class="s">{data.lugar?.nombre}{barra ? ' · CAIGO TAP' : ''}</span>
			</div>
		</div>
	</div>
</div>

<style>
	.vista {
		max-width: 1180px;
		display: flex;
		flex-direction: column;
		gap: 28px;
		animation: tk-up 0.35s ease both;
	}
	.titulo-fila {
		display: flex;
		align-items: center;
		gap: 14px;
	}
	.cabeza {
		display: flex;
		align-items: flex-end;
		gap: 14px;
		flex-wrap: wrap;
	}
	.atras {
		flex: none;
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		background: linear-gradient(180deg, #ffffff, #f3f0fa);
		box-shadow:
			inset 0 2px 0 #ffffff,
			0 3px 0 #dcd5ee;
	}
	.titulos {
		flex: 1 1 280px;
		display: flex;
		flex-direction: column;
		gap: 10px;
		min-width: 0;
	}
	h1 {
		font:
			800 38px/1 'Geist',
			sans-serif;
		letter-spacing: -1.3px;
	}
	.titulos p {
		font-size: 15px;
		font-weight: 600;
		line-height: 1.5;
		color: #5c586b;
	}
	.imprimir {
		height: 52px;
		padding: 0 26px;
		border-radius: 18px;
		font-size: 15px;
	}
	.hoja {
		display: grid;
		place-items: center;
		padding: 20px 0 40px;
	}
	/* 3a: placa de puerta. */
	.placa {
		width: 340px;
		max-width: 100%;
		aspect-ratio: 1;
		border-radius: 64px;
		position: relative;
		overflow: hidden;
		background:
			url(/grain.png), radial-gradient(90% 80% at 30% 15%, #3d3756 0%, #24212d 55%, #17151e 100%);
		color: #faf9f6;
		box-shadow:
			inset 0 2px 0 rgba(255, 255, 255, 0.18),
			inset 0 -6px 0 rgba(0, 0, 0, 0.35),
			0 10px 0 #0e0d14,
			0 40px 60px -26px rgba(36, 33, 45, 0.7);
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 16px;
		print-color-adjust: exact;
		-webkit-print-color-adjust: exact;
	}
	.brillo {
		position: absolute;
		left: 28px;
		top: 22px;
		width: 110px;
		height: 40px;
		border-radius: 20px;
		background: linear-gradient(180deg, rgba(255, 255, 255, 0.18), rgba(255, 255, 255, 0));
		transform: rotate(-12deg);
	}
	.caigo {
		font:
			700 30px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		white-space: nowrap;
	}
	.qr {
		width: 46%;
		border-radius: 22px;
		box-shadow: 0 6px 0 #0e0d14;
	}
	.pie {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 4px;
		text-align: center;
	}
	.t {
		font:
			800 22px/1 'Geist',
			sans-serif;
		letter-spacing: -0.6px;
	}
	.s {
		font-size: 13px;
		font-weight: 700;
		color: #c9c4da;
	}
	/* 3b: calco de barra, redondo y lima. */
	.placa.barra {
		width: 300px;
		border-radius: 50%;
		background:
			url(/grain.png), radial-gradient(80% 80% at 35% 25%, #f4ffd0 0%, #c8f36b 55%, #a3d445 100%);
		color: #24212d;
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.85),
			inset 0 -6px 0 rgba(96, 140, 10, 0.3),
			0 8px 0 #7fa82e,
			0 30px 50px -24px rgba(80, 120, 20, 0.6);
		gap: 10px;
	}
	.barra .brillo {
		display: none;
	}
	.barra .caigo {
		font-size: 22px;
	}
	.barra .qr {
		width: 40%;
		border-radius: 16px;
		box-shadow: none;
	}
	.barra .t {
		font-size: 17px;
	}
	.barra .s {
		font-size: 11px;
		font-weight: 800;
		letter-spacing: 1px;
		color: #24212d;
	}
	@media (max-width: 900px) {
		h1 {
			font-size: 32px;
		}
		.imprimir {
			width: 100%;
		}
	}
	@media print {
		@page {
			margin: 1.5cm;
		}
		:global(.panel > aside),
		.cabeza {
			display: none !important;
		}
		:global(.panel),
		:global(.panel > main) {
			display: block !important;
			padding: 0 !important;
			background: #ffffff !important;
		}
		.vista {
			animation: none;
		}
		.hoja {
			padding: 0;
			place-items: start;
		}
		.placa {
			width: 10cm;
			box-shadow: none;
		}
		.placa.barra {
			width: 7cm;
		}
	}
</style>
