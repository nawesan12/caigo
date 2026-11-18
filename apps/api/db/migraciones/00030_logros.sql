-- +goose Up
-- Premios por ir (niveles, insignias, racha semanal, «Anfitrión»): se calculan de los check-ins que ya
-- existen (internal/dominio/logros). Acá sólo va lo nuevo que hay que guardar.

-- «Ocultar mis insignias»: los demás ven el nivel y la asistencia, no las insignias.
ALTER TABLE usuarios ADD COLUMN ocultar_insignias boolean NOT NULL DEFAULT false;
-- Código para «Traé a alguien» (se arma la primera vez que se pide el link).
ALTER TABLE usuarios ADD COLUMN codigo text UNIQUE CHECK (codigo ~ '^[a-z2-9]{8}$');
GRANT UPDATE (ocultar_insignias, codigo) ON usuarios TO caigo_app;

-- «No me interesa» (deslizar a la izquierda): el plan no aparece en Ahora ni en Explorar hasta `hasta`
-- (el fin del día). «Deshacer» borra la fila.
CREATE TABLE planes_ocultos (
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	plan_id uuid NOT NULL REFERENCES planes (id) ON DELETE CASCADE,
	hasta timestamptz NOT NULL,
	PRIMARY KEY (usuario_id, plan_id)
);
SELECT app.aplicar_rls('planes_ocultos');
GRANT SELECT, INSERT, UPDATE (hasta), DELETE ON planes_ocultos TO caigo_app;

-- Referidos: quién invitó a quién. El premio (una semana de Plus para los dos) llega recién con el DNI
-- aprobado y el primer check-in que cuenta del referido; como mucho 10 premios por mes a quien invita.
CREATE TABLE referidos (
	referido_id uuid PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
	referente_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	creado_en timestamptz NOT NULL DEFAULT now(),
	premiado_en timestamptz,
	-- Se cumplió, pero quien invita ya tenía 10 premios ese mes: el referido sí recibe el suyo.
	sin_premio_referente boolean NOT NULL DEFAULT false,
	CHECK (referido_id <> referente_id)
);
CREATE INDEX referidos_referente ON referidos (referente_id, premiado_en);
CREATE INDEX referidos_pendientes ON referidos (creado_en) WHERE premiado_en IS NULL;
-- Se escribe como sistema (el premio toca a dos personas); cada una lee lo suyo.
ALTER TABLE referidos ENABLE ROW LEVEL SECURITY;
ALTER TABLE referidos FORCE ROW LEVEL SECURITY;
CREATE POLICY propia ON referidos TO caigo_app, caigo_migrador
	USING (referido_id = app.usuario_actual() OR referente_id = app.usuario_actual() OR app.es_sistema())
	WITH CHECK (referido_id = app.usuario_actual() OR app.es_sistema());
GRANT SELECT, INSERT, UPDATE (premiado_en, sin_premio_referente) ON referidos TO caigo_app;

-- La semana de Plus del premio vive con las otras suscripciones.
ALTER TABLE suscripciones DROP CONSTRAINT suscripciones_origen_check;
ALTER TABLE suscripciones ADD CONSTRAINT suscripciones_origen_check
	CHECK (origen IN ('apple', 'google', 'mercadopago', 'referido'));

-- Beneficio por nivel en un lugar aliado (por ejemplo «10 % off» desde Habitué). Lo paga el lugar.
ALTER TABLE lugares ADD COLUMN beneficio_nivel text NOT NULL DEFAULT '' CHECK (length(beneficio_nivel) <= 80);
GRANT UPDATE (beneficio_nivel) ON lugares TO caigo_app;

-- Canjes del beneficio por nivel: uno por persona, por lugar y por día (el lugar los ve en su panel).
CREATE TABLE canjes_nivel (
	lugar_id uuid NOT NULL REFERENCES lugares (id) ON DELETE CASCADE,
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	dia date NOT NULL,
	beneficio text NOT NULL CHECK (length(beneficio) <= 80),
	nivel text NOT NULL CHECK (length(nivel) <= 20),
	creado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (lugar_id, usuario_id, dia)
);
CREATE INDEX canjes_nivel_lugar ON canjes_nivel (lugar_id, creado_en DESC);
-- Los ve y los anota la persona, o quien atiende el lugar (el canje sale de su check-in en la puerta).
ALTER TABLE canjes_nivel ENABLE ROW LEVEL SECURITY;
ALTER TABLE canjes_nivel FORCE ROW LEVEL SECURITY;
CREATE POLICY propia ON canjes_nivel TO caigo_app, caigo_migrador
	USING (usuario_id = app.usuario_actual() OR app.es_sistema() OR EXISTS (SELECT 1 FROM lugar_miembros m
		WHERE m.lugar_id = canjes_nivel.lugar_id AND m.usuario_id = app.usuario_actual()))
	WITH CHECK (usuario_id = app.usuario_actual() OR app.es_sistema() OR EXISTS (SELECT 1 FROM lugar_miembros m
		WHERE m.lugar_id = canjes_nivel.lugar_id AND m.usuario_id = app.usuario_actual()));
GRANT SELECT, INSERT ON canjes_nivel TO caigo_app;

-- +goose Down
DROP TABLE canjes_nivel;
ALTER TABLE lugares DROP COLUMN beneficio_nivel;
DELETE FROM suscripciones WHERE origen = 'referido';
ALTER TABLE suscripciones DROP CONSTRAINT suscripciones_origen_check;
ALTER TABLE suscripciones ADD CONSTRAINT suscripciones_origen_check CHECK (origen IN ('apple', 'google', 'mercadopago'));
DROP TABLE referidos;
DROP TABLE planes_ocultos;
ALTER TABLE usuarios DROP COLUMN codigo, DROP COLUMN ocultar_insignias;
