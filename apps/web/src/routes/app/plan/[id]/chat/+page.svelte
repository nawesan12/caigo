<script lang="ts">
	import { onMount, tick, untrack } from 'svelte';
	import Icono from '#lib/ui/Icono.svelte';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { cuandoCorto } from '#lib/fechas.ts';
	import { primerNombre } from '#lib/plan.ts';
	import { TINTES } from '#lib/app.ts';
	import type { Mensaje } from '#lib/tipos.ts';

	/*
	 * Chat del plan (Mockups v5, 08): plan fijado arriba, mensajes del sistema en la pill lima,
	 * burbujas propias violetas y ajenas blancas. Se actualiza cada 4 s mientras está abierto.
	 */
	let { data } = $props();

	const p = $derived(data.plan);
	let mensajes = $state<Mensaje[]>(untrack(() => data.mensajes));
	let texto = $state('');
	let enviando = $state(false);
	let lista: HTMLDivElement | undefined = $state();

	const estadoTxt = $derived(
		p.faltan <= 0
			? `${p.somos} de ${p.total} · plan completo`
			: `${p.somos} de ${p.total} · ${p.faltan === 1 ? 'falta 1' : `faltan ${p.faltan}`}`
	);

	async function bajar() {
		await tick();
		lista?.scrollTo({ top: lista.scrollHeight });
	}

	async function traer() {
		try {
			const r = await fetch(`/app/plan/${p.id}/chat/mensajes`);
			if (!r.ok) return;
			const j = (await r.json()) as { mensajes: Mensaje[] };
			const ultimo = mensajes.at(-1)?.id ?? 0;
			if ((j.mensajes.at(-1)?.id ?? 0) !== ultimo) {
				const abajo = lista ? lista.scrollHeight - lista.scrollTop - lista.clientHeight < 80 : true;
				mensajes = j.mensajes;
				if (abajo) bajar();
			}
		} catch {
			/* sin red: se reintenta */
		}
	}

	async function enviar(e: SubmitEvent) {
		e.preventDefault();
		const t = texto.trim();
		if (!t || enviando) return;
		enviando = true;
		try {
			const r = await fetch(`/app/plan/${p.id}/chat/mensajes`, {
				method: 'POST',
				headers: { 'content-type': 'application/json' },
				body: JSON.stringify({ texto: t })
			});
			const j = await r.json().catch(() => ({}));
			if (!r.ok) return avisar(j.error || 'No se pudo mandar.');
			texto = '';
			mensajes = [...mensajes.filter((m) => m.id !== j.id), j];
			bajar();
		} finally {
			enviando = false;
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

<svelte:head><title>Chat · {p.titulo} · Caigo</title></svelte:head>

<div class="chat">
	<div class="cab">
		<a class="icono-btn" href="/app/plan/{p.id}" aria-label="Volver al plan"
			><Icono nombre="atras" tam={22} /></a
		>
		<div class="cab-txt">
			<span class="t">{p.titulo}</span>
			<span class="s"><i></i>{estadoTxt}</span>
		</div>
		<div class="avs">
			{#each p.van.slice(0, 3) as v (v.id)}
				<Avatar nombre={v.nombre} foto={v.foto} tam={30} borde="2px solid #FAF9F6" />
			{/each}
		</div>
	</div>

	<a class="fijado" href="/app/plan/{p.id}">
		<span class="fimg" style:background-color={TINTES[p.categoria]}>
			{#if p.foto}<img src={p.foto} alt="" />{/if}
		</span>
		<span class="fcol">
			<b>{cuandoCorto(p.empiezaEn)}{p.lugarNombre ? ` · ${p.lugarNombre}` : ''}</b>
			<span>{p.direccion?.texto || (p.zona ? `Zona ${p.zona}` : p.ciudad)}</span>
		</span>
		<span class="ver">Ver plan</span>
	</a>

	<div class="mensajes" bind:this={lista}>
		{#each mensajes as m (m.id)}
			{#if !m.autor}
				<span class="sistema">{m.texto}</span>
			{:else if m.autor.id === data.yo.id}
				<span class="mio">{m.texto}</span>
			{:else}
				<div class="otro">
					<Avatar nombre={m.autor.nombre} foto={m.autor.foto} tam={28} />
					<div class="ocol">
						<span class="on">{primerNombre(m.autor.nombre)}</span>
						<span class="burbuja">{m.texto}</span>
					</div>
				</div>
			{/if}
		{:else}
			<span class="sistema">Arrancá la charla: ¿quién lleva qué?</span>
		{/each}
	</div>

	<form class="input" onsubmit={enviar}>
		<input
			bind:value={texto}
			placeholder="Escribí algo…"
			aria-label="Mensaje"
			maxlength="1000"
			enterkeyhint="send"
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
		color: #24212d;
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
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
	.cab-txt .s {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 12px;
		font-weight: 600;
		color: #5c586b;
	}
	.cab-txt i {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: #8dc63f;
	}
	.avs {
		display: flex;
		padding-left: 8px;
	}
	.avs :global(.av) {
		margin-left: -8px;
	}
	.fijado {
		flex: none;
		margin: 0 16px;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px;
		border-radius: 20px;
		background: #f0ecff;
		color: inherit;
		text-decoration: none;
	}
	.fimg {
		flex: none;
		width: 52px;
		height: 52px;
		border-radius: 14px;
		overflow: hidden;
	}
	.fimg img {
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.fcol {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.fcol b {
		font-size: 14px;
		font-weight: 700;
	}
	.fcol span {
		font-size: 12px;
		color: #5c586b;
	}
	.ver {
		font-size: 13px;
		font-weight: 700;
		color: #5b43c9;
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
	.sistema {
		align-self: center;
		padding: 8px 14px;
		border-radius: 13px;
		background: var(--grad-lima);
		box-shadow: var(--sh-lima);
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
		color: #5c586b;
	}
	.burbuja {
		padding: 10px 14px;
		border-radius: 18px 18px 18px 6px;
		background: #ffffff;
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 2px 0 #e8e3f4,
			0 12px 24px -12px rgba(80, 60, 160, 0.22);
		font-size: 15px;
		line-height: 1.4;
		overflow-wrap: anywhere;
	}
	.input {
		flex: none;
		padding: 10px 16px calc(30px + env(safe-area-inset-bottom));
		background: #ffffff;
		border-top: 1px solid #ece8f2;
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
		background: #f6f4f0;
		padding: 0 14px;
		font-size: 15px;
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
