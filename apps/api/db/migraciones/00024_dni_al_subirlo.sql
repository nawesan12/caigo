-- +goose Up
-- El DNI se aprueba al subirlo y el equipo lo revisa después (decisión de Nahuel, 16/11: sin DNI
-- aprobado no se puede usar Caigo). revisada_en / revisada_por ya existían: ahora marcan la revisión
-- del equipo, no la aprobación. Una aprobada sin revisar conserva sus fotos hasta que el equipo la
-- revisa (o 30 días: confianza.PurgarFotosDNI).
-- Como el sistema: las tablas tienen RLS forzada.
SELECT set_config('app.sistema', 'si', true);

-- La cola del equipo: aprobadas sin revisar, la más vieja primero.
CREATE INDEX verificaciones_dni_sin_revisar ON verificaciones_dni (creada_en)
	WHERE estado = 'aprobada' AND revisada_en IS NULL;

-- Las que esperaban al equipo quedan aprobadas sin revisar, con la edad del DNI como al aprobar
-- (por año, mes y día). Si el número ya está aprobado en otra cuenta, queda pendiente (lo ve el equipo
-- por soporte: no debería pasar). Una persona menor según el DNI no se aprueba.
CREATE TEMP TABLE aprobar ON COMMIT DROP AS
	SELECT v.id, v.usuario_id, v.nacimiento FROM verificaciones_dni v
	WHERE v.estado = 'pendiente' AND date_part('year', age(current_date, v.nacimiento)) BETWEEN 18 AND 120
		AND NOT EXISTS (SELECT 1 FROM verificaciones_dni o WHERE o.huella = v.huella AND o.estado = 'aprobada')
		AND v.id = (SELECT x.id FROM verificaciones_dni x WHERE x.huella = v.huella AND x.estado = 'pendiente'
			ORDER BY x.creada_en LIMIT 1);
UPDATE verificaciones_dni v SET estado = 'aprobada' FROM aprobar a WHERE a.id = v.id;
UPDATE usuarios u SET dni_en = coalesce(u.dni_en, now()),
		edad = date_part('year', age(current_date, a.nacimiento))::smallint
	FROM aprobar a WHERE a.usuario_id = u.id;
INSERT INTO usuarios_privado (usuario_id, nacimiento) SELECT usuario_id, nacimiento FROM aprobar
	ON CONFLICT (usuario_id) DO UPDATE SET nacimiento = excluded.nacimiento;

SELECT set_config('app.sistema', '', true);

-- +goose Down
-- Lo aprobado no vuelve a pendiente (no se sabe cuáles eran).
DROP INDEX verificaciones_dni_sin_revisar;
