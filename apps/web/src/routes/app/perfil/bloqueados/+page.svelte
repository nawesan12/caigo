<script lang="ts">
	import { enhance } from '$app/forms';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import CabeceraVolver from '#lib/app-ui/CabeceraVolver.svelte';
	import Vacio from '#lib/ui/Vacio.svelte';
	import { avisar } from '#lib/toast.svelte.ts';

	/*
	 * Bloqueados: la lista con "Desbloquear". Se arma con las filas de Solicitudes (avatar, nombre)
	 * y el estado vacío de 13c.
	 */
	let { data } = $props();
</script>

<svelte:head><title>Bloqueos · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<CabeceraVolver
			volver="/app/perfil/ajustes"
			titulo="Bloqueos y reportes"
			sub="Los revisa una persona del equipo, no un bot"
			etiqueta="Volver a ajustes"
		/>

		{#if data.bloqueos.length}
			<div class="lista">
				{#each data.bloqueos as b (b.id)}
					<div class="fila">
						<Avatar nombre={b.nombre} foto={b.foto} tam={44} />
						<span class="nombre">{b.nombre}</span>
						<form
							method="POST"
							action="?/desbloquear"
							use:enhance={() =>
								async ({ result, update }) => {
									if (result.type === 'success') avisar(`Desbloqueaste a ${b.nombre}.`);
									else if (result.type === 'failure')
										avisar(String(result.data?.error ?? 'No se pudo.'));
									await update();
								}}
						>
							<input type="hidden" name="id" value={b.id} />
							<button class="chip">Desbloquear</button>
						</form>
					</div>
				{/each}
			</div>
			<p class="ayuda">
				Quien está bloqueado no ve tus planes ni tu perfil, y no te puede&nbsp;escribir.
			</p>
		{:else}
			<div class="vacio">
				<Vacio
					titulo="No bloqueaste a nadie"
					texto="Si alguien te incomoda, bloquealo o reportalo desde su perfil o el chat del&nbsp;plan."
				/>
			</div>
		{/if}
	</div>
</div>

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px 40px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.lista {
		display: flex;
		flex-direction: column;
		border-radius: 22px;
		background: #ffffff;
		box-shadow: var(--sh-tarjeta-plana);
	}
	.fila {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 14px;
		border-bottom: 1px solid #f0ecff;
	}
	.fila:last-child {
		border-bottom: 0;
	}
	.nombre {
		flex: 1;
		min-width: 0;
		font-size: 15px;
		font-weight: 700;
	}
	.chip {
		padding: 9px 13px;
		font-size: 13px;
	}
	.ayuda {
		text-align: center;
		font-size: 12px;
		line-height: 1.5;
		color: var(--texto-2);
	}
	.vacio {
		padding-top: 40px;
	}
</style>
