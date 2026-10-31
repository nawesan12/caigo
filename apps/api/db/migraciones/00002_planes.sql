-- +goose Up
-- Planes: la unidad central de Caigo. "Somos N, faltan M": el organizador tiene su propia solicitud
-- aceptada con cantidad = somos (su grupo), y cada solicitud aceptada suma su `cantidad` (una
-- persona o un grupo, "somos 2"). Faltan = total − suma de las aceptadas.

CREATE TABLE planes (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	-- Para el link público caigo.com.ar/p/<slug>.
	slug text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,70}[a-z0-9])?$'),
	organizador_id uuid NOT NULL REFERENCES usuarios (id),
	titulo text NOT NULL CHECK (length(titulo) BETWEEN 1 AND 80),
	categoria text NOT NULL CHECK (categoria IN
		('deporte', 'juego', 'previa', 'salida', 'cita_doble', 'cultura', 'gastronomia', 'otro')),
	descripcion text NOT NULL DEFAULT '' CHECK (length(descripcion) <= 600),
	empieza_en timestamptz NOT NULL,
	duracion_min smallint NOT NULL DEFAULT 120 CHECK (duracion_min BETWEEN 15 AND 1440),
	-- Plan que se repite cada semana: cada encuentro es su propio plan con la misma serie.
	recurrente boolean NOT NULL DEFAULT false,
	serie_id uuid,
	-- Dónde, aproximado (lo ve cualquiera). La dirección exacta va en planes_direccion.
	ciudad text NOT NULL CHECK (length(ciudad) BETWEEN 1 AND 60),
	zona text NOT NULL DEFAULT '' CHECK (length(zona) <= 60),
	lugar_nombre text NOT NULL DEFAULT '' CHECK (length(lugar_nombre) <= 80),
	lat double precision NOT NULL CHECK (lat BETWEEN -90 AND 90),
	lng double precision NOT NULL CHECK (lng BETWEEN -180 AND 180),
	-- Cupo.
	somos smallint NOT NULL CHECK (somos >= 1),
	total smallint NOT NULL CHECK (total > somos AND total <= 60),
	-- Cómo se suman y quién lo ve.
	ingreso text NOT NULL DEFAULT 'aprobacion' CHECK (ingreso IN ('libre', 'aprobacion', 'mi_gente')),
	visibilidad text NOT NULL DEFAULT 'publico' CHECK (visibilidad IN ('publico', 'mi_gente', 'link')),
	solo_verificados boolean NOT NULL DEFAULT true,
	ocultar_direccion boolean NOT NULL DEFAULT true,
	-- A quién busca (todo opcional).
	edad_min smallint CHECK (edad_min BETWEEN 18 AND 120),
	edad_max smallint CHECK (edad_max BETWEEN 18 AND 120 AND (edad_min IS NULL OR edad_max >= edad_min)),
	genero text NOT NULL DEFAULT 'cualquiera' CHECK (genero IN ('cualquiera', 'mujeres', 'diversidades')),
	nivel text NOT NULL DEFAULT '' CHECK (length(nivel) <= 30),
	-- Costo estimado por persona, en centavos de pesos. NULL: no dijo.
	costo_centavos integer CHECK (costo_centavos >= 0),
	foto text NOT NULL DEFAULT '' CHECK (length(foto) <= 500),
	cancelado_en timestamptz,
	creado_en timestamptz NOT NULL DEFAULT now()
);
-- Ahora y Explorar buscan por fecha y caja de coordenadas.
CREATE INDEX planes_empieza ON planes (empieza_en) WHERE cancelado_en IS NULL;
CREATE INDEX planes_lugar ON planes (lat, lng) WHERE cancelado_en IS NULL;
CREATE INDEX planes_organizador ON planes (organizador_id, empieza_en);
GRANT SELECT, INSERT ON planes TO caigo_app;
GRANT UPDATE (titulo, descripcion, empieza_en, duracion_min, total, ingreso, visibilidad,
	solo_verificados, ocultar_direccion, edad_min, edad_max, genero, nivel, costo_centavos, foto,
	cancelado_en) ON planes TO caigo_app;

-- Solicitudes para sumarse. Con ingreso libre nacen aceptadas.
CREATE TABLE solicitudes (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	plan_id uuid NOT NULL REFERENCES planes (id) ON DELETE CASCADE,
	usuario_id uuid NOT NULL REFERENCES usuarios (id),
	cantidad smallint NOT NULL DEFAULT 1 CHECK (cantidad BETWEEN 1 AND 60),
	mensaje text NOT NULL DEFAULT '' CHECK (length(mensaje) <= 200),
	estado text NOT NULL DEFAULT 'pendiente' CHECK (estado IN ('pendiente', 'aceptada', 'rechazada', 'cancelada')),
	creada_en timestamptz NOT NULL DEFAULT now(),
	respondida_en timestamptz,
	-- Asistencia real: check-in en el lugar (geo, qr, nfc).
	checkin_en timestamptz,
	checkin_metodo text CHECK (checkin_metodo IN ('geo', 'qr', 'nfc')),
	UNIQUE (plan_id, usuario_id)
);
CREATE INDEX solicitudes_usuario ON solicitudes (usuario_id, estado);
GRANT SELECT, INSERT ON solicitudes TO caigo_app;
GRANT UPDATE (cantidad, mensaje, estado, creada_en, respondida_en, checkin_en, checkin_metodo) ON solicitudes TO caigo_app;

-- +goose StatementBegin
-- Si la persona actual puede ver la dirección exacta: el plan no la oculta, lo organiza o la aceptaron.
CREATE FUNCTION app.ve_direccion(plan uuid) RETURNS boolean
LANGUAGE sql STABLE AS $$
	SELECT app.es_sistema()
		OR EXISTS (SELECT 1 FROM planes p WHERE p.id = plan
			AND (NOT p.ocultar_direccion OR p.organizador_id = app.usuario_actual()))
		OR EXISTS (SELECT 1 FROM solicitudes s WHERE s.plan_id = plan
			AND s.usuario_id = app.usuario_actual() AND s.estado = 'aceptada')
$$;
-- +goose StatementEnd

-- +goose StatementBegin
-- Asistencia real de una persona: a cuántos planes terminados en los que estaba aceptada fue con
-- check-in ("Fue a 23 de 24 planes").
CREATE FUNCTION app.asistencia(persona uuid, OUT fue integer, OUT de integer)
LANGUAGE sql STABLE AS $$
	SELECT count(s.checkin_en)::integer, count(*)::integer
	FROM solicitudes s JOIN planes p ON p.id = s.plan_id
	WHERE s.usuario_id = persona AND s.estado = 'aceptada' AND p.cancelado_en IS NULL
		AND p.empieza_en + make_interval(mins => p.duracion_min) < now()
$$;
-- +goose StatementEnd

-- Dirección exacta: si el plan la oculta, sólo para el organizador y los aceptados.
CREATE TABLE planes_direccion (
	plan_id uuid PRIMARY KEY REFERENCES planes (id) ON DELETE CASCADE,
	direccion text NOT NULL CHECK (length(direccion) BETWEEN 1 AND 160),
	lat double precision NOT NULL CHECK (lat BETWEEN -90 AND 90),
	lng double precision NOT NULL CHECK (lng BETWEEN -180 AND 180)
);
ALTER TABLE planes_direccion ENABLE ROW LEVEL SECURITY;
ALTER TABLE planes_direccion FORCE ROW LEVEL SECURITY;
CREATE POLICY ve ON planes_direccion TO caigo_app, caigo_migrador
	USING (app.ve_direccion(plan_id))
	WITH CHECK (app.es_sistema() OR EXISTS (SELECT 1 FROM planes p WHERE p.id = plan_id AND p.organizador_id = app.usuario_actual()));
GRANT SELECT, INSERT, UPDATE ON planes_direccion TO caigo_app;

-- Mi gente: a quién sumó cada persona después de compartir un plan. Reciben primero sus planes y
-- pueden escribirse por privado.
CREATE TABLE mi_gente (
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	otro_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	creado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (usuario_id, otro_id),
	CHECK (usuario_id <> otro_id)
);
CREATE INDEX mi_gente_otro ON mi_gente (otro_id);
GRANT SELECT, INSERT, DELETE ON mi_gente TO caigo_app;

-- "¿Volverías a salir con…?": privado, sólo lo ve quien lo contestó (y alimenta la reputación).
CREATE TABLE volveria (
	plan_id uuid NOT NULL REFERENCES planes (id) ON DELETE CASCADE,
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	otro_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	si boolean NOT NULL,
	creado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (plan_id, usuario_id, otro_id)
);
SELECT app.aplicar_rls('volveria');
GRANT SELECT, INSERT, UPDATE (si) ON volveria TO caigo_app;

-- Bloqueos: quien bloquea deja de ver y de ser visto por el otro.
CREATE TABLE bloqueos (
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	bloqueado_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	creado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (usuario_id, bloqueado_id),
	CHECK (usuario_id <> bloqueado_id)
);
CREATE INDEX bloqueos_bloqueado ON bloqueos (bloqueado_id);
GRANT SELECT, INSERT, DELETE ON bloqueos TO caigo_app;

-- Reportes a moderación (persona, plan o mensaje). Moderación humana desde el día uno.
CREATE TABLE reportes (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	usuario_id uuid NOT NULL REFERENCES usuarios (id),
	sobre_usuario_id uuid REFERENCES usuarios (id),
	sobre_plan_id uuid REFERENCES planes (id) ON DELETE SET NULL,
	motivo text NOT NULL CHECK (motivo IN ('acoso', 'falso', 'no_vino', 'inapropiado', 'inseguro', 'otro')),
	detalle text NOT NULL DEFAULT '' CHECK (length(detalle) <= 1000),
	estado text NOT NULL DEFAULT 'abierto' CHECK (estado IN ('abierto', 'resuelto', 'descartado')),
	resolucion text NOT NULL DEFAULT '' CHECK (length(resolucion) <= 1000),
	resuelto_por uuid REFERENCES usuarios (id),
	resuelto_en timestamptz,
	creado_en timestamptz NOT NULL DEFAULT now(),
	CHECK (sobre_usuario_id IS NOT NULL OR sobre_plan_id IS NOT NULL)
);
CREATE INDEX reportes_abiertos ON reportes (creado_en) WHERE estado = 'abierto';
SELECT app.aplicar_rls('reportes');
GRANT SELECT, INSERT ON reportes TO caigo_app;
GRANT UPDATE (estado, resolucion, resuelto_por, resuelto_en) ON reportes TO caigo_app;

-- +goose Down
DROP TABLE reportes;
DROP TABLE bloqueos;
DROP TABLE volveria;
DROP TABLE mi_gente;
DROP TABLE planes_direccion;
DROP FUNCTION app.asistencia(uuid);
DROP FUNCTION app.ve_direccion(uuid);
DROP TABLE solicitudes;
DROP TABLE planes;
