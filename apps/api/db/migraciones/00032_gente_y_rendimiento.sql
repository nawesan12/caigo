-- +goose Up
-- Revisión del comité (19/11), con Gente y Citas de vuelta en la cabecera de Ahora.

-- El «me encanta» se cuenta por su propia fecha: pasar de «me interesa» a «me encanta» no corre la de la
-- reacción (creado_en, que cuenta las sumadas del día y olvida los pasos), y el tope de encantas no se
-- saltea reaccionando primero con «me interesa».
ALTER TABLE intereses ADD COLUMN encanta_en timestamptz;
UPDATE intereses SET encanta_en = creado_en WHERE accion = 'encanta';
GRANT UPDATE (encanta_en) ON intereses TO caigo_app;
CREATE INDEX intereses_encanta_hoy ON intereses (usuario_id, encanta_en) WHERE accion = 'encanta';

-- Privados por cada lado (Conversaciones, el borrado de cuentas y los plazos filtran por de_id o por a_id).
CREATE INDEX privados_de ON privados (de_id, id);
CREATE INDEX privados_a_todos ON privados (a_id, id);
-- Mensajes de una persona (plazos y baja).
CREATE INDEX mensajes_usuario ON mensajes (usuario_id);

-- +goose Down
DROP INDEX mensajes_usuario;
DROP INDEX privados_a_todos;
DROP INDEX privados_de;
DROP INDEX intereses_encanta_hoy;
ALTER TABLE intereses DROP COLUMN encanta_en;
