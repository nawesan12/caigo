-- +goose Up
-- Confianza: código por SMS, selfie con prueba de vida, DNI con revisión del equipo y archivos.

-- Códigos de verificación del teléfono (el código se guarda con hash; vence a los 10 minutos).
CREATE TABLE telefono_codigos (
	usuario_id uuid PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
	telefono text NOT NULL CHECK (telefono ~ '^\+[1-9][0-9]{7,14}$'),
	codigo_hash bytea NOT NULL,
	intentos smallint NOT NULL DEFAULT 0,
	enviado_en timestamptz NOT NULL DEFAULT now(),
	vence_en timestamptz NOT NULL
);
SELECT app.aplicar_rls('telefono_codigos');
GRANT SELECT, INSERT, UPDATE, DELETE ON telefono_codigos TO caigo_app;

-- Archivos subidos (fotos de perfil y de planes son públicas; selfie y DNI, privadas).
CREATE TABLE archivos (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	tipo text NOT NULL CHECK (tipo IN ('foto', 'selfie', 'dni')),
	ruta text NOT NULL UNIQUE,
	contenido text NOT NULL,
	bytes integer NOT NULL CHECK (bytes > 0),
	creado_en timestamptz NOT NULL DEFAULT now()
);
-- Los públicos se sirven sin sesión (se leen como sistema); los privados sólo los ve el dueño.
SELECT app.aplicar_rls('archivos');
GRANT SELECT, INSERT, DELETE ON archivos TO caigo_app;

-- Verificación de DNI: la app lee el PDF417 del frente y saca fotos de frente, dorso y una selfie;
-- el equipo compara y aprueba. Al aprobar, la fecha de nacimiento del DNI pasa al perfil.
CREATE TABLE verificaciones_dni (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	frente_id uuid NOT NULL REFERENCES archivos (id),
	dorso_id uuid NOT NULL REFERENCES archivos (id),
	selfie_id uuid NOT NULL REFERENCES archivos (id),
	-- Lo que leyó la app del código PDF417 (número, apellido, nombre, sexo, nacimiento, trámite).
	numero text NOT NULL CHECK (numero ~ '^[0-9]{7,9}$'),
	apellido text NOT NULL CHECK (length(apellido) BETWEEN 1 AND 80),
	nombres text NOT NULL CHECK (length(nombres) BETWEEN 1 AND 80),
	sexo text NOT NULL DEFAULT '' CHECK (sexo IN ('', 'M', 'F', 'X')),
	nacimiento date NOT NULL,
	tramite text NOT NULL DEFAULT '' CHECK (length(tramite) <= 20),
	estado text NOT NULL DEFAULT 'pendiente' CHECK (estado IN ('pendiente', 'aprobada', 'rechazada')),
	motivo text NOT NULL DEFAULT '' CHECK (length(motivo) <= 300),
	revisada_por uuid REFERENCES usuarios (id),
	revisada_en timestamptz,
	creada_en timestamptz NOT NULL DEFAULT now()
);
-- Un DNI aprobado no puede estar en dos cuentas.
CREATE UNIQUE INDEX verificaciones_dni_numero ON verificaciones_dni (numero) WHERE estado = 'aprobada';
CREATE INDEX verificaciones_dni_pendientes ON verificaciones_dni (creada_en) WHERE estado = 'pendiente';
SELECT app.aplicar_rls('verificaciones_dni');
GRANT SELECT, INSERT ON verificaciones_dni TO caigo_app;
GRANT UPDATE (estado, motivo, revisada_por, revisada_en) ON verificaciones_dni TO caigo_app;

-- Suspensión por moderación (distinta de la baja que pide la persona).
ALTER TABLE usuarios ADD COLUMN suspendido_en timestamptz;
ALTER TABLE usuarios ADD COLUMN suspension_motivo text NOT NULL DEFAULT '' CHECK (length(suspension_motivo) <= 300);
GRANT UPDATE (suspendido_en, suspension_motivo) ON usuarios TO caigo_app;

-- +goose Down
ALTER TABLE usuarios DROP COLUMN suspension_motivo;
ALTER TABLE usuarios DROP COLUMN suspendido_en;
DROP TABLE verificaciones_dni;
DROP TABLE archivos;
DROP TABLE telefono_codigos;
