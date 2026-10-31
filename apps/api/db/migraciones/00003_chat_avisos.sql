-- +goose Up
-- Chat del plan, mensajes privados, dispositivos para push y el outbox de eventos.

-- Chat grupal del plan: lo ven el organizador y los aceptados. Se archiva (sólo lectura) unos días
-- después del encuentro. Los mensajes de sistema ("Plan completo. A disfrutar.") no tienen autor.
CREATE TABLE mensajes (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	plan_id uuid NOT NULL REFERENCES planes (id) ON DELETE CASCADE,
	usuario_id uuid REFERENCES usuarios (id),
	texto text NOT NULL CHECK (length(texto) BETWEEN 1 AND 2000),
	creado_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX mensajes_plan ON mensajes (plan_id, id);
GRANT SELECT, INSERT ON mensajes TO caigo_app;

-- Privados: sólo entre personas que compartieron un plan o son "mi gente". Cada uno ve los suyos.
CREATE TABLE privados (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	de_id uuid NOT NULL REFERENCES usuarios (id),
	a_id uuid NOT NULL REFERENCES usuarios (id),
	texto text NOT NULL CHECK (length(texto) BETWEEN 1 AND 2000),
	creado_en timestamptz NOT NULL DEFAULT now(),
	leido_en timestamptz,
	CHECK (de_id <> a_id)
);
CREATE INDEX privados_par ON privados (least(de_id, a_id), greatest(de_id, a_id), id);
CREATE INDEX privados_a ON privados (a_id, id) WHERE leido_en IS NULL;
ALTER TABLE privados ENABLE ROW LEVEL SECURITY;
ALTER TABLE privados FORCE ROW LEVEL SECURITY;
-- Cada uno ve los que mandó y los que recibió; manda sólo como sí mismo y marca leídos sólo los recibidos.
CREATE POLICY ver ON privados FOR SELECT TO caigo_app, caigo_migrador
	USING (app.es_sistema() OR de_id = app.usuario_actual() OR a_id = app.usuario_actual());
CREATE POLICY mandar ON privados FOR INSERT TO caigo_app, caigo_migrador
	WITH CHECK (app.es_sistema() OR de_id = app.usuario_actual());
CREATE POLICY leer ON privados FOR UPDATE TO caigo_app, caigo_migrador
	USING (app.es_sistema() OR a_id = app.usuario_actual())
	WITH CHECK (app.es_sistema() OR a_id = app.usuario_actual());
GRANT SELECT, INSERT, UPDATE (leido_en) ON privados TO caigo_app;

-- Dispositivos para push: APNs (iPhone) y FCM (Android). Un token por dispositivo; si se repite
-- (otro usuario en el mismo teléfono) pasa al último.
CREATE TABLE dispositivos (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	plataforma text NOT NULL CHECK (plataforma IN ('ios', 'android')),
	token text NOT NULL UNIQUE CHECK (length(token) BETWEEN 8 AND 4096),
	-- APNs: sandbox (Xcode) o produccion (TestFlight / App Store).
	entorno text NOT NULL DEFAULT 'produccion' CHECK (entorno IN ('sandbox', 'produccion')),
	creado_en timestamptz NOT NULL DEFAULT now(),
	visto_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX dispositivos_usuario ON dispositivos (usuario_id);
SELECT app.aplicar_rls('dispositivos');
GRANT SELECT, INSERT, UPDATE (usuario_id, entorno, visto_en), DELETE ON dispositivos TO caigo_app;

-- Outbox: eventos que se publican en la misma transacción que el cambio. Los consume el worker.
CREATE TABLE outbox (
	id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	tipo text NOT NULL,
	datos jsonb NOT NULL,
	creado_en timestamptz NOT NULL DEFAULT now(),
	proximo_intento timestamptz NOT NULL DEFAULT now(),
	intentos integer NOT NULL DEFAULT 0,
	ultimo_error text,
	publicado_en timestamptz
);
CREATE INDEX outbox_pendientes ON outbox (proximo_intento, id) WHERE publicado_en IS NULL;
GRANT SELECT, INSERT, UPDATE (proximo_intento, intentos, ultimo_error, publicado_en) ON outbox TO caigo_app;

-- +goose StatementBegin
-- Tiempo real: cada mensaje nuevo avisa por NOTIFY y la API lo reparte por WebSocket a quien esté
-- conectado. Sólo viaja el id; la API lo lee de nuevo con los permisos de cada uno.
CREATE FUNCTION app.avisar_mensaje() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
	PERFORM pg_notify('caigo_tiempo_real', json_build_object('tipo', TG_ARGV[0], 'id', NEW.id)::text);
	RETURN NULL;
END
$$;
-- +goose StatementEnd
CREATE TRIGGER avisar AFTER INSERT ON mensajes FOR EACH ROW EXECUTE FUNCTION app.avisar_mensaje('mensaje');
CREATE TRIGGER avisar AFTER INSERT ON privados FOR EACH ROW EXECUTE FUNCTION app.avisar_mensaje('privado');

-- +goose Down
DROP TRIGGER avisar ON privados;
DROP TRIGGER avisar ON mensajes;
DROP FUNCTION app.avisar_mensaje();
DROP TABLE outbox;
DROP TABLE dispositivos;
DROP TABLE privados;
DROP TABLE mensajes;
