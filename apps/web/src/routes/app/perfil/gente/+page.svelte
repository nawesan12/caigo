<script lang="ts">
	import Icono from '#lib/ui/Icono.svelte';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import CabeceraVolver from '#lib/app-ui/CabeceraVolver.svelte';
	import VacioBurbujas from '#lib/app-ui/VacioBurbujas.svelte';
	import Verificado from '#lib/app-ui/Verificado.svelte';
	import { reputacion } from '#lib/app.ts';
	import { primerNombre } from '#lib/plan.ts';

	/*
	 * Mi gente, como en las apps (MiGente): una tarjeta por persona con su asistencia, que lleva a su
	 * perfil, y el botón para mandarle un mensaje. Vacío: el de Mis planes (13b).
	 */
	let { data } = $props();
	const sub = $derived(data.gente.length === 1 ? '1 persona' : `${data.gente.length} personas`);
</script>

<svelte:head><title>Mi gente · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<CabeceraVolver
			volver="/app/perfil"
			titulo="Mi gente"
			sub={data.gente.length ? sub : ''}
			etiqueta="Volver al perfil"
		/>

		{#each data.gente as g (g.id)}
			<div class="fila">
				<a class="quien" href="/app/persona/{g.id}?volver=%2Fapp%2Fperfil%2Fgente">
					<Avatar nombre={g.nombre} foto={g.fotos?.[0]} tam={44} />
					<span class="col">
						<span class="nombre"
							><b>{g.nombre}{g.edad ? `, ${g.edad}` : ''}</b>{#if g.verificada}<Verificado
									tam={15}
								/>{/if}</span
						>
						<span class="rep">{reputacion(g.asistencia)}</span>
					</span>
				</a>
				<a
					class="mensaje"
					href="/app/mensajes/{g.id}"
					aria-label="Mandarle un mensaje a {primerNombre(g.nombre)}"
					><Icono nombre="enviar" tam={18} color="var(--c-acento)" trazo={2.2} /></a
				>
			</div>
		{:else}
			<VacioBurbujas
				titulo="Todavía no sumaste a nadie"
				texto="Después de cada plan te preguntamos con quién volverías a salir."
				cta="Ver mis planes"
				href="/app/mis"
			/>
		{/each}
	</div>
</div>

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px 40px;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.contenido > :global(.cab) {
		margin-bottom: 6px;
	}
	.fila {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 12px;
		border-radius: 20px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plan);
	}
	.quien {
		flex: 1;
		min-width: 0;
		display: flex;
		align-items: center;
		gap: 12px;
		color: inherit;
		text-decoration: none;
	}
	.col {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 1px;
	}
	.nombre {
		display: flex;
		align-items: center;
		gap: 6px;
		min-width: 0;
		font-size: 15px;
	}
	.nombre b {
		font-weight: 700;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.rep {
		font-size: 12px;
		color: var(--texto-2);
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.mensaje {
		flex: none;
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
	}
</style>
