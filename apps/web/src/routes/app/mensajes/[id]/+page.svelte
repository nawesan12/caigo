<script lang="ts">
	import { onMount, tick, untrack } from 'svelte';
	import { enhance } from '$app/forms';
	import Icono from '#lib/ui/Icono.svelte';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import Confirmar from '#lib/app-ui/Confirmar.svelte';
	import Opciones, { type Opcion } from '#lib/app-ui/Opciones.svelte';
	import Reportar from '#lib/app-ui/Reportar.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { primerNombre } from '#lib/plan.ts';
	import type { Privado } from '#lib/tipos.ts';

	/*
	 * Chat privado, con las piezas del chat del plan (Mockups v5, 08): la misma cabecera, las
	 * burbujas propias violetas y las ajenas blancas con la foto, y la barra de escribir. Como en las
	 * apps (VistaPrivado): la foto de la cabecera lleva al perfil y los tres puntos abren «Ver el
	 * perfil», «Reportar a Lu» y «Bloquear a Lu» (este con confirmación, y vuelve a la bandeja). Se
	 * actualiza cada 4 s mientras está a la vista; arriba de todo, los mensajes anteriores.
	 */
	let { data } = $props();

	const p = $derived(data.persona);
	const n = $derived(primerNombre(p.nombre));
	const perfil = $derived(
		`/app/persona/${p.id}?volver=${encodeURIComponent(`/app/mensajes/${p.id}`)}`
	);
	let privados = $state<Privado[]>(untrack(() => data.privados));
	let hayMas = $state(untrack(() => data.hayMas));
	let texto = $state('');
	let enviando = $state(false);
	let trayendo = $state(false);
	let lista: HTMLDivElement | undefined = $state();
	let campo: HTMLInputElement | undefined = $state();
	let menu = $state(false);
	let reportar = $state(false);
	let bloquear = $state(false);

	// Otra conversación en la misma ruta (de un chat a otro): arranca de cero.
	$effect(() => {
		const d = data;
		untrack(() => {
			privados = d.privados;
			hayMas = d.hayMas;
			bajar();
		});
	});

	const opciones = $derived<Opcion[]>([
		{ texto: 'Ver el perfil', href: perfil },
		{ texto: `Reportar a ${n}`, peligro: true, accion: () => (reportar = true) },
		{ texto: `Bloquear a ${n}`, peligro: true, accion: () => (bloquear = true) }
	]);

	async function bajar() {
		await tick();
		lista?.scrollTo({ top: lista.scrollHeight });
	}

	function agregar(m: Privado) {
		if (!privados.some((x) => x.id === m.id)) privados = [...privados, m];
	}

	/** Lo último (los 100 más nuevos), sin perder los anteriores que ya se trajeron. */
	async function traer() {
		try {
			const r = await fetch(`/app/mensajes/${p.id}/privados`);
			if (!r.ok) return;
			const j = (await r.json()) as { privados: Privado[] };
			if ((j.privados.at(-1)?.id ?? 0) === (privados.at(-1)?.id ?? 0)) return;
			const abajo = lista ? lista.scrollHeight - lista.scrollTop - lista.clientHeight < 80 : true;
			const desde = j.privados[0]?.id ?? Infinity;
			privados = [...privados.filter((m) => m.id < desde), ...j.privados];
			if (abajo) bajar();
		} catch {
			/* sin red: se reintenta */
		}
	}

	/** Los 100 anteriores al más viejo que hay, sin mover lo que se está leyendo. */
	async function anteriores() {
		const primero = privados[0]?.id;
		if (!primero || trayendo) return;
		trayendo = true;
		try {
			const r = await fetch(`/app/mensajes/${p.id}/privados?antes=${primero}`);
			const j = await r.json().catch(() => ({}));
			if (!r.ok) return avisar(j.error || 'No pudimos traer los anteriores.');
			const viejos = (j.privados as Privado[]).filter((m) => !privados.some((x) => x.id === m.id));
			hayMas = j.privados.length >= 100;
			const alto = lista?.scrollHeight ?? 0;
			privados = [...viejos, ...privados];
			await tick();
			if (lista) lista.scrollTop += lista.scrollHeight - alto;
		} catch {
			avisar('No pudimos traer los anteriores.');
		} finally {
			trayendo = false;
		}
	}

	onMount(() => {
		bajar();
		const t = setInterval(() => {
			if (document.visibilityState === 'visible') traer();
		}, 4000);
		return () => clearInterval(t);
	});
</script>

<svelte:head><title>{p.nombre} · Mensajes · Caigo</title></svelte:head>

<div class="chat">
	<div class="cab">
		<a class="icono-btn" href="/app/mensajes" aria-label="Volver a Mensajes"
			><Icono nombre="atras" tam={22} /></a
		>
		<div class="cab-txt">
			<span class="t">{p.nombre}</span>
		</div>
		<a class="av-link" href={perfil} aria-label="Ver el perfil de {n}">
			<Avatar nombre={p.nombre} foto={p.fotos?.[0]} tam={36} />
		</a>
		<button class="icono-btn" aria-label="Opciones del chat" onclick={() => (menu = true)}
			><Icono nombre="puntos" tam={20} trazo={4.4} /></button
		>
	</div>

	<div class="mensajes" bind:this={lista}>
		{#if hayMas}
			<button class="antes" onclick={anteriores} disabled={trayendo}
				>{trayendo ? 'Trayendo…' : 'Ver mensajes anteriores'}</button
			>
		{/if}
		{#each privados as m (m.id)}
			{#if m.deId === data.yo.id}
				<span class="mio">{m.texto}</span>
			{:else}
				<div class="otro">
					<Avatar nombre={p.nombre} foto={p.fotos?.[0]} tam={28} />
					<div class="ocol">
						<span class="on">{n}</span>
						<span class="burbuja">{m.texto}</span>
					</div>
				</div>
			{/if}
		{:else}
			<span class="sistema">Mandale un hola a {n}.</span>
		{/each}
	</div>

	<form
		class="input"
		method="POST"
		action="?/mandar"
		use:enhance={({ cancel }) => {
			if (!texto.trim() || enviando) return cancel();
			enviando = true;
			return async ({ result }) => {
				enviando = false;
				if (result.type === 'success' && result.data?.privado) {
					texto = '';
					agregar(result.data.privado as Privado);
					bajar();
					campo?.focus();
				} else if (result.type === 'failure') {
					avisar(String(result.data?.error ?? 'No se pudo mandar.'));
				} else if (result.type === 'redirect') {
					location.href = result.location;
				} else {
					avisar('No se pudo mandar.');
				}
			};
		}}
	>
		<input
			bind:this={campo}
			bind:value={texto}
			name="texto"
			placeholder="Escribí algo…"
			aria-label="Mensaje"
			maxlength="2000"
			enterkeyhint="send"
			autocomplete="off"
		/>
		<button class="enviar" aria-label="Mandar" disabled={enviando || !texto.trim()}>
			<svg
				width="18"
				height="18"
				viewBox="0 0 24 24"
				fill="none"
				stroke="#FFFFFF"
				stroke-width="2.4"
				stroke-linecap="round"
				stroke-linejoin="round"
				aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" /></svg
			>
		</button>
	</form>
</div>

<Opciones bind:abierta={menu} {opciones} />
<Reportar bind:abierta={reportar} usuarioId={p.id} titulo="Reportar a {n}" />
<Confirmar
	bind:abierta={bloquear}
	titulo="¿Bloqueás a {n}?"
	texto={`${n} no va a ver tus planes ni tu perfil, y no te va a poder escribir. Lo podés deshacer en Ajustes.`}
	boton="Sí, bloquear"
	accion="?/bloquear"
	campos={{ nombre: p.nombre }}
/>

<style>
	.chat {
		position: absolute;
		inset: 0;
		display: flex;
		flex-direction: column;
	}
	.cab {
		flex: none;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: calc(16px + env(safe-area-inset-top)) 16px 12px;
	}
	.icono-btn {
		flex: none;
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		color: var(--carbon);
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
	}
	.cab-txt {
		flex: 1;
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.cab-txt .t {
		font:
			700 19px/1.15 'Geist',
			sans-serif;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.av-link {
		flex: none;
		border-radius: 50%;
		text-decoration: none;
		margin-right: -4px;
	}
	.mensajes {
		flex: 1;
		overflow-y: auto;
		padding: 16px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.mensajes > :first-child {
		margin-top: auto;
	}
	.antes {
		align-self: center;
		padding: 8px 14px;
		font-size: 13px;
		font-weight: 700;
		color: var(--violeta-texto);
	}
	.sistema {
		align-self: center;
		padding: 8px 14px;
		border-radius: 13px;
		background: var(--grad-lima);
		box-shadow: var(--sh-lima);
		color: #24212d;
		font-size: 13px;
		font-weight: 700;
		text-align: center;
	}
	.mio {
		align-self: flex-end;
		max-width: 250px;
		padding: 10px 14px;
		border-radius: 18px 18px 6px 18px;
		background: #7456f1;
		color: #ffffff;
		font-size: 15px;
		line-height: 1.4;
		overflow-wrap: anywhere;
		white-space: pre-line;
		/* Sin una palabra sola en el último renglón. */
		text-wrap: pretty;
	}
	.otro {
		display: flex;
		gap: 8px;
		align-items: flex-end;
	}
	.ocol {
		max-width: 250px;
		display: flex;
		flex-direction: column;
		gap: 3px;
	}
	.on {
		font-size: 11px;
		font-weight: 700;
		color: var(--texto-2);
	}
	.burbuja {
		padding: 10px 14px;
		border-radius: 18px 18px 18px 6px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-info);
		font-size: 15px;
		line-height: 1.4;
		overflow-wrap: anywhere;
		white-space: pre-line;
		/* Sin una palabra sola en el último renglón. */
		text-wrap: pretty;
	}
	.input {
		flex: none;
		padding: 10px 16px calc(30px + env(safe-area-inset-bottom));
		background: var(--c-tarjeta);
		border-top: 1px solid var(--c-borde);
		display: flex;
		gap: 10px;
		align-items: center;
	}
	.input input {
		flex: 1;
		min-width: 0;
		height: 44px;
		border-radius: 15px;
		border: 0;
		background: var(--c-input-2);
		padding: 0 14px;
		font-size: 15px;
		color: var(--carbon);
	}
	.input input::placeholder {
		color: #8a8699;
	}
	.enviar {
		flex: none;
		width: 44px;
		height: 44px;
		border-radius: 15px;
		display: grid;
		place-items: center;
		background: var(--grad-violeta);
		box-shadow: var(--sh-violeta);
	}
	.enviar:disabled {
		opacity: 0.6;
	}
</style>
