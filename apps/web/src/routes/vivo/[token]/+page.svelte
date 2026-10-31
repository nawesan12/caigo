<script lang="ts">
	import { onMount } from 'svelte';
	import { invalidateAll } from '$app/navigation';
	import Seo from '#lib/ui/Seo.svelte';
	import Icono from '#lib/ui/Icono.svelte';
	import Avatares from '#lib/ui/Avatares.svelte';
	import { cuandoCorto, partes } from '#lib/fechas.ts';
	import { lista } from '#lib/plan.ts';

	/*
	 * "Compartir mi plan en vivo" (lo que recibe el contacto de confianza). No hay pantalla en el
	 * handoff: se arma con el check-in (09a: círculos concéntricos, la ficha de "ya llegaron"), la
	 * tarjeta fijada del chat (08) y el "Adentro" de Pases NFC (1e) cuando ya llegó.
	 */
	let { data } = $props();
	const v = $derived(data.vivo);
	const mapa = $derived(
		`https://www.openstreetmap.org/?mlat=${v.lat}&mlon=${v.lng}#map=17/${v.lat}/${v.lng}`
	);

	// Se actualiza solo cada minuto (por si llega mientras lo mirás).
	onMount(() => {
		const t = setInterval(() => invalidateAll(), 60_000);
		return () => clearInterval(t);
	});
</script>

<Seo
	titulo="{v.quien} en vivo · Caigo"
	descripcion="{v.quien} compartió su plan con vos."
	noindex
/>

<div class="fondo">
	<div class="pantalla">
		<header>
			<a class="marca" href="/">Caigo</a>
			<span class="cuando">{v.plan} · {partes(v.empiezaEn).hora}</span>
		</header>

		<div class="aro" aria-hidden="true">
			<div class="aro2">
				<div class="tile" class:llego={v.llego}>
					{#if v.llego}
						<Icono nombre="check" tam={54} color="#C8F36B" trazo={3} />
					{:else}
						<Icono nombre="pin" tam={54} color="#C8F36B" trazo={2.4} />
					{/if}
				</div>
			</div>
		</div>

		<div class="txt">
			<h1>{v.llego ? `${v.quien} ya llegó a ${v.lugar}` : `${v.quien} va a ${v.lugar}`}</h1>
			<p>
				{v.llego
					? 'Hizo check-in en el lugar. Te avisamos por acá si cambia algo.'
					: 'Todavía no hizo check-in en el lugar.'}
			</p>
			{#if v.con.length}
				<div class="con">
					<Avatares personas={v.con.map((n) => ({ nombre: n }))} total={v.con.length} tam={28} />
					<span>Con {lista(v.con)}</span>
				</div>
			{/if}
		</div>

		<div class="fijada">
			<div class="ico"><Icono nombre="pin" tam={24} color="#FFFFFF" /></div>
			<div class="fijada-txt">
				<b>{cuandoCorto(v.empiezaEn)} · {v.lugar}</b>
				<span>{v.direccion} · hasta las {partes(v.terminaEn).hora}</span>
			</div>
		</div>

		<div class="pie">
			<a class="gel gel-lima ver" href={mapa} target="_blank" rel="noopener noreferrer">
				<Icono nombre="pin" tam={20} color="#24212D" trazo={2.6} />Ver en el mapa
			</a>
			<span class="nota"
				><Icono nombre="escudo" tam={16} color="#5B43C9" />{v.quien} compartió su plan en vivo con&nbsp;vos</span
			>
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
		gap: 22px;
	}
	header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 12px;
	}
	.marca {
		font:
			700 30px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #24212d;
		text-decoration: none;
	}
	.cuando {
		font-size: 14px;
		font-weight: 700;
		color: #5c586b;
		text-align: right;
	}
	.aro {
		align-self: center;
		width: 260px;
		height: 260px;
		border-radius: 50%;
		background:
			radial-gradient(circle at 30% 25%, rgba(255, 255, 255, 0.6), rgba(255, 255, 255, 0) 42%),
			#f0ecff;
		display: grid;
		place-items: center;
	}
	.aro2 {
		width: 184px;
		height: 184px;
		border-radius: 50%;
		background:
			radial-gradient(circle at 30% 25%, rgba(255, 255, 255, 0.6), rgba(255, 255, 255, 0) 42%),
			#e4defa;
		display: grid;
		place-items: center;
	}
	.tile {
		width: 112px;
		height: 112px;
		border-radius: 38px;
		display: grid;
		place-items: center;
		background: var(--grad-violeta);
		box-shadow: 0 18px 40px rgba(116, 86, 241, 0.4);
	}
	.tile.llego {
		background: #24212d;
		box-shadow:
			0 8px 0 #0e0d14,
			0 30px 40px -16px rgba(36, 33, 45, 0.6);
	}
	.txt {
		display: flex;
		flex-direction: column;
		gap: 10px;
		align-items: center;
		text-align: center;
	}
	h1 {
		font:
			700 32px/1.05 'Geist',
			sans-serif;
		letter-spacing: -0.8px;
	}
	.txt p {
		font-size: 15px;
		line-height: 1.5;
		color: #5c586b;
	}
	.con {
		display: flex;
		align-items: center;
		gap: 10px;
		margin-top: 4px;
		padding: 8px 14px 8px 10px;
		border-radius: 16px;
		background: #ffffff;
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 2px 0 #e8e3f4,
			0 12px 24px -12px rgba(80, 60, 160, 0.22);
	}
	.con span {
		margin-left: 8px;
		font-size: 13px;
		font-weight: 600;
	}
	.fijada {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px;
		border-radius: 20px;
		background: #f0ecff;
	}
	.ico {
		flex: none;
		width: 52px;
		height: 52px;
		border-radius: 14px;
		display: grid;
		place-items: center;
		background: #5b43c9;
	}
	.fijada-txt {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.fijada-txt b {
		font-size: 14px;
		font-weight: 700;
	}
	.fijada-txt span {
		font-size: 12px;
		color: #5c586b;
	}
	.pie {
		margin-top: auto;
		display: flex;
		flex-direction: column;
		gap: 14px;
		align-items: center;
	}
	.ver {
		align-self: stretch;
		height: 60px;
		border-radius: 20px;
		font-size: 17px;
	}
	.nota {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 14px;
		font-weight: 700;
		color: #5b43c9;
		text-align: center;
	}
</style>
