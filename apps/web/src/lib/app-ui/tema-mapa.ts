/**
 * Mapa en modo oscuro: el mismo estilo de OpenFreeMap que usa Android. En claro sigue el de siempre
 * (PUBLIC_MAPA_ESTILO o las teselas raster de OpenStreetMap).
 */
export const ESTILO_OSCURO = 'https://tiles.openfreemap.org/styles/dark';

/** El tema de /app (cookie caigo_tema, en data-tema del lienzo) más lo que dice el sistema. */
export function esOscuro(tema: string | null | undefined, sistemaOscuro: boolean): boolean {
	return tema === 'oscuro' || (tema === 'sistema' && sistemaOscuro);
}

/**
 * Sigue el tema que le toca a `el` (el [data-tema] más cercano y prefers-color-scheme) y llama a
 * `cb` cada vez que cambia. Devuelve si arranca oscuro y la función para dejar de seguirlo.
 */
export function seguirTema(el: Element, cb: (oscuro: boolean) => void): [boolean, () => void] {
	const lienzo = el.closest('[data-tema]');
	const mq = window.matchMedia('(prefers-color-scheme: dark)');
	const ahora = () => esOscuro(lienzo?.getAttribute('data-tema'), mq.matches);
	let ultimo = ahora();
	const revisar = () => {
		const o = ahora();
		if (o !== ultimo) cb((ultimo = o));
	};
	mq.addEventListener('change', revisar);
	const mo = lienzo ? new MutationObserver(revisar) : null;
	if (lienzo) mo!.observe(lienzo, { attributes: true, attributeFilter: ['data-tema'] });
	return [
		ultimo,
		() => {
			mq.removeEventListener('change', revisar);
			mo?.disconnect();
		}
	];
}
