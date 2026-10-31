#!/usr/bin/env bash
# Despliega la API, el worker y caigoctl de Caigo al VPS. Correr desde una copia limpia en main.
# Uso: infra/vps/desplegar-api.sh [--migrar]
set -euo pipefail
cd "$(dirname "$0")/../../apps/api"
VPS=deploy@187.41.102.190
V=$(git rev-parse --short HEAD)
git diff --quiet HEAD -- . || { echo "hay cambios sin commitear en apps/api" >&2; exit 1; }
mkdir -p bin
for c in api worker caigoctl; do
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -trimpath -ldflags "-s -w -X main.version=$V" -o "bin/$c" "./cmd/$c"
	scp -q "bin/$c" "$VPS:/opt/caigo/bin/$c.nuevo"
done
if [[ "${1:-}" == "--migrar" ]]; then
	# Copia antes de migrar (hay datos reales).
	# /var/backups/vimo/manual es de root: la copia queda en /opt/caigo/copias (600).
	ssh "$VPS" 'cd /opt/caigo && mkdir -p -m 700 copias && umask 077 && docker compose exec -T postgres pg_dump -U postgres -Fc caigo > copias/caigo-$(date +%Y%m%d-%H%M).dump'
fi
ssh "$VPS" 'cd /opt/caigo && for c in api worker caigoctl; do mv bin/$c.nuevo bin/$c; done'
if [[ "${1:-}" == "--migrar" ]]; then
	ssh "$VPS" 'cd /opt/caigo && docker compose run --rm --no-deps -T api /app/bin/caigoctl migrar'
fi
ssh "$VPS" 'cd /opt/caigo && docker compose up -d api worker && docker compose restart api worker && sleep 2 && curl -fsS http://127.0.0.1:4300/salud/listo && echo'
echo "desplegada la versión $V"
