import type { PageServerLoad } from './$types';

/** Recuperar la contraseña: el mail, si vino de /ingresar. Ya con sesión, no hace falta. */
export const load: PageServerLoad = ({ url }) => ({
	email: (url.searchParams.get('email') ?? '').slice(0, 200)
});
