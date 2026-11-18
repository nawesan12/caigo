<script lang="ts">
	import { flushSync, untrack } from 'svelte';
	import { enhance, type SubmitFunction } from '$app/forms';
	import Icono from '#lib/ui/Icono.svelte';
	import CabeceraVolver from '#lib/app-ui/CabeceraVolver.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { INTENCIONES } from '#lib/app.ts';
	import type { Contacto, YoCompleto } from '#lib/tipos.ts';

	/*
	 * Ajustes del perfil. No hay pantalla en el handoff: se arma con la lista "Herramientas para
	 * salir tranqui" de la verificación del Prototipo (filas con ícono, título y bajada), los chips,
	 * el segmented y los toggles de Crear. Lo que la API todavía no devuelve (avisos) arranca
	 * con lo de por defecto y queda como lo dejaste.
	 */
	let { data } = $props();

	const yo = untrack(() => data.yo as YoCompleto);
	let intenciones = $state<string[]>([...(yo.intenciones ?? [])]);
	let contactos = $state<Contacto[]>(
		yo.contactosConfianza?.length
			? yo.contactosConfianza.map((c) => ({ ...c }))
			: yo.contactoConfianza
				? [{ nombre: '', contacto: yo.contactoConfianza }]
				: []
	);
	/** Los renglones del formulario: los que hay y uno vacío para sumar (hasta 3). */
	let borrador = $state<Contacto[]>([]);
	let editandoContacto = $state(false);
	function editarContactos() {
		editandoContacto = !editandoContacto;
		borrador = [...contactos.map((c) => ({ ...c })), { nombre: '', contacto: '' }].slice(0, 3);
	}
	const resumenContactos = $derived(
		contactos.length
			? contactos.map((c) => c.nombre || c.contacto).join(' · ')
			: 'Te cuidan el día del plan con «¿Todo bien?»'
	);
	let avisos = $state({ planesCerca: true, tuGente: true, semana: true, ...(yo.avisos ?? {}) });
	let tema = $state(untrack(() => data.tema));

	/** Envía sin recargar; si sale bien, `aplicar` deja la pantalla como quedó. */
	const enviar =
		(aplicar: (d: Record<string, unknown>) => void, texto = 'Listo.'): SubmitFunction =>
		() =>
		async ({ result, update }) => {
			if (result.type === 'success') {
				aplicar(result.data ?? {});
				avisar(texto);
				await update({ reset: false, refreshAll: false });
			} else if (result.type === 'failure') avisar(String(result.data?.error ?? 'No se pudo.'));
			else await update();
		};

	const TEMAS = [
		['claro', 'Claro'],
		['oscuro', 'Oscuro'],
		['sistema', 'Como el teléfono']
	];
</script>

<svelte:head><title>Ajustes · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<CabeceraVolver volver="/app/perfil" titulo="Ajustes" etiqueta="Volver al perfil" />

		<div class="lista">
			<a class="fila" href="/app/perfil/editar">
				<Icono nombre="usuario" tam={20} color="var(--c-acento)" trazo={2} />
				<span class="ft"
					><b>Editar perfil</b><span>Fotos, sobre mí, preguntas y lo que te copa</span></span
				>
				<Icono nombre="flecha" tam={18} color="#9A95AD" trazo={2.6} />
			</a>
			<a class="fila" href="/app/mensajes">
				<Icono nombre="enviar" tam={20} color="var(--c-acento)" trazo={2} />
				<span class="ft"
					><b>Mensajes privados</b><span>Con tu gente y con quien compartiste un&nbsp;plan</span
					></span
				>
				<Icono nombre="flecha" tam={18} color="#9A95AD" trazo={2.6} />
			</a>
		</div>

		<h2>Qué buscás en Caigo</h2>
		<form
			method="POST"
			action="?/intenciones"
			class="chips"
			use:enhance={enviar(
				(d) => (intenciones = (d.intenciones as string[]) ?? intenciones),
				'Guardado.'
			)}
		>
			{#each intenciones as i (i)}<input type="hidden" name="intencion" value={i} />{/each}
			{#each INTENCIONES as it (it.valor)}
				{@const on = intenciones.includes(it.valor)}
				<button
					class="chip"
					aria-pressed={on}
					onclick={() =>
						flushSync(
							() =>
								(intenciones = on
									? intenciones.filter((x) => x !== it.valor)
									: [...intenciones, it.valor])
						)}>{it.corto}</button
				>
			{/each}
		</form>

		<h2>Herramientas para salir tranqui</h2>
		<div class="lista">
			<div class="fila-col">
				<button class="fila" onclick={editarContactos} aria-expanded={editandoContacto}>
					<Icono nombre="usuario" tam={20} color="var(--c-acento)" trazo={2} />
					<span class="ft"><b>Contactos de confianza</b><span>{resumenContactos}</span></span>
					<Icono nombre="abajo" tam={18} color="#9A95AD" trazo={2.6} />
				</button>
				{#if editandoContacto}
					<form
						method="POST"
						action="?/contacto"
						class="contactos"
						use:enhance={enviar((d) => {
							contactos = (d.contactosConfianza as Contacto[]) ?? contactos;
							editandoContacto = false;
						}, 'Contactos guardados.')}
					>
						{#each borrador as c, i (i)}
							<div class="contacto">
								<input
									class="input nombre"
									name="nombre"
									bind:value={c.nombre}
									maxlength="40"
									placeholder="Mamá"
									aria-label="Nombre del contacto {i + 1}"
								/>
								<input
									class="input"
									name="contacto"
									bind:value={c.contacto}
									maxlength="80"
									placeholder="Teléfono o mail"
									aria-label="Teléfono o mail del contacto {i + 1}"
								/>
							</div>
						{/each}
						<p class="nota">
							Sólo se usan si activás «¿Todo bien?» en un plan. Si es un mail, le escribimos
							nosotros si no llegás.
						</p>
						<button class="gel gel-violeta guardar">Guardar</button>
					</form>
				{/if}
			</div>
			<a class="fila" href="/app/perfil/bloqueados">
				<Icono nombre="escudo" tam={20} color="var(--c-acento)" trazo={2} />
				<span class="ft"
					><b>Bloqueos y reportes</b><span>Los revisa una persona del equipo, no un&nbsp;bot</span
					></span
				>
				<Icono nombre="flecha" tam={18} color="#9A95AD" trazo={2.6} />
			</a>
		</div>

		<h2>Avisos</h2>
		<form
			method="POST"
			action="?/avisos"
			class="lista"
			use:enhance={enviar(
				(d) => (avisos = (d.avisos as typeof avisos) ?? avisos),
				'Avisos guardados.'
			)}
		>
			<input type="hidden" name="pc" value={avisos.planesCerca ? '1' : '0'} />
			<input type="hidden" name="tg" value={avisos.tuGente ? '1' : '0'} />
			<input type="hidden" name="se" value={avisos.semana ? '1' : '0'} />
			<button class="fila" name="cambia" value="pc" role="switch" aria-checked={avisos.planesCerca}>
				<Icono nombre="pin" tam={20} color="var(--c-acento)" trazo={2} />
				<span class="ft"
					><b>Planes cerca</b><span>Cuando se arma algo a menos de 5&nbsp;km</span></span
				>
				<span class="toggle" class:on={avisos.planesCerca}></span>
			</button>
			<button class="fila" name="cambia" value="tg" role="switch" aria-checked={avisos.tuGente}>
				<Icono nombre="personas" tam={20} color="var(--c-acento)" trazo={2} />
				<span class="ft"
					><b>Planes de tu gente</b><span>Cuando alguien de tu gente publica un&nbsp;plan</span
					></span
				>
				<span class="toggle" class:on={avisos.tuGente}></span>
			</button>
			<button class="fila" name="cambia" value="se" role="switch" aria-checked={avisos.semana}>
				<Icono nombre="calendario" tam={20} color="var(--c-acento)" trazo={2} />
				<span class="ft"
					><b>Esta semana en tu ciudad</b><span>Los lunes, lo mejor de la semana</span></span
				>
				<span class="toggle" class:on={avisos.semana}></span>
			</button>
		</form>

		<h2>Tema</h2>
		<form
			method="POST"
			action="?/tema"
			use:enhance={() =>
				async ({ result, update }) => {
					if (result.type === 'success') tema = String(result.data?.tema ?? tema) as typeof tema;
					await update();
				}}
		>
			<div class="seg tema" role="group" aria-label="Tema">
				{#each TEMAS as [k, l] (k)}
					<button name="tema" value={k} aria-pressed={tema === k}>{l}</button>
				{/each}
			</div>
		</form>

		<div class="pie">
			<form method="POST" action="/salir"><button class="salir">Salir</button></form>
			<a class="borrar-cuenta" href="/borrar-cuenta">Borrar mi cuenta</a>
		</div>
	</div>
</div>

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px 40px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	h2 {
		margin-top: 8px;
		font:
			700 20px 'Geist',
			sans-serif;
	}
	.lista {
		display: flex;
		flex-direction: column;
		border-radius: 22px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plana);
	}
	.fila {
		display: flex;
		align-items: center;
		gap: 12px;
		width: 100%;
		padding: 14px 16px;
		border: 0;
		background: none;
		text-align: left;
		font: inherit;
		color: inherit;
		text-decoration: none;
		cursor: pointer;
	}
	.lista > .fila,
	.lista > .fila-col {
		border-bottom: 1px solid var(--c-linea);
	}
	.lista > :last-child {
		border-bottom: 0;
	}
	.ft {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.ft b {
		font-size: 14px;
		font-weight: 800;
	}
	.ft span {
		font-size: 12px;
		color: var(--texto-2);
	}
	.chips {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.contactos {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding: 0 16px 14px;
	}
	.contacto {
		display: grid;
		grid-template-columns: 2fr 3fr;
		gap: 8px;
	}
	.contacto .input {
		min-width: 0;
	}
	.nota {
		font-size: 12px;
		line-height: 1.4;
		color: var(--texto-2);
		text-wrap: pretty;
	}
	.guardar {
		height: 52px;
		border-radius: 18px;
		font-size: 15px;
	}
	.seg.tema {
		display: flex;
	}
	.seg.tema button {
		flex: 1;
		padding: 10px 6px;
		border: 0;
		background: none;
		font: inherit;
		font-size: 14px;
		font-weight: 800;
	}
	.seg.tema button[aria-pressed='true'] {
		background: var(--c-tarjeta);
	}
	.pie {
		margin-top: 10px;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 4px;
	}
	.salir {
		font-size: 14px;
		font-weight: 700;
		color: var(--texto-3);
		padding: 8px;
	}
	.borrar-cuenta {
		text-decoration: none;
		padding: 4px 8px;
		font-size: 13px;
		font-weight: 700;
		color: #d04a3a;
	}
</style>
