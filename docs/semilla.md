# Planes semilla

El equipo carga planes reales (los encuentros piloto: fútbol, truco, previa) para que la app nunca
se vea vacía. Cada plan lo organiza una persona del equipo que ya entró a Caigo (su id de Clerk,
`user_…`, sale del panel de Clerk).

```json
[
  {
    "organizador": "user_…",
    "categoria": "juego",
    "titulo": "Truco en un bar de Güemes",
    "empiezaEn": "2026-11-06T21:30:00-03:00",
    "ciudad": "Mar del Plata",
    "zona": "Güemes",
    "lugarNombre": "Bar …",
    "direccion": "…",
    "lat": -38.0129,
    "lng": -57.5401,
    "somos": 3,
    "faltan": 1,
    "ingreso": "aprobacion",
    "costoCentavos": 300000
  }
]
```

Los campos son los de `POST /v1/planes` (`apps/api/internal/dominio/planes/planes.go`, `Entrada`).
En el VPS:

```bash
scp planes.json deploy@187.41.102.190:/opt/caigo/semilla.json
ssh deploy@187.41.102.190 'cd /opt/caigo && docker compose run --rm --no-deps -T -v /opt/caigo/semilla.json:/semilla.json api /app/bin/caigoctl semilla /semilla.json'
```

## Planes ficticios de Mardel (19/11/2026)

15 planes de muestra, del 20 al 29/11, en lugares públicos de Mar del Plata (plazas, playas, parques),
organizados por Nahuel, todos con ingreso «aprobacion» para que nadie quede adentro sin que los acepte.
Pedido de Nahuel para que la app no se vea vacía. Lista y links: `/opt/caigo/semilla-mardel-ficticia.txt`;
archivo: `/opt/caigo/semilla.json`. Copia previa: `copias/caigo-antes-semilla-mardel-202611190530.dump`.
**Antes del lanzamiento**, cancelarlos o borrarlos (los slugs están en esa lista).
