/** El toast del handoff: abajo al centro, 2,6 s. */
export const toast = $state({ texto: '' });
let t: ReturnType<typeof setTimeout> | undefined;

export function avisar(texto: string) {
	clearTimeout(t);
	toast.texto = texto;
	t = setTimeout(() => (toast.texto = ''), 2600);
}
