<script lang="ts">
	import { enhance } from '$app/forms';
	import { cuandoCorto, partes } from '#lib/fechas.ts';
	import { pesosDeCentavos } from '#lib/precios.ts';

	/* Cobros (Panel Lugares, vista "cobros"); sin Mercado Pago, la misma tarjeta con "Conectar". */
	let { data, form } = $props();

	const lugar = $derived(data.lugar!);
	const ESTADO = {
		en_venta: ['En venta', '#F0ECFF'],
		cobrado: ['Cobrado', '#C8F36B']
	} as const;
	const desde = $derived.by(() => {
		if (!lugar.mpDesde) return '';
		const p = partes(lugar.mpDesde);
		return `Conectado el ${p.dia}/${p.mes + 1}/${p.anio}`;
	});
	let enviando = $state(false);
</script>

<svelte:head><title>Cobros · Caigo para lugares</title></svelte:head>

<div class="vista">
	<div class="titulos">
		<h1>Cobros</h1>
		<p>Lo cobrado se liquida en tu cuenta de Mercado Pago después de cada&nbsp;plan.</p>
	</div>

	{#if data.mp === 'ok'}
		<p class="aviso ok" role="status">Listo, Mercado Pago quedó conectado.</p>
	{:else if data.mp === 'error'}
		<p class="aviso error" role="alert">
			{data.mpError || 'No se pudo conectar Mercado Pago. Probá de nuevo.'}
		</p>
	{/if}
	{#if form?.error}<p class="aviso error" role="alert">{form.error}</p>{/if}

	<div class="mp tarjeta-plana">
		<span class="mp-logo">MP</span>
		<div class="mp-txt">
			<span class="t">{lugar.mpConectado ? 'Mercado Pago conectado' : 'Conectá Mercado Pago'}</span>
			<span class="s"
				>{lugar.mpConectado
					? desde
					: 'Para cobrar entradas y señas. La plata va directo a tu cuenta.'}</span
			>
		</div>
		{#if lugar.mpConectado}
			<span class="activo">Activo</span>
			<form
				method="POST"
				action="?/desconectar"
				use:enhance={({ cancel }) => {
					if (
						!confirm(
							'¿Desconectar Mercado Pago? No vas a poder cobrar entradas hasta conectarlo de nuevo.'
						)
					)
						return cancel();
				}}
			>
				<button class="desconectar">Desconectar</button>
			</form>
		{:else}
			<form
				method="POST"
				action="?/conectar"
				use:enhance={() => {
					enviando = true;
					return async ({ update }) => {
						await update();
						enviando = false;
					};
				}}
			>
				<button class="gel gel-violeta conectar" disabled={enviando}>Conectar</button>
			</form>
		{/if}
	</div>

	<div class="tabla tarjeta-plana">
		<div class="fila cab">
			<span>PLAN</span><span>ENTRADAS</span><span>COBRADO</span><span>ESTADO</span>
		</div>
		{#each data.cobros as c (c.planId)}
			{@const [el, eb] = ESTADO[c.estado] ?? ESTADO.en_venta}
			<div class="fila">
				<div class="plan">
					<span class="pt">{c.titulo}</span>
					<span class="pw">{cuandoCorto(c.empiezaEn)}</span>
				</div>
				<span class="n"><span class="m-rot">Entradas:&nbsp;</span>{c.entradas}</span>
				<span class="m">{pesosDeCentavos(c.cobradoCentavos)}</span>
				<span class="est" style:background={eb}>{el}</span>
			</div>
		{:else}
			<p class="vacia">Cuando vendas entradas o señas, los cobros aparecen&nbsp;acá.</p>
		{/each}
	</div>
</div>

<style>
	.vista {
		max-width: 1180px;
		display: flex;
		flex-direction: column;
		gap: 24px;
		animation: tk-up 0.35s ease both;
	}
	.titulos {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	h1 {
		font:
			800 44px/1 'Geist',
			sans-serif;
		letter-spacing: -1.6px;
	}
	.titulos p {
		font-size: 15px;
		font-weight: 600;
		color: #5c586b;
	}
	.aviso {
		padding: 14px 16px;
		border-radius: 18px;
		font-size: 14px;
		font-weight: 700;
	}
	.aviso.ok {
		background: #c8f36b;
	}
	.aviso.error {
		background: #ffe1db;
		color: #9a2e1f;
	}
	.mp {
		display: flex;
		align-items: center;
		gap: 14px;
		padding: 18px 20px;
		border-radius: 24px;
		flex-wrap: wrap;
	}
	.mp-logo {
		flex: none;
		width: 48px;
		height: 48px;
		border-radius: 15px;
		display: grid;
		place-items: center;
		background: #009ee3;
		color: #ffffff;
		font:
			800 16px/1 'Geist',
			sans-serif;
	}
	.mp-txt {
		flex: 1 1 200px;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.t {
		font-size: 15px;
		font-weight: 800;
	}
	.s {
		font-size: 13px;
		font-weight: 600;
		color: #5c586b;
	}
	.activo {
		padding: 7px 12px;
		border-radius: 12px;
		background: #c8f36b;
		font-size: 12px;
		font-weight: 800;
	}
	.desconectar {
		padding: 7px 12px;
		font-size: 13px;
		font-weight: 700;
		color: #6b6780;
	}
	.conectar {
		height: 48px;
		padding: 0 22px;
		border-radius: 16px;
		font-size: 15px;
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.45),
			0 4px 0 #4a2fc2;
	}
	.tabla {
		border-radius: 28px;
		overflow: hidden;
	}
	.fila {
		display: grid;
		grid-template-columns: 1.6fr 1fr 1fr 1fr;
		gap: 12px;
		align-items: center;
		padding: 16px 22px;
		border-top: 1px solid #f0ecff;
	}
	.fila.cab {
		border-top: 0;
		background: #f5f2fc;
		font-size: 12px;
		font-weight: 800;
		letter-spacing: 0.8px;
		color: #5c586b;
	}
	.plan {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.pt {
		font-size: 15px;
		font-weight: 800;
	}
	.pw {
		font-size: 12px;
		font-weight: 600;
		color: #5c586b;
	}
	.n {
		font-size: 14px;
		font-weight: 700;
	}
	.m-rot {
		display: none;
	}
	.m {
		font:
			800 18px/1 'Geist',
			sans-serif;
	}
	.est {
		justify-self: start;
		padding: 6px 11px;
		border-radius: 11px;
		font-size: 12px;
		font-weight: 800;
	}
	.vacia {
		padding: 24px 22px;
		border-top: 1px solid #f0ecff;
		font-size: 14px;
		font-weight: 600;
		color: #5c586b;
	}
	@media (max-width: 700px) {
		h1 {
			font-size: 36px;
		}
		.fila.cab {
			display: none;
		}
		.fila {
			grid-template-columns: minmax(0, 1fr) auto;
			padding: 16px;
		}
		.fila:nth-child(2) {
			border-top: 0;
		}
		.plan {
			grid-column: 1 / -1;
		}
		.m-rot {
			display: inline;
			font-weight: 600;
			color: #5c586b;
		}
		.n {
			grid-column: 1;
		}
		.m {
			grid-column: 1;
		}
		.est {
			grid-column: 2;
			grid-row: 2 / 4;
			justify-self: end;
			align-self: center;
		}
	}
</style>
