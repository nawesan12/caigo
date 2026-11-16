<script lang="ts">
	import { onDestroy, tick } from 'svelte';
	import { enhance } from '$app/forms';
	import { goto, invalidateAll } from '$app/navigation';
	import Icono from '#lib/ui/Icono.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { APP_STORE, PLAY_STORE } from '#lib/abrir-app.ts';
	import { aJpeg } from '#lib/imagen.ts';
	import {
		cargarDetector,
		falta,
		INICIO,
		instruccion as textoVida,
		leer,
		paso as pasoVida,
		progreso as progresoVida,
		type Estado,
		type Lectura
	} from '#lib/vida.ts';

	/*
	 * Verificación de Mockups v5: 01d "¿Cuál es tu número?", 01e "Ahora, una selfie" y el DNI (como
	 * en las apps: frente, dorso, número y fecha; sin leer el PDF417). La selfie lleva prueba de vida
	 * de verdad (lib/vida.ts): una cara sola y centrada que gira la cabeza y vuelve; recién ahí se
	 * saca la foto. Si el modelo no carga o no hay cámara, se hace desde la app.
	 */
	let { data, form } = $props();

	/** Qué se muestra: el paso que falta; para el DNI hace falta la selfie de la prueba de vida. */
	const vista = $derived.by(() => {
		if (data.paso === 1) return 'telefono';
		if (data.paso === 2) return 'selfie';
		if (data.dni === 'rechazada') return 'rechazada';
		if (data.dni === 'pendiente') return 'pendiente';
		if (!data.conSelfie || (form && 'selfie' in form && form.selfie)) return 'selfie';
		return 'dni';
	});
	const numeroPaso = $derived(vista === 'telefono' ? 1 : vista === 'selfie' ? 2 : 3);

	// ——— Paso 1 ———
	let telefono = $state('');
	let codigo = $state('');
	let enviando = $state(false);
	let espera = $state(0);
	let reloj: ReturnType<typeof setInterval> | undefined;
	const enviado = $derived(!!form && 'enviado' in form && form.enviado);
	let destino = $state('');

	$effect(() => {
		if (form && 'telefono' in form && form.telefono && !telefono) telefono = String(form.telefono);
		if (form && 'codigo' in form && form.codigo) codigo = String(form.codigo);
		if (form && 'destino' in form && form.destino) destino = String(form.destino);
	});

	function arrancarEspera() {
		espera = 30;
		clearInterval(reloj);
		reloj = setInterval(() => {
			espera = Math.max(0, espera - 1);
			if (!espera) clearInterval(reloj);
		}, 1000);
	}

	// ——— Paso 2: selfie con prueba de vida ———
	let video: HTMLVideoElement | undefined = $state();
	let flujo: MediaStream | null = null;
	let camara = $state<'apagada' | 'cargando' | 'prendida' | 'sin' | 'sin_modelo'>('apagada');
	let vida = $state<Estado>(INICIO);
	let lectura = $state<Lectura | null>(null);
	let subiendo = $state(false);
	let anim: number | undefined;
	const progreso = $derived(progresoVida(vida));
	const instruccion = $derived(
		camara === 'prendida'
			? textoVida(vida, lectura)
			: camara === 'cargando'
				? 'Preparando la cámara…'
				: 'Mirá de frente a la cámara'
	);
	const chips = $derived([
		{ t: 'Buena luz', ok: !!lectura && lectura.caras > 0 },
		{ t: 'Cara centrada', ok: vida.fase !== 'buscando' || (!!lectura && falta(lectura) === null) },
		{ t: 'Girá y volvé', ok: vida.fase === 'listo' }
	]);

	async function prenderCamara() {
		try {
			flujo = await navigator.mediaDevices.getUserMedia({
				video: { facingMode: 'user', width: { ideal: 720 }, height: { ideal: 720 } },
				audio: false
			});
			return true;
		} catch {
			return false;
		}
	}

	function apagar() {
		flujo?.getTracks().forEach((t) => t.stop());
		flujo = null;
		if (anim) cancelAnimationFrame(anim);
		anim = undefined;
	}
	onDestroy(() => {
		apagar();
		clearInterval(reloj);
	});

	async function verificarme() {
		if (camara === 'cargando' || camara === 'prendida' || subiendo) return;
		camara = 'cargando';
		vida = INICIO;
		lectura = null;
		const [hayCamara, detector] = await Promise.all([
			prenderCamara(),
			cargarDetector().catch(() => null)
		]);
		if (!hayCamara) {
			apagar();
			camara = 'sin';
			return;
		}
		if (!detector) {
			apagar();
			camara = 'sin_modelo';
			return;
		}
		camara = 'prendida';
		await tick();
		if (!video) return;
		video.srcObject = flujo;
		await video.play().catch(() => undefined);
		let ultimo = -1;
		const cuadro = async () => {
			if (!video || !flujo) return;
			// Un cuadro nuevo del video: se mide (detectForVideo pide tiempos crecientes).
			if (video.readyState >= 2 && video.currentTime !== ultimo) {
				ultimo = video.currentTime;
				const t = performance.now();
				try {
					lectura = leer(detector, video, t);
				} catch {
					apagar();
					camara = 'sin_modelo';
					return;
				}
				vida = pasoVida(vida, lectura, t);
				if (vida.fase === 'listo') {
					const foto = await sacarFoto();
					apagar();
					if (foto) await subir(foto);
					else {
						camara = 'apagada';
						avisar('No pudimos sacar la foto. Probá de nuevo.');
					}
					return;
				}
			}
			anim = requestAnimationFrame(cuadro);
		};
		anim = requestAnimationFrame(cuadro);
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
		let ok = false;
		try {
			// Con la selfie ya verificada (paso 3 sin la de la prueba de vida), sólo se sube para el DNI.
			const r = await fetch(`/app/verificar/selfie${data.paso === 3 ? '?para=dni' : ''}`, {
				method: 'POST',
				headers: { 'content-type': foto.type || 'image/jpeg' },
				body: foto
			});
			const j = await r.json().catch(() => ({}));
			if (!r.ok) avisar(j.error || 'No pudimos subir la selfie. Probá de nuevo.');
			else ok = true;
		} catch {
			avisar('No pudimos subir la selfie. Probá de nuevo.');
		} finally {
			subiendo = false;
		}
		vida = INICIO;
		lectura = null;
		camara = 'apagada';
		if (ok) {
			// Recarga: sigue con el DNI.
			await goto(`/app/verificar?volver=${encodeURIComponent(data.volver)}`, {
				replaceState: true,
				invalidateAll: true
			}).catch(() => undefined);
		}
	}

	// ——— Paso 3: DNI ———
	type Lado = 'frente' | 'dorso';
	let fotos = $state<Record<Lado, { id: string; vista: string; subiendo: boolean }>>({
		frente: { id: '', vista: '', subiendo: false },
		dorso: { id: '', vista: '', subiendo: false }
	});
	let numero = $state('');
	let nacimiento = $state('');
	let mandando = $state(false);
	$effect(() => {
		if (form && 'numero' in form && form.numero && !numero) numero = String(form.numero);
		if (form && 'nacimiento' in form && form.nacimiento && !nacimiento)
			nacimiento = String(form.nacimiento);
	});
	/** El máximo del campo de fecha: hoy hace 18 años. */
	const hace18 = (() => {
		const hoy = new Date();
		return `${hoy.getFullYear() - 18}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
	})();
	const listoDni = $derived(
		!!fotos.frente.id && !!fotos.dorso.id && numero.replace(/\D/g, '').length >= 7 && !!nacimiento
	);

	async function elegirFoto(lado: Lado, e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const f = input.files?.[0];
		input.value = '';
		if (!f) return;
		fotos[lado].subiendo = true;
		try {
			const jpeg = await aJpeg(f);
			const r = await fetch('/app/verificar/foto', {
				method: 'POST',
				headers: { 'content-type': 'image/jpeg' },
				body: jpeg
			});
			const j = await r.json().catch(() => ({}));
			if (!r.ok || !j.id) throw new Error(j.error || 'No pudimos subir la foto. Probá de nuevo.');
			if (fotos[lado].vista) URL.revokeObjectURL(fotos[lado].vista);
			fotos[lado] = { id: j.id, vista: URL.createObjectURL(jpeg), subiendo: false };
		} catch (err) {
			fotos[lado].subiendo = false;
			avisar(err instanceof Error ? err.message : 'No pudimos subir la foto. Probá de nuevo.');
		}
	}
</script>

<svelte:head><title>Verificación · Caigo</title></svelte:head>

{#snippet salir()}
	<form method="POST" action="/salir" class="salir">
		<button>Salir</button>
	</form>
{/snippet}

{#snippet desdeLaApp(texto: string)}
	<div class="app-aviso">
		<img src="/caigo-icon.png" alt="" width="48" height="48" />
		<div class="app-txt">
			<b>Hacelo desde la app</b>
			<span>{texto}</span>
			<span class="app-links"
				><a href={APP_STORE} rel="noopener">App Store</a> ·
				<a href={PLAY_STORE} rel="noopener">Google Play</a></span
			>
		</div>
	</div>
{/snippet}

<div class="scroll">
	<div class="pantalla">
		<div class="arriba">
			{#if vista === 'telefono'}
				<a class="icono-btn" href="/" aria-label="Volver a Caigo"
					><Icono nombre="atras" tam={22} /></a
				>
			{:else}
				<span></span>
			{/if}
			{#if vista !== 'rechazada' && vista !== 'pendiente'}
				<span class="paso">Paso {numeroPaso} de 3</span>
			{/if}
		</div>

		{#if vista === 'telefono'}
			<div class="txt">
				<h1>¿Cuál es tu número?</h1>
				<p>
					{destino
						? `Te mandamos un código por mail a ${destino}. Tu número no aparece en tu perfil.`
						: 'Te mandamos un código por mail. Tu número no aparece en tu perfil.'}
				</p>
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
				{@render salir()}
			</div>
		{:else if vista === 'selfie'}
			<div class="txt">
				<h1>Ahora, una selfie</h1>
				<p>
					{data.paso === 3
						? 'La comparamos con la foto de tu DNI. No se publica.'
						: 'Es para confirmar que sos una persona real. No se publica.'}
				</p>
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
			<div class="instruccion" aria-live="polite"><span>{instruccion}</span></div>
			<div class="chips">
				{#each chips as c (c.t)}
					<span class="chip-v" class:ok={c.ok}>
						{#if c.ok}<Icono nombre="check" tam={14} color="#24212D" trazo={3} />{/if}{c.t}
					</span>
				{/each}
			</div>
			<div class="pie">
				{#if camara === 'sin'}
					<span class="nota aviso"
						>No pudimos usar la cámara. Dale permiso en el navegador y probá de&nbsp;nuevo.</span
					>
				{:else if camara === 'sin_modelo'}
					{@render desdeLaApp(
						'Este navegador no puede hacer la prueba de vida. En la app tarda menos de un minuto.'
					)}
				{/if}
				<span class="nota"
					><Icono nombre="escudo" tam={16} color="var(--c-acento)" trazo={2} />Tu selfie viaja
					encriptada y no es pública</span
				>
				<button
					class="gel gel-lima cta"
					onclick={verificarme}
					disabled={camara === 'cargando' || camara === 'prendida' || subiendo}
				>
					{subiendo
						? 'Subiendo…'
						: camara === 'cargando'
							? 'Preparando…'
							: camara === 'prendida'
								? 'Seguí las indicaciones'
								: camara === 'sin' || camara === 'sin_modelo'
									? 'Probar de nuevo'
									: 'Verificarme'}
				</button>
				{@render salir()}
			</div>
		{:else if vista === 'dni'}
			<div class="txt">
				<h1>Tu DNI</h1>
				<p>
					Sacale una foto al frente y otra al dorso, sin reflejos. Nunca mostramos tu DNI: sólo
					el&nbsp;badge.
				</p>
			</div>
			<form
				method="POST"
				action="?/dni"
				class="form"
				id="form-dni"
				use:enhance={() => {
					mandando = true;
					return async ({ result, update }) => {
						if (result.type === 'failure') avisar(String(result.data?.error ?? 'No se pudo.'));
						await update({ reset: false });
						mandando = false;
					};
				}}
			>
				<div class="lados">
					{#each ['frente', 'dorso'] as const as lado (lado)}
						{@const f = fotos[lado]}
						<label class="lado" class:lleno={!!f.vista}>
							<input
								type="file"
								accept="image/*"
								class="sr"
								disabled={f.subiendo}
								onchange={(e) => elegirFoto(lado, e)}
							/>
							{#if f.vista}<img src={f.vista} alt="" />{/if}
							<span class="lado-txt">
								{#if f.subiendo}
									Subiendo…
								{:else if f.id}
									<Icono nombre="check" tam={16} color="#24212D" trazo={3} />{lado === 'frente'
										? 'Frente'
										: 'Dorso'}
								{:else}
									<Icono nombre="mas" tam={18} color="var(--c-acento)" trazo={2.4} />{lado ===
									'frente'
										? 'Frente del DNI'
										: 'Dorso del DNI'}
								{/if}
							</span>
						</label>
					{/each}
				</div>
				<input type="hidden" name="frenteId" value={fotos.frente.id} />
				<input type="hidden" name="dorsoId" value={fotos.dorso.id} />
				<label class="campo">
					<span class="cl">Número de DNI</span>
					<input
						class="entrada"
						name="numero"
						inputmode="numeric"
						autocomplete="off"
						placeholder="35123456"
						maxlength="10"
						bind:value={numero}
						oninput={() => (numero = numero.replace(/\D/g, '').slice(0, 9))}
					/>
				</label>
				<label class="campo">
					<span class="cl">Fecha de nacimiento</span>
					<input
						class="entrada"
						name="nacimiento"
						type="date"
						max={hace18}
						min="1910-01-01"
						autocomplete="bday"
						bind:value={nacimiento}
					/>
				</label>
			</form>
			<div class="pie">
				<span class="nota">Caigo es solo para mayores de 18.</span>
				<button class="gel gel-lima cta" form="form-dni" disabled={mandando || !listoDni}>
					{mandando ? 'Verificando…' : 'Verificar mi DNI'}
				</button>
				<span class="nota"
					><Icono nombre="escudo" tam={16} color="var(--c-acento)" trazo={2} />Las fotos viajan
					encriptadas y no son públicas</span
				>
				{@render salir()}
			</div>
		{:else if vista === 'rechazada'}
			<div class="txt">
				<h1>No pudimos verificar tu DNI</h1>
				<p>{data.motivo || 'El equipo revisó tus datos y no coincidieron.'}</p>
				<p>
					Si creés que es un error, escribinos a
					<a href="mailto:soporte@caigo.com.ar">soporte@caigo.com.ar</a>.
				</p>
			</div>
			<div class="pie">{@render salir()}</div>
		{:else}
			<div class="txt">
				<h1>Ya estamos revisando tu&nbsp;DNI</h1>
				<p>Lo revisa una persona del equipo, no un bot. Te avisamos por mail.</p>
			</div>
			<div class="pie">
				<button class="gel gel-blanco cta" onclick={() => invalidateAll()}>Ver si ya está</button>
				{@render salir()}
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
	.salir button {
		font-size: 14px;
		font-weight: 700;
		color: var(--texto-2);
		padding: 6px 12px;
	}
	.nota.aviso {
		color: var(--carbon);
		font-weight: 600;
		text-wrap: balance;
	}
	.txt a {
		color: #7456f1;
		font-weight: 700;
	}
	h1,
	.txt p {
		text-wrap: balance;
	}
	/* Hacelo desde la app */
	.app-aviso {
		align-self: stretch;
		display: flex;
		gap: 12px;
		align-items: center;
		padding: 14px;
		border-radius: 20px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plan);
	}
	.app-aviso img {
		flex: none;
		filter: drop-shadow(0 8px 10px rgba(116, 86, 241, 0.3));
	}
	.app-txt {
		display: flex;
		flex-direction: column;
		gap: 2px;
		font-size: 13px;
		line-height: 1.4;
		color: var(--texto-2);
		text-wrap: pretty;
	}
	.app-txt b {
		font-size: 16px;
		color: var(--carbon);
	}
	.app-links a {
		color: #7456f1;
		font-weight: 700;
	}
	/* DNI */
	.lados {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
	.lado {
		position: relative;
		aspect-ratio: 1.58;
		border-radius: 18px;
		overflow: hidden;
		background: var(--c-tarjeta);
		border: 1.5px dashed #c9bff5;
		display: grid;
		place-items: center;
		cursor: pointer;
	}
	.lado.lleno {
		border: 0;
	}
	.lado img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.lado-txt {
		position: relative;
		display: flex;
		align-items: center;
		gap: 4px;
		padding: 6px 10px;
		border-radius: 12px;
		font-size: 13px;
		font-weight: 700;
		text-align: center;
	}
	.lado.lleno .lado-txt {
		background: var(--grad-lima);
		box-shadow: var(--sh-lima);
		color: #24212d;
	}
	.lado:focus-within {
		outline: 2px solid #7456f1;
		outline-offset: 2px;
	}
	.campo {
		display: flex;
		flex-direction: column;
		gap: 8px;
		font-size: 14px;
	}
	.entrada {
		height: 56px;
		padding: 0 18px;
		border-radius: 18px;
		background: var(--c-tarjeta);
		border: 1.5px solid #e6e1f2;
		font-size: 17px;
		font-weight: 600;
		color: var(--carbon);
		min-width: 0;
		width: 100%;
	}
	.entrada:focus {
		border: 2px solid #7456f1;
		outline: none;
	}
</style>
