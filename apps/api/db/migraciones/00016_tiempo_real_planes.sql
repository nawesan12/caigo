-- +goose Up
-- Tiempo real de los planes: cada cambio en una solicitud (pedir, aceptar, rechazar, salir) o en el
-- plan (editar, cancelar) avisa por el mismo canal que los mensajes. La API lo reparte por WebSocket
-- al organizador y a quienes pidieron sumarse, y las pantallas del plan se recargan solas.

-- +goose StatementBegin
CREATE FUNCTION app.avisar_plan() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
	plan uuid;
BEGIN
	IF TG_TABLE_NAME = 'planes' THEN
		plan := NEW.id;
	ELSIF TG_OP = 'DELETE' THEN
		plan := OLD.plan_id;
	ELSE
		plan := NEW.plan_id;
	END IF;
	PERFORM pg_notify('caigo_tiempo_real', json_build_object('tipo', 'plan', 'plan', plan)::text);
	RETURN NULL;
END
$$;
-- +goose StatementEnd
CREATE TRIGGER avisar_plan AFTER INSERT OR UPDATE OR DELETE ON solicitudes FOR EACH ROW EXECUTE FUNCTION app.avisar_plan();
CREATE TRIGGER avisar_plan AFTER UPDATE ON planes FOR EACH ROW EXECUTE FUNCTION app.avisar_plan();

-- +goose Down
DROP TRIGGER avisar_plan ON planes;
DROP TRIGGER avisar_plan ON solicitudes;
DROP FUNCTION app.avisar_plan();
