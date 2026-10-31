-- +goose Up
-- Pedidos de placas Caigo Tap desde el panel de lugares (antes era un mail). El equipo las prepara,
-- coordina el envío con el lugar y marca el pedido como atendido desde /equipo.
CREATE TABLE pedidos_placas (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	lugar_id uuid NOT NULL REFERENCES lugares (id) ON DELETE CASCADE,
	-- Quién lo pidió (un miembro del lugar).
	usuario_id uuid NOT NULL REFERENCES usuarios (id),
	cantidad smallint NOT NULL CHECK (cantidad BETWEEN 1 AND 20),
	-- comun: placa con URL firmada; ntag424: chip NTAG 424 DNA con SUN.
	tipo text NOT NULL CHECK (tipo IN ('comun', 'ntag424')),
	nota text NOT NULL DEFAULT '' CHECK (length(nota) <= 500),
	creado_en timestamptz NOT NULL DEFAULT now(),
	atendido_en timestamptz,
	atendido_por uuid REFERENCES usuarios (id),
	-- Lo que anotó el equipo al atenderlo (envío, seguimiento). Lo ve el lugar.
	respuesta text NOT NULL DEFAULT '' CHECK (length(respuesta) <= 500)
);
CREATE INDEX pedidos_placas_lugar ON pedidos_placas (lugar_id, creado_en DESC);
CREATE INDEX pedidos_placas_pendientes ON pedidos_placas (creado_en) WHERE atendido_en IS NULL;
GRANT SELECT, INSERT ON pedidos_placas TO caigo_app;
GRANT UPDATE (atendido_en, atendido_por, respuesta) ON pedidos_placas TO caigo_app;

-- +goose Down
DROP TABLE pedidos_placas;
