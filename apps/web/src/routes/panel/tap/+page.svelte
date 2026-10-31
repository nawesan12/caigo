<script lang="ts">
	import { untrack } from 'svelte';
	import { enhance } from '$app/forms';
	import Icono from '#lib/ui/Icono.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import type { SubmitFunction } from '$app/forms';

	/*
	 * Caigo Tap + Pase de socio (Panel Lugares, vista "tap"). Lo que el handoff no dibuja (el
	 * formulario de "+ Activar placa" y la clave del chip NTAG 424) va con la tarjeta, los chips y
	 * la tarjeta oscura del mismo panel.
	 */
	let { data, form } = $props();

	const lugar = $derived(data.lugar!);
	let abierto = $state(false);
	let uso = $state<'puerta' | 'barra'>('puerta');
	let sun = $state(false);
	const ini = untrack(() => data.lugar!);
	let socio = $state({
		activo: ini.socioActivo,
		visitas: ini.socioVisitas || 5,
		beneficio: ini.socioBeneficio || ''
	});
	const activada = $derived(form && 'activada' in form ? form.activada : null);

	const DESC = {
		puerta: 'Activa · check-in de entradas y pases de socio',
		barra: 'Activa · canje de beneficios'
	};

	const conAviso =
		(ok: string): SubmitFunction =>
		() =>
		async ({ result, update }) => {
			if (result.type === 'success') {
				if (ok) avisar(ok);
				abierto = false;
			} else if (result.type === 'failure') avisar(String(result.data?.error ?? 'No se pudo.'));
			await update({ reset: false });
		};

	async function copiar(t: string) {
		try {
			await navigator.clipboard.writeText(t);
			avisar('Copiado.');
		} catch {
			avisar(t);
		}
	}
	const mailto = $derived(
		`mailto:hola@caigo.com.ar?subject=${encodeURIComponent(`Placas Caigo Tap para ${lugar.nombre}`)}`
	);
</script>

<svelte:head><title>Caigo Tap · Caigo para lugares</title></svelte:head>

<div class="vista">
	<div class="cabeza">
		<div class="titulos">
			<h1>Caigo Tap</h1>
			<p>
				La gente apoya el celu en tu placa Caigo Tap o te muestra el QR. El check-in, la visita del
				pase de socio y el beneficio pasan&nbsp;solos.
			</p>
		</div>
		<button class="gel gel-violeta activar" onclick={() => (abierto = !abierto)}
			>+ Activar placa</button
		>
	</div>

	<div class="cuerpo">
		<div class="placas">
			<span class="overline">PLACAS</span>

			{#if abierto}
				<form
					method="POST"
					action="?/activar"
					class="nueva tarjeta-plana"
					use:enhance={conAviso('Placa activada. Probala apoyando un celu.')}
				>
					<label class="campo">
						<span>Nombre de la placa</span>
						<input
							class="input"
							name="nombre"
							maxlength="40"
							placeholder="Ej: Puerta principal"
							required
						/>
					</label>
					<div class="campo">
						<span>Dónde va</span>
						<div class="chips">
							<button
								type="button"
								class="chip"
								aria-pressed={uso === 'puerta'}
								onclick={() => (uso = 'puerta')}>Puerta</button
							>
							<button
								type="button"
								class="chip"
								aria-pressed={uso === 'barra'}
								onclick={() => (uso = 'barra')}>Barra</button
							>
						</div>
						<input type="hidden" name="uso" value={uso} />
					</div>
					<button
						type="button"
						class="tg"
						role="switch"
						aria-checked={sun}
						onclick={() => (sun = !sun)}
					>
						<span class="tg-txt"
							><span class="t">Chip NTAG 424 DNA</span><span class="s"
								>Con firma criptográfica. La clave para programarlo se muestra una sola vez.</span
							></span
						>
						<span class="toggle" class:on={sun}></span>
					</button>
					{#if sun}<input type="hidden" name="sun" value="on" />{/if}
					<button class="gel gel-violeta guardar">Activar</button>
				</form>
			{/if}

			{#if activada}
				<div class="clave tarjeta-oscura">
					<span class="t">«{activada.nombre}» activada</span>
					{#if activada.claveSun}
						<span class="s">Clave del chip (no se vuelve a mostrar):</span>
						<code>{activada.claveSun}</code>
						<button type="button" class="copiar" onclick={() => copiar(activada.claveSun!)}
							>Copiar clave</button
						>
					{/if}
					<span class="s">Link que va grabado en la placa o en su QR:</span>
					<code>{activada.url}</code>
					<button type="button" class="copiar" onclick={() => copiar(activada.url)}
						>Copiar link</button
					>
				</div>
			{/if}

			{#each data.placas as x (x.id)}
				<div class="placa tarjeta-plana">
					<span class="ico"><Icono nombre="tap" tam={22} color="#24212D" trazo={2.4} /></span>
					<div class="placa-txt">
						<span class="pn">{x.nombre}</span>
						<span class="pd"><i></i>{DESC[x.uso]}{x.sun ? ' · NTAG 424' : ''}</span>
					</div>
					<div class="placa-acc">
						<a class="probar" href={x.url} target="_blank" rel="noopener">Probar</a>
						<form
							method="POST"
							action="?/baja"
							use:enhance={(input) => {
								if (!confirm(`¿Dar de baja «${x.nombre}»? Deja de servir para siempre.`))
									return input.cancel();
								return conAviso('Placa dada de baja.')(input);
							}}
						>
							<input type="hidden" name="placa" value={x.id} />
							<button class="baja" aria-label="Dar de baja {x.nombre}">Dar de baja</button>
						</form>
					</div>
				</div>
			{:else}
				{#if !abierto}
					<div class="vacia tarjeta-plana">Todavía no activaste ninguna placa.</div>
				{/if}
			{/each}

			<div class="kit tarjeta-oscura">
				<div class="kit-txt">
					<span class="t">¿Querés más placas?</span>
					<span class="s">Te mandamos placas Caigo Tap con tu nombre, listas para pegar.</span>
				</div>
				<a class="gel gel-lima pedir" href={mailto}>Pedir placas</a>
			</div>
		</div>

		<form
			method="POST"
			action="?/socio"
			class="socio tarjeta-plana"
			use:enhance={conAviso('Pase de socio guardado.')}
		>
			<button
				type="button"
				class="tg sin-borde"
				role="switch"
				aria-checked={socio.activo}
				onclick={() => (socio.activo = !socio.activo)}
			>
				<span class="tg-txt"
					><span class="h2">Pase de socio</span><span class="s"
						>Cada tap suma una visita en la Wallet de la persona</span
					></span
				>
				<span class="toggle" class:on={socio.activo}></span>
			</button>
			{#if socio.activo}<input type="hidden" name="activo" value="on" />{/if}
			<div class="campo">
				<span>Visitas para el beneficio</span>
				<div class="chips">
					{#each [3, 5, 8, 10] as n (n)}
						<button
							type="button"
							class="chip num"
							aria-pressed={socio.visitas === n}
							onclick={() => (socio.visitas = n)}>{n}</button
						>
					{/each}
				</div>
				<input type="hidden" name="visitas" value={socio.visitas} />
			</div>
			<label class="campo">
				<span>Beneficio</span>
				<input
					class="input"
					name="beneficio"
					maxlength="80"
					bind:value={socio.beneficio}
					placeholder="Ej: Taller gratis"
				/>
			</label>
			<div class="wallet">
				<div class="w-fila">
					<span class="w-n">{lugar.nombre} · Socio</span>
					<span class="w-v">0 de {socio.visitas}</span>
				</div>
				<span class="w-s">Así arranca en la Wallet. Beneficio: {socio.beneficio || '—'}</span>
			</div>
			<button class="gel gel-violeta guardar">Guardar</button>
		</form>
	</div>
</div>

<style>
	.vista {
		max-width: 1180px;
		display: flex;
		flex-direction: column;
		gap: 24px;
		animation: tk-up 0.35s ease both;
	}
	.cabeza {
		display: flex;
		justify-content: space-between;
		align-items: flex-end;
		gap: 20px;
		flex-wrap: wrap;
	}
	.titulos {
		display: flex;
		flex-direction: column;
		gap: 6px;
		max-width: 640px;
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
	.activar {
		height: 52px;
		padding: 0 22px;
		border-radius: 18px;
		font-size: 15px;
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.45),
			0 4px 0 #4a2fc2;
	}
	.cuerpo {
		display: flex;
		flex-wrap: wrap;
		gap: 24px;
		align-items: flex-start;
	}
	.placas {
		flex: 1 1 460px;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.nueva {
		display: flex;
		flex-direction: column;
		gap: 18px;
		padding: 22px;
		border-radius: 22px;
	}
	.chips {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.chip.num {
		min-width: 52px;
		font-weight: 800;
	}
	.tg {
		width: 100%;
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 14px;
		text-align: left;
	}
	.tg-txt {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.t {
		font-size: 16px;
		font-weight: 800;
	}
	.s {
		font-size: 13px;
		font-weight: 600;
		color: #5c586b;
	}
	.h2 {
		font:
			700 22px/1.1 'Geist',
			sans-serif;
	}
	.guardar {
		align-self: flex-start;
		height: 52px;
		padding: 0 26px;
		border-radius: 18px;
		font-size: 15px;
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.45),
			0 4px 0 #4a2fc2;
	}
	.clave {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding: 18px 20px;
		border-radius: 22px;
	}
	.clave .s {
		color: #d3cee2;
	}
	code {
		padding: 10px 12px;
		border-radius: 12px;
		background: rgba(255, 255, 255, 0.08);
		font-size: 13px;
		word-break: break-all;
		color: #c8f36b;
	}
	.copiar {
		align-self: flex-start;
		padding: 8px 14px;
		border-radius: 12px;
		background: rgba(255, 255, 255, 0.12);
		color: #ffffff;
		font-size: 13px;
		font-weight: 800;
	}
	.placa {
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 18px 20px;
		border-radius: 22px;
	}
	.ico {
		flex: none;
		width: 46px;
		height: 46px;
		border-radius: 15px;
		display: grid;
		place-items: center;
		background: var(--grad-lima);
		box-shadow: 0 3px 0 #94c43a;
	}
	.placa-txt {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.pn {
		font-size: 16px;
		font-weight: 800;
	}
	.pd {
		display: flex;
		align-items: center;
		gap: 7px;
		font-size: 13px;
		font-weight: 600;
		color: #5c586b;
	}
	.pd i {
		flex: none;
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: #7fc41e;
	}
	.placa-acc {
		flex: none;
		display: flex;
		gap: 8px;
		align-items: center;
	}
	.probar,
	.baja {
		padding: 10px 14px;
		border-radius: 14px;
		font-size: 13px;
		font-weight: 800;
		text-decoration: none;
	}
	.probar {
		color: #5b43c9;
		background: #f0ecff;
	}
	.baja {
		color: #9a2e1f;
		background: #ffe1db;
	}
	.vacia {
		padding: 24px 20px;
		border-radius: 22px;
		font-size: 14px;
		font-weight: 600;
		color: #5c586b;
	}
	.kit {
		display: flex;
		align-items: center;
		gap: 16px;
		padding: 18px 20px;
		border-radius: 22px;
		flex-wrap: wrap;
	}
	.kit-txt {
		flex: 1 1 220px;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.kit .s {
		color: #d3cee2;
	}
	.pedir {
		padding: 12px 18px;
		border-radius: 16px;
		font-size: 14px;
		box-shadow: 0 3px 0 #7fa82e;
	}
	.socio {
		flex: 1 1 320px;
		max-width: 460px;
		display: flex;
		flex-direction: column;
		gap: 16px;
		padding: 22px;
		border-radius: 28px;
	}
	.wallet {
		display: flex;
		flex-direction: column;
		gap: 10px;
		padding: 16px;
		border-radius: 18px;
		background:
			url(/grain.png), radial-gradient(120% 80% at 100% 0%, #8a70ff 0%, #5a3dd8 60%, #3b2a9e 100%);
		color: #ffffff;
	}
	.w-fila {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 10px;
	}
	.w-n {
		font-size: 14px;
		font-weight: 800;
	}
	.w-v {
		font-size: 12px;
		font-weight: 800;
		color: #c8f36b;
		white-space: nowrap;
	}
	.w-s {
		font-size: 12px;
		font-weight: 600;
		color: #e4ddff;
	}
	@media (max-width: 700px) {
		h1 {
			font-size: 36px;
		}
		.placa {
			flex-wrap: wrap;
			padding: 16px;
		}
		.placa-acc {
			width: 100%;
			padding-left: 60px;
		}
		.socio {
			max-width: none;
			flex-basis: 100%;
		}
		.placas {
			flex-basis: 100%;
		}
	}
</style>
