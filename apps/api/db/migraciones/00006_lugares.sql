-- +goose Up
-- Lugares aliados (bares, cafés, canchas): se registran solos y el equipo los aprueba. Publican
-- planes con entrada o seña (Mercado Pago marketplace: cobran con su cuenta, Caigo se queda el
-- cargo por servicio que paga quien compra), validan entradas, tienen placas Caigo Tap y pase de socio.

CREATE TABLE lugares (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	slug text NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{0,46}[a-z0-9])?$'),
	nombre text NOT NULL CHECK (length(nombre) BETWEEN 1 AND 80),
	rubro text NOT NULL DEFAULT '' CHECK (length(rubro) <= 40),
	ciudad text NOT NULL CHECK (length(ciudad) BETWEEN 1 AND 60),
	zona text NOT NULL DEFAULT '' CHECK (length(zona) <= 60),
	direccion text NOT NULL CHECK (length(direccion) BETWEEN 1 AND 160),
	lat double precision NOT NULL CHECK (lat BETWEEN -90 AND 90),
	lng double precision NOT NULL CHECK (lng BETWEEN -180 AND 180),
	foto text NOT NULL DEFAULT '' CHECK (length(foto) <= 500),
	instagram text NOT NULL DEFAULT '' CHECK (length(instagram) <= 60),
	telefono text NOT NULL DEFAULT '' CHECK (length(telefono) <= 30),
	estado text NOT NULL DEFAULT 'pendiente' CHECK (estado IN ('pendiente', 'aprobado', 'rechazado', 'suspendido')),
	motivo text NOT NULL DEFAULT '' CHECK (length(motivo) <= 300),
	-- Pase de socio: cada tap (una vez por día) suma una visita; a las N, el beneficio.
	socio_activo boolean NOT NULL DEFAULT false,
	socio_visitas smallint NOT NULL DEFAULT 5 CHECK (socio_visitas BETWEEN 2 AND 30),
	socio_beneficio text NOT NULL DEFAULT '' CHECK (length(socio_beneficio) <= 80),
	creado_en timestamptz NOT NULL DEFAULT now(),
	aprobado_en timestamptz
);
GRANT SELECT, INSERT ON lugares TO caigo_app;
GRANT UPDATE (nombre, rubro, ciudad, zona, direccion, lat, lng, foto, instagram, telefono, estado, motivo,
	socio_activo, socio_visitas, socio_beneficio, aprobado_en) ON lugares TO caigo_app;

-- Quién maneja cada lugar.
CREATE TABLE lugar_miembros (
	lugar_id uuid NOT NULL REFERENCES lugares (id) ON DELETE CASCADE,
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	rol text NOT NULL DEFAULT 'dueno' CHECK (rol IN ('dueno', 'equipo')),
	creado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (lugar_id, usuario_id)
);
CREATE INDEX lugar_miembros_usuario ON lugar_miembros (usuario_id);
GRANT SELECT, INSERT, DELETE ON lugar_miembros TO caigo_app;

-- Conexión de Mercado Pago de cada lugar (tokens cifrados con la clave maestra, contexto = lugar).
CREATE TABLE mp_conexiones (
	lugar_id uuid PRIMARY KEY REFERENCES lugares (id) ON DELETE CASCADE,
	mp_usuario bigint NOT NULL UNIQUE,
	acceso bytea NOT NULL,
	renovacion bytea NOT NULL,
	vence_en timestamptz NOT NULL,
	conectada_en timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT, INSERT, UPDATE, DELETE ON mp_conexiones TO caigo_app;

-- State de un solo uso del OAuth (con el verificador de PKCE).
CREATE TABLE mp_estados (
	estado_hash bytea PRIMARY KEY,
	lugar_id uuid NOT NULL REFERENCES lugares (id) ON DELETE CASCADE,
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	verificador text NOT NULL,
	vence_en timestamptz NOT NULL,
	usado_en timestamptz
);
GRANT SELECT, INSERT, UPDATE (usado_en), DELETE ON mp_estados TO caigo_app;

-- Planes de lugares: categorías propias del panel y cómo se cobra.
ALTER TABLE planes DROP CONSTRAINT planes_categoria_check;
ALTER TABLE planes ADD CONSTRAINT planes_categoria_check CHECK (categoria IN
	('deporte', 'juego', 'previa', 'salida', 'cita_doble', 'cultura', 'gastronomia', 'otro', 'taller', 'musica', 'fiesta'));
ALTER TABLE planes ADD COLUMN lugar_id uuid REFERENCES lugares (id);
ALTER TABLE planes ADD COLUMN cobro text NOT NULL DEFAULT 'gratis' CHECK (cobro IN ('gratis', 'entrada', 'sena'));
-- Precio por persona y seña (porcentaje del precio), en centavos.
ALTER TABLE planes ADD COLUMN precio_centavos integer CHECK (precio_centavos > 0);
ALTER TABLE planes ADD COLUMN sena_pct smallint CHECK (sena_pct BETWEEN 10 AND 90);
ALTER TABLE planes ADD COLUMN incluye text NOT NULL DEFAULT '' CHECK (length(incluye) <= 200);
ALTER TABLE planes ADD COLUMN cancelacion text NOT NULL DEFAULT 'flex' CHECK (cancelacion IN ('flex', 'mod', 'strict'));
ALTER TABLE planes ADD COLUMN beneficio text NOT NULL DEFAULT '' CHECK (length(beneficio) <= 80);
ALTER TABLE planes ADD COLUMN acepta_grupos boolean NOT NULL DEFAULT true;
-- En los planes de un lugar no hay grupo organizador: el cupo arranca en cero.
ALTER TABLE planes DROP CONSTRAINT planes_somos_check;
ALTER TABLE planes ADD CONSTRAINT planes_somos_check CHECK (somos >= 1 OR (lugar_id IS NOT NULL AND somos = 0));
ALTER TABLE planes ADD CONSTRAINT planes_cobro_precio CHECK (cobro = 'gratis' OR precio_centavos IS NOT NULL);
ALTER TABLE planes ADD CONSTRAINT planes_cobro_sena CHECK (cobro <> 'sena' OR sena_pct IS NOT NULL);
CREATE INDEX planes_lugar_id ON planes (lugar_id, empieza_en) WHERE lugar_id IS NOT NULL;
GRANT UPDATE (incluye, cancelacion, beneficio, acepta_grupos) ON planes TO caigo_app;

-- Entradas de planes con cobro. Al pagarse, la persona queda aceptada en el plan con la cantidad.
-- Código corto (TK-4821) para validar en la puerta.
CREATE TABLE entradas (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	codigo text NOT NULL UNIQUE CHECK (codigo ~ '^TK-[0-9]{4,6}$'),
	plan_id uuid NOT NULL REFERENCES planes (id),
	usuario_id uuid NOT NULL REFERENCES usuarios (id),
	cantidad smallint NOT NULL CHECK (cantidad BETWEEN 1 AND 10),
	modo text NOT NULL CHECK (modo IN ('entrada', 'sena')),
	-- Lo que recibe el lugar, el cargo de Caigo y lo que queda para pagar en el lugar (seña).
	lugar_centavos integer NOT NULL CHECK (lugar_centavos >= 0),
	cargo_centavos integer NOT NULL CHECK (cargo_centavos >= 0),
	saldo_centavos integer NOT NULL DEFAULT 0 CHECK (saldo_centavos >= 0),
	estado text NOT NULL DEFAULT 'pendiente' CHECK (estado IN ('pendiente', 'pagada', 'cancelada', 'reembolsada')),
	referencia text NOT NULL UNIQUE,
	mp_preferencia text,
	mp_pago bigint,
	creada_en timestamptz NOT NULL DEFAULT now(),
	pagada_en timestamptz,
	validada_en timestamptz,
	-- Para el pase de Wallet: se actualiza cuando cambia la entrada.
	actualizada_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX entradas_plan ON entradas (plan_id, estado);
CREATE INDEX entradas_usuario ON entradas (usuario_id, creada_en DESC);
GRANT SELECT, INSERT ON entradas TO caigo_app;
GRANT UPDATE (estado, mp_preferencia, mp_pago, pagada_en, validada_en, actualizada_en) ON entradas TO caigo_app;

-- Placas Caigo Tap. El chip lleva una URL https://caigo.com.ar/t/<id>; con NTAG 424 DNA además
-- lleva el mensaje SUN (PICC cifrado + CMAC) que se verifica con la clave de la placa.
CREATE TABLE placas (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	lugar_id uuid NOT NULL REFERENCES lugares (id) ON DELETE CASCADE,
	nombre text NOT NULL CHECK (length(nombre) BETWEEN 1 AND 40),
	uso text NOT NULL DEFAULT 'puerta' CHECK (uso IN ('puerta', 'barra')),
	-- Clave AES-128 del SDM de la placa (cifrada). NULL: placa común (sólo URL firmada).
	clave_sun bytea,
	-- Último contador SUN leído: un tap copiado (contador repetido) no sirve.
	contador integer NOT NULL DEFAULT -1,
	activa boolean NOT NULL DEFAULT true,
	creada_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX placas_lugar ON placas (lugar_id);
GRANT SELECT, INSERT, UPDATE (nombre, uso, clave_sun, contador, activa) ON placas TO caigo_app;

-- Pase de socio de una persona en un lugar.
CREATE TABLE socios (
	lugar_id uuid NOT NULL REFERENCES lugares (id) ON DELETE CASCADE,
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	visitas smallint NOT NULL DEFAULT 0,
	ultima_visita date,
	-- Beneficios ganados y canjeados (en la barra).
	ganados smallint NOT NULL DEFAULT 0,
	canjeados smallint NOT NULL DEFAULT 0,
	creado_en timestamptz NOT NULL DEFAULT now(),
	actualizado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (lugar_id, usuario_id)
);
GRANT SELECT, INSERT, UPDATE (visitas, ultima_visita, ganados, canjeados, actualizado_en) ON socios TO caigo_app;

-- Dispositivos que registraron un pase de Apple Wallet (web service de PassKit), para avisarles
-- cuando el pase cambia.
CREATE TABLE wallet_registros (
	dispositivo text NOT NULL CHECK (length(dispositivo) <= 200),
	serie text NOT NULL CHECK (length(serie) <= 100),
	push_token text NOT NULL CHECK (length(push_token) <= 200),
	creado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (dispositivo, serie)
);
GRANT SELECT, INSERT, UPDATE (push_token), DELETE ON wallet_registros TO caigo_app;

-- +goose Down
DROP TABLE wallet_registros;
DROP TABLE socios;
DROP TABLE placas;
DROP TABLE entradas;
DROP INDEX planes_lugar_id;
ALTER TABLE planes DROP CONSTRAINT planes_cobro_sena;
ALTER TABLE planes DROP CONSTRAINT planes_somos_check;
ALTER TABLE planes ADD CONSTRAINT planes_somos_check CHECK (somos >= 1);
ALTER TABLE planes DROP CONSTRAINT planes_cobro_precio;
ALTER TABLE planes DROP COLUMN acepta_grupos, DROP COLUMN beneficio, DROP COLUMN cancelacion, DROP COLUMN incluye,
	DROP COLUMN sena_pct, DROP COLUMN precio_centavos, DROP COLUMN cobro, DROP COLUMN lugar_id;
ALTER TABLE planes DROP CONSTRAINT planes_categoria_check;
ALTER TABLE planes ADD CONSTRAINT planes_categoria_check CHECK (categoria IN
	('deporte', 'juego', 'previa', 'salida', 'cita_doble', 'cultura', 'gastronomia', 'otro'));
DROP TABLE mp_estados;
DROP TABLE mp_conexiones;
DROP TABLE lugares;
