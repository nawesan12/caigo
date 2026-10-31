<script lang="ts">
	import TarjetaPlan from '#lib/ui/TarjetaPlan.svelte';
	import { CATEGORIAS } from '#lib/plan.ts';
	import { cuandoCorto } from '#lib/fechas.ts';
	import { distancia, TINTES } from '#lib/app.ts';
	import type { Plan } from '#lib/tipos.ts';

	/** Un plan de la API en la tarjeta de "Ahora · lista" (02). */
	let { plan }: { plan: Plan } = $props();

	const donde = $derived(
		[
			plan.lugarNombre || (plan.zona ? `Zona ${plan.zona}` : plan.ciudad),
			distancia(plan.distanciaM)
		]
			.filter(Boolean)
			.join(' · ')
	);
	const accion = $derived(
		plan.miEstado === 'aceptada' || plan.miEstado === 'organizador' || plan.faltan <= 0
			? 'Ver plan'
			: plan.lugar && plan.cobro !== 'gratis'
				? 'Entrada'
				: 'Sumarme'
	);
</script>

<TarjetaPlan
	foto={plan.foto || undefined}
	tinte={TINTES[plan.categoria] ?? '#5B43C9'}
	categoria={CATEGORIAS[plan.categoria] ?? plan.categoria}
	cuando={cuandoCorto(plan.empiezaEn)}
	titulo={plan.titulo}
	{donde}
	faltan={plan.faltan}
	somos={plan.somos}
	total={plan.total}
	personas={plan.van.map((p) => ({ nombre: p.nombre, foto: p.foto }))}
	enlace="/app/plan/{plan.id}"
	href="/app/plan/{plan.id}"
	{accion}
/>
