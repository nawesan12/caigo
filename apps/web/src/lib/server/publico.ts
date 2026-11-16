import { primerNombre } from '#lib/plan.ts';
import type { Plan, PersonaCorta } from '#lib/tipos.ts';

/**
 * Lo que de un plan llega a quien mira sin sesión (el JSON de hidratación de /p/<slug> y de /app sin
 * cuenta): de quienes van y de quien organiza, sólo el primer nombre y la foto (y si está
 * verificada); nada de apellido, edad, ids ni historial de cada uno.
 */
function persona(p: PersonaCorta, i: number, organizador: boolean): PersonaCorta {
	return {
		// Un id falso (sólo para las listas con clave): el de verdad no sale.
		id: `${organizador ? 'o' : 'p'}${i}`,
		nombre: primerNombre(p.nombre),
		foto: p.foto,
		verificada: p.verificada,
		...(p.organizador ? { organizador: true } : {})
	};
}

export function planPublico(p: Plan): Plan {
	const org = p.organizador;
	return {
		...p,
		organizador: {
			...persona(org, 0, true),
			// La reputación del que organiza («Fue a 23 de 24 planes») se ve en el detalle.
			...(org.asistencia ? { asistencia: org.asistencia } : {})
		},
		van: (p.van ?? []).map((v, i) => persona(v, i, false))
	};
}
