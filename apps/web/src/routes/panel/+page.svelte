<script lang="ts">
	import Vacio from '#lib/ui/Vacio.svelte';
	import { cuandoCorto, fechaDeHoy } from '#lib/fechas.ts';
	import { faltanTexto } from '#lib/plan.ts';
	import { pesosDeCentavos } from '#lib/precios.ts';

	/* "Tus planes" del Panel Lugares; sin aprobar, el estado del lugar (estado vacío de Mockups v5). */
	let { data } = $props();

	const COBRO: Record<string, string> = { gratis: 'Gratis', entrada: 'Entrada', sena: 'Seña' };
	const hoy = fechaDeHoy();
</script>

<svelte:head><title>Tus planes · Caigo para lugares</title></svelte:head>

{#if !data.aprobado}
	<div class="espera">
		{#if data.lugar?.estado === 'pendiente'}
			<Vacio
				titulo="{data.lugar.nombre} está en revisión"
				texto="El equipo de Caigo revisa cada lugar antes de que pueda publicar. Te avisamos apenas esté aprobado."
			>
				<a class="link" href="/registro">Sumar otro lugar</a>
			</Vacio>
		{:else if data.lugar}
			<Vacio
				titulo={data.lugar.estado === 'suspendido'
					? `${data.lugar.nombre} está suspendido`
					: `No pudimos aprobar ${data.lugar.nombre}`}
				texto={data.lugar.motivo || 'Escribinos a hola@caigo.com.ar y lo vemos.'}
			>
				<a class="gel gel-lima" href="mailto:hola@caigo.com.ar">Escribirnos</a>
				<a class="link" href="/registro">Registrar otro lugar</a>
			</Vacio>
		{/if}
		<form method="POST" action="/salir" class="salir"><button>Salir</button></form>
	</div>
{:else}
	<div class="vista">
		<div class="cabeza">
			<div class="titulos">
				<span class="fecha">{hoy}</span>
				<h1>Tus planes</h1>
			</div>
			<nav class="seg" aria-label="Qué planes ver">
				<a href="/" aria-current={data.cual === 'proximos' ? 'page' : undefined}>Próximos</a>
				<a href="/?cual=pasados" aria-current={data.cual === 'pasados' ? 'page' : undefined}
					>Pasados</a
				>
			</nav>
		</div>

		<div class="kpis">
			<div class="kpi tarjeta">
				<span class="kl">Planes publicados</span>
				<span class="kv">{data.kpis.publicados}</span>
				<span class="kd">Próximos 7 días</span>
			</div>
			<div class="kpi tarjeta">
				<span class="kl">Personas confirmadas</span>
				<span class="kv">{data.kpis.confirmados}</span>
				<span class="kd">Sueltas y en grupo</span>
			</div>
			<div class="kpi tarjeta">
				<span class="kl">Check-ins de hoy</span>
				<span class="kv">{data.kpis.checkins}</span>
				<span class="kd">Asistencia real</span>
			</div>
		</div>

		<div class="lista">
			{#each data.planes as p (p.id)}
				{@const f = Math.max(0, p.cupo - p.confirmados)}
				<a class="fila tarjeta" href="/plan/{p.id}">
					<div class="foto" style:background-image={p.foto ? `url(${p.foto})` : undefined}></div>
					<div class="medio">
						<div class="chips">
							<span class="oscuro">{cuandoCorto(p.empiezaEn)}</span>
							<span class="lav">{COBRO[p.cobro]}</span>
							{#if p.cancelado}<span class="coral-claro">Cancelado</span>{/if}
							{#if data.nuevo === p.id}<span class="lima">Recién publicado</span>{/if}
						</div>
						<span class="titulo">{p.titulo}</span>
						<div class="barra-fila">
							<div class="barra">
								<div
									class:llena={f === 0}
									style:width="{Math.min(100, Math.round((p.confirmados / p.cupo) * 100))}%"
								></div>
							</div>
							<span class="conf">{p.confirmados} de {p.cupo} confirmados</span>
						</div>
					</div>
					<div class="der">
						<span class={f === 0 ? 'badge-completo badge' : 'badge-coral badge'}
							>{faltanTexto(f)}</span
						>
						<span class="plata"
							>{p.cobro === 'gratis'
								? 'Sin cobro'
								: `${pesosDeCentavos(p.cobradoCentavos)} cobrado`}</span
						>
					</div>
				</a>
			{:else}
				<div class="vacia tarjeta">
					{#if data.cual === 'pasados'}
						Todavía no hay planes pasados.
					{:else}
						Todavía no publicaste planes. <a href="/nuevo">Publicá el primero</a>.
					{/if}
				</div>
			{/each}
		</div>
	</div>
{/if}

<style>
	.espera {
		min-height: 100vh;
		display: flex;
		flex-direction: column;
		justify-content: center;
		gap: 24px;
		padding: 40px 24px;
	}
	.salir {
		display: flex;
		justify-content: center;
	}
	.salir button {
		font-size: 13px;
		font-weight: 700;
		color: #6b6780;
	}
	.vista {
		max-width: 1180px;
		display: flex;
		flex-direction: column;
		gap: 26px;
		animation: tk-up 0.35s ease both;
	}
	.cabeza {
		display: flex;
		justify-content: space-between;
		align-items: flex-end;
		gap: 20px;
		flex-wrap: wrap;
	}
	.titulos {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	.fecha {
		font-size: 14px;
		font-weight: 700;
		color: #5c586b;
	}
	h1 {
		font:
			800 44px/1 'Geist',
			sans-serif;
		letter-spacing: -1.6px;
	}
	.kpis {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
		gap: 16px;
	}
	.kpi {
		display: flex;
		flex-direction: column;
		gap: 8px;
		padding: 20px 22px;
	}
	.kl {
		font-size: 13px;
		font-weight: 700;
		color: #5c586b;
	}
	.kv {
		font:
			800 34px/1 'Geist',
			sans-serif;
		letter-spacing: -1px;
	}
	.kd {
		font-size: 12px;
		font-weight: 600;
		color: #6b6780;
	}
	.lista {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.fila {
		display: grid;
		grid-template-columns: 150px minmax(0, 1fr) auto;
		gap: 20px;
		align-items: center;
		padding: 14px;
		border-radius: 28px;
		color: inherit;
		text-decoration: none;
	}
	.fila:hover {
		color: inherit;
		box-shadow:
			inset 0 0 0 2px #b3a3ff,
			0 3px 0 #e6e0f3,
			0 18px 30px -18px rgba(80, 60, 160, 0.45);
	}
	.foto {
		height: 104px;
		border-radius: 20px;
		background-color: #e4defa;
		background-size: cover;
		background-position: center;
	}
	.medio {
		min-width: 0;
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.chips {
		display: flex;
		gap: 6px;
		flex-wrap: wrap;
	}
	.chips span {
		padding: 5px 10px;
		border-radius: 10px;
		font-size: 12px;
		font-weight: 800;
	}
	.oscuro {
		background: #24212d;
		color: #faf9f6;
	}
	.lav {
		background: #f0ecff;
	}
	.lima {
		background: #c8f36b;
	}
	.coral-claro {
		background: #ffe1db;
		color: #9a2e1f;
	}
	.titulo {
		font:
			700 22px/1.1 'Geist',
			sans-serif;
		letter-spacing: -0.4px;
	}
	.barra-fila {
		display: flex;
		align-items: center;
		gap: 12px;
	}
	.barra {
		flex: 1;
		max-width: 320px;
		height: 10px;
		border-radius: 5px;
		background: #ede8ff;
		overflow: hidden;
	}
	.barra div {
		height: 100%;
		border-radius: 5px;
		background: linear-gradient(90deg, #b3a3ff, #7456f1);
	}
	.barra div.llena {
		background: linear-gradient(90deg, #e2ff9c, #b2e04f);
	}
	.conf {
		font-size: 13px;
		font-weight: 700;
		color: #5c586b;
		white-space: nowrap;
	}
	.der {
		display: flex;
		flex-direction: column;
		align-items: flex-end;
		gap: 10px;
		padding-right: 8px;
	}
	.badge {
		padding: 8px 13px;
		border-radius: 14px;
		font-size: 18px;
	}
	.plata {
		font-size: 14px;
		font-weight: 800;
		color: #5b43c9;
		white-space: nowrap;
	}
	.vacia {
		padding: 40px;
		border-radius: 28px;
		text-align: center;
		font-size: 15px;
		font-weight: 600;
		color: #5c586b;
	}
	@media (max-width: 700px) {
		h1 {
			font-size: 36px;
		}
		.fila {
			grid-template-columns: 96px minmax(0, 1fr);
			gap: 14px;
			padding: 12px;
			border-radius: 24px;
		}
		.foto {
			height: 96px;
			border-radius: 16px;
		}
		.titulo {
			font-size: 18px;
		}
		.der {
			grid-column: 1 / -1;
			flex-direction: row;
			justify-content: space-between;
			align-items: center;
			padding: 0 4px;
		}
		.badge {
			font-size: 16px;
		}
		.barra-fila {
			flex-wrap: wrap;
			gap: 6px 12px;
		}
		.barra {
			flex-basis: 100%;
		}
		.vacia {
			padding: 28px 20px;
		}
	}
</style>
