-- +goose Up
-- El worker borra los eventos publicados hace más de 30 días: faltaba el permiso, y como la
-- limpieza iba en una sola transacción, tampoco se borraban los topes ni las sesiones vencidas.
GRANT DELETE ON outbox TO caigo_app;

-- +goose Down
REVOKE DELETE ON outbox FROM caigo_app;
