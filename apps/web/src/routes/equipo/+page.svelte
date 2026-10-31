<script lang="ts">
	import { enhance } from '$app/forms';
	import Icono, { type NombreIcono } from '#lib/ui/Icono.svelte';
	import Avatar from '#lib/app-ui/Avatar.svelte';
	import { avisar } from '#lib/toast.svelte.ts';
	import { cuandoCorto, partes } from '#lib/fechas.ts';
	import { CATEGORIAS } from '#lib/plan.ts';
	import type { SubmitFunction } from '$app/forms';
	import Grafico from './Grafico.svelte';
	import Semilla from './Semilla.svelte';

	/*
	 * Backoffice del equipo. No está en el handoff: se arma con la barra lateral, las tarjetas, los
	 * KPIs, los chips y los botones gel del Panel Lugares.
	 */
	let { data, form } = $props();

	const TABS: { k: string; l: string; icono: NombreIcono; n: number }[] = $derived([
		{ k: 'metricas', l: 'Métricas', icono: 'cobros', n: 0 },
		{ k: 'usuarios', l: 'Usuarios', icono: 'personas', n: 0 },
		{ k: 'planes', l: 'Planes', icono: 'calendario', n: 0 },
		{ k: 'locales', l: 'Lugares', icono: 'pin', n: 0 },
		{ k: 'lugares', l: 'Lugares pendientes', icono: 'pin', n: data.lugares.length },
		{ k: 'dni', l: 'DNI', icono: 'usuario', n: data.dni.length },
		{ k: 'reportes', l: 'Reportes', icono: 'escudo', n: data.reportes.length },
		{
			k: 'arrepentimientos',
			l: 'Arrepentimientos',
			icono: 'cobros',
			n: data.arrepentimientos.filter((a) => !a.atendidoEn).length
		},
		{ k: 'semilla', l: 'Planes semilla', icono: 'mas', n: 0 }
	]);
	const MOTIVOS: Record<string, string> = {
		acoso: 'Acoso',
		falso: 'Perfil falso',
		no_vino: 'No vino',
		inapropiado: 'Inapropiado',
		inseguro: 'Inseguro',
		otro: 'Otro'
	};
	const ESTADOS_LUGAR: Record<string, string> = {
		pendiente: 'Pendiente',
		aprobado: 'Aprobado',
		rechazado: 'Rechazado',
		suspendido: 'Suspendido'
	};
	const DNI: Record<string, string> = {
		pendiente: 'DNI en revisión',
		aprobado: 'DNI verificado',
		rechazado: 'DNI rechazado'
	};
	/** Pega las dos últimas palabras: nombres y datos que vienen de la API, sin renglones viudos. */
	const pegar = (t: string) => t.replace(/ (\S+)\s*$/, '\u00a0$1');
	const fecha = (f: string) => {
		const p = partes(f);
		return `${p.dia}/${p.mes + 1} ${p.hora}`;
	};
	const dia = (f: string) => {
		const p = partes(f);
		return `${p.dia}/${p.mes + 1}/${String(p.anio).slice(2)}`;
	};
	const resultado: SubmitFunction =
		() =>
		async ({ result, update }) => {
			if (result.type === 'success') avisar(String(result.data?.ok ?? 'Listo.'));
			else if (result.type === 'failure') avisar(String(result.data?.error ?? 'No se pudo.'));
			await update();
		};
	/** Cancelar un plan o suspender a alguien pide confirmar. */
	const confirmar =
		(texto: string): SubmitFunction =>
		(e) => {
			const d = (e.submitter as HTMLButtonElement | null)?.value;
			if ((d === 'suspender' || d === 'cancelar') && !confirm(texto)) e.cancel();
			return resultado(e);
		};

	// Métricas: la última semana contra la anterior.
	const sem = $derived(data.metricas.datos ?? []);
	const etiquetas = $derived(
		sem.map((s) => {
			const [, m, d] = s.semana.split('-');
			return d && m ? `${+d}/${+m}` : s.semana;
		})
	);
	const pct = (n: number) => `${Math.round(n * 100)}%`;
	const horas = (n: number) =>
		!n
			? '—'
			: n < 1
				? `${Math.max(1, Math.round(n * 60))} min`
				: `${n.toFixed(1).replace('.', ',')} h`;
	const kpis = $derived.by(() => {
		const u = sem[sem.length - 1];
		const a = sem[sem.length - 2];
		if (!u) return [];
		const dif = (x: number, y: number | undefined, f: (n: number) => string, inv = false) => {
			if (y === undefined) return 'Primera semana con datos';
			const d = x - y;
			if (Math.abs(d) < 1e-9) return 'Igual que la semana anterior';
			const s = (inv ? d < 0 : d > 0) ? 'Mejor' : 'Peor';
			return `${s} que la anterior (${d > 0 ? '+' : '−'}${f(Math.abs(d))})`;
		};
		const n = (x: number) => String(Math.round(x));
		return [
			{ l: 'Planes publicados', v: n(u.publicados), d: dif(u.publicados, a?.publicados, n) },
			{ l: 'Completados', v: n(u.completados), d: dif(u.completados, a?.completados, n) },
			{ l: 'Asistencia real', v: pct(u.asistencia), d: dif(u.asistencia, a?.asistencia, pct) },
			{
				l: 'Hasta completarse',
				v: horas(u.horasHastaCompletar),
				// Sin datos la API manda null (acá 0): no se compara contra eso.
				d: dif(u.horasHastaCompletar, a?.horasHastaCompletar || undefined, horas, true)
			},
			{
				l: 'Repiten a 30 días',
				v: pct(u.repeticion30),
				d: dif(u.repeticion30, a?.repeticion30, pct)
			},
			{
				l: 'Organizadores activos',
				v: n(u.organizadores),
				d: dif(u.organizadores, a?.organizadores, n)
			}
		];
	});
	const ORIGENES: Record<string, string> = { '': 'Directo' };
	const origen = (k: string) =>
		ORIGENES[k] ?? (k.startsWith('utm:') ? k.slice(4) : k.startsWith('p:') ? `Link de un plan` : k);
	const altas = $derived.by(() => {
		const t: Record<string, number> = {};
		for (const s of sem)
			for (const [k, v] of Object.entries(s.altas)) t[origen(k)] = (t[origen(k)] ?? 0) + v;
		const l = Object.entries(t).sort((a, b) => b[1] - a[1]);
		const max = Math.max(1, ...l.map((x) => x[1]));
		return l.map(([o, v]) => ({ o, v, pct: (v / max) * 100 }));
	});
	const semilla = $derived(form && 'semilla' in form ? form.semilla : null);
</script>

<svelte:head>
	<title>Equipo · Caigo</title>
	<meta name="robots" content="noindex" />
</svelte:head>

<div class="panel">
	<aside>
		<div class="marca">
			<a href="/" class="caigo">Caigo</a>
			<span class="para">EQUIPO</span>
		</div>
		<nav aria-label="Secciones">
			{#each TABS as t (t.k)}
				<a
					href="?tab={t.k}"
					class:on={data.tab === t.k}
					aria-current={data.tab === t.k ? 'page' : undefined}
				>
					<Icono nombre={t.icono} />{t.l}
					{#if t.n}<span class="n">{t.n}</span>{/if}
				</a>
			{/each}
		</nav>
		<form method="POST" action="/salir" class="abajo"><button class="salir">Salir</button></form>
	</aside>

	<main>
		{#if data.tab === 'metricas'}
			<h1>Métricas</h1>
			{#if data.metricas.error}
				<p class="vacia tarjeta-plana">{data.metricas.error}</p>
			{:else if !sem.length}
				<p class="vacia tarjeta-plana">Todavía no hay semanas con datos.</p>
			{:else}
				<p class="sub">
					Últimas {sem.length} semanas. Los números grandes son los de esta&nbsp;semana.
				</p>
				<div class="kpis">
					{#each kpis as k (k.l)}
						<div class="kpi tarjeta">
							<span class="kl">{k.l}</span>
							<span class="kv">{k.v}</span>
							<span class="kd">{k.d}</span>
						</div>
					{/each}
				</div>
				<div class="graficos">
					<Grafico
						titulo="Planes por semana"
						{etiquetas}
						series={[
							{ nombre: 'Publicados', valores: sem.map((s) => s.publicados), color: 'violeta' },
							{ nombre: 'Completados', valores: sem.map((s) => s.completados), color: 'lima' }
						]}
					/>
					<Grafico
						titulo="Asistencia y repetición"
						tipo="linea"
						{etiquetas}
						formato={pct}
						series={[
							{ nombre: 'Asistencia', valores: sem.map((s) => s.asistencia), color: 'violeta' },
							{ nombre: 'Repiten', valores: sem.map((s) => s.repeticion30), color: 'coral' }
						]}
					/>
					<Grafico
						titulo="Horas hasta completarse"
						tipo="linea"
						{etiquetas}
						formato={horas}
						series={[
							{
								nombre: 'Promedio',
								valores: sem.map((s) => s.horasHastaCompletar),
								color: 'violeta'
							}
						]}
					/>
					<Grafico
						titulo="Organizadores activos"
						{etiquetas}
						series={[
							{ nombre: 'Organizan', valores: sem.map((s) => s.organizadores), color: 'lima' }
						]}
					/>
				</div>
				<div class="tarjeta origenes">
					<span class="kl">Altas por origen · últimas {sem.length} semanas</span>
					{#each altas as a (a.o)}
						<div class="origen">
							<span class="o-n">{a.o}</span>
							<span class="o-barra"><span style:width="{Math.max(2, a.pct)}%"></span></span>
							<b>{a.v}</b>
						</div>
					{:else}
						<span class="s">Nadie se dio de alta en estas semanas.</span>
					{/each}
				</div>
			{/if}
		{:else if data.tab === 'usuarios'}
			<h1>Usuarios</h1>
			<form class="buscar" method="GET">
				<input type="hidden" name="tab" value="usuarios" />
				<input
					class="input"
					type="search"
					name="q"
					value={data.q}
					placeholder="Nombre, teléfono o id"
					aria-label="Buscar personas"
				/>
				<button class="gel gel-violeta b">Buscar</button>
			</form>
			<div class="lista">
				{#if data.usuarios.error}
					<p class="vacia tarjeta-plana">{data.usuarios.error}</p>
				{:else}
					{#each data.usuarios.datos ?? [] as u (u.id)}
						<form
							method="POST"
							action="?/usuario"
							class="tarjeta-plana item"
							use:enhance={confirmar(`¿Suspender a ${u.nombre}? No va a poder entrar a Caigo.`)}
						>
							<input type="hidden" name="id" value={u.id} />
							<div class="cab">
								<div class="persona">
									<Avatar nombre={u.nombre || '?'} foto={u.foto} tam={44} />
									<div>
										<span class="t">{pegar(u.nombre || 'Sin nombre')}</span>
										<span class="s"
											>{[u.ciudad, u.creado && `alta\u00a0${dia(u.creado)}`]
												.filter(Boolean)
												.join(' · ')}</span
										>
									</div>
								</div>
								<div class="chips-dato">
									{#if u.suspendido}<span class="chip-dato alerta">Suspendida</span>{/if}
									{#if u.verificada}<span class="chip-dato lima">Verificada</span>{/if}
									{#if u.dni && typeof u.dni === 'string' && DNI[u.dni]}
										<span class="chip-dato">{DNI[u.dni]}</span>
									{:else if u.dni === true}
										<span class="chip-dato">DNI verificado</span>
									{/if}
									{#if u.reportes}
										<a
											class="chip-dato alerta"
											href="?tab=reportes"
											title="Ver los reportes abiertos"
											>{u.reportes} {u.reportes === 1 ? 'reporte' : 'reportes'}</a
										>
									{/if}
								</div>
							</div>
							<span class="s id">{u.id}</span>
							{#if u.suspendido}
								<div class="acc">
									<button class="gel gel-lima b" name="decision" value="reactivar">Reactivar</button
									>
								</div>
							{:else}
								<input
									class="input"
									name="motivo"
									maxlength="300"
									placeholder="Motivo de la suspensión"
								/>
								<div class="acc">
									<button class="b rechazar" name="decision" value="suspender">Suspender</button>
									<a
										class="b gris enlace"
										href="?tab=semilla&org={u.id}&nombre={encodeURIComponent(u.nombre)}"
										>Plan semilla</a
									>
								</div>
							{/if}
						</form>
					{:else}
						<p class="vacia tarjeta-plana">
							{data.q ? `Nadie coincide con «${data.q}».` : 'Todavía no hay personas.'}
						</p>
					{/each}
				{/if}
			</div>
		{:else if data.tab === 'planes'}
			<h1>Planes</h1>
			<div class="filtros">
				<div class="seg">
					<a
						href="?tab=planes&cual=proximos{data.q ? `&q=${encodeURIComponent(data.q)}` : ''}"
						aria-current={data.cual === 'proximos' ? 'page' : undefined}>Próximos</a
					>
					<a
						href="?tab=planes&cual=reportados{data.q ? `&q=${encodeURIComponent(data.q)}` : ''}"
						aria-current={data.cual === 'reportados' ? 'page' : undefined}>Reportados</a
					>
				</div>
				<form class="buscar" method="GET">
					<input type="hidden" name="tab" value="planes" />
					<input type="hidden" name="cual" value={data.cual} />
					<input
						class="input"
						type="search"
						name="q"
						value={data.q}
						placeholder="Título, zona o lugar"
						aria-label="Buscar planes"
					/>
					<button class="gel gel-violeta b">Buscar</button>
				</form>
			</div>
			<div class="lista">
				{#if data.planes.error}
					<p class="vacia tarjeta-plana">{data.planes.error}</p>
				{:else}
					{#each data.planes.datos ?? [] as p (p.id)}
						<form
							method="POST"
							action="?/plan"
							class="tarjeta-plana item"
							use:enhance={confirmar(
								`¿Cancelar «${p.titulo}»? Avisamos a todos y devolvemos lo que pagaron.`
							)}
						>
							<input type="hidden" name="id" value={p.id} />
							<div class="cab">
								<div>
									<span class="t">{pegar(p.titulo)}</span>
									<span class="s"
										>{[
											CATEGORIAS[p.categoria] ?? p.categoria,
											cuandoCorto(p.empiezaEn),
											p.zona || p.ciudad
										]
											.filter(Boolean)
											.join(' · ')} · organiza&nbsp;{pegar(
											p.lugar?.nombre ?? p.organizador.nombre
										).replace(/ /g, '\u00a0')}</span
									>
								</div>
								<div class="chips-dato">
									{#if p.cancelado}<span class="chip-dato alerta">Cancelado</span>{/if}
									{#if p.reportes}<span class="chip-dato alerta"
											>{p.reportes} {p.reportes === 1 ? 'reporte' : 'reportes'}</span
										>{/if}
									<span class="chip-dato">Somos {p.somos} de {p.total}</span>
								</div>
							</div>
							<div class="datos">
								{#if p.lugarNombre}<span><b>Dónde:</b> {p.lugarNombre}</span>{/if}
								{#if p.descripcion}<span>{p.descripcion}</span>{/if}
								<a href="/p/{p.slug}" target="_blank" rel="noopener">Ver el plan</a>
							</div>
							{#if !p.cancelado}
								<input
									class="input"
									name="motivo"
									maxlength="300"
									required
									placeholder="Motivo (les llega a quienes iban)"
								/>
								<div class="acc">
									<button class="b rechazar" name="decision" value="cancelar">Cancelar plan</button>
								</div>
							{/if}
						</form>
					{:else}
						<p class="vacia tarjeta-plana">
							{data.q
								? `Ningún plan coincide con «${data.q}».`
								: data.cual === 'reportados'
									? 'No hay planes reportados.'
									: 'No hay planes próximos.'}
						</p>
					{/each}
				{/if}
			</div>
		{:else if data.tab === 'locales'}
			<h1>Lugares</h1>
			<div class="filtros">
				<div class="seg">
					{#each [['', 'Todos'], ['aprobado', 'Aprobados'], ['pendiente', 'Pendientes'], ['suspendido', 'Suspendidos'], ['rechazado', 'Rechazados']] as [k, l] (k)}
						<a
							href="?tab=locales{k ? `&estado=${k}` : ''}"
							aria-current={data.estado === k ? 'page' : undefined}>{l}</a
						>
					{/each}
				</div>
			</div>
			<div class="lista">
				{#if data.locales.error}
					<p class="vacia tarjeta-plana">{data.locales.error}</p>
				{:else}
					{#each data.locales.datos ?? [] as l (l.id)}
						<form
							method="POST"
							action="?/local"
							class="tarjeta-plana item"
							use:enhance={confirmar(`¿Suspender ${l.nombre}? Sus planes dejan de verse.`)}
						>
							<input type="hidden" name="id" value={l.id} />
							<div class="cab">
								<div>
									<span class="t">{pegar(l.nombre)}</span>
									<span class="s"
										>{pegar([l.rubro, l.ciudad, l.zona].filter(Boolean).join(' · '))}</span
									>
								</div>
								<span
									class="chip-dato"
									class:alerta={l.estado === 'suspendido' || l.estado === 'rechazado'}
									class:lima={l.estado === 'aprobado'}>{ESTADOS_LUGAR[l.estado] ?? l.estado}</span
								>
							</div>
							<div class="datos">
								<span><b>Dirección:</b> {l.direccion}</span>
								{#if l.motivo}<span><b>Motivo:</b> {l.motivo}</span>{/if}
								<span
									><b>Mercado Pago:</b>
									{l.mpConectado ? 'conectado' : 'sin conectar'} · <b>Alta:</b>
									{dia(l.creadoEn)}</span
								>
							</div>
							{#if l.estado === 'aprobado'}
								<input
									class="input"
									name="motivo"
									maxlength="300"
									placeholder="Motivo de la suspensión"
								/>
								<div class="acc">
									<button class="b rechazar" name="decision" value="suspender">Suspender</button>
								</div>
							{:else if l.estado === 'suspendido'}
								<div class="acc">
									<button class="gel gel-lima b" name="decision" value="reactivar">Reactivar</button
									>
								</div>
							{:else if l.estado === 'pendiente'}
								<div class="acc">
									<a class="b gris enlace" href="?tab=lugares">Revisar en pendientes</a>
								</div>
							{/if}
						</form>
					{:else}
						<p class="vacia tarjeta-plana">No hay lugares con ese estado.</p>
					{/each}
				{/if}
			</div>
		{:else if data.tab === 'semilla'}
			<h1>Planes semilla</h1>
			<p class="sub">
				Planes reales del equipo para que la app nunca se vea vacía. Salen como cualquier plan de
				quien&nbsp;organiza.
			</p>
			{#if semilla}
				<div class="tarjeta-plana item listo">
					<span class="t">Publicado: {semilla.titulo}</span>
					<div class="acc">
						<a
							class="gel gel-violeta b enlace"
							href="/p/{semilla.slug}"
							target="_blank"
							rel="noopener">Ver el plan</a
						>
					</div>
				</div>
			{/if}
			{#key data.org}
				<Semilla org={data.org} orgNombre={data.orgNombre} yo={data.yo} {resultado} />
			{/key}
		{:else if data.tab === 'lugares'}
			<h1>Lugares pendientes</h1>
			<div class="lista">
				{#each data.lugares as l (l.id)}
					<form method="POST" action="?/lugar" class="tarjeta-plana item" use:enhance={resultado}>
						<input type="hidden" name="id" value={l.id} />
						<div class="cab">
							<div>
								<span class="t">{pegar(l.nombre)}</span>
								<span class="s"
									>{pegar([l.rubro, l.ciudad, l.zona].filter(Boolean).join(' · '))}</span
								>
							</div>
							<span class="chip-dato">{fecha(l.creadoEn)}</span>
						</div>
						<div class="datos">
							<span><b>Dirección:</b> {l.direccion}</span>
							{#if l.instagram}<span><b>Instagram:</b> @{l.instagram}</span>{/if}
							{#if l.telefono}<span><b>Teléfono:</b> {l.telefono}</span>{/if}
							<a
								href="https://www.openstreetmap.org/?mlat={l.lat}&mlon={l.lng}#map=18/{l.lat}/{l.lng}"
								target="_blank"
								rel="noopener noreferrer">Ver en el mapa</a
							>
						</div>
						<input
							class="input"
							name="motivo"
							maxlength="200"
							placeholder="Motivo (si lo rechazás)"
						/>
						<div class="acc">
							<button class="gel gel-lima b" name="decision" value="aprobar">Aprobar</button>
							<button class="b rechazar" name="decision" value="rechazar">Rechazar</button>
						</div>
					</form>
				{:else}
					<p class="vacia tarjeta-plana">
						{data.lugaresError || 'No hay lugares esperando revisión.'}
					</p>
				{/each}
			</div>
		{:else if data.tab === 'dni'}
			<h1>DNI</h1>
			<div class="lista">
				{#each data.dni as d (d.id)}
					<form method="POST" action="?/dni" class="tarjeta-plana item" use:enhance={resultado}>
						<input type="hidden" name="id" value={d.id} />
						<div class="cab">
							<div>
								<span class="t">{d.nombrePerfil}{d.edadPerfil ? `, ${d.edadPerfil}` : ''}</span>
								<span class="s">Perfil · {fecha(d.creadaEn)}</span>
							</div>
							{#if d.repetido}<span class="chip-dato alerta">DNI usado en otra cuenta</span>{/if}
						</div>
						<div class="datos">
							<span><b>DNI:</b> {d.numero}</span>
							<span><b>Apellido y nombres:</b> {d.apellido}, {d.nombres}</span>
							<span><b>Sexo:</b> {d.sexo} · <b>Nacimiento:</b> {d.nacimiento.slice(0, 10)}</span>
						</div>
						<div class="fotos">
							{#each [['Frente', d.frenteId], ['Dorso', d.dorsoId], ['Selfie', d.selfieId], ['Selfie de ingreso', d.selfieIngresoId]] as [l, f] (l)}
								{#if f}
									<a href="/archivo/{f}" target="_blank" rel="noopener" class="foto">
										<img src="/archivo/{f}" alt={l} loading="lazy" />
										<span>{l}</span>
									</a>
								{/if}
							{/each}
						</div>
						<input
							class="input"
							name="motivo"
							maxlength="200"
							placeholder="Motivo (si lo rechazás)"
						/>
						<div class="acc">
							<button class="gel gel-lima b" name="decision" value="aprobar">Aprobar</button>
							<button class="b rechazar" name="decision" value="rechazar">Rechazar</button>
						</div>
					</form>
				{:else}
					<p class="vacia tarjeta-plana">{data.dniError || 'No hay DNI para revisar.'}</p>
				{/each}
			</div>
		{:else if data.tab === 'arrepentimientos'}
			<h1>Arrepentimientos</h1>
			<div class="lista">
				{#each data.arrepentimientos as a (a.id)}
					<form
						method="POST"
						action="?/arrepentimiento"
						class="tarjeta-plana item"
						use:enhance={resultado}
					>
						<input type="hidden" name="id" value={a.id} />
						<div class="cab">
							<div>
								<span class="t">{a.nombre}</span>
								<span class="s"
									><a href="mailto:{a.email}?subject=Tu pedido {a.codigo}">{a.email}</a> · {fecha(
										a.creadoEn
									)}</span
								>
							</div>
							<span class="chip-dato" class:alerta={!a.atendidoEn}>{a.codigo}</span>
						</div>
						<p class="detalle">{a.compra}</p>
						{#if a.atendidoEn}
							<p class="s">Atendido el {fecha(a.atendidoEn)}{a.nota ? ` · ${a.nota}` : ''}</p>
						{:else}
							<input
								class="input"
								name="nota"
								maxlength="300"
								placeholder="Qué se hizo: reembolso en Mercado Pago, por qué no"
							/>
							<div class="acc">
								<button class="gel gel-violeta b">Marcar atendido</button>
							</div>
						{/if}
					</form>
				{:else}
					<p class="vacia tarjeta-plana">
						{data.arrepentimientosError || 'No hay pedidos de arrepentimiento.'}
					</p>
				{/each}
			</div>
		{:else}
			<h1>Reportes</h1>
			<div class="lista">
				{#each data.reportes as r (r.id)}
					<form method="POST" action="?/reporte" class="tarjeta-plana item" use:enhance={resultado}>
						<input type="hidden" name="id" value={r.id} />
						<div class="cab">
							<div>
								<span class="t">{MOTIVOS[r.motivo] ?? r.motivo}</span>
								<span class="s"
									>{r.deNombre} reportó a {r.sobreNombre || 'un plan'}{r.planTitulo
										? ` · ${r.planTitulo}`
										: ''}</span
								>
							</div>
							<span class="chip-dato" class:alerta={r.sobreReportes > 1}
								>{r.sobreReportes} {r.sobreReportes === 1 ? 'reporte' : 'reportes'}</span
							>
						</div>
						{#if r.detalle}<p class="detalle">{r.detalle}</p>{/if}
						<input
							class="input"
							name="resolucion"
							maxlength="300"
							placeholder="Qué se hizo (queda registrado)"
						/>
						<div class="acc">
							<button class="gel gel-violeta b" name="decision" value="resolver">Resolver</button>
							{#if r.sobreId}
								<button class="b rechazar" name="decision" value="suspender"
									>Suspender a {r.sobreNombre}</button
								>
							{/if}
							<button class="b gris" name="decision" value="descartar">Descartar</button>
						</div>
					</form>
				{:else}
					<p class="vacia tarjeta-plana">{data.reportesError || 'No hay reportes abiertos.'}</p>
				{/each}
			</div>
		{/if}
	</main>
</div>

<style>
	.panel {
		min-height: 100vh;
		display: grid;
		grid-template-columns: 248px minmax(0, 1fr);
		background:
			url(/grain.png), radial-gradient(50% 50% at 100% 0%, #e3d9ff 0%, rgba(227, 217, 255, 0) 70%),
			#f3f0e9;
	}
	aside {
		position: sticky;
		top: 0;
		height: 100vh;
		padding: 28px 18px;
		display: flex;
		flex-direction: column;
		gap: 26px;
		background: url(/grain.png), radial-gradient(120% 60% at 0% 0%, #3b2f78 0%, #201d2b 70%);
		color: #faf9f6;
	}
	.marca {
		display: flex;
		flex-direction: column;
		gap: 6px;
		padding: 0 8px;
	}
	.caigo {
		font:
			700 40px/1 'Geist',
			sans-serif;
		letter-spacing: -0.045em;
		color: #faf9f6;
		text-decoration: none;
	}
	.para {
		font-size: 12px;
		font-weight: 800;
		letter-spacing: 1.5px;
		color: #c8f36b;
	}
	nav {
		display: flex;
		flex-direction: column;
		gap: 6px;
	}
	nav a {
		display: flex;
		align-items: center;
		gap: 12px;
		padding: 12px 14px;
		border-radius: 16px;
		font-size: 15px;
		font-weight: 700;
		color: #c9c4da;
		text-decoration: none;
	}
	nav a.on {
		background: rgba(255, 255, 255, 0.14);
		color: #ffffff;
		box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.15);
	}
	.n {
		margin-left: auto;
		padding: 3px 8px;
		border-radius: 9px;
		background: #ff7868;
		color: #24212d;
		font-size: 12px;
		font-weight: 800;
	}
	.abajo {
		margin-top: auto;
	}
	.salir {
		padding: 6px 14px;
		font-size: 13px;
		font-weight: 700;
		color: #a8a3bd;
	}
	main {
		min-width: 0;
		padding: 34px 44px 90px;
		max-width: 960px;
	}
	h1 {
		font:
			800 44px/1 'Geist',
			sans-serif;
		letter-spacing: -1.6px;
		margin-bottom: 24px;
	}
	.lista {
		display: flex;
		flex-direction: column;
		gap: 14px;
	}
	.item {
		display: flex;
		flex-direction: column;
		gap: 14px;
		padding: 20px 22px;
		border-radius: 24px;
	}
	.cab {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: 12px;
	}
	.cab > div {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.t {
		font:
			700 20px/1.15 'Geist',
			sans-serif;
	}
	.s {
		font-size: 13px;
		font-weight: 600;
		color: #5c586b;
	}
	.chip-dato {
		flex: none;
		padding: 6px 11px;
		border-radius: 11px;
		background: #f0ecff;
		font-size: 12px;
		font-weight: 800;
	}
	.chip-dato.alerta {
		background: #ffe1db;
		color: #9a2e1f;
	}
	.datos {
		display: flex;
		flex-direction: column;
		gap: 4px;
		font-size: 14px;
		color: #5c586b;
	}
	.datos b {
		color: #24212d;
	}
	.datos a {
		font-weight: 700;
	}
	.detalle {
		padding: 12px 14px;
		border-radius: 16px;
		background: #f5f2fc;
		font-size: 14px;
		line-height: 1.5;
	}
	.fotos {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
		gap: 10px;
	}
	.foto {
		display: flex;
		flex-direction: column;
		gap: 6px;
		font-size: 12px;
		font-weight: 800;
		color: #5c586b;
		text-decoration: none;
	}
	.foto img {
		width: 100%;
		aspect-ratio: 4 / 3;
		object-fit: cover;
		border-radius: 16px;
		background: #e4defa;
	}
	.acc {
		display: flex;
		gap: 10px;
		flex-wrap: wrap;
	}
	.b {
		height: 48px;
		padding: 0 20px;
		border-radius: 16px;
		font-size: 15px;
		font-weight: 800;
	}
	.rechazar {
		background: #ffe1db;
		color: #9a2e1f;
	}
	.gris {
		background: #f0ecff;
		color: #5b43c9;
	}
	.sub {
		margin: -12px 0 22px;
		font-size: 15px;
		font-weight: 600;
		color: #5c586b;
		max-width: 620px;
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
	.graficos {
		margin-top: 16px;
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(min(100%, 380px), 1fr));
		gap: 16px;
	}
	.origenes {
		margin-top: 16px;
		display: flex;
		flex-direction: column;
		gap: 12px;
		padding: 20px 22px;
	}
	.origen {
		display: grid;
		grid-template-columns: minmax(90px, 160px) minmax(0, 1fr) auto;
		align-items: center;
		gap: 12px;
		font-size: 14px;
		font-weight: 700;
	}
	.o-n {
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}
	.o-barra {
		height: 12px;
		border-radius: 6px;
		background: #f0ecff;
		overflow: hidden;
	}
	.o-barra span {
		display: block;
		height: 100%;
		border-radius: 6px;
		background: linear-gradient(180deg, #b3a3ff 0%, #8a70ff 30%, #7456f1 62%, #5f41e0 100%);
	}
	.origen b {
		min-width: 28px;
		text-align: right;
		font-weight: 800;
	}
	.filtros {
		display: flex;
		flex-wrap: wrap;
		gap: 12px;
		align-items: center;
		margin-bottom: 16px;
	}
	.filtros .seg {
		max-width: 100%;
		overflow-x: auto;
	}
	.filtros .buscar {
		flex: 1;
		min-width: min(100%, 280px);
		margin: 0;
	}
	.buscar {
		display: flex;
		gap: 10px;
		margin-bottom: 16px;
	}
	.buscar .b {
		flex: none;
		height: 52px;
	}
	.persona {
		display: flex;
		flex-direction: row !important;
		align-items: center;
		gap: 12px !important;
	}
	.persona > div {
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.cab > .chips-dato {
		flex-direction: row;
	}
	.chips-dato {
		display: flex;
		flex-wrap: wrap;
		justify-content: flex-end;
		gap: 6px;
	}
	.chip-dato.lima {
		background: #c8f36b;
	}
	a.chip-dato {
		color: inherit;
		text-decoration: none;
	}
	.id {
		font-size: 12px;
		color: #6b6780;
		font-variant-numeric: tabular-nums;
		word-break: break-all;
	}
	.enlace {
		display: inline-flex;
		align-items: center;
		text-decoration: none;
	}
	.listo {
		margin-bottom: 16px;
		background: #f7ffe3;
	}
	.vacia {
		padding: 28px 22px;
		border-radius: 24px;
		font-size: 15px;
		font-weight: 600;
		color: #5c586b;
	}
	@media (max-width: 900px) {
		.panel {
			grid-template-columns: minmax(0, 1fr);
		}
		aside {
			position: static;
			height: auto;
			padding: 18px 16px;
			gap: 14px;
		}
		.caigo {
			font-size: 30px;
		}
		nav {
			flex-direction: row;
			margin: 0 -16px;
			padding: 0 16px 2px;
			overflow-x: auto;
			scrollbar-width: none;
		}
		nav a {
			flex: none;
			padding: 10px 12px;
			white-space: nowrap;
		}
		.abajo {
			display: none;
		}
		main {
			padding: 22px 16px 60px;
		}
		h1 {
			font-size: 34px;
		}
		.item {
			padding: 18px 16px;
		}
		.cab {
			flex-wrap: wrap;
		}
	}
</style>
