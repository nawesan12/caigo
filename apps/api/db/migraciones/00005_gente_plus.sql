-- +goose Up
-- Modos Gente y Citas (el mazo de personas), invitaciones a planes y Caigo Plus.

-- Lo que una persona hizo con otra en el mazo. prendo (Gente: "ME PRENDO", la suma a su gente),
-- interesa (Citas: "ME INTERESA"), encanta (Citas: "me encanta") o paso. En Citas, si es mutuo,
-- se abre el chat.
CREATE TABLE intereses (
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	otro_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	modo text NOT NULL CHECK (modo IN ('gente', 'citas')),
	accion text NOT NULL CHECK (accion IN ('prendo', 'interesa', 'encanta', 'paso')),
	-- Quien lo recibió lo descartó de su lista ("Te sumaron" / "Les interesás").
	descartado_en timestamptz,
	creado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (usuario_id, otro_id, modo),
	CHECK (usuario_id <> otro_id)
);
CREATE INDEX intereses_otro ON intereses (otro_id, modo, creado_en DESC) WHERE accion <> 'paso';
CREATE INDEX intereses_hoy ON intereses (usuario_id, creado_en) WHERE accion <> 'paso';
GRANT SELECT, INSERT, UPDATE (accion, descartado_en, creado_en) ON intereses TO caigo_app;

-- Invitaciones a un plan ("Te invitan"): le llegan a la persona como una invitación; si acepta,
-- entra (directo si invitó el organizador; si no, como solicitud).
CREATE TABLE invitaciones (
	plan_id uuid NOT NULL REFERENCES planes (id) ON DELETE CASCADE,
	de_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	a_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	estado text NOT NULL DEFAULT 'pendiente' CHECK (estado IN ('pendiente', 'aceptada', 'descartada')),
	creada_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (plan_id, a_id),
	CHECK (de_id <> a_id)
);
CREATE INDEX invitaciones_a ON invitaciones (a_id, creada_en DESC) WHERE estado = 'pendiente';
GRANT SELECT, INSERT, UPDATE (estado) ON invitaciones TO caigo_app;

-- Caigo Plus. Una fila por suscripción en cada tienda (Apple, Google o Mercado Pago en la web);
-- Plus está activo si alguna vence en el futuro.
CREATE TABLE suscripciones (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	origen text NOT NULL CHECK (origen IN ('apple', 'google', 'mercadopago')),
	-- originalTransactionId (Apple), purchaseToken (Google) o id del preapproval (MP).
	externo text NOT NULL,
	plan text NOT NULL CHECK (plan IN ('semana', 'mes', 'temporada')),
	estado text NOT NULL CHECK (estado IN ('pendiente', 'activa', 'prueba', 'cancelada', 'vencida')),
	vence_en timestamptz,
	renueva boolean NOT NULL DEFAULT true,
	creada_en timestamptz NOT NULL DEFAULT now(),
	actualizada_en timestamptz NOT NULL DEFAULT now(),
	UNIQUE (origen, externo)
);
CREATE INDEX suscripciones_usuario ON suscripciones (usuario_id, vence_en DESC);
SELECT app.aplicar_rls('suscripciones');
GRANT SELECT, INSERT, UPDATE (usuario_id, plan, estado, vence_en, renueva, actualizada_en) ON suscripciones TO caigo_app;

-- +goose Down
DROP TABLE suscripciones;
DROP TABLE invitaciones;
DROP TABLE intereses;
