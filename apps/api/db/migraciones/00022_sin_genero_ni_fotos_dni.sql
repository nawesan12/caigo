-- +goose Up
-- AAIP 1.2, segunda parte.
-- - El género ya no va en el perfil: para un plan «Solo mujeres» la persona lo confirma en el mismo
--   pedido de sumarse y no se guarda (como «Solo diversidades» desde la 00021).
-- - Las fotos de la verificación del DNI (frente, dorso y selfie) se borran apenas se resuelve. Las
--   referencias pasan a ser opcionales (una pendiente las tiene siempre) y se limpian las de las ya
--   resueltas: acá se borran las filas de archivos y se encola su borrado del disco (archivo.borrar),
--   que hace el worker. Su purga horaria (confianza.PurgarFotosDNI) levanta cualquier resto.
-- Como el sistema: las tablas tienen RLS forzada.
SELECT set_config('app.sistema', 'si', true);

UPDATE usuarios_privado SET genero = '' WHERE genero <> '';
ALTER TABLE usuarios_privado DROP COLUMN genero;

ALTER TABLE verificaciones_dni ALTER COLUMN frente_id DROP NOT NULL,
	ALTER COLUMN dorso_id DROP NOT NULL, ALTER COLUMN selfie_id DROP NOT NULL;
ALTER TABLE verificaciones_dni ADD CONSTRAINT verificaciones_dni_fotos_pendiente
	CHECK (estado <> 'pendiente' OR (frente_id IS NOT NULL AND dorso_id IS NOT NULL AND selfie_id IS NOT NULL));
GRANT UPDATE (frente_id, dorso_id, selfie_id) ON verificaciones_dni TO caigo_app;

CREATE TEMP TABLE fotos_resueltas ON COMMIT DROP AS
	SELECT DISTINCT x.id FROM verificaciones_dni v, unnest(ARRAY[v.frente_id, v.dorso_id, v.selfie_id]) AS x (id)
	WHERE v.estado <> 'pendiente' AND x.id IS NOT NULL;
UPDATE verificaciones_dni SET frente_id = NULL, dorso_id = NULL, selfie_id = NULL
	WHERE estado <> 'pendiente' AND (frente_id IS NOT NULL OR dorso_id IS NOT NULL OR selfie_id IS NOT NULL);
-- Si alguna también es de una verificación pendiente (no debería), queda.
DELETE FROM fotos_resueltas f USING verificaciones_dni v WHERE f.id IN (v.frente_id, v.dorso_id, v.selfie_id);
INSERT INTO outbox (tipo, datos)
	SELECT 'archivo.borrar', jsonb_build_object('ruta', a.ruta) FROM archivos a JOIN fotos_resueltas f ON f.id = a.id;
DELETE FROM archivos a USING fotos_resueltas f WHERE a.id = f.id;

SELECT set_config('app.sistema', '', true);

-- +goose Down
-- Vuelve la columna del género, vacía; las referencias a fotos quedan opcionales. Lo borrado no se
-- recupera.
REVOKE UPDATE (frente_id, dorso_id, selfie_id) ON verificaciones_dni FROM caigo_app;
ALTER TABLE verificaciones_dni DROP CONSTRAINT verificaciones_dni_fotos_pendiente;
ALTER TABLE usuarios_privado ADD COLUMN genero text NOT NULL DEFAULT ''
	CHECK (genero IN ('', 'mujer', 'varon', 'no_binario'));
