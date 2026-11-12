import { redirect } from '@sveltejs/kit';
import type { PageServerLoad } from './$types';

/**
 * Borrar la cuenta vive en /borrar-cuenta (pública: es la URL de Google Play, se lee sin entrar y,
 * con sesión, tiene el botón). Ésta queda por los links viejos de las apps y del perfil.
 */
export const load: PageServerLoad = () => redirect(303, '/borrar-cuenta');
