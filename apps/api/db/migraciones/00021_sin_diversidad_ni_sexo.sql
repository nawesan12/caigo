-- +goose Up
-- AAIP 1.2 (opción 1): Caigo deja de tener datos sensibles de orientación o identidad de género.
-- - La «diversidad» ya no va en el perfil: para un plan «Solo diversidades» la persona lo confirma
--   en el mismo pedido de sumarse y no se guarda.
-- - Del DNI sólo se valida la edad: el sexo (M/F/X) que leía la app del PDF417 ya no se guarda.
-- Primero se vacían los valores (como el sistema: las dos tablas tienen RLS forzada) y después se
-- borran las columnas.
SELECT set_config('app.sistema', 'si', true);
UPDATE usuarios_privado SET diversidad = false WHERE diversidad;
UPDATE verificaciones_dni SET sexo = '' WHERE sexo <> '';
ALTER TABLE usuarios_privado DROP COLUMN diversidad;
ALTER TABLE verificaciones_dni DROP COLUMN sexo;
SELECT set_config('app.sistema', '', true);

-- +goose Down
-- Vuelven las columnas, vacías: lo que se borró no se recupera.
ALTER TABLE verificaciones_dni ADD COLUMN sexo text NOT NULL DEFAULT '' CHECK (sexo IN ('', 'M', 'F', 'X'));
ALTER TABLE usuarios_privado ADD COLUMN diversidad boolean NOT NULL DEFAULT false;
