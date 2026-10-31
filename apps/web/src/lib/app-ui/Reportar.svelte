<script lang="ts">
	import Hoja from './Hoja.svelte';
	import { avisar } from '#lib/toast.svelte.ts';

	/*
	 * Reportar un plan o a una persona. El handoff sólo tiene el toast del Prototipo («Gracias.
	 * Revisamos… en menos de 24 h.») y la fila «Bloqueos y reportes: los revisa una persona del
	 * equipo, no un bot»; la hoja se arma con los radios de 06c y el campo de 06a.
	 */
	let {
		abierta = $bindable(false),
		planId = '',
		usuarioId = '',
		titulo = 'Reportar el plan'
	}: { abierta?: boolean; planId?: string; usuarioId?: string; titulo?: string } = $props();

	const MOTIVOS: [string, string][] = [
		['acoso', 'Acoso o malos tratos'],
		['inseguro', 'Me sentí en peligro'],
		['falso', 'Es falso o engañoso'],
		['inapropiado', 'Contenido inapropiado'],
		['no_vino', 'No vino o no se hizo'],
		['otro', 'Otra cosa']
	];
	let motivo = $state('');
	let detalle = $state('');
	let enviando = $state(false);

	async function enviar() {
		if (!motivo) return avisar('Elegí un motivo.');
		enviando = true;
		try {
			const r = await fetch('/app/reportar', {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ motivo, detalle, planId, usuarioId })
			});
			const j = await r.json().catch(() => ({}));
			if (!r.ok) return avisar(j.error || 'No pudimos mandar el reporte.');
			abierta = false;
			motivo = '';
			detalle = '';
			avisar('Gracias. Lo revisamos en menos de 24 h.');
		} catch {
			avisar('No pudimos mandar el reporte.');
		} finally {
			enviando = false;
		}
	}
</script>

<Hoja bind:abierta {titulo}>
	<p class="ayuda">
		Lo revisa una persona del equipo, no un bot. Nadie se entera de que&nbsp;fuiste vos.
	</p>
	<div class="radios" role="radiogroup" aria-label="Motivo">
		{#each MOTIVOS as [k, l] (k)}
			<button
				class="radio"
				class:on={motivo === k}
				role="radio"
				aria-checked={motivo === k}
				onclick={() => (motivo = k)}
			>
				<span class="anillo"></span><b>{l}</b>
			</button>
		{/each}
	</div>
	<textarea
		class="campo-b"
		bind:value={detalle}
		maxlength="1000"
		rows="3"
		placeholder="Contanos qué pasó (opcional)"
		aria-label="Qué pasó"></textarea>
	<button class="gel gel-lima cta" onclick={enviar} disabled={enviando}
		>{enviando ? 'Mandando…' : 'Mandar reporte'}</button
	>
</Hoja>

<style>
	.ayuda {
		font-size: 13px;
		font-weight: 600;
		color: var(--texto-2);
		line-height: 1.45;
		margin-top: -6px;
	}
	.radios {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.radio {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 14px;
		border-radius: 18px;
		background: var(--c-tarjeta);
		border: 1.5px solid var(--c-borde);
		text-align: left;
		font-size: 15px;
		font-weight: 700;
		color: var(--carbon);
	}
	.radio.on {
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
		border: 2px solid var(--violeta);
	}
	.anillo {
		flex: none;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		border: 2px solid #cfc8e6;
		background: var(--c-tarjeta);
	}
	.on .anillo {
		border: 7px solid var(--violeta);
	}
	.campo-b {
		border-radius: 16px;
		background: var(--c-tarjeta);
		border: 1.5px solid var(--c-borde);
		padding: 12px 14px;
		font-size: 15px;
		font-weight: 600;
		resize: none;
		color: var(--carbon);
	}
	.cta {
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
	}
</style>
