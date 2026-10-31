-- +goose Up
-- Fundación: la persona activa vive en una variable de la transacción, y RLS la usa en las tablas
-- privadas. Los roles (caigo_app, caigo_migrador) los crea `caigoctl bootstrap`: son del clúster y
-- una migración no debería tener permiso para eso.
--
-- Lo público entre personas (perfil liviano, planes) se autoriza en Go; lo que es de una sola
-- persona (teléfono, DNI, dirección exacta de un plan, mensajes) lo cuida RLS.

CREATE SCHEMA IF NOT EXISTS app;
GRANT USAGE ON SCHEMA app TO caigo_app;

-- +goose StatementBegin
-- La persona de la transacción en curso. NULL si no se fijó: entonces RLS no deja ver nada.
CREATE FUNCTION app.usuario_actual() RETURNS uuid
LANGUAGE sql STABLE AS $$
	SELECT nullif(current_setting('app.usuario_id', true), '')::uuid
$$;
-- +goose StatementEnd

-- +goose StatementBegin
-- Si la transacción corre como el sistema (worker, webhooks, moderación).
CREATE FUNCTION app.es_sistema() RETURNS boolean
LANGUAGE sql STABLE AS $$
	SELECT coalesce(current_setting('app.sistema', true), '') = 'si'
$$;
-- +goose StatementEnd

-- +goose StatementBegin
-- Aplica el aislamiento por persona a una tabla con usuario_id: RLS habilitada y FORZADA (alcanza
-- también al dueño) y la política que compara usuario_id (o el sistema).
CREATE FUNCTION app.aplicar_rls(tabla regclass) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
	EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', tabla);
	EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', tabla);
	EXECUTE format(
		'CREATE POLICY propia ON %s TO caigo_app, caigo_migrador USING (usuario_id = app.usuario_actual() OR app.es_sistema()) WITH CHECK (usuario_id = app.usuario_actual() OR app.es_sistema())',
		tabla);
END
$$;
-- +goose StatementEnd

-- Personas. Es el perfil liviano que ven los demás (nombre, edad, zona, intereses, reputación):
-- sin RLS de filas, la API decide qué columnas devuelve. Lo privado va en usuarios_privado.
CREATE TABLE usuarios (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	clerk_id text NOT NULL UNIQUE CHECK (clerk_id ~ '^user_[A-Za-z0-9]{1,64}$'),
	nombre text NOT NULL DEFAULT '' CHECK (length(nombre) <= 40),
	-- Edad que se muestra; la fecha exacta queda en usuarios_privado.
	edad smallint CHECK (edad BETWEEN 18 AND 120),
	zona text NOT NULL DEFAULT '' CHECK (length(zona) <= 60),
	ciudad text NOT NULL DEFAULT '' CHECK (length(ciudad) <= 60),
	bio text NOT NULL DEFAULT '' CHECK (length(bio) <= 300),
	trabajo text NOT NULL DEFAULT '' CHECK (length(trabajo) <= 60),
	intereses text[] NOT NULL DEFAULT '{}' CHECK (cardinality(intereses) <= 12),
	-- Qué busca: planes, gente, completar, citas (habilita el modo Citas).
	intenciones text[] NOT NULL DEFAULT '{}'
		CHECK (intenciones <@ ARRAY['planes', 'gente', 'completar', 'citas']::text[]),
	-- Prompts del perfil: [{"p": "pregunta", "r": "respuesta"}].
	prompts jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(prompts) = 'array'),
	fotos text[] NOT NULL DEFAULT '{}' CHECK (cardinality(fotos) <= 6),
	-- Capas de confianza: selfie con prueba de vida (obligatoria para entrar) y DNI (opcional).
	selfie_en timestamptz,
	dni_en timestamptz,
	-- Equipo de Caigo (moderación, revisión de DNI y de lugares). Se da de alta con caigoctl.
	equipo boolean NOT NULL DEFAULT false,
	creado_en timestamptz NOT NULL DEFAULT now(),
	actualizado_en timestamptz NOT NULL DEFAULT now(),
	baja_en timestamptz
);
GRANT SELECT, INSERT ON usuarios TO caigo_app;
GRANT UPDATE (nombre, edad, zona, ciudad, bio, trabajo, intereses, intenciones, prompts, fotos,
	selfie_en, dni_en, actualizado_en, baja_en) ON usuarios TO caigo_app;

-- Lo que sólo ve la persona (y el sistema).
CREATE TABLE usuarios_privado (
	usuario_id uuid PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
	telefono text CHECK (telefono ~ '^\+[1-9][0-9]{7,14}$'),
	telefono_verificado_en timestamptz,
	nacimiento date,
	-- Contacto de confianza para "Compartir mi plan en vivo".
	contacto_confianza text NOT NULL DEFAULT '' CHECK (length(contacto_confianza) <= 80)
);
SELECT app.aplicar_rls('usuarios_privado');
GRANT SELECT, INSERT, UPDATE ON usuarios_privado TO caigo_app;

-- +goose Down
DROP TABLE usuarios_privado;
DROP TABLE usuarios;
DROP FUNCTION app.aplicar_rls(regclass);
DROP FUNCTION app.es_sistema();
DROP FUNCTION app.usuario_actual();
DROP SCHEMA app;
