<script lang="ts">
	import { untrack } from 'svelte';
	import { enhance } from '$app/forms';
	import type { SubmitFunction } from '$app/forms';
	import Mapa from '#lib/ui/Mapa.svelte';
	import { CATEGORIAS_CREAR, CENTRO_MDP } from '#lib/app.ts';
	import { aRFC3339, diasParaElegir } from '#lib/fechas.ts';
	import { leerPrecio } from '#lib/precios.ts';

	/*
	 * Planes semilla (contrato §7): el cuerpo de POST /v1/planes + organizadorId, para que la app
	 * nunca se vea vacía. Se arma con las piezas del Nuevo plan del panel (campos, chips, stepper y
	 * toggles) y el mapa de Crear.
	 */
	let {
		org,
		orgNombre,
		yo,
		resultado
	}: {
		org: string;
		orgNombre: string;
		yo: { id: string; nombre: string };
		resultado: SubmitFunction;
	} = $props();

	const CIUDADES: { nombre: string; centro: [number, number] }[] = [
		{ nombre: 'Mar del Plata', centro: CENTRO_MDP },
		{ nombre: 'Villa Gesell', centro: [-37.2635, -56.973] }
	];
	const DIAS = diasParaElegir(new Date(), 5);
	const DURACIONES = [
		{ min: 60, l: '1 h' },
		{ min: 90, l: '1 h 30' },
		{ min: 120, l: '2 h' },
		{ min: 180, l: '3 h' }
	];

	let f = $state({
		organizadorId: untrack(() => org),
		titulo: '',
		categoria: 'juego',
		descripcion: '',
		dia: DIAS[1].valor,
		hora: '21:30',
		dur: 120,
		ciudad: 'Mar del Plata',
		zona: '',
		lugarNombre: '',
		direccion: '',
		lat: 0,
		lng: 0,
		somos: 3,
		faltan: 1,
		ingreso: 'aprobacion',
		soloVerificados: true,
		ocultarDireccion: true,
		recurrente: false,
		precio: ''
	});
	const centro = $derived(CIUDADES.find((c) => c.nombre === f.ciudad)?.centro ?? CENTRO_MDP);
	const datos = $derived({
		organizadorId: f.organizadorId.trim(),
		titulo: f.titulo.trim(),
		categoria: f.categoria,
		descripcion: f.descripcion.trim(),
		empiezaEn: aRFC3339(f.dia, f.hora),
		duracionMin: f.dur,
		recurrente: f.recurrente,
		ciudad: f.ciudad,
		zona: f.zona.trim(),
		lugarNombre: f.lugarNombre.trim(),
		direccion: f.direccion.trim(),
		lat: f.lat || centro[0],
		lng: f.lng || centro[1],
		somos: f.somos,
		faltan: f.faltan,
		ingreso: f.ingreso,
		visibilidad: 'publico',
		soloVerificados: f.soloVerificados,
		ocultarDireccion: f.ocultarDireccion,
		genero: 'cualquiera',
		costoCentavos: leerPrecio(f.precio) ? leerPrecio(f.precio) * 100 : null
	});
	const quien = $derived(
		f.organizadorId === yo.id ? `Vos (${yo.nombre})` : orgNombre || 'Otra persona del equipo'
	);
</script>

<form method="POST" action="?/semilla" class="semilla" use:enhance={resultado}>
	<input type="hidden" name="datos" value={JSON.stringify(datos)} />
	<div class="tarjeta-paso">
		<label class="campo">
			<span>Organiza</span>
			<input
				class="input"
				bind:value={f.organizadorId}
				pattern="[0-9a-fA-F\-]{'{36}'}"
				required
				placeholder="Id de la persona"
			/>
			<span class="s">{quien}. Para otra, buscala en Usuarios y tocá «Plan semilla».</span>
		</label>
		<label class="campo">
			<span>Nombre del plan</span>
			<input
				class="input grande"
				bind:value={f.titulo}
				maxlength="80"
				placeholder="Ej: Truco en un bar de Güemes"
			/>
		</label>
		<div class="campo">
			<span>Categoría</span>
			<div class="chips">
				{#each CATEGORIAS_CREAR as c (c.valor)}
					<button
						type="button"
						class="chip"
						aria-pressed={f.categoria === c.valor}
						onclick={() => (f.categoria = c.valor)}>{c.etiqueta}</button
					>
				{/each}
			</div>
		</div>
		<label class="campo">
			<span>Qué va a pasar</span>
			<textarea
				class="input"
				bind:value={f.descripcion}
				maxlength="600"
				placeholder="Contá en dos líneas cómo es el plan y para quién es."></textarea>
		</label>
	</div>

	<div class="tarjeta-paso">
		<div class="campo">
			<span>Día</span>
			<div class="chips">
				{#each DIAS as x (x.valor)}
					<button
						type="button"
						class="chip"
						aria-pressed={f.dia === x.valor}
						onclick={() => (f.dia = x.valor)}>{x.etiqueta}</button
					>
				{/each}
			</div>
			<input class="input corto" type="date" bind:value={f.dia} min={DIAS[0].valor} />
		</div>
		<div class="dos">
			<label class="campo">
				<span>Hora</span>
				<input class="input corto" type="time" bind:value={f.hora} step="900" />
			</label>
			<div class="campo">
				<span>Duración</span>
				<div class="chips">
					{#each DURACIONES as x (x.min)}
						<button
							type="button"
							class="chip"
							aria-pressed={f.dur === x.min}
							onclick={() => (f.dur = x.min)}>{x.l}</button
						>
					{/each}
				</div>
			</div>
		</div>
		<div class="cupo">
			<div class="cupo-txt">
				<span class="t">Somos</span>
				<span class="s">Los que ya van, contando a quien&nbsp;organiza</span>
			</div>
			<div class="stepper">
				<button
					type="button"
					aria-label="Menos"
					onclick={() => (f.somos = Math.max(1, f.somos - 1))}>−</button
				>
				<span class="n" aria-live="polite">{f.somos}</span>
				<button type="button" aria-label="Más" onclick={() => (f.somos = Math.min(30, f.somos + 1))}
					>+</button
				>
			</div>
		</div>
		<div class="cupo">
			<div class="cupo-txt">
				<span class="t">Faltan</span>
				<span class="s">Los lugares que se ofrecen en&nbsp;Caigo</span>
			</div>
			<div class="stepper">
				<button
					type="button"
					aria-label="Menos"
					onclick={() => (f.faltan = Math.max(1, f.faltan - 1))}>−</button
				>
				<span class="n" aria-live="polite">{f.faltan}</span>
				<button
					type="button"
					aria-label="Más"
					onclick={() => (f.faltan = Math.min(30, f.faltan + 1))}>+</button
				>
			</div>
		</div>
	</div>

	<div class="tarjeta-paso">
		<div class="campo">
			<span>Ciudad</span>
			<div class="seg">
				{#each CIUDADES as c (c.nombre)}
					<button
						type="button"
						aria-pressed={f.ciudad === c.nombre}
						onclick={() => ((f.ciudad = c.nombre), (f.lat = 0), (f.lng = 0))}>{c.nombre}</button
					>
				{/each}
			</div>
		</div>
		<div class="dos">
			<label class="campo">
				<span>Zona</span>
				<input class="input" bind:value={f.zona} maxlength="60" placeholder="Ej: Güemes" />
			</label>
			<label class="campo">
				<span>Lugar</span>
				<input
					class="input"
					bind:value={f.lugarNombre}
					maxlength="80"
					placeholder="Ej: Bar La Fonda"
				/>
			</label>
		</div>
		<label class="campo">
			<span>Dirección</span>
			<input class="input" bind:value={f.direccion} maxlength="160" placeholder="Ej: Güemes 2950" />
		</label>
		<div class="campo">
			<span>Dónde en el mapa</span>
			<span class="s">Tocá el mapa donde es. Sin marcar, va al centro de la&nbsp;ciudad.</span>
			{#key f.ciudad}
				<div class="mapa"><Mapa bind:lat={f.lat} bind:lng={f.lng} {centro} /></div>
			{/key}
		</div>
	</div>

	<div class="tarjeta-paso">
		<div class="campo">
			<span>Cómo se suman</span>
			<div class="seg">
				<button
					type="button"
					aria-pressed={f.ingreso === 'libre'}
					onclick={() => (f.ingreso = 'libre')}>Libre</button
				>
				<button
					type="button"
					aria-pressed={f.ingreso === 'aprobacion'}
					onclick={() => (f.ingreso = 'aprobacion')}>Con aprobación</button
				>
			</div>
		</div>
		<div class="toggles">
			<button
				type="button"
				class="tg"
				role="switch"
				aria-checked={f.soloVerificados}
				onclick={() => (f.soloVerificados = !f.soloVerificados)}
			>
				<span class="tg-txt"
					><span class="t">Solo verificados</span><span class="s"
						>Teléfono y selfie, como pide la app</span
					></span
				>
				<span class="toggle" class:on={f.soloVerificados}></span>
			</button>
			<button
				type="button"
				class="tg"
				role="switch"
				aria-checked={f.ocultarDireccion}
				onclick={() => (f.ocultarDireccion = !f.ocultarDireccion)}
			>
				<span class="tg-txt"
					><span class="t">Ocultar dirección</span><span class="s"
						>Se ve recién cuando aceptan a&nbsp;alguien</span
					></span
				>
				<span class="toggle" class:on={f.ocultarDireccion}></span>
			</button>
			<button
				type="button"
				class="tg"
				role="switch"
				aria-checked={f.recurrente}
				onclick={() => (f.recurrente = !f.recurrente)}
			>
				<span class="tg-txt"
					><span class="t">Se repite cada semana</span><span class="s"
						>Mismo día y hora, con cupo nuevo</span
					></span
				>
				<span class="toggle" class:on={f.recurrente}></span>
			</button>
		</div>
		<label class="campo">
			<span>Costo por persona (opcional)</span>
			<input
				class="input corto"
				bind:value={f.precio}
				inputmode="numeric"
				placeholder="$ 0, gratis"
			/>
		</label>
		<button class="gel gel-lima publicar">Publicar plan semilla</button>
	</div>
</form>

<style>
	.semilla {
		display: flex;
		flex-direction: column;
		gap: 16px;
	}
	.tarjeta-paso {
		display: flex;
		flex-direction: column;
		gap: 22px;
		padding: 28px;
		border-radius: 30px;
		background: #ffffff;
		box-shadow:
			inset 0 1px 0 #ffffff,
			0 3px 0 #e6e0f3,
			0 18px 30px -22px rgba(80, 60, 160, 0.35);
	}
	.input.grande {
		height: 56px;
		font-size: 17px;
	}
	.input.corto {
		max-width: 240px;
	}
	.chips {
		display: flex;
		gap: 8px;
		flex-wrap: wrap;
	}
	.seg {
		align-self: flex-start;
	}
	.seg button {
		font: inherit;
		font-weight: 800;
	}
	.dos {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
		gap: 20px;
	}
	.cupo {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 16px;
		padding: 16px 18px;
		border-radius: 22px;
		background: #f5f2fc;
	}
	.cupo-txt,
	.tg-txt {
		display: flex;
		flex-direction: column;
		gap: 2px;
		text-align: left;
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
	.campo > .s {
		font-weight: 600;
	}
	.stepper {
		flex: none;
		display: flex;
		align-items: center;
		gap: 14px;
	}
	.stepper button {
		width: 44px;
		height: 44px;
		border-radius: 15px;
		display: grid;
		place-items: center;
		background: #ffffff;
		box-shadow: 0 3px 0 #dcd5ee;
		font:
			800 22px/1 'Geist',
			sans-serif;
		color: #5b43c9;
	}
	.stepper .n {
		min-width: 36px;
		text-align: center;
		font:
			800 30px/1 'Geist',
			sans-serif;
	}
	.mapa {
		border-radius: 20px;
		overflow: hidden;
	}
	.toggles {
		display: flex;
		flex-direction: column;
		gap: 4px;
	}
	.tg {
		width: 100%;
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 16px;
		padding: 14px 4px;
		border-bottom: 1px solid #f0ecff;
	}
	.publicar {
		height: 56px;
		border-radius: 18px;
		font-size: 16px;
	}
	@media (max-width: 900px) {
		.tarjeta-paso {
			padding: 20px 16px;
			border-radius: 24px;
		}
	}
</style>
