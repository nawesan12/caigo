#!/usr/bin/env bash
# Borra la base de desarrollo y la vuelve a armar desde las migraciones (sólo local).
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose -f infra/compose.dev.yml down -v
docker compose -f infra/compose.dev.yml up -d --wait
cd apps/api
go run ./cmd/caigoctl bootstrap
go run ./cmd/caigoctl migrar
