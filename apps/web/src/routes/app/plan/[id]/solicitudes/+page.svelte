<script lang="ts">
	import { enhance } from '$app/forms';
	import { PUBLIC_SITIO } from '$app/env/public';
	import Icono from '#lib/ui/Icono.svelte';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import Confirmar from '#lib/app-ui/Confirmar.svelte';
	import Verificado from '#lib/app-ui/Verificado.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { cuandoCorto } from '#lib/fechas.ts';
	import { faltanTexto, primerNombre } from '#lib/plan.ts';
	import type { PersonaCorta } from '#lib/tipos.ts';

	/* Solicitudes (Mockups v5, 07); sin pedidos, 13c "Nadie pidió sumarse todavía". */
	let { data } = $props();

	const p = $derived(data.plan);
	/** Los que ya están adentro (sin quien organiza). */
	const adentro = $derived(p.van.filter((v) => !v.organizador));
	let aSacar = $state<PersonaCorta | null>(null);
	let sacarAbierta = $state(false);
	const sub = $derived(`${p.titulo} · ${cuandoCorto(p.empiezaEn)}`);
	const datoDe = (x: PersonaCorta) => {
		const a = x.asistencia;
		const rep =
			a && a.de > 0
				? `${Math.round((a.fue / a.de) * 100)}% asistencia · ${a.de} ${a.de === 1 ? 'plan' : 'planes'}`
				: 'Nuevo en Caigo · todavía sin planes';
		return x.enMiGente ? `${rep} · está en tu gente` : rep;
	};
	async function compartir() {
		const link = `${PUBLIC_SITIO.replace(/\/+$/, '')}/p/${p.slug}`;
		try {
			if (navigator.share) return await navigator.share({ title: p.titulo, url: link });
			await navigator.clipboard.writeText(link);
			avisar('Link copiado.');
		} catch {
			/* cancelado */
		}
	}
</script>

<svelte:head><title>Solicitudes · Caigo</title></svelte:head>

<div class="scroll">
	<div class="contenido">
		<div class="cab">
			<a class="icono-btn" href="/app/plan/{p.id}" aria-label="Volver al plan"
				><Icono nombre="atras" tam={22} /></a
			>
			<div class="cab-txt">
				<span class="t">Solicitudes</span>
				<span class="s">{sub}</span>
			</div>
		</div>

		{#if data.solicitudes.length}
			<div class="contador">
				<div class="ct">
					<span class="cf" class:completo={p.faltan <= 0}>{faltanTexto(p.faltan)}</span>
					<span class="cs">Somos {p.somos} de {p.total}</span>
				</div>
				<span class="pedidos"
					>{data.solicitudes.length} {data.solicitudes.length === 1 ? 'pedido' : 'pedidos'}</span
				>
			</div>

			{#each data.solicitudes as s (s.id)}
				{@const completan = s.cantidad === p.faltan}
				<form
					method="POST"
					action="?/responder"
					class="sol"
					class:completan
					use:enhance={() =>
						async ({ result, update }) => {
							if (result.type === 'success')
								avisar(
									result.data?.ok === 'aceptar'
										? `${primerNombre(s.persona.nombre)} está adentro.`
										: 'Listo.'
								);
							else if (result.type === 'failure')
								avisar(String(result.data?.error ?? 'No se pudo.'));
							await update();
						}}
				>
					<input type="hidden" name="id" value={s.id} />
					<div class="sol-cab">
						<div class="avs">
							<Avatar
								nombre={s.persona.nombre}
								foto={s.persona.foto}
								tam={44}
								borde="2px solid #FFFFFF"
							/>
							{#if s.cantidad > 1}
								<span class="mas">+{s.cantidad - 1}</span>
							{/if}
						</div>
						<div class="sol-txt">
							<span class="n"
								>{primerNombre(s.persona.nombre)}{s.cantidad > 1
									? ` · somos ${s.cantidad}`
									: s.persona.edad
										? `, ${s.persona.edad}`
										: ''}{#if s.persona.verificada}<Verificado tam={15} />{/if}</span
							>
							<span class="d">{datoDe(s.persona)}</span>
						</div>
						{#if completan}<span class="completan-chip">Completan</span>{/if}
					</div>
					{#if s.mensaje}<p class="msj">{s.mensaje}</p>{/if}
					<div class="botones">
						<button class="no" name="accion" value="rechazar">Ahora no</button>
						<button class="gel gel-lima si" name="accion" value="aceptar"
							>{s.cantidad > 1 ? `Aceptar a los ${s.cantidad}` : 'Aceptar'}</button
						>
					</div>
				</form>
			{/each}
		{:else}
			<div class="vacio">
				<div class="tarjeta-giro">
					<span class="gf">{faltanTexto(p.faltan)}</span>
					<span class="gs">Somos {p.somos} de {p.total}</span>
				</div>
				<h2>Nadie pidió sumarse todavía</h2>
				<p>Pasá el link por el grupo. Siempre hay alguien que se&nbsp;prende.</p>
				<button class="gel gel-lima cta" onclick={compartir}>
					<svg
						width="18"
						height="18"
						viewBox="0 0 24 24"
						fill="none"
						stroke="#201D2B"
						stroke-width="2.4"
						stroke-linecap="round"
						stroke-linejoin="round"
						aria-hidden="true"><path d="M12 3v12M7 8l5-5 5 5M5 14v6h14v-6" /></svg
					>Compartir link del plan
				</button>
			</div>
		{/if}

		{#if adentro.length && !p.cancelado}
			<span class="overline-app">YA VAN</span>
			{#each adentro as v (v.id)}
				<div class="sol">
					<div class="sol-cab">
						<Avatar nombre={v.nombre} foto={v.foto} tam={44} borde="2px solid #FFFFFF" />
						<div class="sol-txt">
							<span class="n"
								>{primerNombre(v.nombre)}{v.edad ? `, ${v.edad}` : ''}{#if v.verificada}<Verificado
										tam={15}
									/>{/if}</span
							>
							<span class="d">{datoDe(v)}</span>
						</div>
						<button
							class="sacar"
							onclick={() => {
								aSacar = v;
								sacarAbierta = true;
							}}>Sacar</button
						>
					</div>
				</div>
			{/each}
		{/if}
	</div>
</div>

{#if aSacar}
	<Confirmar
		bind:abierta={sacarAbierta}
		titulo="¿Sacás a {primerNombre(aSacar.nombre)}?"
		texto="Le avisamos que ya no está en el plan y su lugar queda libre para otra persona."
		boton="Sí, sacar"
		accion="?/sacar"
		campos={{ usuario: aSacar.id }}
		listo="Listo, su lugar quedó libre."
	/>
{/if}

<style>
	.contenido {
		padding: calc(20px + env(safe-area-inset-top)) 20px 40px;
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.cab {
		display: flex;
		align-items: center;
		gap: 12px;
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
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.cab-txt .t {
		font:
			700 22px/1.1 'Geist',
			sans-serif;
	}
	.cab-txt .s {
		font-size: 13px;
		color: var(--texto-2);
	}
	.contador {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 14px 16px;
		border-radius: 22px;
		background: #24212d;
	}
	.ct {
		display: flex;
		flex-direction: column;
	}
	.cf {
		font:
			800 34px/1 'Geist',
			sans-serif;
		color: #ff7868;
	}
	.cf.completo {
		color: #c8f36b;
	}
	.cs {
		font-size: 13px;
		font-weight: 600;
		color: #faf9f6;
	}
	.pedidos {
		padding: 8px 12px;
		border-radius: 12px;
		background: #3a3550;
		color: #faf9f6;
		font-size: 13px;
		font-weight: 700;
	}
	.sol {
		display: flex;
		flex-direction: column;
		gap: 12px;
		padding: 14px;
		border-radius: 24px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-info);
	}
	.sol.completan {
		box-shadow: none;
		border: 2px solid #c8f36b;
	}
	.sol-cab {
		display: flex;
		gap: 12px;
		align-items: center;
	}
	.avs {
		display: flex;
	}
	.mas {
		width: 44px;
		height: 44px;
		margin-left: -14px;
		border-radius: 50%;
		border: 2px solid var(--c-tarjeta-lisa);
		background: #c8f36b;
		display: grid;
		place-items: center;
		font:
			700 17px 'Geist',
			sans-serif;
	}
	.sol-txt {
		flex: 1;
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.n {
		display: flex;
		align-items: center;
		gap: 6px;
		font-size: 15px;
		font-weight: 700;
	}
	.d {
		font-size: 12px;
		color: var(--texto-2);
	}
	.completan-chip {
		flex: none;
		padding: 5px 9px;
		border-radius: 10px;
		background: var(--grad-lima);
		box-shadow: var(--sh-lima);
		font-size: 11px;
		font-weight: 800;
	}
	.msj {
		padding: 10px 12px;
		border-radius: 14px;
		background: var(--grad-off);
		box-shadow: var(--sh-icono);
		font-size: 14px;
		line-height: 1.4;
	}
	.overline-app {
		margin-top: 8px;
		font-size: 13px;
		font-weight: 800;
		letter-spacing: 1px;
		color: var(--texto-2);
	}
	.sacar {
		flex: none;
		font-size: 13px;
		font-weight: 700;
		color: var(--violeta-texto);
	}
	.botones {
		display: grid;
		grid-template-columns: 1fr 1.4fr;
		gap: 8px;
	}
	.no,
	.si {
		height: 46px;
		border-radius: 15px;
		font-size: 15px;
	}
	.no {
		border: 1.5px solid var(--c-borde-chip);
		font-weight: 700;
		color: var(--texto-2);
	}
	.vacio {
		display: flex;
		flex-direction: column;
		align-items: center;
		gap: 18px;
		text-align: center;
		padding: 60px 8px 0;
	}
	.tarjeta-giro {
		padding: 20px 26px;
		border-radius: 28px;
		background: url(/grain.png), radial-gradient(120% 90% at 0% 0%, #3d3756 0%, #24212d 62%);
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 22px 36px -14px rgba(36, 33, 45, 0.6);
		display: flex;
		flex-direction: column;
		align-items: center;
		transform: rotate(4deg);
	}
	.gf {
		font:
			800 56px/1 'Geist',
			sans-serif;
		color: #ff7868;
		letter-spacing: -2px;
	}
	.gs {
		font-size: 13px;
		font-weight: 600;
		color: #faf9f6;
	}
	.vacio h2 {
		font:
			700 30px/1.08 'Geist',
			sans-serif;
		letter-spacing: -0.6px;
	}
	.vacio p {
		font-size: 15px;
		line-height: 1.5;
		color: var(--texto-2);
	}
	.cta {
		align-self: stretch;
		margin-top: 8px;
		height: 58px;
		border-radius: 20px;
		font-size: 16px;
		color: #201d2b;
	}
</style>
