package confianza

// Plazos son las limpiezas de lo que queda después de purgar una cuenta, y de los intereses del
// modo Gente y Citas. El worker las corre cada hora como sistema, una por una (si una falla, las
// demás se hacen igual). Son los plazos de la política de privacidad (apps/web/src/routes/privacidad):
// si cambia uno, cambia el otro.
var Plazos = []string{
	// Mensajes del chat de un plan escritos por una cuenta purgada: quedan anónimos («Cuenta
	// borrada») mientras el plan sigue y se borran al año de que terminó (o de la purga, lo último).
	`DELETE FROM mensajes m USING usuarios u, planes p WHERE u.id = m.usuario_id AND p.id = m.plan_id
		AND u.purgada_en IS NOT NULL
		AND greatest(u.purgada_en, p.empieza_en + make_interval(mins => p.duracion_min)) < now() - interval '1 year'`,
	// Privados con una cuenta purgada (los que mandó y los que recibió): al año de la purga.
	`DELETE FROM privados x USING usuarios u WHERE (x.de_id = u.id OR x.a_id = u.id)
		AND u.purgada_en < now() - interval '1 year'`,
	// Asistencia (a qué planes se sumó y si hizo check-in) y «¿Volverías?»: al año de la purga.
	`DELETE FROM solicitudes s USING usuarios u WHERE s.usuario_id = u.id AND u.purgada_en < now() - interval '1 year'`,
	`DELETE FROM volveria v USING usuarios u WHERE (v.usuario_id = u.id OR v.otro_id = u.id)
		AND u.purgada_en < now() - interval '1 year'`,
	// Reportes que hizo o que le hicieron, abiertos o cerrados: a los 2 años de la purga.
	`DELETE FROM reportes r USING usuarios u WHERE (r.usuario_id = u.id OR r.sobre_usuario_id = u.id)
		AND u.purgada_en < now() - interval '2 years'`,
	// La suspensión: el motivo, a los 2 años de la purga (las huellas vencen solas, abajo).
	`UPDATE usuarios SET suspension_motivo = '' WHERE purgada_en < now() - interval '2 years' AND suspension_motivo <> ''`,
	`DELETE FROM vetados WHERE vence_en < now()`,
	// Intereses (Gente y Citas: a quién le diste «me prendo», «me interesa», «me encanta» o «paso»):
	// 6 meses. Un match (el interés es mutuo) dura mientras se escriban: se borra a los 6 meses del
	// último privado entre los dos, o del match si nunca se escribieron. Sin el match no hay chat de
	// Citas; en Gente el chat sigue por Mi gente.
	`DELETE FROM intereses i WHERE i.creado_en < now() - interval '6 months'
		AND NOT EXISTS (SELECT 1 FROM intereses y WHERE y.usuario_id = i.otro_id AND y.otro_id = i.usuario_id
			AND y.modo = i.modo AND i.accion <> 'paso' AND y.accion <> 'paso'
			AND (y.creado_en >= now() - interval '6 months'
				OR EXISTS (SELECT 1 FROM privados x WHERE x.creado_en >= now() - interval '6 months'
					AND least(x.de_id, x.a_id) = least(i.usuario_id, i.otro_id)
					AND greatest(x.de_id, x.a_id) = greatest(i.usuario_id, i.otro_id))))`,
}
