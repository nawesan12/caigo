-- +goose Up
-- La asistencia real («Fue a N de M planes») cuenta sólo los planes donde se usó el check-in (alguien
-- lo hizo). Antes un plan terminado sin ningún check-in le sumaba una falta a cada persona, también a
-- quien lo organizó: como el check-in es opcional, casi todos quedaban en «0 % asistencia».
-- +goose StatementBegin
CREATE OR REPLACE FUNCTION app.asistencia(persona uuid, OUT fue integer, OUT de integer)
LANGUAGE sql STABLE AS $$
	SELECT count(s.checkin_en)::integer, count(*)::integer
	FROM solicitudes s JOIN planes p ON p.id = s.plan_id
	WHERE s.usuario_id = persona AND s.estado = 'aceptada' AND p.cancelado_en IS NULL
		AND p.empieza_en + make_interval(mins => p.duracion_min) < now()
		AND EXISTS (SELECT 1 FROM solicitudes x WHERE x.plan_id = p.id AND x.checkin_en IS NOT NULL)
$$;
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
CREATE OR REPLACE FUNCTION app.asistencia(persona uuid, OUT fue integer, OUT de integer)
LANGUAGE sql STABLE AS $$
	SELECT count(s.checkin_en)::integer, count(*)::integer
	FROM solicitudes s JOIN planes p ON p.id = s.plan_id
	WHERE s.usuario_id = persona AND s.estado = 'aceptada' AND p.cancelado_en IS NULL
		AND p.empieza_en + make_interval(mins => p.duracion_min) < now()
$$;
-- +goose StatementEnd
