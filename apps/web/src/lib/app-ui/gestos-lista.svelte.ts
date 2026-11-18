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

	/** «Caigo». Devuelve true si quedó adentro (o pendiente) sin salir de la lista. */
	async caigo(p: Plan): Promise<boolean> {
		if (!sumaDirecto(p)) {
			await goto(`/app/plan/${p.id}`);
			return false;
		}
		const r = await gesto(p.id, 'caigo');
		if (r.ir) {
			await goto(r.ir);
			return false;
		}
		if (r.error) {
			avisar(r.error);
			return false;
		}
		if (r.estado === 'aceptada') {
			this.festejo = p;
			this.festejoAbierto = true;
		} else if (r.estado === 'pendiente') {
			avisar(`Le avisamos a ${primerNombre(p.organizador.nombre)}.`);
		}
		invalidateAll();
		return true;
	}

	/** «No me interesa». */
	async noMeInteresa(p: Plan) {
		this.ocultos.add(p.id);
		clearTimeout(this.#t);
		this.deshacer = { plan: p, texto: `Ocultaste ${p.titulo}` };
		this.#t = setTimeout(() => (this.deshacer = null), 5000);
		const r = await gesto(p.id, 'ocultar');
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
