<script lang="ts">
	import { onDestroy, tick } from 'svelte';
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import Icono from '#lib/ui/Icono.svelte';
	import { avisar } from '#lib/toast.svelte.ts';

	/*
	 * Verificación de Mockups v5: 01d "¿Cuál es tu número?" (paso 1 de 2) y 01e "Ahora, una selfie"
	 * (paso 2 de 2). La selfie se saca con la cámara del navegador: una guía de unos 4 s con el anillo
	 * de progreso del handoff y la foto al final (sin análisis automático). Sin cámara, se sube una foto.
	 */
	let { data, form } = $props();

	// ——— Paso 1 ———
	let telefono = $state('');
	let codigo = $state('');
	let enviando = $state(false);
	let espera = $state(0);
	let reloj: ReturnType<typeof setInterval> | undefined;
	const enviado = $derived(!!form && 'enviado' in form && form.enviado);

	$effect(() => {
		if (form && 'telefono' in form && form.telefono && !telefono) telefono = String(form.telefono);
		if (form && 'codigo' in form && form.codigo) codigo = String(form.codigo);
	});

	function arrancarEspera() {
		espera = 30;
		clearInterval(reloj);
		reloj = setInterval(() => {
			espera = Math.max(0, espera - 1);
			if (!espera) clearInterval(reloj);
		}, 1000);
	}

	// ——— Paso 2 ———
	let video: HTMLVideoElement | undefined = $state();
	let flujo: MediaStream | null = null;
	let camara = $state<'apagada' | 'prendida' | 'sin'>('apagada');
	let progreso = $state(0);
	let grabando = $state(false);
	let subiendo = $state(false);
	let anim: number | undefined;
	const INSTRUCCIONES = [
		'Mirá de frente a la cámara',
		'Girá la cabeza despacio a la izquierda',
		'Volvé despacio al centro'
	];
	const instruccion = $derived(
		!grabando && progreso === 0
			? 'Girá la cabeza despacio a la izquierda'
			: progreso >= 1
				? 'Listo'
				: INSTRUCCIONES[Math.min(2, Math.floor(progreso * 3))]
	);

	async function prenderCamara() {
		try {
			flujo = await navigator.mediaDevices.getUserMedia({
				video: { facingMode: 'user', width: { ideal: 720 }, height: { ideal: 720 } },
				audio: false
			});
			camara = 'prendida';
			await tick();
			if (video) {
				video.srcObject = flujo;
				await video.play().catch(() => undefined);
			}
			return true;
		} catch {
			camara = 'sin';
			return false;
		}
	}

	function apagar() {
		flujo?.getTracks().forEach((t) => t.stop());
		flujo = null;
		if (anim) cancelAnimationFrame(anim);
	}
	onDestroy(() => {
		apagar();
		clearInterval(reloj);
	});

	async function verificarme() {
		if (grabando || subiendo) return;
		if (camara !== 'prendida' && !(await prenderCamara())) return;
		grabando = true;
		progreso = 0;
		const ini = performance.now();
		const DUR = 4000;
		await new Promise<void>((fin) => {
			const paso = (t: number) => {
				progreso = Math.min(1, (t - ini) / DUR);
				if (progreso < 1) anim = requestAnimationFrame(paso);
				else fin();
			};
			anim = requestAnimationFrame(paso);
		});
		const foto = await sacarFoto();
		grabando = false;
		if (foto) await subir(foto);
	}

	function sacarFoto(): Promise<Blob | null> {
		if (!video || !video.videoWidth) return Promise.resolve(null);
		const lado = Math.min(video.videoWidth, video.videoHeight);
		const c = document.createElement('canvas');
		c.width = c.height = Math.min(lado, 720);
		const ctx = c.getContext('2d');
		if (!ctx) return Promise.resolve(null);
		ctx.drawImage(
			video,
			(video.videoWidth - lado) / 2,
			(video.videoHeight - lado) / 2,
			lado,
			lado,
			0,
			0,
			c.width,
			c.height
		);
		return new Promise((r) => c.toBlob((b) => r(b), 'image/jpeg', 0.88));
	}

	async function subir(foto: Blob) {
		subiendo = true;
		try {
			const r = await fetch('/app/verificar/selfie', {
				method: 'POST',
				headers: { 'content-type': foto.type || 'image/jpeg' },
				body: foto
			});
			const j = await r.json().catch(() => ({}));
			if (!r.ok) {
				progreso = 0;
				return avisar(j.error || 'No pudimos subir la selfie. Probá de nuevo.');
			}
			apagar();
		} catch {
			progreso = 0;
			avisar('No pudimos subir la selfie. Probá de nuevo.');
		} finally {
			subiendo = false;
		}
		if (!flujo && progreso >= 1)
			await goto(data.volver, { replaceState: true, invalidateAll: true }).catch(() => undefined);
	}

	async function desdeArchivo(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const f = input.files?.[0];
		input.value = '';
		if (f) {
			progreso = 1;
			await subir(f);
		}
	}
</script>

<svelte:head><title>Verificación · Caigo</title></svelte:head>

<div class="scroll">
	<div class="pantalla">
		<div class="arriba">
			{#if data.paso === 1}
				<a class="icono-btn" href="/" aria-label="Volver a Caigo"
					><Icono nombre="atras" tam={22} /></a
				>
			{:else}
				<span></span>
			{/if}
			<span class="paso">Paso {data.paso} de 2</span>
		</div>

		{#if data.paso === 1}
			<div class="txt">
				<h1>¿Cuál es tu número?</h1>
				<p>Te mandamos un código por SMS. No aparece en tu perfil.</p>
			</div>
			<form
				method="POST"
				action={enviado ? '?/confirmar' : '?/telefono'}
				class="form"
				id="form-tel"
				use:enhance={() => {
					enviando = true;
					const pidio = !enviado;
					return async ({ result, update }) => {
						enviando = false;
						if (result.type === 'failure') avisar(String(result.data?.error ?? 'No se pudo.'));
						else if (pidio && result.type === 'success') arrancarEspera();
						await update({ reset: false });
					};
				}}
			>
				<div class="tel">
					<span class="prefijo">AR +54</span>
					<input
						class="numero"
						name="telefono"
						type="tel"
						inputmode="tel"
						autocomplete="tel-national"
						placeholder="223 555 0142"
						aria-label="Tu número de celular"
						bind:value={telefono}
						readonly={enviado}
					/>
				</div>
				{#if enviado}
					<div class="codigo-bloque">
						<div class="codigo-cab">
							<span class="cl">Código</span>
							{#if espera}
								<span class="reenviar">Reenviar en 0:{String(espera).padStart(2, '0')}</span>
							{:else}
								<button
									class="reenviar"
									type="submit"
									formaction="?/telefono"
									onclick={() => (codigo = '')}>Reenviar código</button
								>
							{/if}
						</div>
						<label class="celdas">
							<span class="sr">Código de 6 números</span>
							<input
								name="codigo"
								inputmode="numeric"
								autocomplete="one-time-code"
								maxlength="6"
								bind:value={codigo}
								oninput={() => (codigo = codigo.replace(/\D/g, '').slice(0, 6))}
							/>
							{#each [0, 1, 2, 3, 4, 5] as i (i)}
								<span class="celda" class:llena={!!codigo[i]} class:actual={codigo.length === i}
									>{codigo[i] ?? ''}</span
								>
							{/each}
						</label>
					</div>
				{/if}
			</form>
			<div class="pie">
				<span class="nota">Caigo es solo para mayores de 18.</span>
				<button class="gel gel-lima cta" form="form-tel" disabled={enviando}>Seguir</button>
			</div>
		{:else}
			<div class="txt">
				<h1>Ahora, una selfie</h1>
				<p>Es para confirmar que sos una persona real. No se publica.</p>
			</div>
			<div class="aro" style:--p="{Math.round(progreso * 100)}%">
				<div class="cara">
					{#if camara === 'prendida'}
						<video bind:this={video} playsinline muted autoplay></video>
					{:else}
						<Icono nombre="usuario" tam={96} color="var(--c-acento)" trazo={1.4} />
					{/if}
				</div>
			</div>
			<div class="instruccion"><span>{instruccion}</span></div>
			<div class="chips">
				{#each ['Buena luz', 'Cara centrada', 'Sin lentes de sol'] as c, i (c)}
					{@const ok =
						progreso > (i + 1) / 3 - 0.01 || (i < 2 && camara === 'prendida' && !grabando)}
					<span class="chip-v" class:ok>
						{#if ok}<Icono nombre="check" tam={14} color="#24212D" trazo={3} />{/if}{c}
					</span>
				{/each}
			</div>
			<div class="pie">
				<span class="nota"
					><Icono nombre="escudo" tam={16} color="var(--c-acento)" trazo={2} />Prueba de vida · tus
					datos viajan encriptados</span
				>
				{#if camara === 'sin'}
					<label class="gel gel-lima cta">
						<input type="file" accept="image/*" capture="user" class="sr" onchange={desdeArchivo} />
						{subiendo ? 'Subiendo…' : 'Sacar una selfie'}
					</label>
					<span class="nota">No pudimos usar la cámara: sacate una foto con el celu.</span>
				{:else}
					<button class="gel gel-lima cta" onclick={verificarme} disabled={grabando || subiendo}>
						{subiendo ? 'Verificando…' : grabando ? 'Quedate así…' : 'Verificarme'}
					</button>
				{/if}
			</div>
		{/if}
	</div>
</div>

<style>
	.pantalla {
		min-height: 100%;
		padding: calc(20px + env(safe-area-inset-top)) 24px calc(36px + env(safe-area-inset-bottom));
		display: flex;
		flex-direction: column;
		gap: 28px;
	}
	.arriba {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-left: -4px;
	}
	.icono-btn {
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		color: var(--carbon);
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
	}
	.paso {
		font-size: 14px;
		font-weight: 700;
		color: var(--texto-2);
	}
	.txt {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	h1 {
		font:
			700 34px/1.05 'Geist',
			sans-serif;
		letter-spacing: -0.8px;
	}
	.txt p {
		font-size: 15px;
		line-height: 1.5;
		color: var(--texto-2);
	}
	.form {
		display: flex;
		flex-direction: column;
		gap: 28px;
	}
	.tel {
		display: flex;
		gap: 10px;
	}
	.prefijo {
		flex: none;
		height: 60px;
		padding: 0 16px;
		border-radius: 18px;
		background: var(--c-tarjeta);
		border: 1.5px solid #e6e1f2;
		display: flex;
		align-items: center;
		font-size: 17px;
		font-weight: 700;
	}
	.numero {
		flex: 1;
		min-width: 0;
		height: 60px;
		padding: 0 18px;
		border-radius: 18px;
		background: var(--c-tarjeta);
		border: 2px solid #7456f1;
		font-size: 18px;
		font-weight: 600;
		letter-spacing: 0.5px;
	}
	.numero::placeholder {
		color: #b9b4cc;
	}
	.numero[readonly] {
		border: 1.5px solid #e6e1f2;
	}
	.codigo-bloque {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.codigo-cab {
		display: flex;
		justify-content: space-between;
		font-size: 14px;
	}
	.cl {
		font-weight: 700;
	}
	.reenviar {
		color: #7456f1;
		font-weight: 700;
		font-size: 14px;
	}
	.celdas {
		position: relative;
		display: grid;
		grid-template-columns: repeat(6, 1fr);
		gap: 8px;
	}
	.celdas input {
		position: absolute;
		inset: 0;
		opacity: 0;
		width: 100%;
		font-size: 16px;
	}
	.celda {
		height: 58px;
		border-radius: 16px;
		background: var(--c-tarjeta);
		border: 1.5px solid #e6e1f2;
		display: grid;
		place-items: center;
		font:
			700 26px 'Geist',
			sans-serif;
	}
	.celda.llena {
		border: 0;
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
	}
	.celda.actual {
		border: 2px solid #7456f1;
	}
	.pie {
		margin-top: auto;
		display: flex;
		flex-direction: column;
		gap: 16px;
		align-items: center;
	}
	.nota {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 13px;
		color: var(--texto-2);
		text-align: center;
	}
	.cta {
		align-self: stretch;
		height: 60px;
		border-radius: 20px;
		font-size: 17px;
		color: #201d2b;
	}
	/* 01e */
	.aro {
		align-self: center;
		width: 276px;
		height: 276px;
		border-radius: 50%;
		padding: 8px;
		background: conic-gradient(#7456f1 0 var(--p), #e4defa var(--p) 100%);
		flex: none;
	}
	.cara {
		width: 100%;
		height: 100%;
		border-radius: 50%;
		overflow: hidden;
		background: #e9e4f7;
		border: 6px solid #faf9f6;
		display: grid;
		place-items: center;
	}
	.cara video {
		width: 100%;
		height: 100%;
		object-fit: cover;
		transform: scaleX(-1);
	}
	.instruccion {
		display: flex;
		justify-content: center;
		margin-top: -6px;
	}
	.instruccion span {
		padding: 10px 16px;
		border-radius: 14px;
		background: url(/grain.png), radial-gradient(120% 90% at 0% 0%, #3d3756 0%, #24212d 62%);
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
		color: #faf9f6;
		font-size: 14px;
		font-weight: 600;
		text-align: center;
	}
	/* Una sola fila, como en el handoff (a 375 px usa también los márgenes). */
	.chips {
		display: flex;
		gap: 6px;
		justify-content: center;
		margin: -10px -24px 0;
	}
	.chip-v {
		display: flex;
		align-items: center;
		gap: 4px;
		padding: 8px 10px;
		border-radius: 12px;
		font-size: 13px;
		font-weight: 700;
		color: var(--texto-2);
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
		white-space: nowrap;
	}
	.chip-v.ok {
		color: var(--carbon);
		background: var(--grad-lima);
		box-shadow: var(--sh-lima);
	}
</style>
