-- +goose Up
-- Topes de pedidos por clave (IP o sesión) y ventana: un upsert atómico por pedido.
CREATE TABLE intentos (
	clave text PRIMARY KEY,
	cantidad integer NOT NULL,
	desde timestamptz NOT NULL
);
GRANT SELECT, INSERT, UPDATE, DELETE ON intentos TO caigo_app;

-- +goose Down
DROP TABLE intentos;
