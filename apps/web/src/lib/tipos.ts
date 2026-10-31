/** Lo que devuelve la API (apps/api/internal/dominio/*). */

export type PersonaCorta = {
	id: string;
	nombre: string;
	foto?: string;
	edad?: number;
	verificada: boolean;
	asistencia?: { fue: number; de: number };
	enMiGente?: boolean;
	organizador?: boolean;
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
	lugar?: { id: string; slug: string; nombre: string; zona: string; foto?: string };
	cobro: 'gratis' | 'entrada' | 'sena';
	precioCentavos?: number;
	senaPct?: number;
	cargoCentavos?: number;
	incluye?: string;
	cancelacion?: string;
	beneficio?: string;
	aceptaGrupos: boolean;
};

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
};

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
};

export type DNIPendiente = {
	id: string;
	usuarioId: string;
	nombrePerfil: string;
	edadPerfil?: number;
	numero: string;
	apellido: string;
	nombres: string;
	sexo: string;
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
