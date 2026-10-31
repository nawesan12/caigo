<script lang="ts">
	import { untrack } from 'svelte';
	import { enhance } from '$app/forms';
	import Icono from '#lib/ui/Icono.svelte';
	import Mapa from '#lib/ui/Mapa.svelte';
	import { avisar } from '#lib/toast.svelte.ts';

	/*
	 * Datos y foto del lugar. No está en el handoff: el formulario es el del registro (tarjeta, campos,
	 * chips y mapa) y la foto, el selector de portada del wizard "Nuevo plan".
	 */
	let { data, form } = $props();

	const CIUDADES: { nombre: string; centro: [number, number] }[] = [
		{ nombre: 'Mar del Plata', centro: [-38.0055, -57.5426] },
		{ nombre: 'Villa Gesell', centro: [-37.2636, -56.9733] }
	];
	const l = untrack(() => data.lugar!);
	let ciudad = $state(l.ciudad || 'Mar del Plata');
	let lat = $state(l.lat);
	let lng = $state(l.lng);
	let foto = $state(l.foto);
	let subiendo = $state(false);
	let enviando = $state(false);
	const centro = $derived(CIUDADES.find((c) => c.nombre === ciudad)?.centro ?? CIUDADES[0].centro);

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
			foto = j.url;
		} catch {
			avisar('No pudimos subir la foto.');
		} finally {
			subiendo = false;
		}
	}
</script>

<svelte:head><title>Tu lugar · Caigo para lugares</title></svelte:head>

<div class="vista">
	<div class="titulos">
		<h1>Tu lugar</h1>
		<p>Así aparece en Caigo: en tus planes, en el pase de socio y en el&nbsp;mapa.</p>
	</div>

	<form
		method="POST"
		class="tarjeta-form"
		use:enhance={() => {
			enviando = true;
			return async ({ result, update }) => {
				enviando = false;
				if (result.type === 'success') avisar('Datos guardados.');
				await update({ reset: false });
			};
		}}
	>
		<div class="campo">
			<span>Foto del lugar</span>
			<div class="fotos">
				{#if foto}
					<button
						type="button"
						class="foto-tile on"
						style:background-image="url({foto})"
						aria-label="Sacar la foto"
						onclick={() => (foto = '')}
					></button>
				{/if}
				<label class="foto-tile subir" class:cargando={subiendo}>
					<input type="file" accept="image/jpeg,image/png,image/webp" class="sr" onchange={subir} />
					<Icono nombre="mas" tam={22} color="#5B43C9" trazo={2.6} />
					<span>{subiendo ? 'Subiendo…' : foto ? 'Cambiar foto' : 'Subir foto'}</span>
				</label>
			</div>
			<input type="hidden" name="foto" value={foto} />
		</div>
		<label class="campo">
			<span>Nombre del lugar</span>
			<input class="input grande" name="nombre" required maxlength="80" defaultValue={l.nombre} />
		</label>
		<label class="campo">
			<span>Rubro</span>
			<input
				class="input"
				name="rubro"
				maxlength="40"
				placeholder="Ej: café, bar, cancha"
				defaultValue={l.rubro}
			/>
		</label>
		<div class="campo">
			<span>Ciudad</span>
			<div class="chips">
				{#each CIUDADES as c (c.nombre)}
					<button
						type="button"
						class="chip"
						aria-pressed={ciudad === c.nombre}
						onclick={() => ((ciudad = c.nombre), (lat = 0), (lng = 0))}>{c.nombre}</button
					>
				{/each}
			</div>
			<input type="hidden" name="ciudad" value={ciudad} />
		</div>
		<div class="dos">
			<label class="campo">
				<span>Zona</span>
				<input
					class="input"
					name="zona"
					maxlength="60"
					placeholder="Ej: Güemes"
					defaultValue={l.zona}
				/>
			</label>
			<label class="campo">
				<span>Dirección</span>
				<input
					class="input"
					name="direccion"
					required
					maxlength="160"
					placeholder="Ej: Alsina 2450"
					defaultValue={l.direccion}
				/>
			</label>
		</div>
		<div class="campo">
			<span>Dónde queda</span>
			<span class="ayuda">Tocá el mapa en la puerta del lugar. Podés arrastrar el&nbsp;pin.</span>
			<Mapa bind:lat bind:lng {centro} />
			<input type="hidden" name="lat" value={lat} />
			<input type="hidden" name="lng" value={lng} />
		</div>
		<div class="dos">
			<label class="campo">
				<span>Instagram <small>(opcional)</small></span>
				<input
					class="input"
					name="instagram"
					maxlength="60"
					placeholder="@cafemolienda"
					defaultValue={l.instagram}
				/>
			</label>
			<label class="campo">
				<span>Teléfono <small>(opcional)</small></span>
				<input
					class="input"
					name="telefono"
					maxlength="30"
					inputmode="tel"
					placeholder="223 555-0000"
					defaultValue={l.telefono}
				/>
			</label>
		</div>
		{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}
		<div class="pie">
			<span></span>
			<button class="gel gel-lima enviar" disabled={enviando || subiendo}
				>{enviando ? 'Guardando…' : 'Guardar cambios'}</button
			>
		</div>
	</form>
</div>

<style>
	.vista {
		max-width: 720px;
		display: flex;
		flex-direction: column;
		gap: 26px;
		animation: tk-up 0.35s ease both;
	}
	.titulos {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	h1 {
		font:
			800 44px/1 'Geist',
			sans-serif;
		letter-spacing: -1.6px;
	}
	.titulos p {
		font-size: 15px;
		font-weight: 600;
		line-height: 1.5;
		color: #5c586b;
	}
	.tarjeta-form {
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
	small {
		font-weight: 600;
		color: #6b6780;
	}
	.chips {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.dos {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
		gap: 20px;
	}
	.ayuda {
		font-size: 13px;
		font-weight: 600;
		color: #5c586b;
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
	.error {
		padding: 14px 16px;
		border-radius: 18px;
		background: #ffe1db;
		color: #9a2e1f;
		font-size: 14px;
		font-weight: 700;
	}
	.pie {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 16px;
		flex-wrap: wrap;
	}
	.enviar {
		height: 58px;
		padding: 0 28px;
		border-radius: 20px;
		font-size: 16px;
	}
	@media (max-width: 900px) {
		h1 {
			font-size: 36px;
		}
		.tarjeta-form {
			padding: 20px;
			border-radius: 26px;
		}
		.enviar {
			width: 100%;
		}
	}
</style>
