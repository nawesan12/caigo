-- +goose Up
-- Sesiones propias de Caigo: el token de Clerk se canjea una vez (POST /v1/sesion) por un token de
-- Caigo (cs_…) que dura 60 días y se renueva con el uso. Se guarda sólo el hash.
CREATE TABLE sesiones (
	token_hash bytea PRIMARY KEY,
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	-- web | ios | android
	cliente text NOT NULL CHECK (cliente IN ('web', 'ios', 'android')),
	creada_en timestamptz NOT NULL DEFAULT now(),
	usada_en timestamptz NOT NULL DEFAULT now(),
	vence_en timestamptz NOT NULL
);
CREATE INDEX sesiones_usuario ON sesiones (usuario_id);
GRANT SELECT, INSERT, UPDATE (usada_en, vence_en), DELETE ON sesiones TO caigo_app;

-- +goose Down
DROP TABLE sesiones;
