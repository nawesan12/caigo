-- +goose Up
-- Moderación automática: un filtro propio y, para lo dudoso, Claude Haiku (Anthropic).

-- Estado de moderación de lo que se publica. ok: normal. pendiente: lo revisa el worker con la IA
-- (se ve igual, son segundos). revisar: en la cola del equipo (se ve igual). oculto: sólo lo ve el autor.
ALTER TABLE planes ADD COLUMN moderacion text NOT NULL DEFAULT 'ok'
	CHECK (moderacion IN ('ok', 'pendiente', 'revisar', 'oculto'));
ALTER TABLE mensajes ADD COLUMN moderacion text NOT NULL DEFAULT 'ok'
	CHECK (moderacion IN ('ok', 'pendiente', 'revisar', 'oculto'));
ALTER TABLE privados ADD COLUMN moderacion text NOT NULL DEFAULT 'ok'
	CHECK (moderacion IN ('ok', 'pendiente', 'revisar', 'oculto'));
GRANT UPDATE (moderacion) ON planes, mensajes, privados TO caigo_app;
-- Los planes ocultos son pocos: el índice parcial sirve para la cola, no para las listas.
CREATE INDEX planes_moderacion ON planes (id) WHERE moderacion <> 'ok';

-- La cola del equipo: lo que la moderación marcó, con una copia del texto que se revisó (el objeto
-- puede cambiar o no existir: lo que frenó el filtro no se publicó). Con RLS por usuario_id (el autor):
-- la app lo anota en la transacción del autor; el equipo lo lee como sistema.
CREATE TABLE moderaciones (
	id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
	tipo text NOT NULL CHECK (tipo IN ('plan', 'mensaje', 'privado')),
	-- uuid del plan o id del mensaje/privado (texto, porque son de tipos distintos). NULL: no se publicó.
	objeto_id text,
	-- El plan del objeto (si es un plan o un mensaje de su chat), para mostrarlo en la cola.
	plan_id uuid REFERENCES planes (id) ON DELETE SET NULL,
	usuario_id uuid NOT NULL REFERENCES usuarios (id),
	texto text NOT NULL CHECK (length(texto) <= 3000),
	origen text NOT NULL CHECK (origen IN ('filtro', 'ia', 'sin_ia')),
	categoria text NOT NULL DEFAULT 'otro' CHECK (length(categoria) <= 30),
	motivo text NOT NULL DEFAULT '' CHECK (length(motivo) <= 300),
	-- Cómo quedó el objeto cuando entró a la cola: revision | oculto | bloqueado (el filtro no lo dejó salir).
	estado text NOT NULL CHECK (estado IN ('revision', 'oculto', 'bloqueado')),
	resolucion text CHECK (resolucion IN ('aprobado', 'ocultado', 'suspendido')),
	resuelta_por uuid REFERENCES usuarios (id),
	resuelta_en timestamptz,
	creada_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX moderaciones_abiertas ON moderaciones (creada_en) WHERE resuelta_en IS NULL;
CREATE INDEX moderaciones_usuario ON moderaciones (usuario_id);
-- Un objeto tiene a lo sumo una entrada abierta (otra edición del mismo plan no la duplica).
CREATE UNIQUE INDEX moderaciones_objeto ON moderaciones (tipo, objeto_id) WHERE resuelta_en IS NULL AND objeto_id IS NOT NULL;
SELECT app.aplicar_rls('moderaciones');
GRANT SELECT, INSERT, DELETE ON moderaciones TO caigo_app;
GRANT UPDATE (texto, origen, categoria, motivo, estado, resolucion, resuelta_por, resuelta_en) ON moderaciones TO caigo_app;

-- Uso de la IA por día y ruta (sin datos de nadie): pedidos, tokens y costo en micro-dólares. Los topes
-- se miden acá, en la base, para que la API y el worker vean lo mismo.
CREATE TABLE ia_uso (
	dia date NOT NULL,
	ruta text NOT NULL CHECK (length(ruta) <= 40),
	pedidos integer NOT NULL DEFAULT 0,
	entrada bigint NOT NULL DEFAULT 0,
	cache_leido bigint NOT NULL DEFAULT 0,
	cache_escrito bigint NOT NULL DEFAULT 0,
	salida bigint NOT NULL DEFAULT 0,
	costo_micro bigint NOT NULL DEFAULT 0,
	-- Pedidos que fallaron o no se hicieron por el tope (para ver si la IA está apagada).
	fallidos integer NOT NULL DEFAULT 0,
	PRIMARY KEY (dia, ruta)
);
GRANT SELECT, INSERT, UPDATE ON ia_uso TO caigo_app;

-- +goose Down
DROP TABLE ia_uso;
DROP TABLE moderaciones;
DROP INDEX planes_moderacion;
REVOKE UPDATE (moderacion) ON planes, mensajes, privados FROM caigo_app;
ALTER TABLE privados DROP COLUMN moderacion;
ALTER TABLE mensajes DROP COLUMN moderacion;
ALTER TABLE planes DROP COLUMN moderacion;
