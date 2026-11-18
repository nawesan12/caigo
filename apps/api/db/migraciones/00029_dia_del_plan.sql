-- +goose Up
-- El día del plan, después del plan y el ritual semanal (informe del 18/11, frente «día del plan»):
-- contactos de confianza y «¿Todo bien?», «¿Seguís yendo?» la víspera, lista de espera con «Se liberó un
-- lugar», caritas después del plan (privadas), fotos sólo para quienes fueron y «Esta semana en tu ciudad».

-- Contactos de confianza (hasta 3): [{"nombre": "Mamá", "contacto": "+54 9 223… o un mail"}]. Privados:
-- sólo se usan si la persona activa «¿Todo bien?» en un plan. contacto_confianza queda como el primero
-- (las versiones viejas de las apps lo leen y lo escriben).
ALTER TABLE usuarios_privado ADD COLUMN contactos jsonb NOT NULL DEFAULT '[]'
	CHECK (jsonb_typeof(contactos) = 'array' AND jsonb_array_length(contactos) <= 3);
-- «Esta semana en tu ciudad», los lunes (se apaga desde Ajustes, como los otros avisos).
ALTER TABLE usuarios_privado ADD COLUMN avisos_semana boolean NOT NULL DEFAULT true;
UPDATE usuarios_privado SET contactos = jsonb_build_array(jsonb_build_object('nombre', '', 'contacto', contacto_confianza))
	WHERE contacto_confianza <> '';

-- «¿Seguís yendo?»: confirmó la víspera. La carita (1 a 5) que le puso al plan después: privada, nadie
-- la ve; alimenta la reputación de quien organiza como promedio (sólo con 3 o más).
ALTER TABLE solicitudes ADD COLUMN confirmada_en timestamptz;
ALTER TABLE solicitudes ADD COLUMN carita smallint CHECK (carita BETWEEN 1 AND 5);
GRANT UPDATE (confirmada_en, carita) ON solicitudes TO caigo_app;

-- «¿Todo bien?»: avisarle a un contacto si no hay check-in a tiempo. Guarda una copia del contacto
-- elegido (si lo cambia en el perfil, el aviso va al que eligió) y nada de ubicación. Se borra a las
-- 24 h del final del plan (el worker).
CREATE TABLE todo_bien (
	plan_id uuid NOT NULL REFERENCES planes (id) ON DELETE CASCADE,
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	contacto_nombre text NOT NULL DEFAULT '' CHECK (length(contacto_nombre) <= 40),
	contacto text NOT NULL CHECK (length(contacto) BETWEEN 3 AND 80),
	-- Si a esta hora no hizo check-in (ni lo apagó), se avisa.
	avisa_en timestamptz NOT NULL,
	avisado_en timestamptz,
	creado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (plan_id, usuario_id)
);
CREATE INDEX todo_bien_avisa ON todo_bien (avisa_en) WHERE avisado_en IS NULL;
SELECT app.aplicar_rls('todo_bien');
GRANT SELECT, INSERT, UPDATE (contacto_nombre, contacto, avisa_en, avisado_en), DELETE ON todo_bien TO caigo_app;

-- Lista de espera de un plan completo. esperando → avisado («Se liberó un lugar», con 15 min para
-- entrar) → entro | vencido. Si no entra a tiempo, el lugar pasa al siguiente.
CREATE TABLE espera (
	plan_id uuid NOT NULL REFERENCES planes (id) ON DELETE CASCADE,
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	cantidad smallint NOT NULL DEFAULT 1 CHECK (cantidad BETWEEN 1 AND 10),
	estado text NOT NULL DEFAULT 'esperando' CHECK (estado IN ('esperando', 'avisado', 'entro', 'vencido')),
	creada_en timestamptz NOT NULL DEFAULT now(),
	avisado_en timestamptz,
	vence_en timestamptz,
	PRIMARY KEY (plan_id, usuario_id)
);
CREATE INDEX espera_fila ON espera (plan_id, creada_en) WHERE estado IN ('esperando', 'avisado');
CREATE INDEX espera_vence ON espera (vence_en) WHERE estado = 'avisado';
GRANT SELECT, INSERT, UPDATE (cantidad, estado, creada_en, avisado_en, vence_en), DELETE ON espera TO caigo_app;

-- Fotos del plan: las ven sólo quienes fueron (y no con un bloqueo de por medio). El archivo es de tipo
-- «plan»: no es público como las fotos del perfil, se sirve con sesión a quien puede verlo. Se borran a
-- los 6 meses del plan (el worker); quien la subió la puede sacar antes, y cualquiera que fue la reporta.
ALTER TABLE archivos DROP CONSTRAINT archivos_tipo_check;
ALTER TABLE archivos ADD CONSTRAINT archivos_tipo_check CHECK (tipo IN ('foto', 'selfie', 'dni', 'plan'));
CREATE TABLE fotos_plan (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	plan_id uuid NOT NULL REFERENCES planes (id) ON DELETE CASCADE,
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	archivo_id uuid NOT NULL UNIQUE REFERENCES archivos (id) ON DELETE CASCADE,
	-- ok, o oculto por el equipo (después de un reporte).
	moderacion text NOT NULL DEFAULT 'ok' CHECK (moderacion IN ('ok', 'oculto')),
	creada_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX fotos_plan_plan ON fotos_plan (plan_id, creada_en);
GRANT SELECT, INSERT, UPDATE (moderacion), DELETE ON fotos_plan TO caigo_app;

-- Avisos nuevos que dependen de la hora: la víspera, «¿Cómo estuvo?» a quienes fueron, el de «¿Todo
-- bien?» y el de «Se liberó un lugar» (estos dos se anotan para no repetir en un reintento).
ALTER TABLE avisos_enviados DROP CONSTRAINT avisos_enviados_tipo_check;
ALTER TABLE avisos_enviados ADD CONSTRAINT avisos_enviados_tipo_check CHECK (tipo IN
	('recordatorio', 'checkin', 'repetimos', 'tu_gente', 'cerca', 'vispera', 'como_estuvo'));

-- «Esta semana en tu ciudad»: uno por persona y por semana (el lunes de esa semana, en Argentina).
CREATE TABLE avisos_semana (
	usuario_id uuid NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
	semana date NOT NULL,
	enviado_en timestamptz NOT NULL DEFAULT now(),
	PRIMARY KEY (usuario_id, semana)
);
GRANT SELECT, INSERT, DELETE ON avisos_semana TO caigo_app;

-- +goose Down
DROP TABLE avisos_semana;
DELETE FROM avisos_enviados WHERE tipo IN ('vispera', 'como_estuvo');
ALTER TABLE avisos_enviados DROP CONSTRAINT avisos_enviados_tipo_check;
ALTER TABLE avisos_enviados ADD CONSTRAINT avisos_enviados_tipo_check
	CHECK (tipo IN ('recordatorio', 'checkin', 'repetimos', 'tu_gente', 'cerca'));
DROP TABLE fotos_plan;
DELETE FROM archivos WHERE tipo = 'plan';
ALTER TABLE archivos DROP CONSTRAINT archivos_tipo_check;
ALTER TABLE archivos ADD CONSTRAINT archivos_tipo_check CHECK (tipo IN ('foto', 'selfie', 'dni'));
DROP TABLE espera;
DROP TABLE todo_bien;
REVOKE UPDATE (confirmada_en, carita) ON solicitudes FROM caigo_app;
ALTER TABLE solicitudes DROP COLUMN carita, DROP COLUMN confirmada_en;
ALTER TABLE usuarios_privado DROP COLUMN avisos_semana, DROP COLUMN contactos;
