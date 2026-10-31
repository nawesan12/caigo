-- +goose Up
-- Botón de arrepentimiento (Resolución 424/2020 de la Secretaría de Comercio Interior): cualquiera
-- puede pedir revocar una compra hecha en caigo.com.ar, sin cuenta, y recibe un código al instante.
-- El equipo lo atiende desde /equipo (el reembolso se hace en Mercado Pago).
CREATE TABLE arrepentimientos (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	codigo text NOT NULL UNIQUE CHECK (codigo ~ '^ARR-[0-9A-Z]{6}$'),
	nombre text NOT NULL CHECK (length(nombre) BETWEEN 1 AND 80),
	email text NOT NULL CHECK (length(email) BETWEEN 3 AND 200 AND email LIKE '%@%'),
	-- Qué compró y cuándo (lo escribe la persona; puede incluir el número de operación de MP).
	compra text NOT NULL CHECK (length(compra) BETWEEN 1 AND 500),
	creado_en timestamptz NOT NULL DEFAULT now(),
	atendido_en timestamptz,
	atendido_por uuid REFERENCES usuarios (id),
	nota text NOT NULL DEFAULT '' CHECK (length(nota) <= 500)
);
CREATE INDEX arrepentimientos_pendientes ON arrepentimientos (creado_en) WHERE atendido_en IS NULL;
GRANT SELECT, INSERT ON arrepentimientos TO caigo_app;
GRANT UPDATE (atendido_en, atendido_por, nota) ON arrepentimientos TO caigo_app;

-- +goose Down
DROP TABLE arrepentimientos;
