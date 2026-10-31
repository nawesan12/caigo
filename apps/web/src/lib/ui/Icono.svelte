<script lang="ts" module>
	/**
	 * Íconos lineales de trazo redondeado, copiados del handoff (`const D` del prototipo y los SVG
	 * inline de Mockups v5, Panel Lugares y Pases NFC). Los círculos van como arcos.
	 */
	const c = (cx: number, cy: number, r: number) =>
		`M${cx - r} ${cy}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0z`;

	export const D = {
		reloj: 'M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18zM12 7v5l3 2',
		lupa: 'M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14zM20 20l-4-4',
		calendario:
			'M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1zM4 10h16M8 3v4M16 3v4',
		usuario: 'M12 4a4 4 0 1 0 0 8a4 4 0 1 0 0-8zM4 20c1.5-4 4.5-6 8-6s6.5 2 8 6',
		pin: 'M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z' + c(12, 10, 2.5),
		personas:
			c(9, 8, 3.5) +
			'M2.5 20c1-3.5 3.5-5.5 6.5-5.5s5.5 2 6.5 5.5M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5c2 .6 3.2 2.5 3.5 5.5',
		mas: 'M12 4v16M4 12h16',
		check: 'M5 12l5 5L20 7',
		cerrar: 'M6 6l12 12M18 6L6 18',
		atras: 'M15 5l-7 7 7 7',
		flecha: 'M5 12h14M13 6l6 6-6 6',
		abajo: 'M6 9l6 6 6-6',
		escanear:
			'M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M4 12h16',
		tap: 'M8 7a7 7 0 0 1 0 10M12 4a11 11 0 0 1 0 16M4 10a3 3 0 0 1 0 4',
		cobros: 'M3 7h18v10H3zM3 11h18M7 15h3',
		regalo:
			'M4 11h16v9H4zM3 7h18v4H3zM12 7v13M12 7c-1.5-3-5-3-5-.5S10 7 12 7zM12 7c1.5-3 5-3 5-.5S14 7 12 7z',
		campana: 'M6 16V11a6 6 0 0 1 12 0v5l1.5 2h-15zM10 20.5a2 2 0 0 0 4 0',
		escudo: 'M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6z',
		/** Los tres puntos del menú (con trazo grueso, cada punto es un trazo de largo 0). */
		puntos: 'M5 12h.01M12 12h.01M19 12h.01'
	} as const;

	export type NombreIcono = keyof typeof D;
</script>

<script lang="ts">
	let {
		nombre,
		tam = 20,
		color = 'currentColor',
		trazo = 2.2,
		etiqueta
	}: {
		nombre: NombreIcono;
		tam?: number;
		color?: string;
		trazo?: number;
		etiqueta?: string;
	} = $props();
</script>

<svg
	width={tam}
	height={tam}
	viewBox="0 0 24 24"
	fill="none"
	style:stroke={color}
	stroke-width={trazo}
	stroke-linecap="round"
	stroke-linejoin="round"
	role={etiqueta ? 'img' : undefined}
	aria-label={etiqueta}
	aria-hidden={etiqueta ? undefined : 'true'}
	style="flex:none"><path d={D[nombre]} /></svg
>
