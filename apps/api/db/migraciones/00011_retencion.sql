-- +goose Up
-- Los reportes cerrados se borran a los 2 años (lo dice la política de privacidad); el worker lo hace.
GRANT DELETE ON reportes TO caigo_app;

-- +goose Down
REVOKE DELETE ON reportes FROM caigo_app;
