<script lang="ts">
	import { untrack } from 'svelte';
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import Icono from '#lib/ui/Icono.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { aRFC3339, diasParaElegir } from '#lib/fechas.ts';
	import { CATEGORIAS_LUGAR } from '#lib/plan.ts';
	import { desglose, leerPrecio, pesos, type Cobro } from '#lib/precios.ts';

	/*
	 * "Nuevo plan" del Panel Lugares: wizard de 4 pasos con el preview "ASÍ LO VE LA GENTE EN CAIGO"
	 * y el desglose "LA PERSONA PAGA / VOS RECIBÍS" (lógica del script del handoff, en lib/precios).
	 * Diferencias con el handoff: la foto se sube (no hay fotos de muestra), "Otro día" / "Otra hora"
	 * abren un campo del mismo estilo, y no está "Armar mesas con gente que va sola" (la API no lo
	 * tiene todavía).
	 */
	let { data, form } = $props();

	const DIAS = diasParaElegir();
	const HORAS = ['18:00', '19:30', '21:00', '22:30'];
	const DURACIONES = [
		{ l: '1 h', min: 60 },
		{ l: '2 h', min: 120 },
		{ l: '3 h', min: 180 },
		{ l: 'Toda la noche', min: 360 }
	];
	const COBROS: { k: Cobro; l: string; d: string }[] = [
		{ k: 'gratis', l: 'Gratis', d: 'Sin entrada. Ideal para atraer gente nueva.' },
		{ k: 'entrada', l: 'Entrada', d: 'Se paga todo al sumarse.' },
		{ k: 'sena', l: 'Seña', d: 'Reserva con una parte; el resto en el lugar.' }
	];
	const CANCELACIONES = [
		{ k: 'flex', l: 'Flexible', d: 'Reembolso total hasta 24 h antes' },
		{ k: 'mod', l: 'Moderada', d: 'Reembolso hasta 72 h antes; después se retiene la seña' },
		{ k: 'strict', l: 'Sin reembolso', d: 'La entrada se puede transferir a otra persona' }
	];
	const QUIEN = [
		{ k: 'cualquiera', l: 'Cualquiera' },
		{ k: 'verificados', l: 'Solo verificados con DNI' },
		{ k: 'mujeres', l: 'Solo mujeres' }
	];
	const PASOS = ['Qué', 'Cuándo y cupo', 'Entrada', 'Beneficio y publicar'];

	let paso = $state(1);
	let f = $state({
		titulo: '',
		categoria: 'taller',
		descripcion: '',
		foto: '',
		dia: DIAS[2].valor,
		otroDia: false,
		hora: '19:30',
		otraHora: false,
		dur: 120,
		cupo: 12,
		grupos: true,
		rec: false,
		// Sin Mercado Pago conectado no se puede cobrar: arranca en Gratis.
		cobro: (untrack(() => data.mpConectado) ? 'entrada' : 'gratis') as Cobro,
		precio: '8000',
		sena: 30,
		incluye: '',
		cancel: 'flex',
		perkOn: true,
		perk: 'Café de regalo',
		quien: 'cualquiera'
	});
	let subiendo = $state(false);
	let enviando = $state(false);

	const precio = $derived(leerPrecio(f.precio));
	const pago = $derived(f.cobro !== 'gratis');
	const esSena = $derived(f.cobro === 'sena');
	const d = $derived(desglose(f.cobro, precio, f.sena));
	const etiquetaDia = $derived(
		DIAS.find((x) => x.valor === f.dia)?.etiqueta ??
			f.dia.split('-').reverse().slice(0, 2).join('/')
	);
	const cuando = $derived(`${etiquetaDia} · ${f.hora}`);
	const durL = $derived(DURACIONES.find((x) => x.min === f.dur)?.l ?? `${f.dur} min`);
	const catL = $derived(CATEGORIAS_LUGAR.find((c) => c.valor === f.categoria)?.etiqueta ?? '');

	const datos = $derived({
		titulo: f.titulo.trim(),
		categoria: f.categoria,
		descripcion: f.descripcion.trim(),
		foto: f.foto,
		empiezaEn: aRFC3339(f.dia, f.hora),
		duracionMin: f.dur,
		cupo: f.cupo,
		aceptaGrupos: f.grupos,
		recurrente: f.rec,
		cobro: f.cobro,
		precioCentavos: pago ? precio * 100 : 0,
		senaPct: esSena ? f.sena : 0,
		incluye: pago ? f.incluye.trim() : '',
		cancelacion: f.cancel,
		beneficio: f.perkOn ? f.perk.trim() : '',
		quien: f.quien
	});

	function irA(n: number) {
		if (n > 1 && !f.titulo.trim()) return avisar('Ponele un nombre al plan.');
		if (n > 3 && pago && precio < 500) return avisar('Poné un precio por persona.');
		paso = n;
	}

	async function subir(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const archivo = input.files?.[0];
		input.value = '';
		if (!archivo) return;
		subiendo = true;
		try {
			const r = await fetch('/foto', {
				method: 'POST',
				headers: { 'content-type': archivo.type || 'application/octet-stream' },
				body: archivo
			});
			const j = await r.json().catch(() => ({}));
			if (!r.ok || !j.url) return avisar(j.error || 'No pudimos subir la foto.');
			f.foto = j.url;
		} catch {
			avisar('No pudimos subir la foto.');
		} finally {
			subiendo = false;
		}
	}

	const publicado = $derived(form && 'publicado' in form ? form.publicado : null);
	async function copiar() {
		if (!publicado?.link) return;
		try {
			await navigator.clipboard.writeText(publicado.link);
			avisar('Link copiado: ' + publicado.link.replace(/^https?:\/\//, ''));
		} catch {
			avisar(publicado.link);
		}
	}
</script>

<svelte:head><title>Nuevo plan · Caigo para lugares</title></svelte:head>

<div class="crear">
	<div class="izq">
		<div class="titulo-fila">
			<a class="cerrar" href="/" aria-label="Cerrar"><Icono nombre="cerrar" color="#24212D" trazo={2.6} /></a>
			<h1>Nuevo plan</h1>
		</div>

		<ol class="pasos">
			{#each PASOS as l, i (l)}
				<li>
					<button type="button" onclick={() => irA(i + 1)} class:actual={paso === i + 1}>
						<span class="barra" class:on={i + 1 <= paso}></span>
						<span class="paso-l">{i + 1}. {l}</span>
					</button>
				</li>
			{/each}
		</ol>

		{#if paso === 1}
			<div class="tarjeta-paso">
				<label class="campo">
					<span>Nombre del plan</span>
					<input
						class="input grande"
						bind:value={f.titulo}
						maxlength="80"
						placeholder="Ej: Taller de cerámica"
					/>
				</label>
				<div class="campo">
					<span>Categoría</span>
					<div class="chips">
						{#each CATEGORIAS_LUGAR as c (c.valor)}
							<button
								type="button"
								class="chip"
								aria-pressed={f.categoria === c.valor}
								onclick={() => (f.categoria = c.valor)}>{c.etiqueta}</button
							>
						{/each}
					</div>
				</div>
				<label class="campo">
					<span>Qué va a pasar</span>
					<textarea
						class="input"
						bind:value={f.descripcion}
						maxlength="600"
						placeholder="Contá en dos líneas cómo es el plan y para quién es."
					></textarea>
				</label>
				<div class="campo">
					<span>Foto de portada</span>
					<div class="fotos">
						{#if f.foto}
							<button
								type="button"
								class="foto-tile on"
								style:background-image="url({f.foto})"
								aria-label="Sacar la foto"
								onclick={() => (f.foto = '')}
							></button>
						{/if}
						<label class="foto-tile subir" class:cargando={subiendo}>
							<input type="file" accept="image/jpeg,image/png,image/webp" class="sr" onchange={subir} />
							<Icono nombre="mas" tam={22} color="#5B43C9" trazo={2.6} />
							<span>{subiendo ? 'Subiendo…' : f.foto ? 'Cambiar foto' : 'Subir foto'}</span>
						</label>
					</div>
				</div>
			</div>
		{:else if paso === 2}
			<div class="tarjeta-paso">
				<div class="campo">
					<span>Día</span>
					<div class="chips">
						{#each DIAS as x (x.valor)}
							<button
								type="button"
								class="chip"
								aria-pressed={!f.otroDia && f.dia === x.valor}
								onclick={() => ((f.dia = x.valor), (f.otroDia = false))}>{x.etiqueta}</button
							>
						{/each}
						<button
							type="button"
							class="chip"
							aria-pressed={f.otroDia}
							onclick={() => (f.otroDia = true)}>Otro día</button
						>
					</div>
					{#if f.otroDia}
						<input class="input corto" type="date" bind:value={f.dia} min={DIAS[0].valor} />
					{/if}
				</div>
				<div class="dos">
					<div class="campo">
						<span>Hora</span>
						<div class="chips">
							{#each HORAS as h (h)}
								<button
									type="button"
									class="chip angosto"
									aria-pressed={!f.otraHora && f.hora === h}
									onclick={() => ((f.hora = h), (f.otraHora = false))}>{h}</button
								>
							{/each}
							<button
								type="button"
								class="chip angosto"
								aria-pressed={f.otraHora}
								onclick={() => (f.otraHora = true)}>Otra</button
							>
						</div>
						{#if f.otraHora}
							<input class="input corto" type="time" bind:value={f.hora} step="900" />
						{/if}
					</div>
					<div class="campo">
						<span>Duración</span>
						<div class="chips">
							{#each DURACIONES as x (x.min)}
								<button
									type="button"
									class="chip angosto"
									aria-pressed={f.dur === x.min}
									onclick={() => (f.dur = x.min)}>{x.l}</button
								>
							{/each}
						</div>
					</div>
				</div>
				<div class="cupo">
					<div class="cupo-txt">
						<span class="t">Cupo total</span>
						<span class="s">Cuando se llena, el plan se cierra solo</span>
					</div>
					<div class="stepper">
						<button
							type="button"
							aria-label="Menos"
							onclick={() => (f.cupo = Math.max(2, f.cupo - 1))}>−</button
						>
						<span class="n" aria-live="polite">{f.cupo}</span>
						<button
							type="button"
							aria-label="Más"
							onclick={() => (f.cupo = Math.min(60, f.cupo + 1))}>+</button
						>
					</div>
				</div>
				<div class="toggles">
					<button type="button" class="tg" onclick={() => (f.grupos = !f.grupos)} role="switch" aria-checked={f.grupos}>
						<span class="tg-txt"
							><span class="t">Aceptar grupos</span><span class="s"
								>Hasta 6 personas se suman juntas con una sola compra</span
							></span
						>
						<span class="toggle" class:on={f.grupos}></span>
					</button>
					<button type="button" class="tg" onclick={() => (f.rec = !f.rec)} role="switch" aria-checked={f.rec}>
						<span class="tg-txt"
							><span class="t">Se repite cada semana</span><span class="s"
								>Mismo día y hora, con cupo nuevo</span
							></span
						>
						<span class="toggle" class:on={f.rec}></span>
					</button>
				</div>
			</div>
		{:else if paso === 3}
			<div class="tarjeta-paso">
				<div class="campo">
					<span>Cómo se cobra</span>
					<div class="cobros">
						{#each COBROS as c (c.k)}
							<button
								type="button"
								class="radio-card"
								aria-pressed={f.cobro === c.k}
								onclick={() => (f.cobro = c.k)}
							>
								<span class="t">{c.l}</span>
								<span class="s">{c.d}</span>
							</button>
						{/each}
					</div>
				</div>
				{#if pago}
					{#if !data.mpConectado}
						<p class="aviso-lav">
							Para cobrar entradas, conectá Mercado Pago en <a href="/cobros">Cobros</a>.
						</p>
					{/if}
					<div class="campo">
						<span>Precio por persona</span>
						<div class="precio-fila">
							<div class="precio-caja">
								<span class="signo">$</span>
								<input
									aria-label="Precio por persona"
									inputmode="numeric"
									value={precio ? precio.toLocaleString('es-AR') : ''}
									oninput={(e) =>
										(f.precio = (e.currentTarget as HTMLInputElement).value
											.replace(/\D/g, '')
											.slice(0, 7))}
								/>
							</div>
							{#each [5000, 8000, 12000] as n (n)}
								<button
									type="button"
									class="chip angosto"
									aria-pressed={precio === n}
									onclick={() => (f.precio = String(n))}>{pesos(n)}</button
								>
							{/each}
						</div>
					</div>
					{#if esSena}
						<div class="campo">
							<span>Seña al reservar</span>
							<div class="chips centrado">
								{#each [20, 30, 50] as n (n)}
									<button
										type="button"
										class="chip"
										aria-pressed={f.sena === n}
										onclick={() => (f.sena = n)}>{n}%</button
									>
								{/each}
								<span class="sena-txt"
									>Seña de {pesos(d.sena)} · el resto, {pesos(d.resto)}, en el&nbsp;lugar</span
								>
							</div>
						</div>
					{/if}
					<label class="campo">
						<span>Qué incluye</span>
						<input
							class="input"
							bind:value={f.incluye}
							maxlength="200"
							placeholder="Ej: materiales, horneado y un café"
						/>
					</label>
					<div class="desglose tarjeta-oscura">
						<div>
							<span class="dl">LA PERSONA PAGA</span>
							<span class="dv">{pesos(d.paga)}</span>
							<span class="dd"
								>{esSena ? 'Seña' : 'Entrada'}
								{pesos(d.recibe)} + {pesos(d.cargo)} de servicio</span
							>
						</div>
						<div>
							<span class="dl lima">VOS RECIBÍS</span>
							<span class="dv lima">{pesos(d.recibe)}</span>
							<span class="dd"
								>{esSena
									? `Por persona al reservar, más ${pesos(d.resto)} en el\u00a0lugar`
									: 'Por persona. Sin comisión para el\u00a0lugar.'}</span
							>
						</div>
					</div>
					<div class="campo">
						<span>Si alguien cancela</span>
						<div class="cancelas">
							{#each CANCELACIONES as c (c.k)}
								<button
									type="button"
									class="radio-card fila-radio"
									aria-pressed={f.cancel === c.k}
									onclick={() => (f.cancel = c.k)}
								>
									<span class="anillo" class:on={f.cancel === c.k}></span>
									<span class="col"
										><span class="t chico">{c.l}</span><span class="s">{c.d}</span></span
									>
								</button>
							{/each}
						</div>
					</div>
				{:else}
					<p class="aviso-lav">
						Gratis para la gente y para vos. Podés pedir consumo mínimo en la&nbsp;descripción.
					</p>
				{/if}
			</div>
		{:else}
			<div class="tarjeta-paso">
				<button
					type="button"
					class="tg sin-borde"
					onclick={() => (f.perkOn = !f.perkOn)}
					role="switch"
					aria-checked={f.perkOn}
				>
					<span class="tg-txt"
						><span class="t">Beneficio por check-in</span><span class="s"
							>Se desbloquea cuando la persona llega. Premia la asistencia real.</span
						></span
					>
					<span class="toggle" class:on={f.perkOn}></span>
				</button>
				{#if f.perkOn}
					<input
						class="input"
						aria-label="Beneficio"
						bind:value={f.perk}
						maxlength="80"
						placeholder="Ej: café de regalo"
					/>
				{/if}
				<div class="campo">
					<span>Quién puede sumarse</span>
					<div class="chips">
						{#each QUIEN as q (q.k)}
							<button
								type="button"
								class="chip"
								aria-pressed={f.quien === q.k}
								onclick={() => (f.quien = q.k)}>{q.l}</button
							>
						{/each}
					</div>
				</div>
				<dl class="resumen">
					<div><dt>Plan</dt><dd>{f.titulo.trim() || 'Sin nombre todavía'}</dd></div>
					<div><dt>Cuándo</dt><dd>{cuando} · {durL}</dd></div>
					<div><dt>Cupo</dt><dd>{f.cupo} personas{f.grupos ? ' · acepta grupos' : ''}</dd></div>
					<div>
						<dt>Cobro</dt>
						<dd>
							{pago ? (esSena ? `Seña ${f.sena}% de ${pesos(precio)}` : `Entrada ${pesos(precio)}`) : 'Gratis'}
						</dd>
					</div>
					<div>
						<dt>Cancelación</dt>
						<dd>{pago ? CANCELACIONES.find((c) => c.k === f.cancel)?.l : '—'}</dd>
					</div>
					<div><dt>Quién</dt><dd>{QUIEN.find((q) => q.k === f.quien)?.l}</dd></div>
					<div>
						<dt>Beneficio</dt>
						<dd>{f.perkOn ? f.perk || '—' : 'Sin beneficio'}</dd>
					</div>
				</dl>
			</div>
		{/if}

		{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}

		<form
			method="POST"
			class="botones"
			use:enhance={({ cancel }) => {
				if (paso < 4) {
					cancel();
					irA(paso + 1);
					return;
				}
				enviando = true;
				return async ({ update }) => {
					await update({ reset: false });
					enviando = false;
				};
			}}
		>
			<input type="hidden" name="datos" value={JSON.stringify(datos)} />
			{#if paso > 1}
				<button type="button" class="gel gel-blanco atras" onclick={() => (paso = Math.max(1, paso - 1))}
					>Atrás</button
				>
			{/if}
			<span></span>
			<button class="gel siguiente {paso < 4 ? 'gel-violeta' : 'gel-lima'}" disabled={enviando}>
				{paso < 4 ? 'Siguiente' : enviando ? 'Publicando…' : 'Publicar plan'}
			</button>
		</form>
	</div>

	<div class="der">
		<span class="overline">ASÍ LO VE LA GENTE EN CAIGO</span>
		<div class="preview">
			<div class="pv-foto" style:background-image={f.foto ? `url(${f.foto})` : undefined}>
				<div class="pv-velo"></div>
				<div class="pv-chips">
					<span class="claro">{catL}</span>
					<span class="oscuro">{cuando}</span>
					<span class="lima">Lugar aliado</span>
				</div>
				<span class="badge-coral pv-badge">Faltan {f.cupo}</span>
			</div>
			<div class="pv-cuerpo">
				<div class="pv-tit">
					<span class="t">{f.titulo.trim() || 'El nombre de tu plan'}</span>
					<span class="s">{data.lugar?.nombre}{data.lugar?.zona ? ` · Zona ${data.lugar.zona}` : ''}</span>
				</div>
				<div class="pv-precio">
					<div>
						<span class="m">{pago ? pesos(d.paga) : 'Gratis'}</span>
						<span class="s"
							>{pago
								? esSena
									? `Seña · resto ${pesos(d.resto)} en el\u00a0lugar`
									: 'Entrada con servicio incluido'
								: 'Sin entrada'}</span
						>
					</div>
					<span class="pv-cta">{pago ? (esSena ? 'Señar' : 'Comprar entrada') : 'Sumarme'}</span>
				</div>
				{#if f.perkOn}
					<div class="pv-perk">
						<Icono nombre="regalo" tam={18} color="#5B43C9" />Con check-in: {f.perk || 'tu beneficio'}
					</div>
				{/if}
			</div>
		</div>
		<span class="nota">Publicar es gratis. Caigo cobra un cargo por servicio a quien compra, nunca al&nbsp;lugar.</span>
	</div>
</div>

{#if publicado}
	<div class="velo" role="dialog" aria-modal="true" aria-labelledby="pub-t">
		<div class="pub">
			<img src="/caigo-icon.png" alt="" width="120" height="120" />
			<h2 id="pub-t">Tu plan ya está en Caigo</h2>
			<p>{publicado.titulo}. Lo ve la gente cerca y tu gente recibe el aviso&nbsp;primero.</p>
			<div class="pub-botones">
				<button type="button" class="pub-copiar" onclick={copiar}>Copiar link</button>
				<button
					type="button"
					class="gel gel-lima pub-ver"
					onclick={() => goto(`/plan/${publicado.id}`)}>Ver el plan</button
				>
			</div>
		</div>
	</div>
{/if}

<style>
	.crear {
		max-width: 1180px;
		display: flex;
		flex-wrap: wrap;
		gap: 40px;
		align-items: flex-start;
		animation: tk-up 0.35s ease both;
	}
	.izq {
		flex: 1 1 460px;
		display: flex;
		flex-direction: column;
		gap: 22px;
		min-width: 0;
	}
	.titulo-fila {
		display: flex;
		align-items: center;
		gap: 14px;
	}
	.cerrar {
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
	h1 {
		font:
			800 38px/1 'Geist',
			sans-serif;
		letter-spacing: -1.3px;
	}
	.pasos {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(110px, 1fr));
		gap: 10px;
	}
	.pasos button {
		width: 100%;
		display: flex;
		flex-direction: column;
		gap: 8px;
		text-align: left;
	}
	.barra {
		height: 7px;
		border-radius: 4px;
		background: #e4defa;
	}
	.barra.on {
		background: #7456f1;
	}
	.paso-l {
		font-size: 13px;
		font-weight: 800;
		color: #6b6780;
	}
	.actual .paso-l {
		color: #24212d;
	}
	.tarjeta-paso {
		display: flex;
		flex-direction: column;
		gap: 22px;
		padding: 28px;
		border-radius: 30px;
		background: #ffffff;
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 3px 0 #e6e0f3,
			0 24px 40px -28px rgba(80, 60, 160, 0.4);
	}
	.input.grande {
		height: 56px;
		font-size: 17px;
	}
	.input.corto {
		max-width: 240px;
	}
	.chips {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.chips.centrado {
		align-items: center;
		gap: 10px;
	}
	.chip.angosto {
		padding: 11px 14px;
	}
	.fotos {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(110px, 1fr));
		gap: 10px;
	}
	.foto-tile {
		aspect-ratio: 4 / 3;
		border-radius: 18px;
		background-size: cover;
		background-position: center;
		cursor: pointer;
	}
	.foto-tile.on {
		box-shadow:
			0 0 0 3px #ffffff,
			0 0 0 6px #7456f1;
	}
	.subir {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 6px;
		background: #f5f2fc;
		box-shadow: var(--sh-input);
		font-size: 13px;
		font-weight: 800;
		color: #5b43c9;
	}
	.subir.cargando {
		opacity: 0.6;
		pointer-events: none;
	}
	.subir:has(input:focus-visible) {
		outline: 3px solid #b3a3ff;
	}
	.dos {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
		gap: 20px;
	}
	.cupo {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 16px;
		padding: 16px 18px;
		border-radius: 22px;
		background: #f5f2fc;
	}
	.cupo-txt,
	.tg-txt {
		display: flex;
		flex-direction: column;
		gap: 2px;
		text-align: left;
	}
	.t {
		font-size: 15px;
		font-weight: 800;
	}
	.t.chico {
		font-size: 14px;
	}
	.s {
		font-size: 13px;
		font-weight: 600;
		color: #5c586b;
	}
	.stepper {
		flex: none;
		display: flex;
		align-items: center;
		gap: 14px;
	}
	.stepper button {
		width: 44px;
		height: 44px;
		border-radius: 15px;
		display: grid;
		place-items: center;
		background: #ffffff;
		box-shadow: 0 3px 0 #dcd5ee;
		font:
			800 22px/1 'Geist',
			sans-serif;
		color: #5b43c9;
	}
	.stepper .n {
		min-width: 36px;
		text-align: center;
		font:
			800 30px/1 'Geist',
			sans-serif;
	}
	.toggles {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.tg {
		width: 100%;
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 16px;
		padding: 14px 4px;
		border-bottom: 1px solid #f0ecff;
	}
	.tg.sin-borde {
		border: 0;
		padding: 0;
	}
	.cobros {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
		gap: 10px;
	}
	.radio-card {
		display: flex;
		flex-direction: column;
		gap: 4px;
		padding: 16px;
		border-radius: 20px;
		text-align: left;
		background: #ffffff;
		box-shadow:
			inset 0 0 0 1.5px #e6e0f3,
			0 3px 0 #e6e0f3;
	}
	.radio-card .s {
		font-size: 12px;
		line-height: 1.4;
	}
	.radio-card[aria-pressed='true'] {
		background: #f0ecff;
		box-shadow:
			inset 0 0 0 2.5px #7456f1,
			0 3px 0 #c9bef5;
	}
	.cancelas {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.fila-radio {
		flex-direction: row;
		align-items: center;
		gap: 14px;
		padding: 14px 16px;
		border-radius: 18px;
	}
	.fila-radio .col {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.anillo {
		flex: none;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		background: #ffffff;
		border: 2px solid #cfc8e6;
	}
	.anillo.on {
		border: 7px solid #7456f1;
	}
	.precio-fila {
		display: flex;
		align-items: center;
		gap: 12px;
		flex-wrap: wrap;
	}
	.precio-caja {
		display: flex;
		align-items: center;
		height: 60px;
		padding: 0 18px;
		border-radius: 18px;
		background: #f5f2fc;
		box-shadow: var(--sh-input);
	}
	.signo {
		font:
			800 26px/1 'Geist',
			sans-serif;
		color: #5c586b;
	}
	.precio-caja input {
		width: 150px;
		height: 56px;
		padding: 0 8px;
		border: 0;
		background: transparent;
		font:
			800 28px/1 'Geist',
			sans-serif;
		color: #24212d;
	}
	.sena-txt {
		font-size: 14px;
		font-weight: 700;
		color: #5c586b;
	}
	.desglose {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
		gap: 12px;
		padding: 18px;
		border-radius: 24px;
	}
	.desglose div {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.dl {
		font-size: 12px;
		font-weight: 800;
		letter-spacing: 0.8px;
		color: #c9c4da;
	}
	.dv {
		font:
			800 26px/1 'Geist',
			sans-serif;
	}
	.lima {
		color: #c8f36b;
	}
	.dd {
		font-size: 12px;
		font-weight: 600;
		color: #d3cee2;
	}
	.aviso-lav {
		padding: 16px 18px;
		border-radius: 20px;
		background: #f0ecff;
		font-size: 14px;
		font-weight: 700;
		line-height: 1.5;
	}
	.resumen {
		margin: 0;
		display: flex;
		flex-direction: column;
		border-radius: 22px;
		background: #f5f2fc;
		overflow: hidden;
	}
	.resumen div {
		display: flex;
		justify-content: space-between;
		gap: 16px;
		padding: 13px 18px;
		border-bottom: 1px solid #eae4f8;
	}
	.resumen div:last-child {
		border-bottom: 0;
	}
	dt {
		font-size: 14px;
		font-weight: 600;
		color: #5c586b;
	}
	dd {
		margin: 0;
		font-size: 14px;
		font-weight: 800;
		text-align: right;
	}
	.error {
		padding: 14px 16px;
		border-radius: 18px;
		background: #ffe1db;
		color: #9a2e1f;
		font-size: 14px;
		font-weight: 700;
	}
	.botones {
		display: flex;
		justify-content: space-between;
		gap: 12px;
	}
	.atras,
	.siguiente {
		height: 58px;
		border-radius: 20px;
		font-size: 16px;
	}
	.atras {
		padding: 0 26px;
	}
	.siguiente {
		padding: 0 32px;
	}
	.gel-violeta.siguiente {
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.45),
			inset 0 -4px 0 rgba(40, 20, 140, 0.35),
			0 4px 0 #4a2fc2;
	}
	.gel-lima.siguiente {
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.85),
			inset 0 -4px 0 rgba(96, 140, 10, 0.3),
			0 4px 0 #7fa82e;
	}

	/* Preview */
	.der {
		flex: 0 1 360px;
		min-width: 300px;
		position: sticky;
		top: 34px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.preview {
		border-radius: 28px;
		overflow: hidden;
		background: #ffffff;
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 3px 0 #e6e0f3,
			0 30px 50px -26px rgba(80, 60, 160, 0.5);
	}
	.pv-foto {
		position: relative;
		height: 190px;
		background-color: #5b43c9;
		background-image: url(/grain.png);
		background-size: cover;
		background-position: center;
	}
	.pv-velo {
		position: absolute;
		inset: 0;
		background: linear-gradient(180deg, rgba(32, 29, 43, 0.35) 0%, rgba(32, 29, 43, 0) 45%);
	}
	.pv-chips {
		position: absolute;
		top: 12px;
		left: 12px;
		right: 12px;
		display: flex;
		gap: 6px;
		flex-wrap: wrap;
	}
	.pv-chips span {
		padding: 6px 11px;
		border-radius: 12px;
		font-size: 12px;
		font-weight: 800;
	}
	.pv-chips .claro {
		background: #faf9f6;
	}
	.pv-chips .oscuro {
		background: #24212d;
		color: #faf9f6;
	}
	.pv-chips .lima {
		background: #c8f36b;
		color: #24212d;
	}
	.pv-badge {
		position: absolute;
		right: 12px;
		bottom: 12px;
		padding: 8px 13px;
		border-radius: 14px;
		font-size: 19px;
		box-shadow:
			inset 0 2px 1px rgba(255, 255, 255, 0.6),
			0 3px 0 #d04a3a;
	}
	.pv-cuerpo {
		padding: 16px 18px 18px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.pv-tit {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.pv-tit .t {
		font:
			700 21px/1.15 'Geist',
			sans-serif;
		letter-spacing: -0.3px;
	}
	.pv-tit .s {
		font-weight: 500;
	}
	.pv-precio {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 10px;
	}
	.pv-precio > div {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.pv-precio .m {
		font:
			800 20px/1 'Geist',
			sans-serif;
	}
	.pv-precio .s {
		font-size: 12px;
	}
	.pv-cta {
		flex: none;
		padding: 11px 16px;
		border-radius: 16px;
		font-size: 14px;
		font-weight: 800;
		background: var(--grad-lima);
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.85),
			0 3px 0 #94c43a;
	}
	.pv-perk {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 10px 12px;
		border-radius: 16px;
		background: #f0ecff;
		font-size: 13px;
		font-weight: 700;
	}
	.nota {
		font-size: 12px;
		font-weight: 600;
		line-height: 1.5;
		color: #5c586b;
	}

	/* Publicado */
	.velo {
		position: fixed;
		inset: 0;
		z-index: 40;
		display: grid;
		place-items: center;
		padding: 20px;
		background: rgba(32, 29, 43, 0.55);
		backdrop-filter: blur(6px);
	}
	.pub {
		width: 100%;
		max-width: 440px;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 16px;
		padding: 34px 30px 28px;
		border-radius: 34px;
		text-align: center;
		background:
			url(/grain.png),
			radial-gradient(120% 70% at 85% 0%, #a38fff 0%, #7456f1 45%, #5a3dd8 100%);
		color: #ffffff;
		animation: tk-pop 0.45s cubic-bezier(0.3, 1.4, 0.5, 1) both;
	}
	.pub img {
		width: 120px;
		height: auto;
		filter: drop-shadow(0 18px 20px rgba(20, 10, 80, 0.5));
	}
	.pub h2 {
		font:
			800 32px/1.05 'Geist',
			sans-serif;
		letter-spacing: -1px;
	}
	.pub p {
		font-size: 15px;
		font-weight: 600;
		line-height: 1.5;
	}
	.pub-botones {
		align-self: stretch;
		display: flex;
		gap: 10px;
		margin-top: 6px;
	}
	.pub-copiar,
	.pub-ver {
		flex: 1;
		height: 54px;
		border-radius: 18px;
		font-size: 15px;
		font-weight: 800;
	}
	.pub-copiar {
		color: #ffffff;
		background: rgba(255, 255, 255, 0.16);
	}
	.pub-ver {
		box-shadow: 0 4px 0 #7fa82e;
	}
	@media (max-width: 700px) {
		.crear {
			gap: 28px;
		}
		.der {
			position: static;
			min-width: 0;
			flex-basis: 100%;
		}
		.tarjeta-paso {
			padding: 20px;
			border-radius: 26px;
		}
		h1 {
			font-size: 32px;
		}
		.cupo {
			flex-wrap: wrap;
		}
	}
</style>
