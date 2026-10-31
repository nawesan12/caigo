<script lang="ts">
	import { enhance } from '$app/forms';
	import Legal from '#lib/ui/Legal.svelte';

	/*
	 * Botón de arrepentimiento (Resolución 424/2020 de la Secretaría de Comercio Interior). No está en
	 * el handoff: es una página legal con un formulario de campos del panel.
	 */
	let { form } = $props();
	let enviando = $state(false);
</script>

<Legal
	titulo="Botón de&nbsp;arrepentimiento"
	ruta="/arrepentimiento"
	descripcion="¿Compraste Caigo Plus, una entrada o una seña en caigo.com.ar? Tenés 10 días corridos para arrepentirte, sin dar motivo y sin costo. Pedilo acá."
>
	{#if form && 'codigo' in form && form.codigo}
		<h2>Recibimos tu pedido</h2>
		<p>Tu código de trámite es:</p>
		<p class="codigo">{form.codigo}</p>
		<p>
			Guardalo. Te escribimos a {form.email} para confirmar la devolución.
		</p>
	{:else}
		<p>
			Si compraste en caigo.com.ar (Caigo Plus, una entrada o una seña), podés arrepentirte dentro
			de los 10 días corridos desde la compra, sin dar motivo y sin costo, siempre que el plan no
			haya empezado. No hace falta tener la cuenta abierta.
		</p>
		<p>
			Las compras hechas dentro de la app se devuelven por la App Store o Google Play, desde tu
			cuenta de esa tienda.
		</p>
		<form
			method="POST"
			use:enhance={() => {
				enviando = true;
				return async ({ update }) => {
					await update({ reset: false });
					enviando = false;
				};
			}}
		>
			<label>
				<span>Nombre y apellido</span>
				<input
					class="input"
					name="nombre"
					required
					maxlength="80"
					autocomplete="name"
					value={form?.nombre ?? ''}
				/>
			</label>
			<label>
				<span>Mail</span>
				<input
					class="input"
					name="email"
					type="email"
					required
					maxlength="200"
					autocomplete="email"
					value={form?.email ?? ''}
				/>
			</label>
			<label>
				<span>Qué compraste y cuándo</span>
				<textarea
					class="input"
					name="compra"
					required
					maxlength="500"
					rows="3"
					placeholder="Por ejemplo: Caigo Plus mes, el 30/10. Si lo tenés, el número de operación de Mercado Pago."
					>{form?.compra ?? ''}</textarea
				>
			</label>
			{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}
			<button class="gel gel-violeta enviar" disabled={enviando}>Pedir la devolución</button>
		</form>
	{/if}
</Legal>

<style>
	form {
		display: flex;
		flex-direction: column;
		gap: 14px;
		margin-top: 8px;
	}
	label {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	label span {
		font-size: 13px;
		font-weight: 800;
		color: #5c586b;
	}
	.input {
		width: 100%;
		padding: 14px 16px;
		border: 0;
		border-radius: 16px;
		font: inherit;
		font-size: 15px;
		color: #24212d;
		background: #f6f3ff;
		box-shadow: inset 0 0 0 1.5px #e2dcf0;
	}
	textarea.input {
		resize: vertical;
	}
	.enviar {
		height: 56px;
		border-radius: 20px;
		font-size: 17px;
	}
	.error {
		margin: 0;
		font-size: 14px;
		font-weight: 700;
		color: #d04a3a;
	}
	:global(.texto) p.codigo {
		font:
			800 34px/1 'Geist',
			sans-serif;
		letter-spacing: 1px;
		color: #5b43c9;
	}
</style>
