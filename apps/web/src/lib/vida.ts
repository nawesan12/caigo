/**
 * Prueba de vida de la selfie (web): con FaceLandmarker de MediaPipe se exige una cara sola,
 * centrada y cerca, que gire la cabeza hacia un costado y vuelva al centro. Recién ahí se saca la
 * foto. Una foto impresa o una pantalla quieta no giran, y sin cara no avanza.
 *
 * Lo que decide (`paso`) es puro y se prueba en vida.spec.ts; lo que carga el modelo y lee la cámara
 * sólo corre en el navegador.
 */

/** Un punto de la malla (normalizado de 0 a 1 sobre el cuadro de la cámara, sin espejar). */
export type Punto = { x: number; y: number };

/** Cuánto giró la cabeza: 0 de frente; ±1 de perfil (signo: hacia dónde, en la imagen). */
export function giro(caras: Punto[]): number {
	// 1: punta de la nariz; 234 y 454: los bordes de la cara a la altura de los pómulos.
	const nariz = caras[1],
		a = caras[234],
		b = caras[454];
	if (!nariz || !a || !b) return 0;
	const ancho = b.x - a.x;
	if (Math.abs(ancho) < 1e-3) return 0;
	return ((nariz.x - a.x) / ancho - 0.5) * 2;
}

/** La caja de la cara (normalizada). */
export function caja(caras: Punto[]) {
	let x0 = 1,
		y0 = 1,
		x1 = 0,
		y1 = 0;
	for (const p of caras) {
		if (p.x < x0) x0 = p.x;
		if (p.x > x1) x1 = p.x;
		if (p.y < y0) y0 = p.y;
		if (p.y > y1) y1 = p.y;
	}
	return { cx: (x0 + x1) / 2, cy: (y0 + y1) / 2, ancho: x1 - x0 };
}

export type Fase = 'buscando' | 'centrada' | 'girar' | 'volver' | 'listo';

export type Estado = {
	fase: Fase;
	/** Desde cuándo se cumple lo que pide la fase (ms), para pedir que se sostenga un momento. */
	desde: number;
};

export const INICIO: Estado = { fase: 'buscando', desde: 0 };

/** Lo que mide cada cuadro. */
export type Lectura =
	{ caras: 0 } | { caras: number; giro: number; cx: number; cy: number; ancho: number };

const DE_FRENTE = 0.18;
const GIRADA = 0.5;
const SOSTENER_MS = 350;

/** Qué le falta a una cara para estar bien puesta (o null si está bien). */
export function falta(l: Lectura): string | null {
	if (l.caras === 0) return 'No vemos tu cara: ponete frente a la cámara';
	if (l.caras > 1) return 'Que se vea una sola cara';
	if (!('ancho' in l)) return 'No vemos tu cara: ponete frente a la cámara';
	if (l.ancho < 0.22) return 'Acercate un poco más';
	if (l.ancho > 0.8) return 'Alejate un poco';
	if (Math.abs(l.cx - 0.5) > 0.17 || Math.abs(l.cy - 0.5) > 0.2) return 'Poné la cara en el centro';
	return null;
}

/**
 * Un cuadro más: de dónde viene y qué se lee ahora → adónde va. Si en cualquier momento se pierde la
 * cara (o aparece otra), vuelve a empezar.
 */
export function paso(e: Estado, l: Lectura, t: number): Estado {
	if (e.fase === 'listo') return e;
	const bien = falta(l) === null;
	const g = 'giro' in l ? Math.abs(l.giro) : 0;
	// Girada, la cara se corre del centro: en «volver» alcanza con que siga habiendo una sola.
	const sola = l.caras === 1;
	switch (e.fase) {
		case 'buscando':
			return bien && g < DE_FRENTE ? { fase: 'centrada', desde: t } : e;
		case 'centrada':
			if (!bien || g >= DE_FRENTE) return INICIO;
			return t - e.desde >= SOSTENER_MS ? { fase: 'girar', desde: t } : e;
		case 'girar':
			if (!sola) return INICIO;
			return g >= GIRADA ? { fase: 'volver', desde: t } : e;
		case 'volver':
			if (!sola) return INICIO;
			if (!bien || g >= DE_FRENTE) return { fase: 'volver', desde: t };
			return t - e.desde >= SOSTENER_MS ? { fase: 'listo', desde: t } : e;
	}
}

/** Lo que se le pide a la persona en cada fase (o lo que falta para seguir). */
export function instruccion(e: Estado, l: Lectura | null): string {
	if (e.fase === 'listo') return 'Listo';
	if (!l) return 'Mirá de frente a la cámara';
	if (e.fase === 'buscando' || e.fase === 'centrada') {
		const f = falta(l);
		if (f) return f;
		return e.fase === 'centrada' ? 'Quedate así…' : 'Mirá de frente a la cámara';
	}
	if (l.caras !== 1) return 'No vemos tu cara: ponete frente a la cámara';
	return e.fase === 'girar' ? 'Girá la cabeza despacio a la izquierda' : 'Volvé despacio al centro';
}

/** De 0 a 1, para el anillo. */
export function progreso(e: Estado): number {
	return { buscando: 0, centrada: 0.15, girar: 0.34, volver: 0.67, listo: 1 }[e.fase];
}

// ——— Navegador ———

type Detector = {
	detectForVideo(v: HTMLVideoElement, t: number): { faceLandmarks: Punto[][] };
	close(): void;
};

let cargando: Promise<Detector> | null = null;

/**
 * Carga MediaPipe (JS, WASM y el modelo, todo servido por la web: nada de CDNs). En iPhone va por
 * CPU (el delegado de GPU de Safari no es confiable); en el resto, GPU.
 */
export function cargarDetector(): Promise<Detector> {
	cargando ??= (async () => {
		const [{ FaceLandmarker, FilesetResolver }, simdJs, simdWasm, js, wasm] = await Promise.all([
			import('@mediapipe/tasks-vision'),
			import('@mediapipe/tasks-vision/vision_wasm_internal.js?url'),
			import('@mediapipe/tasks-vision/vision_wasm_internal.wasm?url'),
			import('@mediapipe/tasks-vision/vision_wasm_nosimd_internal.js?url'),
			import('@mediapipe/tasks-vision/vision_wasm_nosimd_internal.wasm?url')
		]);
		const simd = await FilesetResolver.isSimdSupported();
		const archivos = simd
			? { wasmLoaderPath: simdJs.default, wasmBinaryPath: simdWasm.default }
			: { wasmLoaderPath: js.default, wasmBinaryPath: wasm.default };
		const ua = navigator.userAgent;
		// iPadOS se presenta como Mac: se reconoce por el touch.
		const ios =
			/iphone|ipad|ipod/i.test(ua) || (/Macintosh/.test(ua) && navigator.maxTouchPoints > 1);
		const crear = (delegate: 'GPU' | 'CPU') =>
			FaceLandmarker.createFromOptions(archivos, {
				baseOptions: { modelAssetPath: '/vida/face_landmarker.task', delegate },
				runningMode: 'VIDEO',
				numFaces: 2,
				minFaceDetectionConfidence: 0.6,
				minFacePresenceConfidence: 0.6,
				minTrackingConfidence: 0.5
			});
		const d = ios ? await crear('CPU') : await crear('GPU').catch(() => crear('CPU'));
		return d as unknown as Detector;
	})();
	cargando.catch(() => (cargando = null));
	return cargando;
}

/** Lee un cuadro del video. */
export function leer(d: Detector, v: HTMLVideoElement, t: number): Lectura {
	const r = d.detectForVideo(v, t);
	const caras = r.faceLandmarks ?? [];
	if (!caras.length) return { caras: 0 };
	const c = caja(caras[0]);
	return { caras: caras.length, giro: giro(caras[0]), ...c };
}
