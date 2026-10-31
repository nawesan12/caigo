import { h, img, type Estilo, type Hijo, type Nodo } from './motor.ts';
import { MARCA } from './recursos.ts';

/*
 * Piezas del handoff (README · Sombras "gel / 3D soft") pasadas a estilos que satori entiende:
 * gradiente vertical, brillo interno arriba, sombra interna abajo y la "base" sólida desplazada.
 */

export const C = {
	coral: '#FF7868',
	violeta: '#7456F1',
	lima: '#C8F36B',
	grafito: '#201D2B',
	carbon: '#24212D',
	calido: '#FAF9F6',
	lavanda: '#F0ECFF',
	gris: '#5C586B',
	violetaTexto: '#5B43C9'
};

export const GEL = {
	lima: {
		backgroundImage: 'linear-gradient(180deg,#F0FFC2 0%,#D6FA80 32%,#C8F36B 62%,#ABDB4B 100%)',
		boxShadow:
			'inset 0 3px 2px rgba(255,255,255,.85), inset 0 -5px 0 rgba(96,140,10,.32), 0 5px 0 #94C43A, 0 18px 28px -10px rgba(120,170,30,.7)',
		color: C.carbon
	},
	violeta: {
		backgroundImage: 'linear-gradient(180deg,#B3A3FF 0%,#8A70FF 30%,#7456F1 62%,#5F41E0 100%)',
		boxShadow:
			'inset 0 3px 2px rgba(255,255,255,.45), inset 0 -5px 0 rgba(40,20,140,.35), 0 5px 0 #4A2FC2, 0 18px 28px -10px rgba(116,86,241,.75)',
		color: '#FFFFFF'
	},
	coral: {
		backgroundImage: 'linear-gradient(180deg,#FFB2A6 0%,#FF7868 55%,#F05E4C 100%)',
		boxShadow:
			'inset 0 2px 1px rgba(255,255,255,.6), inset 0 -3px 0 rgba(160,40,30,.25), 0 3px 0 #D04A3A',
		color: C.carbon
	},
	apagado: {
		backgroundImage: 'linear-gradient(180deg,#F8F6FF,#EDE8FF)',
		boxShadow: 'inset 0 1.5px 0 #FFFFFF, 0 2px 0 #DCD5EE',
		color: C.carbon
	},
	oscuro: {
		backgroundImage: 'linear-gradient(180deg,#3D3756,#24212D)',
		boxShadow: 'inset 0 1px 0 rgba(255,255,255,.15), 0 3px 0 #0E0D14',
		color: C.calido
	}
} satisfies Record<string, Estilo>;

export type TonoGel = keyof typeof GEL;

/** El grain del handoff, en mosaico, encima de lo que haya. */
export function grano(opacidad = 1, extra: Estilo = {}): Nodo {
	return h('div', {
		position: 'absolute',
		top: 0,
		left: 0,
		right: 0,
		bottom: 0,
		backgroundImage: `url(${MARCA.grano})`,
		backgroundRepeat: 'repeat',
		backgroundSize: '160px 160px',
		opacity: opacidad,
		...extra
	});
}

/** Un chip/badge gel ("Faltan 2", "Deporte", "Ahora · en 2 h"). */
export function chip(texto: Hijo | Hijo[], tono: TonoGel, tam = 22, extra: Estilo = {}): Nodo {
	return h(
		'div',
		{
			alignItems: 'center',
			padding: `${Math.round(tam * 0.42)}px ${Math.round(tam * 0.72)}px`,
			borderRadius: Math.round(tam * 0.75),
			fontSize: tam,
			fontWeight: 700,
			lineHeight: 1,
			letterSpacing: '-0.01em',
			whiteSpace: 'nowrap',
			...GEL[tono],
			...extra
		},
		...(Array.isArray(texto) ? texto : [texto])
	);
}

function svgDato(svg: string): string {
	return `data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}`;
}

/** El "+" de Sumarme / Publicar plan (el trazo redondeado del handoff). */
export function iconoMas(tam: number, color = C.carbon): Nodo {
	return img(
		svgDato(
			`<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path d="M12 4v16M4 12h16" stroke="${color}" stroke-width="4" stroke-linecap="round" fill="none"/></svg>`
		),
		tam,
		tam
	);
}

/** El check (Geist no trae ✓). */
export function iconoCheck(tam: number, color = C.carbon): Nodo {
	return img(
		svgDato(
			`<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7" stroke="${color}" stroke-width="3.4" stroke-linecap="round" stroke-linejoin="round" fill="none"/></svg>`
		),
		tam,
		tam
	);
}

/** La marca: la C 3D con sus gotitas y "Caigo" al lado. */
export function marca(tam = 44, color = C.carbon, extra: Estilo = {}): Nodo {
	const ancho = Math.round(tam * 1.21);
	return h(
		'div',
		{ alignItems: 'center', gap: Math.round(tam * 0.22), ...extra },
		img(MARCA.caigoC, ancho, tam),
		h(
			'div',
			{ fontSize: tam * 0.92, fontWeight: 700, letterSpacing: '-0.045em', color, lineHeight: 1 },
			'Caigo'
		)
	);
}

const TONOS_AVATAR = [
	{ fondo: C.violeta, texto: '#FFFFFF' },
	{ fondo: C.lima, texto: C.carbon },
	{ fondo: C.coral, texto: C.carbon },
	{ fondo: C.lavanda, texto: C.violetaTexto }
];

export type Cara = { nombre: string; foto?: string | null };

/** Un avatar redondo: foto (data: URL) o la inicial sobre el color con el brillo del handoff. */
export function avatar(c: Cara, i: number, tam: number, borde = '', extra: Estilo = {}): Nodo {
	const t = TONOS_AVATAR[i % TONOS_AVATAR.length];
	const base: Estilo = {
		width: tam,
		height: tam,
		borderRadius: tam / 2,
		alignItems: 'center',
		justifyContent: 'center',
		overflow: 'hidden',
		flexShrink: 0,
		...(borde ? { border: borde } : {}),
		...extra
	};
	if (c.foto)
		return h(
			'div',
			{ ...base, backgroundColor: t.fondo },
			img(c.foto, tam, tam, { objectFit: 'cover', width: '100%', height: '100%' })
		);
	const inicial = (c.nombre.trim()[0] ?? '·').toUpperCase();
	return h(
		'div',
		{
			...base,
			backgroundImage: `radial-gradient(circle at 30% 25%, rgba(255,255,255,.6), rgba(255,255,255,0) 42%), linear-gradient(${t.fondo}, ${t.fondo})`,
			color: t.texto,
			fontSize: Math.round(tam * 0.4),
			fontWeight: 800
		},
		inicial
	);
}

/** Avatares superpuestos (+N al final), como en el contador del detalle (05). */
export function avatares(caras: Cara[], total: number, tam: number, borde: string): Nodo {
	const vistas = caras.slice(0, total > 4 ? 3 : 4);
	const resto = total - vistas.length;
	return h(
		'div',
		{ alignItems: 'center' },
		...vistas.map((c, i) =>
			avatar(c, i, tam, `${Math.max(2, Math.round(tam / 16))}px solid ${borde}`, {
				marginLeft: i === 0 ? 0 : -Math.round(tam * 0.25)
			})
		),
		resto > 0 &&
			h(
				'div',
				{
					width: tam,
					height: tam,
					borderRadius: tam / 2,
					marginLeft: vistas.length ? -Math.round(tam * 0.25) : 0,
					border: `${Math.max(2, Math.round(tam / 16))}px solid ${borde}`,
					backgroundColor: '#4A4460',
					color: C.calido,
					alignItems: 'center',
					justifyContent: 'center',
					fontSize: Math.round(tam * 0.34),
					fontWeight: 800
				},
				`+${resto}`
			)
	);
}

/** La barra de cupo: lleno lima, vacío punteado coral. Con mucho cupo, se escala a 14. */
export function segmentos(somos: number, total: number, alto = 12, gap = 6): Nodo {
	const n = Math.max(1, Math.min(total, 14));
	const llenos =
		total <= 14 ? Math.min(somos, n) : Math.min(n, Math.round((somos / Math.max(total, 1)) * n));
	return h(
		'div',
		{ gap, width: '100%' },
		...Array.from({ length: n }, (_, i) =>
			h(
				'div',
				i < llenos
					? {
							flexGrow: 1,
							height: alto,
							borderRadius: alto / 2,
							backgroundImage: 'linear-gradient(180deg,#E2FF9C,#B2E04F)'
						}
					: {
							flexGrow: 1,
							height: alto,
							borderRadius: alto / 2,
							border: `2px dashed ${C.coral}`
						}
			)
		)
	);
}

/** Fondo claro con textura del handoff: radial lavanda arriba a la derecha + grain. */
export function fondoClaro(): Nodo[] {
	return [
		h('div', {
			position: 'absolute',
			top: 0,
			left: 0,
			right: 0,
			bottom: 0,
			backgroundImage:
				'radial-gradient(90% 60% at 100% 0%, #ECE5FF 0%, rgba(236,229,255,0) 70%), radial-gradient(60% 50% at 0% 100%, #FFF1E8 0%, rgba(255,241,232,0) 70%)'
		}),
		grano(0.9)
	];
}

/** Saca lo que Geist no tiene (emojis y compañía) para que no aparezcan cuadraditos. */
export function limpio(t: string): string {
	return t
		.replace(/\p{Extended_Pictographic}|[\u{1F1E6}-\u{1F1FF}]|\u200D|\uFE0F|\uFE0E|\u20E3/gu, '')
		.replace(/\s+/g, ' ')
		.trim();
}

/** Corta un texto largo en el último espacio antes de `max` y le pone "…". */
export function cortar(t: string, max: number): string {
	if (t.length <= max) return t;
	const c = t.slice(0, max - 1);
	const i = c.lastIndexOf(' ');
	let r = i > max * 0.5 ? c.slice(0, i) : c;
	// Que no termine en "el…" o "de…": se van las palabras cortas del final.
	r = r.replace(/(\s\S{1,2})+$/, '').replace(/[\s,.;:·-]+$/, '');
	return r + '…';
}

/**
 * Sin renglones viudos: une las dos últimas palabras con un espacio duro (y las palabras cortas
 * con la que sigue), así nunca queda una palabra sola abajo.
 */
export function sinViudas(t: string): string {
	const p = t.split(' ');
	if (p.length < 3) return t;
	const fin = p.splice(-2).join(' ');
	return [...p, fin].join(' ').replace(/(^|\s)(\S{1,2}) /g, '$1$2 ');
}
