<script lang="ts">
	import { enhance } from '$app/forms';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import Verificado from '#lib/app-ui/Verificado.svelte';
	import BajateLaApp from '#lib/app-ui/BajateLaApp.svelte';
	import { avisar } from '#lib/toast.svelte.ts';

	/*
	 * Perfil propio (Mockups v5, 11), simple: datos, asistencia, planes y "Le copa". Editar nombre,
	 * ciudad, zona e intereses sale del botón de ajustes. Mi gente, Citas, Plus y los pases quedan
	 * para la app.
	 */
	let { data } = $props();

	const yo = $derived(data.yo);
	let editando = $state(false);
	let intereses = $state<string[]>([]);
	let nuevo = $state('');
	let guardando = $state(false);

	const pct = $derived(
		yo.asistencia.de ? Math.round((yo.asistencia.fue / yo.asistencia.de) * 100) : null
	);
	const donde = $derived(
		[[yo.zona, yo.ciudad].filter(Boolean).join(', '), yo.verificada ? 'Verificada' : '']
			.filter(Boolean)
			.join(' · ')
	);

	function abrir() {
		intereses = [...yo.intereses];
		editando = !editando;
	}
	function sumar() {
		const t = nuevo.trim();
		if (t && !intereses.includes(t) && intereses.length < 12) intereses = [...intereses, t];
		nuevo = '';
	}
</script>

<svelte:head><title>Perfil · Caigo</title></svelte:head>

<div class="scroll">
	<button class="ajustes" onclick={abrir} aria-label="Editar el perfil" aria-expanded={editando}>
		<svg
			width="20"
			height="20"
			viewBox="0 0 24 24"
			fill="none"
			stroke="#24212D"
			stroke-width="2"
			stroke-linecap="round"
			stroke-linejoin="round"
			aria-hidden="true"
			><circle cx="12" cy="12" r="3" /><path
				d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"
			/></svg
		>
	</button>

	<div class="contenido">
		<div class="quien">
			<Avatar nombre={yo.nombre || '?'} foto={yo.fotos[0]} tam={112} />
			<div class="nombre">
				<span>{yo.nombre || 'Sin nombre'}{yo.edad ? `, ${yo.edad}` : ''}</span>
				{#if yo.verificada}<Verificado tam={22} />{/if}
			</div>
			{#if donde}<span class="donde">{donde}</span>{/if}
		</div>

		{#if editando}
			<form
				method="POST"
				class="editar"
				use:enhance={() => {
					guardando = true;
					return async ({ result, update }) => {
						guardando = false;
						if (result.type === 'success') {
							avisar('Perfil guardado.');
							editando = false;
						} else if (result.type === 'failure')
							avisar(String(result.data?.error ?? 'No se pudo.'));
						await update({ reset: false });
					};
				}}
			>
				<label class="campo-a"
					><span>Nombre</span><input name="nombre" value={yo.nombre} maxlength="40" /></label
				>
				<label class="campo-a"
					><span>Zona</span><input
						name="zona"
						value={yo.zona}
						maxlength="60"
						placeholder="Ej: Güemes"
					/></label
				>
				<div class="campo-a">
					<span>Ciudad</span>
					<div class="chips">
						{#each ['Mar del Plata', 'Villa Gesell'] as c (c)}
							<label class="chip-r">
								<input
									type="radio"
									name="ciudad"
									value={c}
									checked={yo.ciudad === c}
									class="sr"
								/>{c}
							</label>
						{/each}
					</div>
				</div>
				<div class="campo-a">
					<span>Le copa</span>
					<div class="chips">
						{#each intereses as t (t)}
							<button
								type="button"
								class="interes on"
								onclick={() => (intereses = intereses.filter((x) => x !== t))}
								aria-label="Sacar {t}"
							>
								{t} ×
							</button>
							<input type="hidden" name="interes" value={t} />
						{/each}
					</div>
					<input
						bind:value={nuevo}
						placeholder="Sumá algo y apretá Enter (ej: Truco)"
						maxlength="30"
						onkeydown={(e) => {
							if (e.key === 'Enter') {
								e.preventDefault();
								sumar();
							}
						}}
						onblur={sumar}
					/>
				</div>
				<button class="gel gel-violeta guardar" disabled={guardando}>Guardar</button>
			</form>
		{:else}
			<div class="stats">
				<div class="st oscura">
					<span class="sv lima">{pct === null ? '—' : `${pct}%`}</span>
					<span class="sl">Asistencia real</span>
					<span class="sd"
						>{yo.asistencia.de
							? `${yo.asistencia.fue} de ${yo.asistencia.de} con check-in`
							: 'Todavía sin planes'}</span
					>
				</div>
				<div class="st clara">
					<span class="sv violeta">{yo.planes}</span>
					<span class="sl">Planes</span>
					<span class="sd"
						>{yo.organizados} {yo.organizados === 1 ? 'organizado' : 'organizados'}</span
					>
				</div>
			</div>

			{#if yo.intereses.length}
				<div class="bloque">
					<span class="rot">Le copa</span>
					<div class="chips">
						{#each yo.intereses as t (t)}<span class="interes">{t}</span>{/each}
					</div>
				</div>
			{/if}

			<BajateLaApp
				titulo="Mi gente, Citas y Plus, en la app"
				texto="Tu gente, el modo Citas, Caigo Plus, los pases de Wallet y el check-in con Caigo Tap están en la app."
			/>

			<form method="POST" action="/salir" class="salir"><button>Salir</button></form>
		{/if}
	</div>
</div>

<style>
	.ajustes {
		position: absolute;
		top: calc(16px + env(safe-area-inset-top));
		right: 20px;
		z-index: 2;
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
		box-shadow: var(--sh-icono);
	}
	.contenido {
		padding: calc(42px + env(safe-area-inset-top)) 20px 120px;
		display: flex;
		flex-direction: column;
		gap: 18px;
	}
	.quien {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 10px;
		text-align: center;
	}
	.nombre {
		display: flex;
		align-items: center;
		gap: 8px;
		font:
			700 28px/1 'Geist',
			sans-serif;
	}
	.donde {
		font-size: 14px;
		color: #5c586b;
	}
	.stats {
		display: grid;
		grid-template-columns: 1.2fr 1fr;
		gap: 10px;
	}
	.st {
		padding: 16px;
		border-radius: 22px;
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.oscura {
		background: url(/grain.png), radial-gradient(120% 90% at 0% 0%, #3d3756 0%, #24212d 62%);
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
	}
	.clara {
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
		box-shadow: var(--sh-icono);
	}
	.sv {
		font:
			800 40px/1 'Geist',
			sans-serif;
	}
	.lima {
		color: #c8f36b;
	}
	.violeta {
		color: #5b43c9;
	}
	.sl {
		font-size: 13px;
		font-weight: 600;
	}
	.oscura .sl {
		color: #faf9f6;
	}
	.sd {
		font-size: 12px;
		color: #5c586b;
	}
	.oscura .sd {
		color: #b9b4cc;
	}
	.bloque {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.rot {
		font-size: 15px;
		font-weight: 700;
	}
	.chips {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.interes,
	.chip-r {
		padding: 8px 12px;
		border-radius: 13px;
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
		box-shadow: var(--sh-icono);
		font-size: 13px;
		font-weight: 600;
		color: #24212d;
		cursor: pointer;
	}
	.interes.on,
	.chip-r:has(input:checked) {
		background: var(--grad-violeta);
		box-shadow: var(--sh-sel);
		color: #ffffff;
		font-weight: 700;
	}
	.editar {
		display: flex;
		flex-direction: column;
		gap: 16px;
		padding: 18px;
		border-radius: 24px;
		background: #ffffff;
		box-shadow: var(--sh-tarjeta-plan);
	}
	.campo-a {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.campo-a > span {
		font-size: 14px;
		font-weight: 700;
	}
	.campo-a input:not(.sr) {
		height: 50px;
		border-radius: 16px;
		border: 1.5px solid #ece8f2;
		background: #ffffff;
		padding: 0 14px;
		font-size: 15px;
		font-weight: 600;
		min-width: 0;
	}
	.guardar {
		height: 54px;
		border-radius: 18px;
		font-size: 16px;
	}
	.salir {
		display: flex;
		justify-content: center;
	}
	.salir button {
		font-size: 14px;
		font-weight: 700;
		color: #6b6780;
		padding: 8px;
	}
</style>
