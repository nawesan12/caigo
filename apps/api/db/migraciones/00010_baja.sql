-- +goose Up
-- Darse de baja desde la app (lo piden App Store y Play). La fila de usuarios queda anónima (para
-- que el historial de planes y pagos no se rompa) y libera el id de Clerk: si la persona vuelve a
-- entrar, arranca de cero. Se borran fotos, selfie, DNI, teléfono, sesiones y dispositivos.
GRANT UPDATE (clerk_id) ON usuarios TO caigo_app;
GRANT DELETE ON verificaciones_dni TO caigo_app;

-- +goose Down
REVOKE DELETE ON verificaciones_dni FROM caigo_app;
REVOKE UPDATE (clerk_id) ON usuarios FROM caigo_app;
