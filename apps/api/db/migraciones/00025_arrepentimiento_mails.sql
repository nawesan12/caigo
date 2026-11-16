-- +goose Up
-- Los mails del botón de arrepentimiento (antes sólo salía un push al equipo, y sin dispositivos nadie
-- se enteraba): el acuse a quien lo pidió y el aviso a hola@caigo.com.ar. Cada uno se marca antes de
-- mandarlo (y se desmarca si el SMTP falla), así un reintento del outbox no lo manda dos veces.
ALTER TABLE arrepentimientos ADD COLUMN acuse_en timestamptz, ADD COLUMN aviso_en timestamptz;
GRANT UPDATE (acuse_en, aviso_en) ON arrepentimientos TO caigo_app;

-- +goose Down
REVOKE UPDATE (acuse_en, aviso_en) ON arrepentimientos FROM caigo_app;
ALTER TABLE arrepentimientos DROP COLUMN acuse_en, DROP COLUMN aviso_en;
