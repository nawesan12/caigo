<script lang="ts">
	import { untrack } from 'svelte';
	import { enhance } from '$app/forms';
	import { PUBLIC_SITIO } from '$app/env/public';
	import Icono from '#lib/ui/Icono.svelte';
	import Mapa from '#lib/ui/Mapa.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { CATEGORIAS_CREAR } from '#lib/app.ts';
	import { aRFC3339, diasParaElegir } from '#lib/fechas.ts';
	import { faltanTexto } from '#lib/plan.ts';
	import { leerPrecio, pesos } from '#lib/precios.ts';

	/*
	 * Crear plan en 3 pasos (Mockups v5, 06a–06c) con los valores por defecto del README (Hoy 21:30,
	 * Somos 3 / Falta 1, Con aprobación, Solo verificados y Ocultar dirección prendidos) y la
	 * pantalla "Plan publicado" del Prototipo. "¿Dónde?" abre el mapa del registro de lugares.
	 */
	let { data, form } = $props();

	const ini = untrack(() => ({ yo: data.yo, centro: data.centro }));
	const HOY = diasParaElegir(new Date(), 2);
	const NIVELES_JUEGO = ['Recién aprendo', 'Me defiendo', 'Canto falta envido'];
	const NIVELES = ['Principiante', 'Nivel medio', 'Avanzado'];

	let paso = $state(1);
	let f = $state({
		titulo: '',
		categoria: 'juego',
		cuando: 'hoy' as 'ahora' | 'hoy' | 'otro',
		dia: HOY[1].valor,
		hora: '21:30',
		otraHora: false,
		lugarNombre: '',
		direccion: '',
		zona: ini.yo.zona || '',
		ciudad: ini.yo.ciudad || 'Mar del Plata',
		lat: 0,
		lng: 0,
		recurrente: false,
		somos: 3,
		faltan: 1,
		edad: false,
		edadMin: 20,
		edadMax: 35,
		genero: 'cualquiera',
		nivel: '',
		ingreso: 'aprobacion',
		visibilidad: 'publico',
		soloVerificados: true,
		ocultarDireccion: true,
		costo: false,
		precio: ''
	});
	let dondeAbierto = $state(false);
	let enviando = $state(false);

	const niveles = $derived(f.categoria === 'juego' ? NIVELES_JUEGO : NIVELES);
	const empiezaEn = $derived.by(() => {
		if (f.cuando === 'ahora') return new Date(Date.now() + 5 * 60000).toISOString();
		return aRFC3339(f.cuando === 'hoy' ? HOY[0].valor : f.dia, f.hora);
	});
	const datos = $derived({
		titulo: f.titulo.trim(),
		categoria: f.categoria,
		empiezaEn,
		duracionMin: 120,
		recurrente: f.recurrente,
		ciudad: f.ciudad,
		zona: f.zona.trim(),
		lugarNombre: f.lugarNombre.trim(),
		direccion: f.direccion.trim(),
		lat: f.lat || ini.centro.lat,
		lng: f.lng || ini.centro.lng,
		somos: f.somos,
		faltan: f.faltan,
		ingreso: f.ingreso,
		visibilidad: f.visibilidad,
		soloVerificados: f.soloVerificados,
		ocultarDireccion: f.ocultarDireccion,
		edadMin: f.edad ? f.edadMin : null,
		edadMax: f.edad ? f.edadMax : null,
		genero: f.genero,
		nivel: f.nivel,
		costoCentavos: f.costo && leerPrecio(f.precio) ? leerPrecio(f.precio) * 100 : null
	});
	const publicado = $derived(form && 'publicado' in form ? form.publicado : null);
	const link = $derived(publicado ? `${PUBLIC_SITIO.replace(/\/+$/, '')}/p/${publicado.slug}` : '');

	function siguiente() {
		if (paso === 1 && !f.lat && !f.lugarNombre.trim()) {
			dondeAbierto = true;
			return avisar('Decí dónde es el plan.');
		}
		paso++;
	}
	async function copiar() {
		try {
			await navigator.clipboard.writeText(link);
			avisar('Link copiado.');
		} catch {
			avisar(link);
		}
	}
	const CIUDADES: { nombre: string; centro: [number, number] }[] = [
		{ nombre: 'Mar del Plata', centro: [-38.0055, -57.5426] },
		{ nombre: 'Villa Gesell', centro: [-37.2636, -56.9733] }
	];
	const centroMapa = $derived<[number, number]>(
		f.lat
			? [f.lat, f.lng]
			: f.ciudad === 'Villa Gesell'
				? CIUDADES[1].centro
				: [ini.centro.lat, ini.centro.lng]
	);
</script>

<svelte:head><title>Crear plan · Caigo</title></svelte:head>

{#if publicado}
	<div class="publicado">
		<img src="/caigo-icon.png" alt="" width="190" height="190" />
		<h1>Plan publicado</h1>
		<p>«{publicado.titulo}» ya aparece en Ahora. Te avisamos cuando alguien se&nbsp;prenda.</p>
		<div class="pub-acc">
			<a
				class="gel gel-lima pub-wa"
				href="https://wa.me/?text={encodeURIComponent(`${publicado.titulo}: ${link}`)}"
				target="_blank"
				rel="noopener noreferrer">Compartir link por WhatsApp</a
			>
			<button class="pub-copiar" onclick={copiar}>Copiar link</button>
			<a class="pub-link" href="/app/mis">Ver mis planes</a>
		</div>
	</div>
{:else}
	<div class="scroll">
		<div class="pantalla">
			<div class="arriba">
				<div class="arriba-fila">
					{#if paso === 1}
						<a class="icono-btn" href="/app" aria-label="Cerrar"
							><Icono nombre="cerrar" tam={20} trazo={2.4} /></a
						>
					{:else}
						<button class="icono-btn" onclick={() => paso--} aria-label="Atrás"
							><Icono nombre="atras" tam={22} /></button
						>
					{/if}
					<span class="paso">{paso} de 3</span>
				</div>
				<div class="progreso">
					{#each [1, 2, 3] as n (n)}<span class:on={n <= paso}></span>{/each}
				</div>
			</div>

			{#if paso === 1}
				<h1>¿Qué hacemos?</h1>
				<input
					class="titulo"
					bind:value={f.titulo}
					maxlength="80"
					placeholder="Ej: Truco en un bar de Güemes"
					aria-label="Nombre del plan"
				/>
				<div class="chips">
					{#each CATEGORIAS_CREAR as c (c.valor)}
						<button
							class="chip-a"
							class:sel={f.categoria === c.valor}
							onclick={() => (f.categoria = c.valor)}>{c.etiqueta}</button
						>
					{/each}
				</div>
				<div class="bloque">
					<span class="rot">¿Cuándo?</span>
					<div class="seg3">
						{#each [['ahora', 'Ahora'], ['hoy', 'Hoy'], ['otro', 'Otro día']] as [k, l] (k)}
							<button class:on={f.cuando === k} onclick={() => (f.cuando = k as typeof f.cuando)}
								>{l}</button
							>
						{/each}
					</div>
					{#if f.cuando === 'otro'}
						<input
							class="campo-b"
							type="date"
							bind:value={f.dia}
							min={HOY[0].valor}
							aria-label="Día"
						/>
					{/if}
					{#if f.cuando !== 'ahora'}
						<div class="horas">
							{#each ['20:30', '21:30', '22:30'] as h (h)}
								<button
									class="hora"
									class:on={!f.otraHora && f.hora === h}
									onclick={() => ((f.hora = h), (f.otraHora = false))}>{h}</button
								>
							{/each}
							<button class="hora" class:on={f.otraHora} onclick={() => (f.otraHora = true)}
								>Otra</button
							>
						</div>
						{#if f.otraHora}
							<input
								class="campo-b corto"
								type="time"
								bind:value={f.hora}
								step="900"
								aria-label="Hora"
							/>
						{/if}
					{/if}
				</div>
				<div class="bloque">
					<span class="rot">¿Dónde?</span>
					<button
						class="donde"
						onclick={() => (dondeAbierto = !dondeAbierto)}
						aria-expanded={dondeAbierto}
					>
						<Icono nombre="pin" tam={20} color="#7456F1" trazo={2} />
						<span class="dcol">
							<b>{f.lugarNombre || (f.lat ? 'Punto en el mapa' : 'Elegí el lugar')}</b>
							<span>{[f.direccion, f.zona].filter(Boolean).join(', ') || f.ciudad}</span>
						</span>
						<span class="cambiar">{dondeAbierto ? 'Listo' : 'Cambiar'}</span>
					</button>
					{#if dondeAbierto}
						<div class="donde-form">
							<input
								class="campo-b"
								bind:value={f.lugarNombre}
								maxlength="80"
								placeholder="Nombre del lugar (ej: Bar La Cumbre)"
								aria-label="Nombre del lugar"
							/>
							<input
								class="campo-b"
								bind:value={f.direccion}
								maxlength="160"
								placeholder="Dirección (ej: Alsina 2450)"
								aria-label="Dirección"
							/>
							<input
								class="campo-b"
								bind:value={f.zona}
								maxlength="60"
								placeholder="Zona (ej: Güemes)"
								aria-label="Zona"
							/>
							<div class="chips">
								{#each CIUDADES as c (c.nombre)}
									<button
										class="chip-a"
										class:sel={f.ciudad === c.nombre}
										onclick={() => (f.ciudad = c.nombre)}>{c.nombre}</button
									>
								{/each}
							</div>
							<span class="ayuda"
								>Tocá el mapa donde es. La dirección exacta se puede ocultar hasta que aceptes
								a&nbsp;alguien.</span
							>
							<Mapa bind:lat={f.lat} bind:lng={f.lng} centro={centroMapa} />
						</div>
					{/if}
				</div>
				<button
					class="tg"
					onclick={() => (f.recurrente = !f.recurrente)}
					role="switch"
					aria-checked={f.recurrente}
				>
					<span class="tg-txt"
						><b>Se repite cada semana</b><span>Mismo día y hora, todas las semanas</span></span
					>
					<span class="tog" class:on={f.recurrente}></span>
				</button>
			{:else if paso === 2}
				<h1>¿Cuántos son?</h1>
				<div class="cuantos">
					<div class="caja">
						<span class="cl">Somos</span>
						<div class="stp">
							<button aria-label="Menos" onclick={() => (f.somos = Math.max(1, f.somos - 1))}
								>−</button
							>
							<b>{f.somos}</b>
							<button aria-label="Más" onclick={() => (f.somos = Math.min(59, f.somos + 1))}
								>+</button
							>
						</div>
					</div>
					<div class="caja oscura">
						<span class="cl coral">Faltan</span>
						<div class="stp">
							<button aria-label="Menos" onclick={() => (f.faltan = Math.max(1, f.faltan - 1))}
								>−</button
							>
							<b class="coral">{f.faltan}</b>
							<button
								aria-label="Más"
								onclick={() => (f.faltan = Math.min(60 - f.somos, f.faltan + 1))}>+</button
							>
						</div>
					</div>
				</div>
				<div class="previa">
					<span class="pl">Se va a ver así:</span>
					<span class="badge-coral pb">{faltanTexto(f.faltan)}</span>
					<span class="pt">· somos {f.somos + f.faltan} en total</span>
				</div>
				<div class="sec">
					<h2>¿A quién buscás?</h2>
					<span>Todo opcional</span>
				</div>
				<div class="bloque">
					<div class="edad-cab">
						<button class="rot rot-btn" onclick={() => (f.edad = !f.edad)} aria-pressed={f.edad}
							>Edad</button
						>
						<span class="edad-v">{f.edad ? `${f.edadMin} a ${f.edadMax}` : 'Cualquiera'}</span>
					</div>
					<div
						class="rango"
						class:apagado={!f.edad}
						style:--a="{((f.edadMin - 18) / 52) * 100}%"
						style:--b="{((f.edadMax - 18) / 52) * 100}%"
					>
						<span class="pista"></span><span class="tramo"></span>
						<input
							type="range"
							min="18"
							max="70"
							bind:value={f.edadMin}
							oninput={() => ((f.edad = true), (f.edadMin = Math.min(f.edadMin, f.edadMax - 1)))}
							aria-label="Edad mínima"
						/>
						<input
							type="range"
							min="18"
							max="70"
							bind:value={f.edadMax}
							oninput={() => ((f.edad = true), (f.edadMax = Math.max(f.edadMax, f.edadMin + 1)))}
							aria-label="Edad máxima"
						/>
					</div>
				</div>
				<div class="bloque">
					<span class="rot">Quiénes</span>
					<div class="chips">
						{#each [['cualquiera', 'Cualquiera'], ['mujeres', 'Solo mujeres'], ['diversidades', 'Solo diversidades']] as [k, l] (k)}
							<button class="chip-a" class:sel={f.genero === k} onclick={() => (f.genero = k)}
								>{l}</button
							>
						{/each}
					</div>
				</div>
				<div class="bloque">
					<span class="rot">Nivel</span>
					<div class="chips">
						{#each niveles as n (n)}
							<button
								class="chip-a"
								class:sel={f.nivel === n}
								onclick={() => (f.nivel = f.nivel === n ? '' : n)}>{n}</button
							>
						{/each}
					</div>
				</div>
			{:else}
				<h1>¿Cómo se suman?</h1>
				<div class="radios">
					{#each [['libre', 'Libre', 'El que llega, entra'], ['aprobacion', 'Con aprobación', 'Vos elegís quién entra · recomendado'], ['mi_gente', 'Solo mi gente', 'Gente con la que ya compartiste un plan']] as [k, l, d] (k)}
						<button class="radio" class:on={f.ingreso === k} onclick={() => (f.ingreso = k)}>
							<span class="anillo"></span>
							<span class="rcol"><b>{l}</b><span>{d}</span></span>
						</button>
					{/each}
				</div>
				<div class="bloque">
					<span class="rot">Quién lo ve</span>
					<div class="seg3">
						{#each [['publico', 'Público'], ['mi_gente', 'Mi gente'], ['link', 'Por link']] as [k, l] (k)}
							<button class:on={f.visibilidad === k} onclick={() => (f.visibilidad = k)}>{l}</button
							>
						{/each}
					</div>
				</div>
				<div class="opciones">
					<button
						class="op"
						role="switch"
						aria-checked={f.soloVerificados}
						onclick={() => (f.soloVerificados = !f.soloVerificados)}
					>
						<span>Solo verificados</span><span class="tog" class:on={f.soloVerificados}></span>
					</button>
					<button
						class="op"
						role="switch"
						aria-checked={f.ocultarDireccion}
						onclick={() => (f.ocultarDireccion = !f.ocultarDireccion)}
					>
						<span>Ocultar dirección hasta aceptar</span><span
							class="tog"
							class:on={f.ocultarDireccion}
						></span>
					</button>
					<div class="op">
						<span>Costo por persona</span>
						{#if f.costo}
							<span class="costo">
								$<input
									inputmode="numeric"
									value={leerPrecio(f.precio) ? leerPrecio(f.precio).toLocaleString('es-AR') : ''}
									oninput={(e) =>
										(f.precio = (e.currentTarget as HTMLInputElement).value
											.replace(/\D/g, '')
											.slice(0, 7))}
									aria-label="Costo por persona"
								/>
							</span>
						{:else}
							<button class="cada" onclick={() => (f.costo = true)}>Cada uno lo suyo</button>
						{/if}
					</div>
				</div>
				{#if f.costo && leerPrecio(f.precio)}
					<span class="ayuda centro"
						>{pesos(leerPrecio(f.precio))} por persona, se arregla entre ustedes.</span
					>
				{/if}
			{/if}
			{#if form?.error}<p class="error" role="alert">{form.error}</p>{/if}
		</div>
	</div>

	<form
		method="POST"
		class="pie"
		use:enhance={({ cancel }) => {
			if (paso < 3) {
				cancel();
				siguiente();
				return;
			}
			enviando = true;
			return async ({ update }) => {
				await update({ reset: false });
				enviando = false;
			};
		}}
	>
		<input type="hidden" name="datos" value={JSON.stringify(datos)} />
		<button class="gel gel-lima cta" disabled={enviando}>
			{#if paso === 3}<svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true"
					><path
						d="M12 4v16M4 12h16"
						stroke="#24212D"
						stroke-width="4"
						stroke-linecap="round"
					/></svg
				>{/if}
			{paso < 3 ? 'Siguiente' : enviando ? 'Publicando…' : 'Publicar plan'}
		</button>
	</form>
{/if}

<style>
	.scroll {
		padding-bottom: 120px;
	}
	.pantalla {
		padding: calc(16px + env(safe-area-inset-top)) 20px 20px;
		display: flex;
		flex-direction: column;
		gap: 20px;
	}
	.arriba {
		display: flex;
		flex-direction: column;
		gap: 14px;
		margin-bottom: 14px;
	}
	.arriba-fila {
		display: flex;
		justify-content: space-between;
		align-items: center;
	}
	.icono-btn {
		width: 44px;
		height: 44px;
		border-radius: 16px;
		display: grid;
		place-items: center;
		color: #24212d;
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
		box-shadow: var(--sh-icono);
	}
	.paso {
		font-size: 14px;
		font-weight: 700;
		color: #5c586b;
	}
	.progreso {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 6px;
	}
	.progreso span {
		height: 6px;
		border-radius: 3px;
		background: #e4defa;
	}
	.progreso span.on {
		background: #7456f1;
	}
	h1 {
		font:
			700 32px/1.05 'Geist',
			sans-serif;
		letter-spacing: -0.8px;
	}
	h2 {
		font:
			700 20px 'Geist',
			sans-serif;
	}
	.titulo {
		height: 58px;
		border-radius: 18px;
		background: #ffffff;
		border: 2px solid #7456f1;
		padding: 0 18px;
		font-size: 18px;
		font-weight: 600;
		min-width: 0;
	}
	.titulo::placeholder {
		color: #b9b4cc;
	}
	.chips {
		display: flex;
		flex-wrap: wrap;
		gap: 8px;
	}
	.chip-a {
		padding: 9px 13px;
		border-radius: 13px;
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
		box-shadow: var(--sh-icono);
		font-size: 14px;
		font-weight: 600;
		color: #24212d;
	}
	.chip-a.sel {
		background: var(--grad-violeta);
		box-shadow: var(--sh-violeta);
		color: #ffffff;
		font-weight: 700;
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
	.seg3 {
		display: flex;
		padding: 4px;
		border-radius: 16px;
		background: #f0ecff;
	}
	.seg3 button {
		flex: 1;
		text-align: center;
		padding: 10px 0;
		border-radius: 12px;
		font-size: 14px;
		font-weight: 600;
		color: #5c586b;
	}
	.seg3 button.on {
		background: #ffffff;
		color: #24212d;
		font-weight: 700;
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 3px 8px -2px rgba(80, 60, 160, 0.25);
	}
	.horas {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.hora {
		padding: 9px 13px;
		border-radius: 13px;
		background: #ffffff;
		border: 1.5px solid #ece8f2;
		font-size: 14px;
		font-weight: 600;
	}
	.hora.on {
		border-color: transparent;
		background: url(/grain.png), radial-gradient(120% 90% at 0% 0%, #3d3756 0%, #24212d 62%);
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
		color: #faf9f6;
		font-weight: 700;
	}
	.campo-b {
		height: 50px;
		border-radius: 16px;
		background: #ffffff;
		border: 1.5px solid #ece8f2;
		padding: 0 14px;
		font-size: 15px;
		font-weight: 600;
		min-width: 0;
	}
	.campo-b.corto {
		max-width: 180px;
	}
	.donde {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 14px 16px;
		border-radius: 18px;
		background: #ffffff;
		border: 1.5px solid #ece8f2;
		text-align: left;
	}
	.dcol {
		flex: 1;
		display: flex;
		flex-direction: column;
		min-width: 0;
	}
	.dcol b {
		font-size: 15px;
		font-weight: 700;
	}
	.dcol span {
		font-size: 12px;
		color: #5c586b;
	}
	.cambiar {
		font-size: 13px;
		font-weight: 700;
		color: #7456f1;
	}
	.donde-form {
		display: flex;
		flex-direction: column;
		gap: 10px;
	}
	.ayuda {
		font-size: 13px;
		font-weight: 600;
		color: #5c586b;
		line-height: 1.45;
	}
	.ayuda.centro {
		text-align: center;
	}
	.tg {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		padding: 4px 2px;
		text-align: left;
	}
	.tg-txt {
		display: flex;
		flex-direction: column;
	}
	.tg-txt b {
		font-size: 15px;
		font-weight: 700;
	}
	.tg-txt span {
		font-size: 12px;
		color: #5c586b;
	}
	.tog {
		flex: none;
		position: relative;
		width: 48px;
		height: 28px;
		border-radius: 14px;
		background: #ddd8ea;
		transition: background 0.15s;
	}
	.tog::after {
		content: '';
		position: absolute;
		top: 3px;
		left: 3px;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		background: #ffffff;
		transition: left 0.15s;
	}
	.tog.on {
		background: #7456f1;
	}
	.tog.on::after {
		left: 23px;
	}
	.cuantos {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 10px;
	}
	.caja {
		padding: 14px;
		border-radius: 22px;
		background: #ffffff;
		border: 1.5px solid #ece8f2;
		display: flex;
		flex-direction: column;
		gap: 8px;
		align-items: center;
	}
	.caja.oscura {
		border: 0;
		background: url(/grain.png), radial-gradient(120% 90% at 0% 0%, #3d3756 0%, #24212d 62%);
		box-shadow:
			inset 0 1px 0 rgba(255, 255, 255, 0.08),
			0 12px 26px -10px rgba(36, 33, 45, 0.55);
	}
	.cl {
		font-size: 13px;
		font-weight: 700;
		color: #5c586b;
	}
	.coral {
		color: #ff7868 !important;
	}
	.stp {
		display: flex;
		align-items: center;
		gap: 14px;
	}
	.stp button {
		width: 36px;
		height: 36px;
		border-radius: 12px;
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
		box-shadow: var(--sh-icono);
		font-size: 20px;
		font-weight: 700;
		color: #24212d;
	}
	.oscura .stp button {
		background: #3a3550;
		box-shadow: none;
		color: #faf9f6;
	}
	.stp b {
		min-width: 30px;
		text-align: center;
		font:
			800 48px/1 'Geist',
			sans-serif;
	}
	.previa {
		display: flex;
		align-items: center;
		gap: 10px;
		padding: 12px 14px;
		border-radius: 16px;
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
		box-shadow: var(--sh-icono);
		font-size: 14px;
		flex-wrap: wrap;
	}
	.pl {
		font-weight: 600;
		color: #5c586b;
	}
	.pb {
		padding: 5px 10px;
		border-radius: 10px;
		font-size: 15px;
	}
	.pt {
		font-weight: 600;
	}
	.sec {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
	}
	.sec span {
		font-size: 13px;
		color: #5c586b;
	}
	.edad-cab {
		display: flex;
		justify-content: space-between;
		font-size: 14px;
	}
	.rot-btn {
		font-size: 14px;
	}
	.edad-v {
		font-weight: 700;
		color: #7456f1;
	}
	.rango {
		position: relative;
		height: 28px;
	}
	.pista,
	.tramo {
		position: absolute;
		top: 12px;
		height: 4px;
		border-radius: 2px;
	}
	.pista {
		left: 0;
		right: 0;
		background: #e4defa;
	}
	.tramo {
		left: var(--a);
		right: calc(100% - var(--b));
		background: #7456f1;
	}
	.apagado .tramo {
		background: #cfc8e6;
	}
	.rango input {
		position: absolute;
		inset: 0;
		width: 100%;
		margin: 0;
		background: none;
		pointer-events: none;
		-webkit-appearance: none;
		appearance: none;
	}
	.rango input::-webkit-slider-thumb {
		-webkit-appearance: none;
		pointer-events: auto;
		width: 24px;
		height: 24px;
		border-radius: 50%;
		background: #ffffff;
		border: 3px solid #7456f1;
		cursor: pointer;
	}
	.rango input::-moz-range-thumb {
		pointer-events: auto;
		width: 18px;
		height: 18px;
		border-radius: 50%;
		background: #ffffff;
		border: 3px solid #7456f1;
		cursor: pointer;
	}
	.radios {
		display: flex;
		flex-direction: column;
		gap: 8px;
	}
	.radio {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 14px;
		border-radius: 18px;
		background: #ffffff;
		border: 1.5px solid #ece8f2;
		text-align: left;
	}
	.radio.on {
		background: linear-gradient(180deg, #f8f6ff 0%, #ede8ff 100%);
		box-shadow: var(--sh-icono);
		border: 2px solid #7456f1;
	}
	.anillo {
		flex: none;
		width: 22px;
		height: 22px;
		border-radius: 50%;
		border: 2px solid #cfc8e6;
		background: #ffffff;
	}
	.on .anillo {
		border: 7px solid #7456f1;
	}
	.rcol {
		display: flex;
		flex-direction: column;
	}
	.rcol b {
		font-size: 15px;
		font-weight: 700;
	}
	.rcol span {
		font-size: 12px;
		color: #5c586b;
	}
	.opciones {
		display: flex;
		flex-direction: column;
		border-radius: 20px;
		background: #ffffff;
		border: 1.5px solid #ece8f2;
	}
	.op {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 12px;
		padding: 13px 14px;
		border-bottom: 1px solid #f0ecff;
		font-size: 14px;
		font-weight: 600;
		text-align: left;
	}
	.op:last-child {
		border-bottom: 0;
	}
	.cada {
		font-size: 14px;
		font-weight: 700;
		color: #7456f1;
	}
	.costo {
		display: flex;
		align-items: center;
		gap: 2px;
		font-weight: 700;
		color: #7456f1;
	}
	.costo input {
		width: 90px;
		border: 0;
		border-bottom: 2px solid #7456f1;
		background: transparent;
		font-size: 15px;
		font-weight: 700;
		color: #24212d;
		text-align: right;
	}
	.error {
		padding: 12px 14px;
		border-radius: 16px;
		background: #ffe1db;
		color: #9a2e1f;
		font-size: 14px;
		font-weight: 700;
	}
	.pie {
		position: absolute;
		left: 20px;
		right: 20px;
		bottom: calc(34px + env(safe-area-inset-bottom));
		z-index: 4;
	}
	.cta {
		width: 100%;
		height: 58px;
		border-radius: 20px;
		font-size: 17px;
		color: #201d2b;
	}
	/* Plan publicado (Prototipo) */
	.publicado {
		position: absolute;
		inset: 0;
		background:
			url(/grain.png), radial-gradient(120% 70% at 85% 0%, #a38fff 0%, #7456f1 45%, #5a3dd8 100%);
		display: flex;
		flex-direction: column;
		align-items: center;
		justify-content: center;
		gap: 18px;
		padding: 30px;
		text-align: center;
		color: #ffffff;
		overflow-y: auto;
	}
	.publicado img {
		width: 190px;
		height: auto;
		animation: tk-pop 0.6s cubic-bezier(0.3, 1.4, 0.5, 1) both;
		filter: drop-shadow(0 26px 26px rgba(20, 10, 80, 0.5));
	}
	.publicado h1 {
		font:
			800 40px/1 'Geist',
			sans-serif;
		letter-spacing: -1.4px;
	}
	.publicado p {
		font-size: 16px;
		line-height: 1.5;
	}
	.pub-acc {
		align-self: stretch;
		display: flex;
		flex-direction: column;
		gap: 12px;
		margin-top: 10px;
	}
	.pub-wa {
		height: 60px;
		border-radius: 22px;
		font-size: 17px;
		box-shadow:
			inset 0 3px 2px rgba(255, 255, 255, 0.85),
			inset 0 -5px 0 rgba(96, 140, 10, 0.32),
			0 5px 0 #7fa82e;
	}
	.pub-copiar {
		height: 54px;
		border-radius: 18px;
		background: rgba(255, 255, 255, 0.16);
		color: #ffffff;
		font-size: 15px;
		font-weight: 800;
	}
	.pub-link {
		font-size: 15px;
		font-weight: 800;
		color: #ffffff;
		padding: 10px;
		text-decoration: none;
	}
</style>
