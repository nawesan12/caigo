import { goto, invalidateAll } from '$app/navigation';
import { SvelteSet } from 'svelte/reactivity';
import { avisar } from '#lib/toast.svelte.ts';
import { primerNombre } from '#lib/plan.ts';
import { sumaDirecto } from '#lib/gestos.ts';
import { gesto } from './gesto.ts';
import type { Plan } from '#lib/tipos.ts';

/**
 * Lo que hacen los gestos de una lista (Ahora, Explorar y «Decidí rápido»): «Caigo» suma directo
 * si no hay nada que confirmar (y festeja) o abre el detalle; «No me interesa» lo oculta por hoy con
 * «Deshacer» durante 5 s.
 */
export class GestosLista {
	ocultos = new SvelteSet<string>();
	deshacer = $state<{ plan: Plan; texto: string } | null>(null);
	festejo = $state<Plan | null>(null);
	festejoAbierto = $state(false);
	#t: ReturnType<typeof setTimeout> | undefined;

	visibles = (ps: Plan[]) => ps.filter((p) => !this.ocultos.has(p.id));

	/**
	 * «Sumarme» con el gesto. Devuelve el estado si quedó adentro (o pendiente) sin salir de la lista.
	 * `festejar`: en la lista, el festejo a pantalla completa; en «Decidí rápido», no (el mazo marca
	 * «Adentro» en la carta y resume al final: nada de un pop-up por plan).
	 */
	async caigo(p: Plan, festejar = true): Promise<'aceptada' | 'pendiente' | null> {
		if (!sumaDirecto(p)) {
			await goto(`/app/plan/${p.id}`);
			return null;
		}
		const r = await gesto(p.id, 'caigo');
		if (r.ir) {
			await goto(r.ir);
			return null;
		}
		if (r.error) {
			avisar(r.error);
			return null;
		}
		if (r.estado === 'aceptada' && festejar) {
			this.festejo = p;
			this.festejoAbierto = true;
		} else if (r.estado === 'pendiente') {
			avisar(`Le avisamos a ${primerNombre(p.organizador.nombre)}.`);
		}
		invalidateAll();
		return r.estado === 'aceptada' || r.estado === 'pendiente' ? r.estado : null;
	}

	/** «No me interesa». */
	async noMeInteresa(p: Plan) {
		this.ocultos.add(p.id);
		clearTimeout(this.#t);
		this.deshacer = { plan: p, texto: `Ocultaste ${p.titulo}` };
		this.#t = setTimeout(() => (this.deshacer = null), 5000);
		const r = await gesto(p.id, 'ocultar');
		// Sin cuenta, «No me interesa» queda en esta pestaña (no hay dónde guardarlo).
		if (r.local) return;
		if (r.ir) {
			this.ocultos.delete(p.id);
			this.deshacer = null;
			await goto(r.ir);
		} else if (r.error) {
			this.ocultos.delete(p.id);
			this.deshacer = null;
			avisar(r.error);
		}
	}

	async volver() {
		const d = this.deshacer;
		if (!d) return;
		clearTimeout(this.#t);
		this.deshacer = null;
		this.ocultos.delete(d.plan.id);
		const r = await gesto(d.plan.id, 'mostrar');
		if (r.error) avisar(r.error);
	}
}
