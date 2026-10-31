<script lang="ts">
	import { untrack } from 'svelte';
	import { enhance } from '$app/forms';
	import Icono from '#lib/ui/Icono.svelte';
	import CabeceraVolver from '#lib/app-ui/CabeceraVolver.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { INTERESES_SUGERIDOS, PREGUNTAS } from '#lib/app.ts';
	import type { Prompt, YoCompleto } from '#lib/tipos.ts';

	/*
	 * Editar el perfil: fotos (hasta 6), datos, "Sobre mí", hasta 3 preguntas y "Le copa". No está
	 * en el handoff como pantalla: se arma con la cabecera de Solicitudes, los campos del Panel
	 * (input, chips, mosaico de fotos de la portada) y las tarjetas del perfil de persona.
	 */
	let { data, form } = $props();

	const yo = untrack(() => data.yo as YoCompleto);
	let f = $state({
		nombre: yo.nombre ?? '',
		zona: yo.zona ?? '',
		ciudad: yo.ciudad || 'Mar del Plata',
		bio: yo.bio ?? '',
		trabajo: yo.trabajo ?? '',
		fotos: [...(yo.fotos ?? [])],
		prompts: [...(yo.prompts ?? [])] as Prompt[],
		intereses: [...(yo.intereses ?? [])]
	});
	let nuevo = $state('');
	let subiendo = $state(false);
	let enviando = $state(false);

	const sugeridos = $derived([...new Set([...INTERESES_SUGERIDOS, ...f.intereses])]);
	const libres = $derived(PREGUNTAS.filter((p) => !f.prompts.some((x) => x.p === p)));

	async function subir(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const archivos = [...(input.files ?? [])].slice(0, 6 - f.fotos.length);
		input.value = '';
		if (!archivos.length) return;
		subiendo = true;
		try {
			for (const a of archivos) {
				const r = await fetch('/app/perfil/foto', {
					method: 'POST',
					headers: { 'content-type': a.type || 'application/octet-stream' },
					body: a
				});
				const j = await r.json().catch(() => ({}));
				if (!r.ok || !j.url) {
					avisar(j.error || 'No pudimos subir la foto.');
					break;
				}
				f.fotos = [...f.fotos, j.url];
			}
		} catch {
			avisar('No pudimos subir la foto.');
		} finally {
			subiendo = false;
		}
	}
	function principal(i: number) {
		const [x] = f.fotos.splice(i, 1);
		f.fotos = [x, ...f.fotos];
	}
	function alternar(t: string) {
		if (f.intereses.includes(t)) f.intereses = f.intereses.filter((x) => x !== t);
		else if (f.intereses.length < 12) f.intereses = [...f.intereses, t];
		else avisar('Hasta 12. Sacá uno para sumar otro.');
	}
	function sumar() {
		const t = nuevo.trim();
		nuevo = '';
		if (t && !f.intereses.includes(t)) alternar(t);
	}
</script>

<svelte:head><title>Editar perfil · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<CabeceraVolver volver="/app/perfil" titulo="Editar perfil" etiqueta="Volver al perfil" />

		<section class="tarjeta-plana bloque">
			<span class="rotulo">Fotos</span>
			<div class="fotos">
				{#each f.fotos as foto, i (foto)}
					<div class="foto-tile" class:on={i === 0} style:background-image="url({foto})">
						<button
							type="button"
							class="sacar"
							aria-label="Sacar la foto {i + 1}"
							onclick={() => (f.fotos = f.fotos.filter((x) => x !== foto))}
							><Icono nombre="cerrar" tam={14} trazo={3} /></button
						>
						{#if i > 0}
							<button type="button" class="hacer" onclick={() => principal(i)}>Principal</button>
						{/if}
					</div>
				{/each}
				{#if f.fotos.length < 6}
					<label class="foto-tile subir" class:cargando={subiendo}>
						<input
							type="file"
							accept="image/jpeg,image/png,image/webp"
							multiple
							class="sr"
							onchange={subir}
						/>
						<Icono nombre="mas" tam={22} color="#5B43C9" trazo={2.6} />
						<span>{subiendo ? 'Subiendo…' : 'Sumar foto'}</span>
					</label>
				{/if}
			</div>
			<span class="ayuda">La primera es la que ve la gente. Hasta 6&nbsp;fotos.</span>
		</section>

		<section class="tarjeta-plana bloque">
			<label class="campo"
				><span>Nombre</span><input class="input" bind:value={f.nombre} maxlength="40" /></label
			>
			<div class="campo">
				<span>Ciudad</span>
				<div class="chips">
					{#each ['Mar del Plata', 'Villa Gesell'] as c (c)}
						<button
							type="button"
							class="chip"
							aria-pressed={f.ciudad === c}
							onclick={() => (f.ciudad = c)}>{c}</button
						>
					{/each}
				</div>
			</div>
			<label class="campo"
				><span>Zona</span><input
					class="input"
					bind:value={f.zona}
					maxlength="60"
					placeholder="Ej: Güemes"
				/></label
			>
			<label class="campo"
				><span>A qué te dedicás</span><input
					class="input"
					bind:value={f.trabajo}
					maxlength="60"
					placeholder="Ej: Estudio en la UNMDP"
				/></label
			>
			<label class="campo"
				><span>Sobre mí</span><textarea
					class="input"
					bind:value={f.bio}
					maxlength="300"
					rows="4"
					placeholder="Contá en dos líneas qué te gusta hacer."></textarea><span
					class="ayuda cuenta">{f.bio.length}/300</span
				></label
			>
		</section>

		<section class="tarjeta-plana bloque">
			<span class="rotulo">Preguntas</span>
			{#each f.prompts as pr, i (i)}
				<div class="pregunta">
					<div class="pq-fila">
						<select class="input" bind:value={pr.p} aria-label="Pregunta {i + 1}">
							{#each [pr.p, ...libres] as q (q)}<option value={q}>{q}</option>{/each}
						</select>
						<button
							type="button"
							class="sacar-p"
							aria-label="Sacar la pregunta {i + 1}"
							onclick={() => (f.prompts = f.prompts.filter((_, j) => j !== i))}
							><Icono nombre="cerrar" tam={16} trazo={2.6} /></button
						>
					</div>
					<textarea
						class="input"
						bind:value={pr.r}
						maxlength="140"
						rows="2"
						placeholder="Tu respuesta"
						aria-label="Respuesta a {pr.p}"></textarea>
				</div>
			{/each}
			{#if f.prompts.length < 3}
				<button
					type="button"
					class="chip sumar-p"
					onclick={() => (f.prompts = [...f.prompts, { p: libres[0], r: '' }])}
					><Icono nombre="mas" tam={16} trazo={2.6} />Sumar una pregunta</button
				>
			{/if}
		</section>

		<section class="tarjeta-plana bloque">
			<span class="rotulo">Le copa</span>
			<div class="chips">
				{#each sugeridos as t (t)}
					<button
						type="button"
						class="chip chico"
						aria-pressed={f.intereses.includes(t)}
						onclick={() => alternar(t)}>{t}</button
					>
				{/each}
			</div>
			<input
				class="input"
				bind:value={nuevo}
				placeholder="Sumá otro y apretá Enter"
				maxlength="30"
				aria-label="Sumar otro interés"
				onkeydown={(e) => {
					if (e.key === 'Enter') {
						e.preventDefault();
						sumar();
					}
				}}
				onblur={sumar}
			/>
		</section>

		{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}

		<form
			method="POST"
			use:enhance={() => {
				enviando = true;
				return async ({ result, update }) => {
					enviando = false;
					if (result.type === 'redirect') avisar('Perfil guardado.');
					await update({ reset: false });
				};
			}}
		>
			<input type="hidden" name="datos" value={JSON.stringify(f)} />
			<button class="gel gel-violeta guardar" disabled={enviando || subiendo}>Guardar</button>
		</form>
	</div>
</div>

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px 40px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.bloque {
		display: flex;
		flex-direction: column;
		gap: 14px;
		padding: 18px;
	}
	.chips {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.chip.chico {
		padding: 9px 13px;
		font-size: 13px;
	}
	.ayuda {
		font-size: 12px;
		color: var(--texto-2);
	}
	.cuenta {
		align-self: flex-end;
	}
	.fotos {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 10px;
	}
	.foto-tile {
		position: relative;
		aspect-ratio: 3 / 4;
		border-radius: 18px;
		background-size: cover;
		background-position: center;
	}
	.foto-tile.on {
		box-shadow:
			0 0 0 3px #ffffff,
			0 0 0 6px var(--violeta);
	}
	.sacar {
		position: absolute;
		top: 6px;
		right: 6px;
		width: 28px;
		height: 28px;
		border-radius: 10px;
		display: grid;
		place-items: center;
		color: var(--carbon);
		background: var(--blanco-calido);
	}
	.hacer {
		position: absolute;
		left: 6px;
		right: 6px;
		bottom: 6px;
		padding: 5px 0;
		border-radius: 10px;
		background: rgba(255, 255, 255, 0.18);
		backdrop-filter: blur(8px);
		color: #ffffff;
		font-size: 11px;
		font-weight: 800;
	}
	.subir {
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 6px;
		background: var(--input);
		box-shadow: var(--sh-input);
		font-size: 13px;
		font-weight: 800;
		color: var(--violeta-texto);
		cursor: pointer;
	}
	.subir.cargando {
		opacity: 0.6;
		pointer-events: none;
	}
	.subir:has(input:focus-visible) {
		outline: 3px solid #b3a3ff;
	}
	.pregunta {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.pq-fila {
		display: flex;
		gap: 8px;
	}
	select.input {
		appearance: none;
		font-weight: 700;
	}
	textarea.input {
		min-height: 0;
	}
	.sacar-p {
		flex: none;
		width: 52px;
		height: 52px;
		border-radius: 18px;
		display: grid;
		place-items: center;
		color: var(--texto-2);
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
	}
	.sumar-p {
		align-self: flex-start;
	}
	.guardar {
		width: 100%;
		height: 54px;
		border-radius: 18px;
		font-size: 16px;
	}
	.error {
		padding: 10px 12px;
		border-radius: 14px;
		background: #ffe1db;
		color: #9a2e1f;
		font-size: 13px;
		font-weight: 700;
		text-align: center;
	}
</style>
