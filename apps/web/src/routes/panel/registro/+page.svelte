<script lang="ts">
	import { untrack } from 'svelte';
	import { enhance } from '$app/forms';
	import { PUBLIC_SITIO } from '$app/env/public';
	import Mapa from '#lib/ui/Mapa.svelte';

	/*
	 * Registro de un lugar. No está en el handoff: la columna oscura es la barra lateral del panel y
	 * el formulario, las tarjetas, campos y chips del wizard "Nuevo plan".
	 */
	let { data, form } = $props();

	const CIUDADES: { nombre: string; centro: [number, number] }[] = [
		{ nombre: 'Mar del Plata', centro: [-38.0055, -57.5426] },
		{ nombre: 'Villa Gesell', centro: [-37.2636, -56.9733] }
	];
	// Lo que se había escrito, si la API devolvió un error (los campos no se vacían).
	const previo = $derived(form?.datos);
	const inicial = untrack(() => form?.datos);
	let ciudad = $state(inicial?.ciudad || 'Mar del Plata');
	let lat = $state(inicial?.lat || 0);
	let lng = $state(inicial?.lng || 0);
	let enviando = $state(false);
	const centro = $derived(CIUDADES.find((c) => c.nombre === ciudad)?.centro ?? CIUDADES[0].centro);
	const primero = $derived(data.lugares.length === 0);
</script>

<svelte:head><title>Registrá tu lugar · Caigo para lugares</title></svelte:head>

<div class="registro">
	<aside>
		<div class="marca">
			<a href={PUBLIC_SITIO} class="caigo">Caigo</a>
			<span class="para">PARA LUGARES</span>
		</div>
		<div class="intro">
			<h1>{primero ? 'Sumá tu lugar a Caigo' : 'Sumá otro lugar'}</h1>
			<p>
				Publicá planes con entrada o seña, hacé el check-in en la puerta con Caigo Tap y cobrá con
				Mercado&nbsp;Pago.
			</p>
			<p class="chico">
				Publicar es gratis. Caigo cobra un cargo por servicio a quien compra, nunca al&nbsp;lugar.
			</p>
		</div>
		{#if !primero}<a class="volver" href="/">← Volver al panel</a>{/if}
	</aside>

	<main>
		<form
			method="POST"
			class="tarjeta-form"
			use:enhance={() => {
				enviando = true;
				return async ({ update }) => {
					await update({ reset: false });
					enviando = false;
				};
			}}
		>
			<label class="campo">
				<span>Nombre del lugar</span>
				<input
					class="input grande"
					name="nombre"
					required
					maxlength="80"
					placeholder="Ej: Café Molienda"
					defaultValue={previo?.nombre ?? ''}
				/>
			</label>
			<label class="campo">
				<span>Rubro</span>
				<input
					class="input"
					name="rubro"
					maxlength="40"
					placeholder="Ej: café, bar, cancha"
					defaultValue={previo?.rubro ?? ''}
				/>
			</label>
			<div class="campo">
				<span>Ciudad</span>
				<div class="chips">
					{#each CIUDADES as c (c.nombre)}
						<button
							type="button"
							class="chip"
							aria-pressed={ciudad === c.nombre}
							onclick={() => (ciudad = c.nombre)}>{c.nombre}</button
						>
					{/each}
				</div>
				<input type="hidden" name="ciudad" value={ciudad} />
			</div>
			<div class="dos">
				<label class="campo">
					<span>Zona</span>
					<input
						class="input"
						name="zona"
						maxlength="60"
						placeholder="Ej: Güemes"
						defaultValue={previo?.zona ?? ''}
					/>
				</label>
				<label class="campo">
					<span>Dirección</span>
					<input
						class="input"
						name="direccion"
						required
						maxlength="160"
						placeholder="Ej: Alsina 2450"
						defaultValue={previo?.direccion ?? ''}
					/>
				</label>
			</div>
			<div class="campo">
				<span>Dónde queda</span>
				<span class="ayuda">Tocá el mapa en la puerta del lugar. Podés arrastrar el&nbsp;pin.</span>
				<Mapa bind:lat bind:lng {centro} />
				<input type="hidden" name="lat" value={lat} />
				<input type="hidden" name="lng" value={lng} />
			</div>
			<div class="dos">
				<label class="campo">
					<span>Instagram <small>(opcional)</small></span>
					<input
						class="input"
						name="instagram"
						maxlength="60"
						placeholder="@cafemolienda"
						defaultValue={previo?.instagram ?? ''}
					/>
				</label>
				<label class="campo">
					<span>Teléfono <small>(opcional)</small></span>
					<input
						class="input"
						name="telefono"
						maxlength="30"
						inputmode="tel"
						placeholder="223 555-0000"
						defaultValue={previo?.telefono ?? ''}
					/>
				</label>
			</div>
			{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}
			<div class="pie">
				<span class="nota">El equipo de Caigo revisa cada lugar antes de que pueda publicar.</span>
				<button class="gel gel-lima enviar" disabled={enviando}>Enviar para revisión</button>
			</div>
		</form>
	</main>
</div>

<style>
	.registro {
		min-height: 100vh;
		display: grid;
		grid-template-columns: 340px minmax(0, 1fr);
	}
	aside {
		position: sticky;
		top: 0;
		height: 100vh;
		padding: 28px 26px;
		display: flex;
		flex-direction: column;
		gap: 40px;
		background: url(/grain.png), radial-gradient(120% 60% at 0% 0%, #3b2f78 0%, #201d2b 70%);
		color: #faf9f6;
	}
	.marca {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.caigo {
		font:
			700 40px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #faf9f6;
		text-decoration: none;
	}
	.para {
		font-size: 12px;
		font-weight: 800;
		letter-spacing: 1.5px;
		color: #c8f36b;
	}
	.intro {
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	h1 {
		font:
			800 38px/1 'Geist',
			sans-serif;
		letter-spacing: -1.3px;
	}
	.intro p {
		font-size: 15px;
		font-weight: 600;
		line-height: 1.5;
		color: #d3cee2;
	}
	.intro .chico {
		font-size: 13px;
		color: #c9c4da;
	}
	.volver {
		margin-top: auto;
		font-size: 13px;
		font-weight: 700;
		color: #c9c4da;
		text-decoration: none;
	}
	main {
		padding: 34px 44px 90px;
	}
	.tarjeta-form {
		max-width: 720px;
		display: flex;
		flex-direction: column;
		gap: 22px;
		padding: 28px;
		border-radius: 30px;
		background: #ffffff;
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 3px 0 #e6e0f3,
			0 24px 40px -28px rgba(80, 60, 160, 0.4);
		animation: tk-up 0.35s ease both;
	}
	.input.grande {
		height: 56px;
		font-size: 17px;
	}
	small {
		font-weight: 600;
		color: #6b6780;
	}
	.chips {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.dos {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
		gap: 20px;
	}
	.ayuda {
		font-size: 13px;
		font-weight: 600;
		color: #5c586b;
	}
	.error {
		padding: 14px 16px;
		border-radius: 18px;
		background: #ffe1db;
		color: #9a2e1f;
		font-size: 14px;
		font-weight: 700;
	}
	.pie {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 16px;
		flex-wrap: wrap;
	}
	.nota {
		flex: 1 1 220px;
		font-size: 13px;
		font-weight: 600;
		color: #5c586b;
	}
	.enviar {
		height: 58px;
		padding: 0 28px;
		border-radius: 20px;
		font-size: 16px;
	}
	@media (max-width: 900px) {
		.registro {
			grid-template-columns: minmax(0, 1fr);
		}
		aside {
			position: static;
			height: auto;
			padding: 20px 16px 26px;
			gap: 22px;
		}
		.caigo {
			font-size: 30px;
		}
		h1 {
			font-size: 32px;
		}
		main {
			padding: 16px 16px 60px;
		}
		.tarjeta-form {
			padding: 20px;
			border-radius: 26px;
		}
		.enviar {
			width: 100%;
		}
	}
</style>
