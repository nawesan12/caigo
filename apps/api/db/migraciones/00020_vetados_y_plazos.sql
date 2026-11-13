-- +goose Up
-- Lista de vetados: quien fue suspendido no vuelve con otra cuenta. Sólo huellas (HMAC-SHA256 con
-- una clave derivada de CAIGO_CLAVE_VERIFICACION) del número de DNI y del mail, nunca el dato en
-- claro. Mientras la cuenta suspendida existe, la huella está atada a su fila (vence_en NULL; si el
-- equipo la reactiva, se borra). La purga la suelta (usuario_id NULL) y vence a los 2 años; el
-- worker borra las vencidas. Las lee el sistema: el alta (mail) y la verificación del DNI (número).
CREATE TABLE vetados (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	huella bytea NOT NULL CHECK (length(huella) = 32),
	tipo text NOT NULL CHECK (tipo IN ('dni', 'email')),
	usuario_id uuid REFERENCES usuarios (id),
	creado_en timestamptz NOT NULL DEFAULT now(),
	vence_en timestamptz,
	-- Suelta (cuenta purgada), tiene que vencer.
	CHECK (usuario_id IS NOT NULL OR vence_en IS NOT NULL),
	UNIQUE NULLS NOT DISTINCT (huella, usuario_id)
);
CREATE INDEX vetados_usuario ON vetados (usuario_id) WHERE usuario_id IS NOT NULL;
CREATE INDEX vetados_vencen ON vetados (vence_en) WHERE vence_en IS NOT NULL;
ALTER TABLE vetados ENABLE ROW LEVEL SECURITY;
ALTER TABLE vetados FORCE ROW LEVEL SECURITY;
-- Ni la persona ve las suyas: sólo el sistema.
CREATE POLICY sistema ON vetados TO caigo_app, caigo_migrador
	USING (app.es_sistema()) WITH CHECK (app.es_sistema());
GRANT SELECT, INSERT, DELETE, UPDATE (usuario_id, vence_en) ON vetados TO caigo_app;

-- Plazos después de la purga de una cuenta (el worker; lo dice la política de privacidad):
-- mensajes del chat del plan (anónimos) al año de que terminó el plan, privados y asistencia al
-- año de la purga, reportes a los 2 años.
GRANT DELETE ON mensajes, solicitudes, volveria TO caigo_app;
GRANT DELETE ON privados TO caigo_app;
CREATE POLICY borrar ON privados FOR DELETE TO caigo_app, caigo_migrador USING (app.es_sistema());

-- +goose Down
DROP POLICY borrar ON privados;
REVOKE DELETE ON privados FROM caigo_app;
REVOKE DELETE ON mensajes, solicitudes, volveria FROM caigo_app;
DROP TABLE vetados;
