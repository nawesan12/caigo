// Imágenes de marca para las OG, embebidas en el bundle del servidor como data: URL.
// Las fotos son las de static/fotos achicadas a 640 px (sips, una sola vez): pesan la mitad y
// satori/resvg las decodifican más rápido.
import caigoC from './recursos/caigo-c.png?inline';
import caigoIcono from './recursos/caigo-icon.png?inline';
import grano from './recursos/grain.png?inline';
import playa from './recursos/playa.jpg?inline';
import truco from './recursos/truco.jpg?inline';
import previa from './recursos/previa.jpg?inline';
import grupo from './recursos/grupo.jpg?inline';
import lugar from './recursos/lugar.jpg?inline';
import amigos from './recursos/amigos.jpg?inline';

export const MARCA = { caigoC, caigoIcono, grano };

/** Las fotos de /fotos/*.jpg: si un plan usa una de estas, no hace falta bajarla. */
export const FOTOS: Record<string, string> = { playa, truco, previa, grupo, lugar, amigos };
