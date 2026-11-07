-- +goose Up
-- Entradas cobradas con la API de Orders de Mercado Pago (reemplaza a Preferencias): la orden y su
-- transacción, para conciliar los avisos de tipo order y para devolver con /v1/orders/{id}/refund.
-- Las entradas viejas siguen con mp_preferencia y mp_pago.
ALTER TABLE entradas ADD COLUMN mp_orden text, ADD COLUMN mp_transaccion text;
GRANT UPDATE (mp_orden, mp_transaccion) ON entradas TO caigo_app;

-- +goose Down
REVOKE UPDATE (mp_orden, mp_transaccion) ON entradas FROM caigo_app;
ALTER TABLE entradas DROP COLUMN mp_orden, DROP COLUMN mp_transaccion;
