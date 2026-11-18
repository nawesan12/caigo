<script lang="ts">
	import { onMount } from 'svelte';
	import { enhance } from '$app/forms';
	import Icono from '#lib/ui/Icono.svelte';
	import Avatar from './Avatar.svelte';
	import Hoja from './Hoja.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import {
		avisaA,
		comoLlegar,
		cuentaRegresiva,
		esTelefono,
		linkUbicacion,
		puedeActivarTodoBien,
		whatsapp
	} from '#lib/dia.ts';
	import { partes } from '#lib/fechas.ts';
	import { primerNombre } from '#lib/plan.ts';
	import type { Contacto, Plan, TodoBien } from '#lib/tipos.ts';

	/*
	 * El día del plan en una sola pantalla (informe del 18/11, 6.10): cuenta regresiva, «Cómo llegar»,
	 * «Llego en 10», «Llegué» y el escudo «¿Todo bien?». Se arma con piezas del handoff: el contador
	 * oscuro del detalle (05), los botones gel lima y blanco, y la hoja que sube con filas de ajustes.
	 */
	let {
		plan: p,
		todoBien,
		yoId = '',
		parte = 'tarjeta',
		hoja = $bindable(false),
		llegarAlAbrir = false,
		onReportar
	}: {
		plan: Plan;
		todoBien?: TodoBien | null;
		yoId?: string;
		/** La tarjeta va en el detalle; la hoja, al final de la página (para que tape la pantalla entera). */
		parte?: 'tarjeta' | 'hoja';
		hoja?: boolean;
		/** Desde la tarjeta de «Hoy»: hace el «Llegué» apenas abre. */
		llegarAlAbrir?: boolean;
		onReportar?: () => void;
	} = $props();

	/** A quién le llega el aviso, dicho sin promesas falsas: con un teléfono, a vos. */
	const nombreDe = (c: Contacto) => c.nombre || c.contacto;
	const filaTitulo = (c: Contacto) =>
		avisaA(c) === 'contacto'
			? `Le escribimos a ${nombreDe(c)} si no llegás`
			: `Te avisamos para que le escribas a ${nombreDe(c)}`;
	const toastActivo = (c: Contacto) =>
		avisaA(c) === 'contacto'
			? `Si no llegás, le escribimos a ${nombreDe(c)}.`
			: 'Si no hacés check-in, te avisamos.';
	const activable = $derived(puedeActivarTodoBien(p));
	let nuevoContacto = $state(false);

	let ahora = $state(new Date());
	onMount(() => {
		const t = setInterval(() => (ahora = new Date()), 30_000);
		return () => clearInterval(t);
	});
	const falta = $derived(cuentaRegresiva(p.empiezaEn, ahora));
	const llegaron = $derived(p.van.filter((v) => v.llego && v.id !== yoId));
	const llegue = $derived(!!p.mio?.llegue);
	const destino = $derived(
		p.direccion ?? (p.lat != null && p.lng != null ? { lat: p.lat, lng: p.lng } : null)
	);

	let enviando = $state('');
	let ubicando = $state(false);

	const contacto = $derived(todoBien?.contacto ?? todoBien?.contactos?.[0]);
	const telefono = $derived(contacto && esTelefono(contacto.contacto) ? contacto.contacto : '');

	/** La ubicación actual va del teléfono al contacto: no pasa por Caigo ni se guarda. */
	function compartirUbicacion() {
		if (!navigator.geolocation) return avisar('Tu navegador no comparte la ubicación.');
		ubicando = true;
		navigator.geolocation.getCurrentPosition(
			async (pos) => {
				ubicando = false;
				const texto = `Estoy en ${p.titulo}: ${linkUbicacion(pos.coords.latitude, pos.coords.longitude)}`;
				try {
					if (telefono) window.open(whatsapp(telefono, texto), '_blank', 'noopener');
					else if (navigator.share) await navigator.share({ text: texto });
					else {
						await navigator.clipboard.writeText(texto);
						avisar('Copiamos tu ubicación para que la pegues.');
					}
				} catch {
					/* cancelado */
				}
			},
			() => {
				ubicando = false;
				avisar('No pudimos saber dónde estás.');
			},
			{ enableHighAccuracy: true, timeout: 10_000 }
		);
	}

	/** «Llegué» con la ubicación del navegador (la API mira que estés a 150 m). */
	let formLlegue = $state<HTMLFormElement>();
	function llegar() {
		const form = formLlegue;
		if (!form) return;
		if (!navigator.geolocation) return avisar('Tu navegador no comparte la ubicación.');
		enviando = 'llegue';
		navigator.geolocation.getCurrentPosition(
			(pos) => {
				(form.elements.namedItem('lat') as HTMLInputElement).value = String(pos.coords.latitude);
				(form.elements.namedItem('lng') as HTMLInputElement).value = String(pos.coords.longitude);
				form.requestSubmit();
			},
			() => {
				enviando = '';
				avisar('No pudimos saber dónde estás. Pedile el QR a quien organiza desde la app.');
			},
			{ enableHighAccuracy: true, timeout: 10_000 }
		);
	}

	onMount(() => {
		if (llegarAlAbrir && !llegue) llegar();
	});

	const hora = (f?: string) => (f ? partes(f).hora : '');
	const despues =
		(texto: string) =>
		() =>
		async ({
			result,
			update
		}: {
			result: { type: string; data?: Record<string, unknown> };
			update: (o?: { reset?: boolean }) => Promise<void>;
		}) => {
			enviando = '';
			if (result.type === 'success') {
				hoja = false;
				avisar(texto);
			} else if (result.type === 'failure') avisar(String(result.data?.error ?? 'No se pudo.'));
			await update({ reset: false });
		};
</script>

{#if parte === 'tarjeta'}<section class="dia" aria-label="Hoy">
		<div class="cab">
			<div class="cuando">
				<span class="falta">{falta}</span>
				<span class="sub"
					>{llegaron.length
						? `${llegaron.map((v) => primerNombre(v.nombre)).join(', ')} ya ${llegaron.length === 1 ? 'llegó' : 'llegaron'}`
						: `Somos ${p.somos} de ${p.total}`}</span
				>
			</div>
			<div class="avs">
				{#each p.van.slice(0, 4) as v (v.id)}
					<span class="av" class:llego={v.llego}
						><Avatar nombre={v.nombre} foto={v.foto} tam={30} borde="2px solid #24212D" /></span
					>
				{/each}
			</div>
		</div>
		<div class="dos">
			{#if destino}
				<a
					class="gel gel-blanco boton"
					href={comoLlegar(destino.lat, destino.lng)}
					target="_blank"
					rel="noopener"><Icono nombre="pin" tam={18} trazo={2.2} />Cómo llegar</a
				>
			{/if}
			<form
				method="POST"
				action="?/llegoEn"
				use:enhance={() => {
					enviando = 'llego';
					return despues('Les avisamos en el chat.')();
				}}
			>
				<button class="gel gel-blanco boton" disabled={enviando === 'llego'}
					><Icono nombre="reloj" tam={18} trazo={2.2} />Llego en 10</button
				>
			</form>
		</div>
		{#if llegue}
			<span class="llegaste"
				><Icono nombre="check" tam={18} color="#24212D" trazo={3.4} />Llegaste</span
			>
		{:else}
			<form
				method="POST"
				action="?/llegue"
				bind:this={formLlegue}
				use:enhance={despues('Listo, tu asistencia quedó registrada.')}
			>
				<input type="hidden" name="lat" />
				<input type="hidden" name="lng" />
				<button
					type="button"
					class="gel gel-lima llegue"
					disabled={enviando === 'llegue'}
					onclick={llegar}
					><Icono nombre="check" tam={20} color="#24212D" trazo={3.4} />Llegué</button
				>
			</form>
		{/if}
		<button class="todo-bien" onclick={() => (hoja = true)}>
			<Icono nombre="escudo" tam={18} color="#C8F36B" trazo={2.2} />¿Todo bien?
			{#if todoBien?.activo}<span class="on">Activo</span>{/if}
		</button>
	</section>
{:else}
	<Hoja bind:abierta={hoja} titulo="¿Todo bien?">
		<div class="lista">
			{#if todoBien?.activo && todoBien.contacto}
				<div class="fila">
					<Icono nombre="escudo" tam={20} color="var(--c-acento)" trazo={2} />
					<span class="ft"
						><b
							>{avisaA(todoBien.contacto) === 'contacto'
								? `Le escribimos a ${nombreDe(todoBien.contacto)}`
								: `Te avisamos para que le escribas a ${nombreDe(todoBien.contacto)}`}</b
						><span
							>{todoBien.avisado
								? 'Ya avisamos.'
								: `Si a las ${hora(todoBien.avisaEn)} no hiciste check-in. Antes te preguntamos.`}</span
						></span
					>
					<form
						method="POST"
						action="?/apagarTodoBien"
						use:enhance={despues('Listo, lo apagamos.')}
					>
						<button class="chico">Apagar</button>
					</form>
				</div>
			{:else if !activable}
				<!-- Después del final ya no se activa: queda compartir la ubicación y volver. -->
			{:else if todoBien === null || todoBien === undefined}
				<a class="fila" href="/app/plan/{p.id}?todobien=1" data-sveltekit-reload>
					<Icono nombre="escudo" tam={20} color="var(--c-acento)" trazo={2} />
					<span class="ft"><b>No pudimos cargar «¿Todo bien?»</b><span>Probar de nuevo</span></span>
					<Icono nombre="flecha" tam={18} color="#9A95AD" trazo={2.6} />
				</a>
			{:else if todoBien.contactos.length && !nuevoContacto}
				{#each todoBien.contactos as c, i (i)}
					<form method="POST" action="?/todoBien" use:enhance={despues(toastActivo(c))}>
						<input type="hidden" name="contacto" value={i} />
						<button class="fila">
							<Icono nombre="escudo" tam={20} color="var(--c-acento)" trazo={2} />
							<span class="ft"
								><b>{filaTitulo(c)}</b><span
									>{avisaA(c) === 'contacto'
										? 'A la media hora, si no hice check-in'
										: 'No mandamos SMS: con un mail, le escribimos nosotros'}</span
								></span
							>
							<Icono nombre="flecha" tam={18} color="#9A95AD" trazo={2.6} />
						</button>
					</form>
				{/each}
			{:else}
				<!-- Sin contactos: se carga acá mismo y queda activo, sin ir a Ajustes. -->
				<form
					class="nuevo"
					method="POST"
					action="?/contactoTodoBien"
					use:enhance={despues('Listo. Si no llegás, te avisamos.')}
				>
					<span class="nuevo-t">¿A quién avisamos si no llegás?</span>
					<input
						name="nombre"
						maxlength="40"
						placeholder="Nombre (Mamá, Sofi…)"
						autocomplete="off"
						aria-label="Nombre"
					/>
					<input
						name="contacto"
						required
						maxlength="80"
						inputmode="email"
						placeholder="Mail (le escribimos) o teléfono"
						autocomplete="off"
						aria-label="Mail o teléfono"
					/>
					<button class="gel gel-lima" disabled={enviando === 'contacto'}>Activar</button>
				</form>
			{/if}
			<button class="fila" onclick={compartirUbicacion} disabled={ubicando}>
				<Icono nombre="pin" tam={20} color="var(--c-acento)" trazo={2} />
				<span class="ft"
					><b>Compartir mi ubicación</b><span>Sale de tu teléfono; no la guardamos</span></span
				>
				<Icono nombre="enviar" tam={18} color="#9A95AD" trazo={2.2} />
			</button>
			{#if telefono}
				<a class="fila" href="tel:{telefono.replace(/[^\d+]/g, '')}">
					<Icono nombre="telefono" tam={20} color="var(--c-acento)" trazo={2} />
					<span class="ft"
						><b>Me quiero ir</b><span>Llamar a {contacto?.nombre || telefono}</span></span
					>
					<Icono nombre="flecha" tam={18} color="#9A95AD" trazo={2.6} />
				</a>
			{/if}
			<a
				class="fila"
				href="https://m.uber.com/ul/?action=setPickup&pickup=my_location"
				target="_blank"
				rel="noopener"
			>
				<Icono nombre="auto" tam={20} color="var(--c-acento)" trazo={2} />
				<span class="ft"><b>Pedir un auto</b><span>Para volver ya</span></span>
				<Icono nombre="flecha" tam={18} color="#9A95AD" trazo={2.6} />
			</a>
		</div>
		<div class="urgencias">
			<a class="gel urgente" href="tel:911">Llamar al 911</a>
			<a class="gel gel-violeta" href="tel:144">Línea 144</a>
		</div>
		<p class="nota">La 144 atiende violencia de género, gratis y las 24&nbsp;h.</p>
		<button
			class="reportar"
			onclick={() => {
				hoja = false;
				onReportar?.();
			}}>Reportar algo del plan</button
		>
	</Hoja>
{/if}

<style>
	.dia {
		padding: 16px;
		border-radius: 24px;
		background: var(--c-contador);
		outline: 1px solid var(--c-contador-borde);
		outline-offset: -1px;
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
		display: flex;
		flex-direction: column;
		gap: 12px;
	}
	.cab {
		display: flex;
		justify-content: space-between;
		align-items: flex-end;
		gap: 10px;
	}
	.cuando {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.falta {
		font:
			800 34px/1 'Geist',
			sans-serif;
		letter-spacing: -1.2px;
		color: #c8f36b;
		white-space: nowrap;
	}
	.sub {
		font-size: 14px;
		font-weight: 600;
		color: #faf9f6;
		text-wrap: balance;
	}
	.avs {
		display: flex;
		padding-left: 8px;
		flex: none;
	}
	.av {
		margin-left: -8px;
		border-radius: 50%;
	}
	.av.llego {
		box-shadow: 0 0 0 2px #c8f36b;
	}
	.dos {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
	.dos form {
		display: contents;
	}
	.boton {
		height: 48px;
		border-radius: 16px;
		font-size: 15px;
		gap: 6px;
		white-space: nowrap;
	}
	.llegue,
	.llegaste {
		width: 100%;
		height: 56px;
		border-radius: 20px;
		font-size: 17px;
		gap: 8px;
	}
	.llegaste {
		display: flex;
		align-items: center;
		justify-content: center;
		font-weight: 800;
		color: #24212d;
		background: #c8f36b;
	}
	.todo-bien {
		align-self: center;
		display: flex;
		align-items: center;
		gap: 6px;
		padding: 4px 8px;
		font-size: 15px;
		font-weight: 700;
		color: #faf9f6;
	}
	.on {
		padding: 2px 8px;
		border-radius: 8px;
		background: #c8f36b;
		color: #24212d;
		font-size: 11px;
		font-weight: 800;
	}
	.nuevo {
		display: flex;
		flex-direction: column;
		gap: 10px;
		padding: 16px;
	}
	.nuevo-t {
		font-size: 15px;
		font-weight: 700;
	}
	.nuevo input {
		height: 48px;
		padding: 0 14px;
		border-radius: 14px;
		border: 1px solid var(--c-borde);
		background: var(--c-campo, var(--c-tarjeta-lisa));
		font: inherit;
		font-size: 16px;
		color: inherit;
	}
	.nuevo .gel {
		height: 48px;
		border-radius: 16px;
		font-size: 16px;
	}
	.lista {
		display: flex;
		flex-direction: column;
		border-radius: 22px;
		background: var(--c-tarjeta);
		box-shadow: var(--sh-tarjeta-plana);
	}
	.lista > * {
		border-bottom: 1px solid var(--c-linea);
	}
	.lista > :last-child {
		border-bottom: 0;
	}
	.fila {
		display: flex;
		align-items: center;
		gap: 12px;
		width: 100%;
		padding: 14px 16px;
		border: 0;
		background: none;
		text-align: left;
		font: inherit;
		color: inherit;
		text-decoration: none;
		cursor: pointer;
	}
	.ft {
		flex: 1;
		min-width: 0;
		display: flex;
		flex-direction: column;
	}
	.ft b {
		font-size: 14px;
		font-weight: 800;
	}
	.ft span {
		font-size: 12px;
		color: var(--texto-2);
	}
	.chico {
		font-size: 14px;
		font-weight: 700;
		color: var(--violeta-texto);
	}
	.urgencias {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
	.urgencias .gel {
		height: 54px;
		border-radius: 18px;
		font-size: 16px;
		text-decoration: none;
	}
	.urgente {
		color: #24212d;
		background: var(--grad-coral);
		box-shadow: var(--sh-coral);
	}
	.nota {
		font-size: 12px;
		color: var(--texto-2);
		text-align: center;
	}
	.reportar {
		align-self: center;
		font-size: 14px;
		font-weight: 700;
		color: var(--violeta-texto);
	}
</style>
