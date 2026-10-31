-- +goose Up
-- Lo que suma Caigo Plus además de las sumadas: "Tu plan destacado" (1 por semana, va primero en
-- Ahora y Explorar hasta que pasa). "Me encanta" (1 por día gratis, 5 con Plus) y los filtros
-- finos no necesitan columnas: salen de intereses y de los planes.
ALTER TABLE planes ADD COLUMN destacado_en timestamptz;
CREATE INDEX planes_destacados ON planes (organizador_id, destacado_en DESC) WHERE destacado_en IS NOT NULL;
GRANT UPDATE (destacado_en) ON planes TO caigo_app;

-- +goose Down
REVOKE UPDATE (destacado_en) ON planes FROM caigo_app;
DROP INDEX planes_destacados;
ALTER TABLE planes DROP COLUMN destacado_en;
