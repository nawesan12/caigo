-- +goose Up
-- Lo que faltaba contra el handoff (docs/contrato-faltantes.md): género autodeclarado, avisos con
-- tiempo y por cercanía, editar/cancelar/sacar gente, reembolsos, backoffice, lista de espera,
-- origen de las altas y recurrencia automática.

-- Género y diversidad: autodeclarados, privados (sólo los ve la persona). Preferencias de avisos.
ALTER TABLE usuarios_privado ADD COLUMN genero text NOT NULL DEFAULT ''
	CHECK (genero IN ('', 'mujer', 'varon', 'no_binario'));
ALTER TABLE usuarios_privado ADD COLUMN diversidad boolean NOT NULL DEFAULT false;
ALTER TABLE usuarios_privado ADD COLUMN avisos_cerca boolean NOT NULL DEFAULT true;
ALTER TABLE usuarios_privado ADD COLUMN avisos_gente boolean NOT NULL DEFAULT true;

-- De dónde llegó la persona (p:<slug>, utm:instagram). Se guarda una sola vez.
ALTER TABLE usuarios ADD COLUMN origen text NOT NULL DEFAULT '' CHECK (length(origen) <= 80);
GRANT UPDATE (origen) ON usuarios TO caigo_app;

-- Última ubicación de la persona, redondeada (~1 km). La app la manda al abrir; el worker la borra
-- a las 24 h. Sólo sirve para el aviso de planes cerca.
CREATE TABLE ubicaciones (
	usuario_id uuid PRIMARY KEY REFERENCES usuarios (id) ON DELETE CASCADE,
	lat double precision NOT NULL CHECK (lat BETWEEN -90 AND 90),
	lng double precision NOT NULL CHECK (lng BETWEEN -180 AND 180),
	actualizada_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ubicaciones_actualizada ON ubicaciones (actualizada_en);
SELECT app.aplicar_rls('ubicaciones');
GRANT SELECT, INSERT, UPDATE (lat, lng, actualizada_en), DELETE ON ubicaciones TO caigo_app;

-- Avisos que ya salieron (recordatorios, check-in, ¿Repetimos?, tu gente, cerca): el worker corre
-- cada minuto y esta tabla lo hace idempotente. También cuenta los topes por día.
CREATE TABLE avisos_enviados (
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	plan_id uuid NOT NULL REFERENCES planes (id) ON DELETE CASCADE,
	tipo text NOT NULL CHECK (tipo IN ('recordatorio', 'checkin', 'repetimos', 'tu_gente', 'cerca')),
	enviado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (usuario_id, plan_id, tipo)
);
CREATE INDEX avisos_enviados_tope ON avisos_enviados (usuario_id, tipo, enviado_en);
GRANT SELECT, INSERT, DELETE ON avisos_enviados TO caigo_app;

-- Editar un plan: también dónde, la categoría y el cobro (planes de lugares). Cortar la serie
-- ("¿Repetimos? No") pone recurrente en false. Motivo de una cancelación del equipo.
ALTER TABLE planes ADD COLUMN cancelado_motivo text NOT NULL DEFAULT '' CHECK (length(cancelado_motivo) <= 300);
GRANT UPDATE (categoria, zona, lugar_nombre, lat, lng, cobro, precio_centavos, sena_pct, recurrente,
	cancelado_motivo) ON planes TO caigo_app;
CREATE INDEX planes_serie ON planes (serie_id, empieza_en) WHERE serie_id IS NOT NULL;

-- Check-in hecho por el lugar en la puerta (planes gratis).
ALTER TABLE solicitudes DROP CONSTRAINT solicitudes_checkin_metodo_check;
ALTER TABLE solicitudes ADD CONSTRAINT solicitudes_checkin_metodo_check
	CHECK (checkin_metodo IN ('geo', 'qr', 'nfc', 'puerta'));

-- Reembolsos: cuánto se devolvió de cada entrada.
ALTER TABLE entradas ADD COLUMN reembolso_centavos integer CHECK (reembolso_centavos >= 0);
ALTER TABLE entradas ADD COLUMN reembolsada_en timestamptz;
GRANT UPDATE (reembolso_centavos, reembolsada_en) ON entradas TO caigo_app;

-- Lista de espera de la landing (ciudades donde todavía no está Caigo).
CREATE TABLE lista_espera (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	email text NOT NULL CHECK (length(email) BETWEEN 3 AND 200 AND email LIKE '%@%'),
	ciudad text NOT NULL DEFAULT '' CHECK (length(ciudad) <= 60),
	creado_en timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX lista_espera_email ON lista_espera (lower(email));
GRANT SELECT, INSERT ON lista_espera TO caigo_app;

-- +goose Down
DROP TABLE lista_espera;
REVOKE UPDATE (reembolso_centavos, reembolsada_en) ON entradas FROM caigo_app;
ALTER TABLE entradas DROP COLUMN reembolsada_en, DROP COLUMN reembolso_centavos;
UPDATE solicitudes SET checkin_metodo = 'qr' WHERE checkin_metodo = 'puerta';
ALTER TABLE solicitudes DROP CONSTRAINT solicitudes_checkin_metodo_check;
ALTER TABLE solicitudes ADD CONSTRAINT solicitudes_checkin_metodo_check CHECK (checkin_metodo IN ('geo', 'qr', 'nfc'));
DROP INDEX planes_serie;
REVOKE UPDATE (categoria, zona, lugar_nombre, lat, lng, cobro, precio_centavos, sena_pct, recurrente,
	cancelado_motivo) ON planes FROM caigo_app;
ALTER TABLE planes DROP COLUMN cancelado_motivo;
DROP TABLE avisos_enviados;
DROP TABLE ubicaciones;
REVOKE UPDATE (origen) ON usuarios FROM caigo_app;
ALTER TABLE usuarios DROP COLUMN origen;
ALTER TABLE usuarios_privado DROP COLUMN avisos_gente, DROP COLUMN avisos_cerca, DROP COLUMN diversidad, DROP COLUMN genero;
