/**
 * El muro de Caigo Plus: las filas de la tabla Gratis / Plus y el texto chico de la suscripción, iguales en
 * la web, iOS (Nucleo/Logica.swift › PlusTabla) y Android (nucleo/Logica.kt › PlusTabla).
 */

/** (fila, gratis, plus). «✓» es un tilde, «—» que no lo tiene. Lo de seguridad no va: es gratis siempre. */
export function filasPlus(limite = 3, encantasGratis = 1): [string, string, string][] {
	return [
		['Sumadas en Gente', `${limite} por día`, 'Sin límite'],
		['Ver quién te sumó', '—', '✓'],
		['Me encanta en Citas', `${encantasGratis} por día`, '5 por día'],
		['Tu plan destacado', '—', '1 por semana'],
		['Filtros de edad, cupo y precio', '—', '✓']
	];
}

/** Lo de seguridad, debajo de la tabla. */
export const SEGURIDAD_GRATIS =
	'Lo de seguridad es gratis siempre: todos con DNI verificado y el filtro «Sólo chicas».';

/**
 * «$X por semana, se renueva sola. Cancelás cuando quieras desde Mercado Pago.» Con prueba gratis (sólo el
 * mes): «Gratis 7 días, después $X por mes…».
 */
export function textoFino(
	plan: string,
	precio: string,
	diasGratis: number,
	tienda: string
): string {
	const nb = ' ';
	const periodo =
		plan === 'semana'
			? `${precio} por${nb}semana`
			: plan === 'temporada'
				? `${precio} cada 3${nb}meses`
				: `${precio} por${nb}mes`;
	const inicio =
		plan === 'mes' && diasGratis > 0
			? `Gratis ${diasGratis}${nb}días, después ${periodo}`
			: periodo;
	return `${inicio}, se renueva sola. Cancelás cuando quieras desde${nb}${tienda.replaceAll(' ', nb)}.`;
}
