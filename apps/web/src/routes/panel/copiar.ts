/**
 * Copia un texto al portapapeles. `navigator.clipboard` sólo anda en contextos seguros y con la
 * pestaña enfocada; si no, se usa un textarea con execCommand. Devuelve si se pudo.
 */
export async function copiarTexto(t: string): Promise<boolean> {
	try {
		if (navigator.clipboard && window.isSecureContext) {
			await navigator.clipboard.writeText(t);
			return true;
		}
	} catch {
		/* sigue con el respaldo */
	}
	const ta = document.createElement('textarea');
	ta.value = t;
	ta.setAttribute('readonly', '');
	ta.style.position = 'fixed';
	ta.style.opacity = '0';
	document.body.appendChild(ta);
	ta.select();
	let ok: boolean;
	try {
		ok = document.execCommand('copy');
	} catch {
		ok = false;
	}
	ta.remove();
	return ok;
}
