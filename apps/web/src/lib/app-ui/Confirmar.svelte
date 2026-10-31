<script lang="ts">
	import { enhance } from '$app/forms';
	import Hoja from './Hoja.svelte';
	import { avisar } from '#lib/toast.svelte.ts';

	/*
	 * Confirmar algo que no tiene vuelta (salir, cancelar, sacar a alguien): la hoja con el texto y
	 * los dos botones de 07 Solicitudes («Ahora no» / el gel lima). Manda el formulario a `accion`.
	 */
	let {
		abierta = $bindable(false),
		titulo,
		texto,
		boton,
		accion,
		campos = {},
		listo = ''
	}: {
		abierta?: boolean;
		titulo: string;
		texto: string;
		boton: string;
		accion: string;
		campos?: Record<string, string>;
		/** Toast cuando sale bien (si la acción no redirige). */
		listo?: string;
	} = $props();

	let enviando = $state(false);
</script>

<Hoja bind:abierta {titulo}>
	<p class="texto">{texto}</p>
	<form
		method="POST"
		action={accion}
		use:enhance={() => {
			enviando = true;
			return async ({ result, update }) => {
				enviando = false;
				if (result.type === 'failure') avisar(String(result.data?.error ?? 'No se pudo.'));
				else {
					abierta = false;
					if (result.type === 'success' && listo) avisar(listo);
				}
				await update({ reset: false });
			};
		}}
	>
		{#each Object.entries(campos) as [k, v] (k)}<input type="hidden" name={k} value={v} />{/each}
		<div class="botones">
			<button type="button" class="no" onclick={() => (abierta = false)}>Mejor no</button>
			<button class="gel gel-lima si" disabled={enviando}>{boton}</button>
		</div>
	</form>
</Hoja>

<style>
	.texto {
		font-size: 15px;
		line-height: 1.5;
		color: var(--texto-2);
		margin-top: -4px;
	}
	.botones {
		display: grid;
		grid-template-columns: 1fr 1.4fr;
		gap: 8px;
		margin-top: 6px;
	}
	.no,
	.si {
		height: 54px;
		border-radius: 18px;
		font-size: 16px;
	}
	.no {
		border: 1.5px solid var(--c-borde-chip);
		font-weight: 700;
		color: var(--texto-2);
	}
</style>
