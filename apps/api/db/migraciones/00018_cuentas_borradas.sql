-- +goose Up
-- Borrar la cuenta en dos tiempos (igual en las cuatro apps de Grupo Vimo): DELETE /v1/yo la
-- desactiva en el momento (baja_en; sin sesiones, dispositivos ni ubicación, y sus planes futuros
-- cancelados) y el worker borra sus datos personales a los 30 días (purgada_en). Mientras tanto el
-- clerk_id se queda en la fila: el ingreso responde cuenta_borrada (409) y la cuenta se puede
-- recuperar a mano (caigoctl recuperar-cuenta) si la persona escribe a hola@. Purgada, el clerk_id se
-- libera y si vuelve a entrar arranca de cero.
ALTER TABLE usuarios ADD COLUMN purgada_en timestamptz;
CREATE INDEX usuarios_por_purgar ON usuarios (baja_en) WHERE baja_en IS NOT NULL AND purgada_en IS NULL;
GRANT UPDATE (purgada_en) ON usuarios TO caigo_app;
-- El mail de la cuenta en Clerk (se copia al entrar): GET /v1/yo lo devuelve para confirmar la baja
-- escribiéndolo, y con él se encuentra una cuenta borrada cuando la persona escribe. La purga lo vacía.
ALTER TABLE usuarios_privado ADD COLUMN email text NOT NULL DEFAULT '' CHECK (length(email) <= 200);
-- La purga borra lo que dice con quién se interesó en Gente y Citas.
GRANT DELETE ON intereses, invitaciones TO caigo_app;

-- +goose Down
REVOKE DELETE ON intereses, invitaciones FROM caigo_app;
ALTER TABLE usuarios_privado DROP COLUMN email;
DROP INDEX usuarios_por_purgar;
REVOKE UPDATE (purgada_en) ON usuarios FROM caigo_app;
ALTER TABLE usuarios DROP COLUMN purgada_en;
