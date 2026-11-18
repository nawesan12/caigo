-- +goose Up
-- Lo que pidió la revisión del comité (19/11) sobre el día del plan y los logros.

-- «¿Todo bien?» en dos pasos: a la hora, primero un push a la persona («¿Llegaste? Tocá Estoy bien o en
-- 10 min le escribimos a X»); recién si sigue sin check-in, el mail al contacto (avisado_en).
ALTER TABLE todo_bien ADD COLUMN preguntado_en timestamptz;
GRANT UPDATE (preguntado_en) ON todo_bien TO caigo_app;

-- Los mails de «¿Todo bien?» que salieron: uno por plan y persona, y un tope por dirección (de todas las
-- cuentas). La dirección se guarda como huella (sha256 del mail en minúsculas), nunca en claro. Se borran
-- a los 7 días (el plan ya pasó).
CREATE TABLE todo_bien_mails (
	plan_id uuid NOT NULL REFERENCES planes (id) ON DELETE CASCADE,
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	destino text NOT NULL CHECK (destino ~ '^[0-9a-f]{64}$'),
	enviado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (plan_id, usuario_id)
);
CREATE INDEX todo_bien_mails_destino ON todo_bien_mails (destino, enviado_en);
SELECT app.aplicar_rls('todo_bien_mails');
GRANT SELECT, INSERT, DELETE ON todo_bien_mails TO caigo_app;

-- Las direcciones que pidieron no recibir más mails de «¿Todo bien?» (el link de baja). Huella, no el mail.
-- Sólo el sistema la lee y la escribe.
CREATE TABLE correo_supresion (
	destino text PRIMARY KEY CHECK (destino ~ '^[0-9a-f]{64}$'),
	creado_en timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE correo_supresion ENABLE ROW LEVEL SECURITY;
ALTER TABLE correo_supresion FORCE ROW LEVEL SECURITY;
CREATE POLICY sistema ON correo_supresion TO caigo_app, caigo_migrador USING (app.es_sistema()) WITH CHECK (app.es_sistema());
GRANT SELECT, INSERT ON correo_supresion TO caigo_app;

-- Referidos: cuándo se miró por última vez uno que todavía no cumple (la fila del worker no se estanca).
ALTER TABLE referidos ADD COLUMN revisado_en timestamptz;
GRANT UPDATE (revisado_en) ON referidos TO caigo_app;
DROP INDEX referidos_pendientes;
CREATE INDEX referidos_pendientes ON referidos ((coalesce(revisado_en, creado_en))) WHERE premiado_en IS NULL;
-- Las políticas, separadas: el referido sólo inserta su fila; los campos del premio los escribe el sistema.
DROP POLICY propia ON referidos;
CREATE POLICY ver ON referidos FOR SELECT TO caigo_app, caigo_migrador
	USING (referido_id = app.usuario_actual() OR referente_id = app.usuario_actual() OR app.es_sistema());
CREATE POLICY alta ON referidos FOR INSERT TO caigo_app, caigo_migrador
	WITH CHECK (referido_id = app.usuario_actual() OR app.es_sistema());
CREATE POLICY sistema ON referidos FOR UPDATE TO caigo_app, caigo_migrador
	USING (app.es_sistema()) WITH CHECK (app.es_sistema());
CREATE POLICY borrar ON referidos FOR DELETE TO caigo_app, caigo_migrador USING (app.es_sistema());
GRANT DELETE ON referidos TO caigo_app;

-- La semana de regalo de un referido, para quien ya paga una suscripción que se renueva, queda guardada
-- (estado 'pendiente', sin vencimiento) y arranca cuando esa suscripción deja de renovarse.
GRANT DELETE ON suscripciones TO caigo_app;

-- El promedio de caritas de quien organiza: lo calcula el worker una vez por día (no en cada pedido, que
-- deja deducir la carita de cada uno comparando antes y después) y redondeado a medio punto.
CREATE TABLE caritas_organizador (
	usuario_id uuid PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
	promedio numeric(2, 1) NOT NULL CHECK (promedio BETWEEN 1 AND 5),
	-- En escalones (3, 5, 10, 20, 50, 100), nunca la cantidad exacta.
	opiniones integer NOT NULL CHECK (opiniones >= 3),
	calculado_en timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE caritas_organizador ENABLE ROW LEVEL SECURITY;
ALTER TABLE caritas_organizador FORCE ROW LEVEL SECURITY;
CREATE POLICY ver ON caritas_organizador FOR SELECT TO caigo_app, caigo_migrador USING (true);
CREATE POLICY sistema ON caritas_organizador FOR ALL TO caigo_app, caigo_migrador USING (app.es_sistema()) WITH CHECK (app.es_sistema());
GRANT SELECT, INSERT, UPDATE, DELETE ON caritas_organizador TO caigo_app;

-- «Marcar que vino»: quien organiza corrige el check-in de alguien que fue y no tocó «Llegué».
ALTER TABLE solicitudes DROP CONSTRAINT solicitudes_checkin_metodo_check;
ALTER TABLE solicitudes ADD CONSTRAINT solicitudes_checkin_metodo_check
	CHECK (checkin_metodo IN ('geo', 'qr', 'nfc', 'puerta', 'organizador'));

-- «No marcaste Llegué en X»: a la persona, al día siguiente de un plan donde quedó sin check-in.
ALTER TABLE avisos_enviados DROP CONSTRAINT avisos_enviados_tipo_check;
ALTER TABLE avisos_enviados ADD CONSTRAINT avisos_enviados_tipo_check CHECK (tipo IN
	('recordatorio', 'checkin', 'repetimos', 'tu_gente', 'cerca', 'vispera', 'como_estuvo', 'sin_llegue'));

-- Plazos (confianza.Plazos) y la purga de una cuenta: los canjes del beneficio por nivel se borran.
GRANT DELETE ON canjes_nivel TO caigo_app;

-- +goose Down
REVOKE DELETE ON canjes_nivel FROM caigo_app;
DELETE FROM avisos_enviados WHERE tipo = 'sin_llegue';
ALTER TABLE avisos_enviados DROP CONSTRAINT avisos_enviados_tipo_check;
ALTER TABLE avisos_enviados ADD CONSTRAINT avisos_enviados_tipo_check CHECK (tipo IN
	('recordatorio', 'checkin', 'repetimos', 'tu_gente', 'cerca', 'vispera', 'como_estuvo'));
UPDATE solicitudes SET checkin_metodo = 'qr' WHERE checkin_metodo = 'organizador';
ALTER TABLE solicitudes DROP CONSTRAINT solicitudes_checkin_metodo_check;
ALTER TABLE solicitudes ADD CONSTRAINT solicitudes_checkin_metodo_check CHECK (checkin_metodo IN ('geo', 'qr', 'nfc', 'puerta'));
DROP TABLE caritas_organizador;
REVOKE DELETE ON suscripciones FROM caigo_app;
DELETE FROM suscripciones WHERE origen = 'referido' AND estado = 'pendiente';
REVOKE DELETE ON referidos FROM caigo_app;
DROP POLICY borrar ON referidos;
DROP POLICY sistema ON referidos;
DROP POLICY alta ON referidos;
DROP POLICY ver ON referidos;
CREATE POLICY propia ON referidos TO caigo_app, caigo_migrador
	USING (referido_id = app.usuario_actual() OR referente_id = app.usuario_actual() OR app.es_sistema())
	WITH CHECK (referido_id = app.usuario_actual() OR app.es_sistema());
DROP INDEX referidos_pendientes;
CREATE INDEX referidos_pendientes ON referidos (creado_en) WHERE premiado_en IS NULL;
ALTER TABLE referidos DROP COLUMN revisado_en;
DROP TABLE correo_supresion;
DROP TABLE todo_bien_mails;
ALTER TABLE todo_bien DROP COLUMN preguntado_en;
