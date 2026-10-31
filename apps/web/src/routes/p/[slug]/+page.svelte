<script lang="ts">
	import { enhance } from '$app/forms';
	import Seo from '#lib/ui/Seo.svelte';
	import Icono from '#lib/ui/Icono.svelte';
	import { cuandoLargo } from '#lib/fechas.ts';
	import { faltanTexto, lista, primerNombre } from '#lib/plan.ts';
	import { desglose, pesos } from '#lib/precios.ts';

	/*
	 * Link público del plan: pantalla 12 de Mockups v5 ("Link público en la web"), igual en
	 * escritorio pero centrada. Lo que la pantalla no muestra (precio y beneficio de los planes de
	 * lugares, estados "Esperando…" / "Adentro") sale del preview del Panel Lugares y del Prototipo.
	 */
	let { data, form } = $props();

	const plan = $derived(data.plan);
	const estado = $derived(form && 'estado' in form ? form.estado : data.miEstado);
	const anfitrion = $derived(plan.lugar?.nombre || primerNombre(plan.organizador.nombre));
	const nombres = $derived.by(() => {
		const n = plan.van.map((p) => primerNombre(p.nombre)).filter(Boolean);
		if (n.length <= 3) return lista(n);
		return `${n.slice(0, 2).join(', ')} y ${plan.somos - 2} más`;
	});
	const donde = $derived(
		[plan.zona ? `Zona ${plan.zona}` : plan.lugarNombre, plan.ciudad].filter(Boolean).join(', ')
	);
	const precio = $derived.by(() => {
		if (plan.cobro === 'gratis' || !plan.precioCentavos) return null;
		const d = desglose(plan.cobro, plan.precioCentavos / 100, plan.senaPct ?? 0);
		return plan.cobro === 'sena'
			? { monto: pesos(d.paga), detalle: `Seña · resto ${pesos(d.resto)} en el\u00a0lugar` }
			: { monto: pesos(d.paga), detalle: 'Entrada con servicio incluido' };
	});
	const cta = $derived(
		plan.cobro === 'sena' && plan.lugar
			? 'Señar'
			: plan.cobro === 'entrada' && plan.lugar
				? 'Comprar entrada'
				: 'Sumarme'
	);
	const app = $derived(`ar.com.caigo.app://p/${plan.slug}`);
	const descripcionOg = $derived(
		`${anfitrion} te invita a ${plan.titulo}. ${cuandoLargo(plan.empiezaEn)} · ${donde}. ${faltanTexto(plan.faltan)}.`
	);
	let enviando = $state(false);
</script>

<Seo
	titulo="{plan.titulo} · Caigo"
	descripcion={descripcionOg}
	imagen={plan.foto || '/fotos/grupo.jpg'}
	ruta="/p/{plan.slug}"
/>

<div class="fondo">
	<div class="pantalla">
		<header>
			<a class="marca" href="/" aria-label="Caigo, inicio">Caigo</a>
			<a class="abrir" href={app}>Abrir en la app</a>
		</header>

		{#if data.pago}
			<p class="aviso {data.pago}" role="status">
				{#if data.pago === 'ok'}
					Pago aprobado. Ya tenés tu lugar.
				{:else if data.pago === 'pendiente'}
					Tu pago está pendiente. Te avisamos cuando se&nbsp;acredite.
				{:else}
					El pago no salió. Podés probar de&nbsp;nuevo.
				{/if}
			</p>
		{/if}

		<div class="foto">
			{#if plan.foto}<img src={plan.foto} alt="" />{/if}
			<span class={plan.faltan > 0 ? 'badge-coral contador' : 'badge-completo contador'}
				>{faltanTexto(plan.faltan)}</span
			>
		</div>

		<div class="titulos">
			<span class="invita">{anfitrion} te invita a</span>
			<h1>{plan.titulo}</h1>
		</div>

		<ul class="datos">
			<li>
				<Icono nombre="reloj" tam={18} color="#7456F1" trazo={2} />{cuandoLargo(plan.empiezaEn)}
			</li>
			<li><Icono nombre="pin" tam={18} color="#7456F1" trazo={2} />{donde}</li>
			<li>
				<Icono nombre="personas" tam={18} color="#7456F1" trazo={2} />
				<span>Somos {plan.somos} de {plan.total}{nombres ? ` · ${nombres}` : ''}</span>
			</li>
			{#if precio}
				<li>
					<Icono nombre="cobros" tam={18} color="#7456F1" trazo={2} />
					<span><b>{precio.monto}</b> · {precio.detalle}</span>
				</li>
			{/if}
			{#if plan.beneficio}
				<li>
					<Icono nombre="regalo" tam={18} color="#7456F1" trazo={2} />Con check-in: {plan.beneficio}
				</li>
			{/if}
		</ul>

		{#if plan.descripcion}
			<p class="descripcion">{plan.descripcion}</p>
		{/if}

		<div class="pie">
			{#if form?.error}
				<p class="error" role="alert">{form.error}</p>
			{/if}
			{#if plan.cancelado}
				<span class="estado esperando">Este plan se canceló</span>
			{:else if estado === 'aceptada'}
				<span class="estado adentro"
					><Icono nombre="check" tam={18} color="#C8F36B" trazo={3.4} />Adentro</span
				>
				<span class="nota">Abrí la app para ver la dirección y el chat del&nbsp;plan.</span>
			{:else if estado === 'pendiente'}
				<span class="estado esperando">Esperando…</span>
				<span class="nota">Te avisamos cuando te acepten.</span>
			{:else if plan.faltan <= 0}
				<span class="estado esperando">Completo</span>
			{:else}
				<form
					method="POST"
					action="?/sumarme"
					use:enhance={() => {
						enviando = true;
						return async ({ update }) => {
							await update({ reset: false });
							enviando = false;
						};
					}}
				>
					<button class="gel gel-lima sumarme" disabled={enviando}>
						<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"
							><path
								d="M12 4v16M4 12h16"
								stroke="#24212D"
								stroke-width="4"
								stroke-linecap="round"
							/></svg
						>{cta}
					</button>
				</form>
				<span class="nota">
					{#if data.conSesion}
						Te sumás desde la web, sin bajar la&nbsp;app.
					{:else}
						No hace falta bajar la app: entrás con Google o&nbsp;Apple.
					{/if}
				</span>
			{/if}
		</div>
	</div>
</div>

<style>
	.fondo {
		min-height: 100vh;
		min-height: 100dvh;
		background:
			url(/grain.png), radial-gradient(90% 38% at 100% 0%, #ece5ff 0%, rgba(236, 229, 255, 0) 70%),
			#faf9f6;
	}
	.pantalla {
		max-width: 430px;
		min-height: 100vh;
		min-height: 100dvh;
		margin: 0 auto;
		padding: 16px 20px 28px;
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-bottom: 4px;
	}
	.marca {
		font:
			700 30px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #24212d;
		text-decoration: none;
	}
	.abrir {
		padding: 8px 12px;
		border-radius: 12px;
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
		box-shadow: var(--sh-icono);
		font-size: 13px;
		font-weight: 700;
		color: #24212d;
		text-decoration: none;
	}
	.foto {
		position: relative;
		flex: none;
		height: 190px;
		border-radius: 24px;
		overflow: hidden;
		background: #5b43c9;
	}
	.foto img {
		position: absolute;
		inset: 0;
		width: 100%;
		height: 100%;
		object-fit: cover;
	}
	.contador {
		position: absolute;
		right: 12px;
		bottom: 12px;
		padding: 8px 12px;
		border-radius: 13px;
		font-size: 20px;
	}
	.titulos {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.invita {
		font-size: 14px;
		font-weight: 600;
		color: #5c586b;
	}
	h1 {
		font:
			700 28px/1.05 'Geist',
			sans-serif;
		letter-spacing: -0.6px;
	}
	.datos {
		list-style: none;
		margin: 0;
		padding: 0;
		display: flex;
		flex-direction: column;
		gap: 8px;
		font-size: 14px;
		font-weight: 600;
	}
	.datos li {
		display: flex;
		gap: 10px;
		align-items: center;
	}
	.datos b {
		font-weight: 800;
	}
	.descripcion {
		font-size: 14px;
		line-height: 1.5;
		color: #5c586b;
	}
	.pie {
		margin-top: auto;
		padding-top: 12px;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.pie form {
		display: contents;
	}
	.sumarme {
		width: 100%;
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
	}
	.estado {
		height: 58px;
		border-radius: 20px;
		display: flex;
		align-items: center;
		justify-content: center;
		gap: 8px;
		font-size: 17px;
		font-weight: 800;
	}
	.esperando {
		color: #5b43c9;
		background: #ede8ff;
		box-shadow: inset 0 2px 4px rgba(116, 86, 241, 0.18);
	}
	.adentro {
		color: #ffffff;
		background: #24212d;
	}
	.nota {
		text-align: center;
		font-size: 13px;
		line-height: 1.45;
		color: #5c586b;
		text-wrap: pretty;
	}
	.error {
		padding: 12px 14px;
		border-radius: 16px;
		background: #ffe1db;
		color: #9a2e1f;
		font-size: 14px;
		font-weight: 700;
		line-height: 1.45;
		text-align: center;
	}
	/* El mensaje de sistema del chat (pill lima) para la vuelta de Mercado Pago. */
	.aviso {
		align-self: center;
		padding: 8px 14px;
		border-radius: 14px;
		font-size: 13px;
		font-weight: 700;
		line-height: 1.4;
		text-align: center;
	}
	.aviso.ok {
		background: #c8f36b;
		color: #24212d;
	}
	.aviso.pendiente {
		background: #f0ecff;
		color: #5b43c9;
	}
	.aviso.error {
		background: #ffe1db;
		color: #9a2e1f;
	}
	/* En escritorio, la misma pantalla centrada (con el alto del teléfono del handoff). */
	@media (min-width: 700px) {
		.fondo {
			display: grid;
			place-items: center;
			padding: 40px 0;
		}
		.pantalla {
			width: 100%;
			min-height: 0;
			height: auto;
		}
		.pie {
			margin-top: 18px;
		}
	}
</style>
