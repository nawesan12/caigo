import type { Plan } from '../tipos.ts';

/** Un plan público como lo devuelve GET /v1/publico/planes/{slug} (para los tests). */
export function planDePrueba(cambios: Partial<Plan> = {}): Plan {
	return {
		id: 'd43b4be1-e44f-44b1-b6a6-e34eee1d029b',
		slug: 'previa-al-atardecer-en-playa-grande-6e23',
		titulo: 'Previa al atardecer en Playa Grande',
		categoria: 'previa',
		descripcion: 'Llevamos parlante y algo para tomar.',
		empiezaEn: '2026-11-01T21:30:00Z',
		duracionMin: 150,
		ciudad: 'Mar del Plata',
		zona: 'Playa Grande',
		lugarNombre: 'Playa Grande',
		lat: -38.028,
		lng: -57.531,
		somos: 4,
		total: 8,
		faltan: 4,
		ingreso: 'libre',
		visibilidad: 'publico',
		soloVerificados: true,
		ocultarDireccion: true,
		genero: 'cualquiera',
		nivel: '',
		foto: 'https://caigo.com.ar/fotos/playa.jpg',
		cancelado: false,
		organizador: { id: 'o1', nombre: 'Nahuel Santillán', verificada: true },
		van: [{ id: 'o1', nombre: 'Nahuel Santillán', verificada: true, organizador: true }],
		cobro: 'gratis',
		aceptaGrupos: true,
		...cambios
	};
}
