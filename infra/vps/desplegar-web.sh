#!/usr/bin/env bash
# Despliega la web de Caigo (SvelteKit, adapter-node) al VPS. Correr desde una copia limpia en main.
set -euo pipefail
cd "$(dirname "$0")/../../apps/web"
VPS=deploy@187.41.102.190
git diff --quiet HEAD -- . || { echo "hay cambios sin commitear en apps/web" >&2; exit 1; }
# Los links que abren la app (apps/pruebas/enlaces.json → AASA y assetlinks): Caddy los sirve de /opt/caigo/well-known.
python3 ../../infra/vps/well-known/generar.py --revisar
rsync -a ../../infra/vps/well-known/apple-app-site-association ../../infra/vps/well-known/assetlinks.json "$VPS:/opt/caigo/well-known/"
bun install --frozen-lockfile >/dev/null
bun run build >/dev/null
# El build nuevo se arma al lado y se cambia de un saque.
ssh "$VPS" "mkdir -p /opt/caigo/web.nuevo"
rsync -a --delete build/ "$VPS:/opt/caigo/web.nuevo/build/"
ssh "$VPS" 'cd /opt/caigo && rm -rf web.viejo && { [ -d web ] && mv web web.viejo || true; } && mv web.nuevo web && docker compose up -d web && docker compose restart web && sleep 2 && curl -fsS -o /dev/null -w "web %{http_code}\n" -H "Host: caigo.com.ar" http://127.0.0.1:3500/'
