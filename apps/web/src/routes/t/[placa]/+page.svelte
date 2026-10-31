<script lang="ts">
	import { enhance } from '$app/forms';
	import Seo from '#lib/ui/Seo.svelte';
	import Icono from '#lib/ui/Icono.svelte';
	import { pesosDeCentavos } from '#lib/precios.ts';
	import { primerNombre } from '#lib/plan.ts';

	/*
	 * Landing del Caigo Tap cuando el celu no tiene la app. Pantallas de `Caigo Pases NFC`:
	 * 1d "Apoyá el celu en la placa" (fondo, aros lima y el botón tap) y, al hacer el check-in desde
	 * la web, 1e "Adentro".
	 */
	let { data, form } = $props();
	const r = $derived(form && 'resultado' in form ? form.resultado : null);
	let enviando = $state(false);
</script>

<Seo titulo="Caigo Tap · Caigo" descripcion="Hacé check-in con Caigo Tap." noindex />

{#if r}
	<div class="adentro">
		<div class="col">
			<span class="check"><Icono nombre="check" tam={60} color="#C8F36B" trazo={3} /></span>
			<h1>Adentro</h1>
			<p class="hecho">
				{r.plan?.repetido
					? `Ya habías hecho check-in en ${r.lugar}.`
					: `Check-in hecho en ${r.lugar}.`}{#if form?.nombre}&nbsp;Que lo disfrutes, {primerNombre(
						form.nombre
					)}.{/if}
			</p>
			{#if r.plan?.beneficio}
				<div class="beneficio">
					<span class="ico"><Icono nombre="regalo" tam={24} color="#C8F36B" /></span>
					<div>
						<b>{r.plan.beneficio} desbloqueado</b>
						<span>Mostralo en la barra</span>
					</div>
				</div>
			{/if}
			{#if r.plan && r.plan.saldoCentavos > 0}
				<div class="fila gris">
					<b>Seña paga · en el lugar pagás {pesosDeCentavos(r.plan.saldoCentavos)}</b>
				</div>
			{/if}
			{#if r.socio?.gano || r.socio?.canjeo}
				<div class="beneficio">
					<span class="ico"><Icono nombre="regalo" tam={24} color="#C8F36B" /></span>
					<div>
						<b>{r.socio.beneficio} {r.socio.canjeo ? 'canjeado' : 'desbloqueado'}</b>
						<span>{r.socio.canjeo ? 'Ya lo usaste' : 'Mostralo en la barra'}</span>
					</div>
				</div>
			{/if}
			{#if r.socio?.sumo}
				<span class="visita"
					>+1 visita en tu pase de {r.lugar} ({r.socio.visitas} de {r.socio.meta})</span
				>
			{/if}
		</div>
	</div>
{:else}
	<div class="placa">
		<div class="col">
			<div class="ficha">
				<img src="/caigo-icon.png" alt="" width="40" height="40" />
				<div>
					<b>Caigo Tap</b>
					<span>Check-in con la placa del lugar</span>
				</div>
			</div>
			<div class="aros" aria-hidden="true">
				<span class="a1"></span><span class="a2"></span><span class="a3"></span>
				<span class="tile"><Icono nombre="tap" tam={44} color="#24212D" trazo={2.4} /></span>
			</div>
			<h1 class="titulo-placa">Apoyá el celu en la placa</h1>
			<p class="texto-placa">
				Con la app de Caigo, el check-in, el beneficio y la visita de tu pase de socio pasan solos.
				Si no la tenés, hacelo desde&nbsp;acá.
			</p>
			<div class="acciones">
				<a class="gel gel-lima boton" href={data.app}>Abrir en la app</a>
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
					<button class="boton vidrio" disabled={enviando}>
						{data.conSesion ? 'Hacer check-in desde la web' : 'Entrar y hacer check-in'}
					</button>
				</form>
				{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}
			</div>
		</div>
	</div>
{/if}

<style>
	.col {
		max-width: 400px;
		min-height: 100vh;
		min-height: 100dvh;
		margin: 0 auto;
		padding: 40px 22px 30px;
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 18px;
		text-align: center;
	}
	/* 1d */
	.placa {
		background: url(/grain.png), radial-gradient(90% 55% at 50% 62%, #3b2f78 0%, #15131c 75%);
		color: #faf9f6;
	}
	.ficha {
		align-self: stretch;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px;
		border-radius: 18px;
		background: #24212d;
		box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.08);
		text-align: left;
	}
	.ficha div {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.ficha b {
		font-size: 14px;
		font-weight: 800;
	}
	.ficha span {
		font-size: 12px;
		font-weight: 600;
		color: #c9c4da;
	}
	.aros {
		position: relative;
		width: 290px;
		height: 290px;
		max-width: 100%;
		display: grid;
		place-items: center;
	}
	.aros span {
		position: absolute;
		border-radius: 50%;
	}
	.a1 {
		width: 290px;
		height: 290px;
		box-shadow: inset 0 0 0 1.5px rgba(200, 243, 107, 0.14);
	}
	.a2 {
		width: 220px;
		height: 220px;
		box-shadow: inset 0 0 0 2px rgba(200, 243, 107, 0.28);
	}
	.a3 {
		width: 150px;
		height: 150px;
		background: rgba(200, 243, 107, 0.1);
		box-shadow: inset 0 0 0 2.5px rgba(200, 243, 107, 0.55);
	}
	.aros .tile {
		position: relative;
		width: 92px;
		height: 92px;
		border-radius: 30px;
		display: grid;
		place-items: center;
		background: var(--grad-lima);
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.85),
			inset 0 -5px 0 rgba(96, 140, 10, 0.32),
			0 5px 0 #7fa82e,
			0 0 60px rgba(200, 243, 107, 0.5);
	}
	.titulo-placa {
		font:
			800 30px/1.05 'Geist',
			sans-serif;
		letter-spacing: -1px;
	}
	.texto-placa {
		font-size: 14px;
		font-weight: 600;
		line-height: 1.5;
		color: #c9c4da;
	}
	.acciones {
		margin-top: auto;
		align-self: stretch;
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.acciones form {
		display: contents;
	}
	.boton {
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
		font-weight: 800;
		width: 100%;
	}
	.vidrio {
		color: #ffffff;
		background: rgba(255, 255, 255, 0.12);
		box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.18);
	}
	.error {
		font-size: 14px;
		font-weight: 600;
		line-height: 1.5;
		color: #ffd9d3;
	}
	/* 1e */
	.adentro {
		background:
			url(/grain.png), radial-gradient(100% 60% at 50% 30%, #f0ffc2 0%, #c8f36b 55%, #9fd13b 100%);
		color: #24212d;
	}
	.adentro .col {
		padding-top: 90px;
		gap: 16px;
	}
	.check {
		width: 120px;
		height: 120px;
		border-radius: 40px;
		display: grid;
		place-items: center;
		background: #24212d;
		box-shadow:
			0 8px 0 #0e0d14,
			0 30px 40px -16px rgba(36, 33, 45, 0.6);
		animation: tk-pop 0.45s cubic-bezier(0.3, 1.4, 0.5, 1) both;
	}
	.adentro h1 {
		font:
			800 58px/1 'Geist',
			sans-serif;
		letter-spacing: -2.4px;
	}
	.hecho {
		font-size: 16px;
		font-weight: 700;
		line-height: 1.45;
		color: #2e4a00;
	}
	.beneficio {
		align-self: stretch;
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 16px;
		border-radius: 22px;
		background: #ffffff;
		text-align: left;
		box-shadow: 0 4px 0 #94c43a;
	}
	.beneficio .ico {
		flex: none;
		width: 46px;
		height: 46px;
		border-radius: 15px;
		display: grid;
		place-items: center;
		background: #24212d;
	}
	.beneficio div {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.beneficio b {
		font-size: 15px;
		font-weight: 800;
	}
	.beneficio span {
		font-size: 12px;
		font-weight: 600;
		color: #5c586b;
	}
	.fila {
		align-self: stretch;
		padding: 14px 16px;
		border-radius: 22px;
		text-align: left;
		font-size: 14px;
		font-weight: 800;
	}
	.gris {
		background: rgba(36, 33, 45, 0.08);
	}
	.visita {
		margin-top: auto;
		font-size: 13px;
		font-weight: 700;
		color: #2e4a00;
	}
</style>
