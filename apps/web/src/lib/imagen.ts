/**
 * Fotos en el navegador: cualquier foto que el navegador pueda abrir (incluida la HEIC del iPhone en
 * Safari) pasa a JPEG por un canvas, con el lado mayor acotado y por debajo de un peso máximo (el
 * servidor de la web acepta cuerpos chicos y la API no recibe HEIC).
 */

/** El tamaño final: el lado mayor en `max` como mucho, sin agrandar. */
export function escala(ancho: number, alto: number, max: number): { ancho: number; alto: number } {
	const k = Math.min(1, max / Math.max(ancho, alto, 1));
	return { ancho: Math.round(ancho * k), alto: Math.round(alto * k) };
}

function abrir(archivo: Blob): Promise<HTMLImageElement> {
	return new Promise((ok, mal) => {
		const url = URL.createObjectURL(archivo);
		const img = new Image();
		img.onload = () => {
			URL.revokeObjectURL(url);
			ok(img);
		};
		img.onerror = () => {
			URL.revokeObjectURL(url);
			mal(new Error('No pudimos abrir esa foto. Probá con otra (JPG o PNG).'));
		};
		img.src = url;
	});
}

/** La foto como JPEG: lado mayor `max` (baja si hace falta) y menos de `pesoMax` bytes. */
export async function aJpeg(archivo: Blob, max = 1600, pesoMax = 450_000): Promise<Blob> {
	const img = await abrir(archivo);
	let lado = max;
	for (let i = 0; i < 5; i++) {
		const t = escala(img.naturalWidth, img.naturalHeight, lado);
		const c = document.createElement('canvas');
		c.width = t.ancho;
		c.height = t.alto;
		const ctx = c.getContext('2d');
		if (!ctx) break;
		ctx.drawImage(img, 0, 0, t.ancho, t.alto);
		const b = await new Promise<Blob | null>((r) => c.toBlob(r, 'image/jpeg', 0.85));
		if (b && (b.size <= pesoMax || i === 4)) return b;
		lado = Math.round(lado * 0.8);
	}
	throw new Error('No pudimos preparar esa foto. Probá con otra.');
}
