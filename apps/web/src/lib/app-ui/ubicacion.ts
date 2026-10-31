import { invalidateAll } from '$app/navigation';
import { COOKIE_UBIC } from '#lib/app.ts';

/**
 * Pide la ubicación al navegador y la deja en una cookie para que el servidor busque los planes
 * cerca. Sin permiso, la búsqueda sigue desde el centro de Mar del Plata.
 */
export function pedirUbicacion(): Promise<boolean> {
	return new Promise((listo) => {
		if (!('geolocation' in navigator)) return listo(false);
		navigator.geolocation.getCurrentPosition(
			async (p) => {
				const v = `${p.coords.latitude.toFixed(4)},${p.coords.longitude.toFixed(4)}`;
				document.cookie = `${COOKIE_UBIC}=${v}; path=/; max-age=${60 * 60 * 6}; samesite=lax`;
				await invalidateAll();
				listo(true);
			},
			() => listo(false),
			{ enableHighAccuracy: false, timeout: 8000, maximumAge: 10 * 60 * 1000 }
		);
	});
}
