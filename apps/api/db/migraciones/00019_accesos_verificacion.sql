-- +goose Up
-- Quién del equipo abre una selfie o una foto del DNI ajena (GET /v1/archivos/{id}): quién, de quién,
-- qué archivo, cuándo y desde qué IP. Lo escribe la API como sistema antes de mostrar el archivo; la
-- dueña mirando lo suyo no se anota. Con RLS por usuario_id, como el resto de lo privado: cada
-- persona puede ver quién miró lo suyo. La app sólo agrega y lee; el worker borra lo de más de 2 años.
CREATE TABLE accesos_verificacion (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	-- Quién del equipo lo abrió.
	equipo_id uuid NOT NULL REFERENCES usuarios (id),
	-- De quién es el archivo.
	usuario_id uuid NOT NULL REFERENCES usuarios (id),
	-- Sin FK: la purga borra el archivo a los 30 días de la baja y el registro tiene que quedar.
	archivo_id uuid NOT NULL,
	tipo text NOT NULL CHECK (tipo IN ('selfie', 'dni')),
	ip inet,
	visto_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX accesos_verificacion_usuario ON accesos_verificacion (usuario_id, visto_en DESC);
CREATE INDEX accesos_verificacion_equipo ON accesos_verificacion (equipo_id, visto_en DESC);
CREATE INDEX accesos_verificacion_viejos ON accesos_verificacion (visto_en);
SELECT app.aplicar_rls('accesos_verificacion');
GRANT SELECT, INSERT, DELETE ON accesos_verificacion TO caigo_app;

-- +goose Down
DROP TABLE accesos_verificacion;
