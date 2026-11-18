/** Lo que devuelve la API (apps/api/internal/dominio/*). */

import type { Moderacion } from './moderacion.ts';

export type PersonaCorta = {
	id: string;
	nombre: string;
	foto?: string;
	edad?: number;
	verificada: boolean;
	asistencia?: { fue: number; de: number };
	enMiGente?: boolean;
	organizador?: boolean;
	/** Ya hizo check-in en el plan. */
	llego?: boolean;
	/** Promedio de las caritas de sus planes (sólo de quien organiza, con 3 o más). */
	caritas?: { promedio: number; cantidad: number };
	/** «Anfitrión»: sus planes se hacen (sólo en quien organiza). */
	anfitrion?: boolean;
	/** Faltazos en 30 días, si son 2 o más (sólo en las solicitudes pendientes). */
	faltazos?: number;
};

/** Lo de quien mira en un plan donde está (día del plan, después, lista de espera). */
export type Mio = {
	llegue: boolean;
	confirme: boolean;
	carita?: number;
	todoBien: boolean;
	fotos: number;
	espera?: { estado: 'esperando' | 'avisado'; posicion: number; venceEn?: string };
};

/** Contacto de confianza (perfil): nombre y teléfono o mail. */
export type Contacto = { nombre: string; contacto: string };

/** GET /v1/planes/{id}/todo-bien. */
export type TodoBien = {
	activo: boolean;
	contacto?: Contacto;
	avisaEn?: string;
	avisado: boolean;
	contactos: Contacto[];
};

/** GET /v1/planes/{id}/fotos. */
export type FotoPlan = {
	id: string;
	url: string;
	autor: PersonaCorta;
	mia: boolean;
	creadaEn: string;
};

/** GET /v1/semana: «Esta semana en <ciudad>». */
export type Semana = {
	ciudad: string;
	desde: string;
	hasta: string;
	total: number;
	aliados: number;
	deTuGente: number;
	dias: { dia: string; cantidad: number }[];
	planes: Plan[];
};

/** Premios por ir (GET /v1/yo y /v1/personas/{id}). Racha y comodín, sólo en el propio. */
export type Logros = {
	nivel: string;
	planes: number;
	siguiente?: string;
	faltan?: number;
	racha?: number;
	comodin?: boolean;
	insignias: { id: string; nombre: string; detalle: string }[];
	anfitrion: boolean;
	ocultas?: boolean;
};

/** GET /v1/yo/invitar («Traé a alguien»). */
export type Invitacion = {
	codigo: string;
	link: string;
	invitados: number;
	premiados: number;
	puedeUsarCodigo: boolean;
};

export type Plan = {
	id: string;
	slug: string;
	titulo: string;
	categoria: string;
	descripcion: string;
	empiezaEn: string;
	duracionMin: number;
	recurrente?: boolean;
	ciudad: string;
	zona: string;
	lugarNombre: string;
	lat?: number;
	lng?: number;
	distanciaM?: number;
	visibilidad?: string;
	ocultarDireccion?: boolean;
	edadMin?: number;
	edadMax?: number;
	direccion?: { texto: string; lat: number; lng: number };
	pendientes?: number;
	somos: number;
	total: number;
	faltan: number;
	ingreso: string;
	soloVerificados: boolean;
	genero: string;
	nivel: string;
	costoCentavos?: number;
	foto: string;
	cancelado: boolean;
	organizador: PersonaCorta;
	van: PersonaCorta[];
	miEstado?: string;
	miCantidad?: number;
	lugar?: {
		id: string;
		slug: string;
		nombre: string;
		zona: string;
		foto?: string;
		/** Beneficio por nivel (Habitué o más). */
		beneficioNivel?: string;
	};
	cobro: 'gratis' | 'entrada' | 'sena';
	precioCentavos?: number;
	senaPct?: number;
	cargoCentavos?: number;
	incluye?: string;
	cancelacion?: string;
	beneficio?: string;
	aceptaGrupos: boolean;
	/** Sólo para quien organiza: el equipo lo revisa o lo ocultó. */
	moderacion?: Moderacion;
	/** La línea de confianza de la tarjeta (ver lineaConfianza). Las respuestas viejas no la traen. */
	confianza?: Confianza;
	/** Lo de quien mira, si está en el plan o en la lista de espera. */
	mio?: Mio;
};

/** `confianza` de un plan (API, 18/11). `chicas` sólo en planes «Solo mujeres» y desde 2. */
export type Confianza = { todosVerificados: boolean; soloChicas: boolean; chicas?: number };

export type Lugar = {
	id: string;
	slug: string;
	nombre: string;
	rubro: string;
	ciudad: string;
	zona: string;
	direccion: string;
	lat: number;
	lng: number;
	foto: string;
	instagram: string;
	telefono: string;
	estado: 'pendiente' | 'aprobado' | 'rechazado' | 'suspendido';
	motivo?: string;
	socioActivo: boolean;
	socioVisitas: number;
	socioBeneficio: string;
	/** Beneficio por nivel: para quien es Habitué o más, uno por día. */
	beneficioNivel?: string;
	rol?: string;
	mpConectado: boolean;
	mpDesde?: string;
	creadoEn: string;
};

export type PlanDelLugar = {
	id: string;
	slug: string;
	titulo: string;
	categoria: string;
	empiezaEn: string;
	foto: string;
	cupo: number;
	confirmados: number;
	cobro: 'gratis' | 'entrada' | 'sena';
	/** Precio por persona y % de seña (sólo con cobro). */
	precioCentavos?: number;
	senaPct?: number;
	beneficio?: string;
	duracionMin: number;
	entradas: number;
	cobradoCentavos: number;
	cancelado: boolean;
	link: string;
};

export type Asistente = {
	nombre: string;
	foto: string;
	cantidad: number;
	pago: 'gratis' | 'pago' | 'sena';
	codigo?: string;
	saldoCentavos?: number;
	checkin?: string;
};

export type Validacion = {
	nombre: string;
	cantidad: number;
	plan: string;
	saldoCentavos: number;
	beneficio?: string;
	/** Beneficio por nivel que le toca hoy y su nivel (ya quedó canjeado). */
	beneficioNivel?: string;
	nivel?: string;
	repetida: boolean;
};

export type Placa = {
	id: string;
	nombre: string;
	uso: 'puerta' | 'barra';
	sun: boolean;
	activa: boolean;
	url: string;
	claveSun?: string;
};

/** Pedido de placas Caigo Tap (POST /v1/lugares/{id}/placas/pedido). Los campos lugar* y
 * pidioNombre vienen sólo en la cola del equipo. */
export type PedidoPlacas = {
	id: string;
	cantidad: number;
	tipo: 'comun' | 'ntag424';
	nota: string;
	creadoEn: string;
	atendidoEn?: string;
	respuesta?: string;
	lugarId?: string;
	lugarNombre?: string;
	lugarCiudad?: string;
	lugarDireccion?: string;
	lugarTelefono?: string;
	lugarInstagram?: string;
	pidioNombre?: string;
};

export type CobroPlan = {
	planId: string;
	titulo: string;
	empiezaEn: string;
	entradas: number;
	cobradoCentavos: number;
	estado: 'en_venta' | 'cobrado';
};

export type EnVivo = {
	quien: string;
	plan: string;
	empiezaEn: string;
	terminaEn: string;
	lugar: string;
	direccion: string;
	lat: number;
	lng: number;
	con: string[];
	llego: boolean;
};

export type ResultadoTap = {
	lugar: string;
	uso: string;
	plan?: {
		id: string;
		titulo: string;
		beneficio?: string;
		saldoCentavos: number;
		repetido: boolean;
	};
	socio?: {
		visitas: number;
		meta: number;
		beneficio: string;
		gano: boolean;
		canjeo: boolean;
		sumo: boolean;
	};
};

export type EstadoPlus = {
	activo: boolean;
	plan?: string;
	origen?: string;
	vence?: string;
	renueva: boolean;
	sumadasHoy: number;
	limite: number;
	reiniciaEn: string;
	encantasHoy?: number;
	limiteEncantas?: number;
	destacarDesde?: string;
};

export type Yo = {
	id: string;
	nombre: string;
	edad?: number;
	zona: string;
	ciudad: string;
	intereses: string[];
	fotos: string[];
	selfie: boolean;
	verificada: boolean;
	asistencia: { fue: number; de: number };
	planes: number;
	organizados: number;
	miGente: number;
	equipo?: boolean;
	logros?: Logros;
};

/** GET /v1/verificacion. `dni`: ninguna | pendiente | aprobada | rechazada (con `dniMotivo`). */
export type Verificacion = { telefono?: string; selfie: boolean; dni: string; dniMotivo?: string };

export type Solicitud = {
	id: string;
	persona: PersonaCorta;
	cantidad: number;
	mensaje: string;
	estado: string;
	creadaEn: string;
};

export type Mensaje = {
	id: number;
	planId: string;
	autor?: { id: string; nombre: string; foto?: string };
	texto: string;
	creadoEn: string;
	/** Sólo en los propios. */
	moderacion?: Moderacion;
};

export type DNIPendiente = {
	id: string;
	usuarioId: string;
	nombrePerfil: string;
	edadPerfil?: number;
	nacimiento: string;
	frenteId: string;
	dorsoId: string;
	selfieId: string;
	selfieIngresoId?: string;
	repetido: boolean;
	creadaEn: string;
};

export type Reporte = {
	id: string;
	de: string;
	deNombre: string;
	sobreId?: string;
	sobreNombre?: string;
	sobreReportes: number;
	planId?: string;
	planTitulo?: string;
	motivo: string;
	detalle: string;
	creadoEn: string;
};

/** Backoffice: un ítem de la cola de moderación (GET /v1/equipo/moderacion). */
export type ModeracionEquipo = {
	id: string;
	tipo: 'plan' | 'mensaje' | 'privado';
	objetoId?: string;
	planId?: string;
	planTitulo?: string;
	autor: { id: string; nombre: string };
	autorReportes: number;
	texto: string;
	origen: 'filtro' | 'ia' | 'sin_ia';
	categoria: string;
	motivo: string;
	/** `bloqueado`: el filtro lo frenó y no se publicó, pero el equipo lo tiene que ver. */
	estado: 'revision' | 'oculto' | 'bloqueado';
	creadaEn: string;
};

/** Pedido del botón de arrepentimiento (Resolución 424/2020). */
export type Arrepentimiento = {
	id: string;
	codigo: string;
	nombre: string;
	email: string;
	compra: string;
	creadoEn: string;
	atendidoEn?: string;
	nota?: string;
};

/** Backoffice: una persona en /v1/equipo/usuarios (contrato §7). */
export type UsuarioEquipo = {
	id: string;
	nombre: string;
	foto?: string;
	ciudad: string;
	creado: string;
	verificada: boolean;
	/** Estado del DNI ("", "ninguna", "pendiente", "aprobada", "rechazada") o, si la API lo manda así, true/false. */
	dni: string | boolean;
	/** Suspendida: true o la fecha. */
	suspendido?: boolean | string;
	reportes: number;
};

/** Backoffice: un plan en /v1/equipo/planes (el Plan de siempre, con sus reportes). */
export type PlanEquipo = Plan & { reportes?: number };

/** Backoffice: una semana de /v1/equipo/metricas (las últimas 8, de la más vieja a la más nueva). */
export type SemanaMetricas = {
	/** Lunes de la semana (YYYY-MM-DD). */
	semana: string;
	publicados: number;
	/** Llegaron al cupo y empezaron. */
	completados: number;
	/** Check-ins / aceptados, de 0 a 1. */
	asistencia: number;
	/** Horas promedio desde que se publica hasta que se completa. */
	horasHastaCompletar: number;
	/** De 0 a 1: quienes volvieron a un plan dentro de los 30 días. */
	repeticion30: number;
	organizadores: number;
	/** Altas por origen (p:<slug>, utm:instagram, "" = directo). */
	altas: Record<string, number>;
};

/** Perfil propio completo (GET /v1/yo): lo que suma el contrato (§1, §3, §4) llega opcional. */
export type Prompt = { p: string; r: string };
export type YoCompleto = Yo & {
	/** Lo que le falta al perfil (GET /v1/yo): edad, ciudad, zona, bio, fotos, intereses. */
	faltan?: string[];
	bio: string;
	trabajo: string;
	intenciones: string[];
	prompts: Prompt[];
	telefono?: string;
	contactoConfianza?: string;
	contactosConfianza?: Contacto[];
	avisos?: { planesCerca: boolean; tuGente: boolean; semana?: boolean };
	origen?: string;
};

/** GET /v1/bloqueos (contrato §4). */
export type Bloqueado = { id: string; nombre: string; foto?: string };

/** Panel: un asistente con su id (contrato §6, para el check-in de planes gratis). */
export type AsistenteConId = Asistente & { usuarioId?: string };

/** Panel: lo que se edita de un plan del lugar (PATCH /v1/lugares/{lugar}/planes/{plan}, §2). */
export type EdicionPlanLugar = {
	id: string;
	titulo: string;
	categoria: string;
	descripcion: string;
	foto: string;
	empiezaEn: string;
	duracionMin: number;
	cupo: number;
	confirmados: number;
	entradas: number;
	aceptaGrupos: boolean;
	recurrente: boolean;
	cobro: 'gratis' | 'entrada' | 'sena';
	precioCentavos?: number;
	senaPct?: number;
	incluye: string;
	cancelacion: string;
	beneficio: string;
	quien: 'cualquiera' | 'verificados' | 'mujeres';
};

/** Un privado entre dos personas (GET/POST /v1/privados/{id}). */
export type Privado = {
	id: number;
	deId: string;
	aId: string;
	texto: string;
	creadoEn: string;
	leidoEn?: string;
	/** Sólo en los propios. */
	moderacion?: Moderacion;
};

/** GET /v1/privados: el último privado con cada persona, la más reciente primero. */
export type Conversacion = {
	con: { id: string; nombre: string; foto?: string };
	ultimo: Privado;
	sinLeer: number;
};

/** GET /v1/personas/{id}: el perfil de otra persona (sin lo privado). */
export type PerfilPersona = {
	id: string;
	nombre: string;
	edad?: number;
	zona: string;
	ciudad: string;
	bio: string;
	trabajo: string;
	intereses: string[];
	prompts: Prompt[];
	fotos: string[];
	verificada: boolean;
	asistencia: { fue: number; de: number };
	planes: number;
	organizados: number;
	enMiGente?: boolean;
	compartidos?: number;
	logros?: Logros;
	proximos?: { id: string; titulo: string; categoria: string; empiezaEn: string; foto?: string }[];
};

/** GET /v1/gente: la gente que sumé (el mismo perfil que /v1/personas/{id}). */
export type DeMiGente = PerfilPersona;
