<script lang="ts">
	import { invalidateAll } from '$app/navigation';
	import { escucharVivo } from '#lib/vivo.ts';

	// En vivo: un privado o un mensaje de plan nuevo actualiza la lista.
	onMount(() =>
		escucharVivo((e) => {
			if (e.tipo === 'privado' || e.tipo === 'mensaje') invalidateAll();
		})
	);
	import { onMount } from 'svelte';
	import { invalidate, replaceState } from '$app/navigation';
	import { page } from '$app/state';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import CabeceraVolver from '#lib/app-ui/CabeceraVolver.svelte';
	import VacioBurbujas from '#lib/app-ui/VacioBurbujas.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { hace } from '#lib/fechas.ts';
	import { primerNombre } from '#lib/plan.ts';

	/*
	 * Mensajes privados. El handoff no tiene la pantalla: se arma como en las apps (VistaPrivados),
	 * con la cabecera de Solicitudes, las tarjetas de Mis planes y su vacío (13b). Cada fila: foto,
	 * nombre, «hace 5 min», el último mensaje («Vos: …» si es tuyo, en negrita si no lo leíste) y el
	 * contador coral de no leídos. Se refresca cada 10 s mientras está a la vista.
	 */
	let { data } = $props();

	const total = $derived(data.conversaciones.reduce((n, c) => n + c.sinLeer, 0));

	onMount(() => {
		if (data.bloqueaste !== null) {
			avisar(
				data.bloqueaste
					? `Bloqueaste a ${primerNombre(data.bloqueaste)}. No se van a ver más.`
					: 'Listo, la bloqueaste. No se van a ver más.'
			);
			// Que el toast no vuelva a salir al recargar.
			replaceState('/app/mensajes', page.state);
		}
		const t = setInterval(() => {
			if (document.visibilityState === 'visible') invalidate('app:privados');
		}, 10_000);
		return () => clearInterval(t);
	});
</script>

<svelte:head><title>{total ? `(${total}) ` : ''}Mensajes · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<CabeceraVolver
			volver="/app/mis"
			titulo="Mensajes"
			sub={total ? (total === 1 ? '1 sin leer' : `${total} sin leer`) : ''}
			etiqueta="Volver a Mis planes"
		/>

		{#each data.conversaciones as c (c.con.id)}
			{@const nuevo = c.sinLeer > 0}
			<a class="fila" href="/app/mensajes/{c.con.id}">
				<Avatar nombre={c.con.nombre} foto={c.con.foto} tam={44} />
				<span class="col">
					<span class="arriba">
						<b class="nombre">{c.con.nombre}</b>
						<span class="hora">{hace(c.ultimo.creadoEn)}</span>
					</span>
					<span class="ultimo" class:nuevo
						>{c.ultimo.deId === data.yo.id ? 'Vos: ' : ''}{c.ultimo.texto}</span
					>
				</span>
				{#if nuevo}
					<span class="cuenta" aria-label="{c.sinLeer} sin leer"
						>{c.sinLeer > 99 ? '99+' : c.sinLeer}</span
					>
				{/if}
			</a>
		{:else}
			<VacioBurbujas
				titulo="Todavía no hay mensajes"
				texto="Podés escribirle a la gente con la que compartiste un plan."
				cta="Ver mi gente"
				href="/app/perfil/gente"
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
		gap: 12px;
		padding: 12px;
		border-radius: 20px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plan);
		color: inherit;
		text-decoration: none;
	}
	.col {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.arriba {
		display: flex;
		align-items: baseline;
		gap: 8px;
	}
	.nombre,
	.ultimo {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.nombre {
		flex: 1;
		min-width: 0;
		font-size: 15px;
		font-weight: 700;
	}
	.hora {
		flex: none;
		font-size: 12px;
		color: var(--texto-2);
	}
	.ultimo {
		font-size: 13px;
		color: var(--texto-2);
	}
	.ultimo.nuevo {
		font-weight: 700;
		color: var(--carbon);
	}
	.cuenta {
		flex: none;
		min-width: 22px;
		height: 22px;
		padding: 0 6px;
		border-radius: 11px;
		display: grid;
		place-items: center;
		background: var(--coral);
		color: #24212d;
		font-size: 11px;
		font-weight: 800;
	}
</style>
