<script lang="ts">
	import { onDestroy } from 'svelte';
	import { enhance } from '$app/forms';
	import Icono from '#lib/ui/Icono.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { cuandoCorto } from '#lib/fechas.ts';
	import { faltanTexto } from '#lib/plan.ts';
	import { pesosDeCentavos } from '#lib/precios.ts';
	import type { Validacion } from '#lib/tipos.ts';

	/*
	 * Un plan del lugar: "Quiénes vienen" + "Check-in en la puerta" (Panel Lugares, vista "plan").
	 * El código se valida en la API; "Escanear QR" usa BarcodeDetector si el navegador lo tiene.
	 */
	let { data } = $props();

	const p = $derived(data.plan);
	const f = $derived(Math.max(0, p.cupo - p.confirmados));
	const aCobrar = $derived(data.asistentes.reduce((n, a) => n + (a.saldoCentavos ?? 0), 0));
	const checkins = $derived(data.asistentes.reduce((n, a) => n + (a.checkin ? a.cantidad : 0), 0));
	const personas = $derived(data.asistentes.reduce((n, a) => n + a.cantidad, 0));

	const PAGO = {
		pago: ['Pagó todo', '#C8F36B', '#24212D'],
		sena: ['Señó', '#FFE1DB', '#9A2E1F'],
		gratis: ['Gratis', '#F0ECFF', '#24212D']
	} as const;
	const pagoDe = (pago: 'gratis' | 'pago' | 'sena') =>
		p.cobro === 'entrada' && pago === 'pago'
			? (['Pagó', '#C8F36B', '#24212D'] as const)
			: pago === 'sena' && p.senaPct
				? ([`Señó ${p.senaPct}%`, '#FFE1DB', '#9A2E1F'] as const)
				: PAGO[pago];

	let codigo = $state('');
	let formValidar: HTMLFormElement | undefined = $state();
	let validando = $state(false);

	function mensaje(v: Validacion): string {
		if (v.repetida) return `${v.nombre} ya hizo check-in.`;
		let t = `Check-in de ${v.nombre}${v.cantidad > 1 ? ` (${v.cantidad})` : ''}`;
		t +=
			v.saldoCentavos > 0
				? `. Cobrá el resto en el lugar: ${pesosDeCentavos(v.saldoCentavos)}.`
				: '.';
		if (v.beneficio) t += ` Beneficio desbloqueado: ${v.beneficio}.`;
		return t;
	}

	function validarCodigo(c: string) {
		codigo = c;
		queueMicrotask(() => formValidar?.requestSubmit());
	}

	// ——— Cámara ———
	let escaneando = $state(false);
	let video: HTMLVideoElement | undefined = $state();
	let flujo: MediaStream | null = null;
	let timer: ReturnType<typeof setInterval> | undefined;

	function cerrarCamara() {
		clearInterval(timer);
		flujo?.getTracks().forEach((t) => t.stop());
		flujo = null;
		escaneando = false;
	}
	async function escanear() {
		const BD = (
			globalThis as {
				BarcodeDetector?: new (o: { formats: string[] }) => {
					detect: (v: HTMLVideoElement) => Promise<{ rawValue: string }[]>;
				};
			}
		).BarcodeDetector;
		if (!BD || !navigator.mediaDevices?.getUserMedia)
			return avisar('Este navegador no puede leer QR. Escribí el código de la entrada.');
		try {
			flujo = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } });
		} catch {
			return avisar('No pudimos abrir la cámara.');
		}
		escaneando = true;
		await Promise.resolve();
		if (!video) return cerrarCamara();
		video.srcObject = flujo;
		await video.play().catch(() => undefined);
		const det = new BD({ formats: ['qr_code'] });
		timer = setInterval(async () => {
			if (!video) return;
			const r = await det.detect(video).catch(() => []);
			const v = r[0]?.rawValue;
			if (!v) return;
			cerrarCamara();
			// El QR de la entrada trae el código (TK-1234), solo o dentro de un link.
			validarCodigo((/TK-\d{4}/i.exec(v)?.[0] ?? v).toUpperCase());
		}, 300);
	}
	onDestroy(cerrarCamara);
</script>

<svelte:head><title>{p.titulo} · Caigo para lugares</title></svelte:head>

<div class="vista">
	<div class="hero" style:background-image={p.foto ? `url(${p.foto})` : undefined}>
		<div class="velo"></div>
		<a class="atras" href="/" aria-label="Volver a tus planes"
			><Icono nombre="atras" color="#24212D" trazo={2.6} /></a
		>
		<div class="hero-pie">
			<div class="hero-txt">
				<span class="cuando">{cuandoCorto(p.empiezaEn)}{p.cancelado ? ' · Cancelado' : ''}</span>
				<h1>{p.titulo}</h1>
			</div>
			<span class={f === 0 ? 'badge-completo badge' : 'badge-coral badge'}>{faltanTexto(f)}</span>
		</div>
	</div>

	<div class="stats">
		<div class="tarjeta-plana stat">
			<span class="sl">Confirmados</span><span class="sv">{p.confirmados} de {p.cupo}</span>
		</div>
		<div class="tarjeta-plana stat">
			<span class="sl">Cobrado</span><span class="sv"
				>{p.cobro === 'gratis' ? 'Gratis' : pesosDeCentavos(p.cobradoCentavos)}</span
			>
		</div>
		<div class="tarjeta-plana stat">
			<span class="sl">A cobrar en el lugar</span><span class="sv">{pesosDeCentavos(aCobrar)}</span>
		</div>
		<div class="tarjeta-plana stat">
			<span class="sl">Check-ins</span><span class="sv">{checkins}</span>
		</div>
	</div>

	<div class="cuerpo">
		<section class="vienen tarjeta-plana" aria-labelledby="vienen">
			<div class="vienen-cab">
				<h2 id="vienen">Quiénes vienen</h2>
				<span>{data.asistentes.length} reservas · {personas} personas</span>
			</div>
			{#each data.asistentes as a, i (i)}
				{@const [pl, pbg, pfg] = pagoDe(a.pago)}
				<div class="asistente">
					<div class="av" style:background-image={a.foto ? `url(${a.foto})` : undefined}></div>
					<div class="a-txt">
						<span class="a-n">{a.nombre}</span>
						<span class="a-s"
							>{a.cantidad > 1 ? `${a.cantidad} personas` : 'Va solo'}{a.codigo
								? ` · ${a.codigo}`
								: ''}</span
						>
					</div>
					<span class="pago" style:background={pbg} style:color={pfg}>{pl}</span>
					{#if a.checkin}
						<span class="ck llego">Llegó</span>
					{:else if a.codigo}
						<button class="ck" type="button" onclick={() => validarCodigo(a.codigo!)}
							>Hacer check-in</button
						>
					{:else}
						<span class="ck vacio-ck"></span>
					{/if}
				</div>
			{:else}
				<p class="nadie">Todavía no se sumó nadie. Compartí el link del plan para&nbsp;arrancar.</p>
			{/each}
		</section>

		<section class="puerta tarjeta-oscura" aria-labelledby="puerta">
			<h2 id="puerta">Check-in en la puerta</h2>
			<p>Pedí el código de la entrada. Al validarlo se desbloquea el beneficio.</p>
			<form
				method="POST"
				action="?/validar"
				bind:this={formValidar}
				use:enhance={() => {
					validando = true;
					return async ({ result, update }) => {
						validando = false;
						if (result.type === 'success' && result.data?.validacion) {
							avisar(mensaje(result.data.validacion as Validacion));
							codigo = '';
						} else if (result.type === 'failure') {
							avisar(String(result.data?.error ?? 'Ese código no es de este plan.'));
						}
						await update({ reset: false, invalidateAll: true });
					};
				}}
			>
				<label class="sr" for="codigo">Código de la entrada</label>
				<input
					id="codigo"
					name="codigo"
					class="codigo"
					bind:value={codigo}
					placeholder="TK-4821"
					autocomplete="off"
					autocapitalize="characters"
				/>
				<button class="gel gel-lima validar" disabled={validando}>Validar entrada</button>
			</form>
			<button type="button" class="qr" onclick={escanear}>
				<Icono nombre="escanear" color="#FFFFFF" />Escanear QR con la cámara
			</button>
			<p class="ayuda">El código está en la entrada de la persona, en la app o en su Wallet.</p>
			{#if p.beneficio}
				<div class="beneficio">
					<Icono nombre="regalo" tam={18} color="#C8F36B" />Beneficio: {p.beneficio}
				</div>
			{/if}
		</section>
	</div>
</div>

{#if escaneando}
	<div class="camara" role="dialog" aria-modal="true" aria-label="Escanear QR">
		<video bind:this={video} playsinline muted></video>
		<div class="mira" aria-hidden="true"></div>
		<button class="gel gel-blanco cerrar-cam" type="button" onclick={cerrarCamara}>Cerrar</button>
	</div>
{/if}

<style>
	.vista {
		max-width: 1180px;
		display: flex;
		flex-direction: column;
		gap: 24px;
		animation: tk-up 0.35s ease both;
	}
	.hero {
		position: relative;
		height: 220px;
		border-radius: 32px;
		overflow: hidden;
		background-color: #5b43c9;
		background-size: cover;
		background-position: center 35%;
	}
	.velo {
		position: absolute;
		inset: 0;
		background: linear-gradient(90deg, rgba(32, 29, 43, 0.88) 0%, rgba(32, 29, 43, 0.35) 70%);
	}
	.atras {
		position: absolute;
		top: 18px;
		left: 18px;
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		background: rgba(255, 255, 255, 0.92);
	}
	.hero-pie {
		position: absolute;
		left: 32px;
		bottom: 26px;
		right: 32px;
		display: flex;
		justify-content: space-between;
		align-items: flex-end;
		gap: 20px;
		color: #ffffff;
		flex-wrap: wrap;
	}
	.hero-txt {
		display: flex;
		flex-direction: column;
		gap: 10px;
		min-width: 0;
	}
	.cuando {
		align-self: flex-start;
		padding: 6px 11px;
		border-radius: 12px;
		background: #c8f36b;
		color: #24212d;
		font-size: 12px;
		font-weight: 800;
	}
	h1 {
		font:
			800 36px/1.05 'Geist',
			sans-serif;
		letter-spacing: -1px;
	}
	.badge {
		padding: 10px 16px;
		border-radius: 16px;
		font-size: 22px;
		box-shadow: 0 3px 0 #d04a3a;
	}
	.badge-completo.badge {
		box-shadow: 0 3px 0 #94c43a;
	}
	.stats {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
		gap: 16px;
	}
	.stat {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding: 20px 22px;
		border-radius: 24px;
	}
	.sl {
		font-size: 13px;
		font-weight: 700;
		color: #5c586b;
	}
	.sv {
		font:
			800 32px/1 'Geist',
			sans-serif;
		letter-spacing: -1px;
	}
	.cuerpo {
		display: flex;
		flex-wrap: wrap;
		gap: 24px;
		align-items: flex-start;
	}
	.vienen {
		flex: 1 1 460px;
		min-width: 0;
		display: flex;
		flex-direction: column;
		border-radius: 28px;
		overflow: hidden;
	}
	.vienen-cab {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 12px;
		padding: 20px 22px;
		border-bottom: 1px solid #f0ecff;
	}
	h2 {
		font:
			700 22px/1.1 'Geist',
			sans-serif;
	}
	.vienen-cab span {
		font-size: 13px;
		font-weight: 700;
		color: #5c586b;
		text-align: right;
	}
	.asistente {
		display: grid;
		grid-template-columns: 48px minmax(0, 1fr) auto auto;
		gap: 14px;
		align-items: center;
		padding: 14px 22px;
		border-bottom: 1px solid #f5f2fc;
	}
	.av {
		width: 48px;
		height: 48px;
		border-radius: 50%;
		background-color: #e4defa;
		background-size: cover;
		background-position: center;
	}
	.a-txt {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.a-n {
		font-size: 15px;
		font-weight: 800;
	}
	.a-s {
		font-size: 12px;
		font-weight: 600;
		color: #5c586b;
	}
	.pago {
		padding: 6px 11px;
		border-radius: 11px;
		font-size: 12px;
		font-weight: 800;
		white-space: nowrap;
	}
	.ck {
		min-width: 112px;
		text-align: center;
		padding: 10px 14px;
		border-radius: 14px;
		font-size: 13px;
		font-weight: 800;
		white-space: nowrap;
		background: #24212d;
		color: #ffffff;
		box-shadow: 0 3px 0 #0e0d14;
	}
	.ck.llego {
		background: #c8f36b;
		color: #24212d;
		box-shadow: none;
	}
	.ck.vacio-ck {
		background: none;
		box-shadow: none;
		padding: 0;
	}
	.nadie {
		padding: 30px 22px;
		font-size: 14px;
		font-weight: 600;
		color: #5c586b;
	}
	.puerta {
		flex: 1 1 300px;
		min-width: 0;
		max-width: 420px;
		position: sticky;
		top: 34px;
		display: flex;
		flex-direction: column;
		gap: 14px;
		padding: 22px;
		border-radius: 28px;
	}
	.puerta p {
		font-size: 13px;
		font-weight: 600;
		line-height: 1.5;
		color: #d3cee2;
	}
	.puerta form {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.codigo {
		width: 100%;
		min-width: 0;
		height: 58px;
		padding: 0 18px;
		border-radius: 18px;
		border: 0;
		font:
			800 24px/1 'Geist',
			sans-serif;
		letter-spacing: 1px;
		text-transform: uppercase;
		color: #24212d;
		background: #ffffff;
	}
	.codigo::placeholder {
		color: #a8a3bd;
	}
	.validar {
		height: 54px;
		border-radius: 18px;
		font-size: 16px;
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.85),
			0 4px 0 #7fa82e;
	}
	.qr {
		height: 50px;
		border-radius: 18px;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 10px;
		font-size: 15px;
		font-weight: 800;
		color: #ffffff;
		background: rgba(255, 255, 255, 0.12);
		box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.18);
	}
	.beneficio {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 12px 14px;
		border-radius: 16px;
		background: rgba(255, 255, 255, 0.08);
		font-size: 13px;
		font-weight: 700;
	}
	.puerta .ayuda {
		font-size: 12px;
		color: #c9c4da;
	}
	.camara {
		position: fixed;
		inset: 0;
		z-index: 45;
		display: grid;
		place-items: center;
		background: #15131c;
	}
	.camara video {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.mira {
		position: relative;
		width: 240px;
		height: 240px;
		border-radius: 30px;
		box-shadow:
			0 0 0 3px #c8f36b,
			0 0 0 9999px rgba(21, 19, 28, 0.55);
	}
	.cerrar-cam {
		position: absolute;
		bottom: 40px;
		height: 54px;
		padding: 0 28px;
		border-radius: 18px;
		font-size: 16px;
	}
	@media (max-width: 700px) {
		.hero {
			height: auto;
			min-height: 200px;
			padding: 76px 20px 18px;
			border-radius: 26px;
		}
		.hero-pie {
			position: relative;
			left: auto;
			right: auto;
			bottom: auto;
			gap: 12px;
		}
		.vienen-cab {
			flex-wrap: wrap;
			padding: 18px 16px;
		}
		h1 {
			font-size: 28px;
		}
		.badge {
			font-size: 18px;
			padding: 8px 12px;
		}
		.stats {
			grid-template-columns: 1fr 1fr;
			gap: 12px;
		}
		.stat {
			padding: 16px;
		}
		.sv {
			font-size: 24px;
		}
		.asistente {
			grid-template-columns: 40px minmax(0, 1fr) auto;
			padding: 12px 16px;
			gap: 10px;
		}
		.av {
			width: 40px;
			height: 40px;
		}
		.asistente .ck {
			grid-column: 2 / -1;
			justify-self: start;
		}
		.asistente .ck.vacio-ck {
			display: none;
		}
		.puerta {
			position: static;
			max-width: none;
			flex-basis: 100%;
		}
		.vienen {
			flex-basis: 100%;
		}
	}
</style>
