#!/usr/bin/env bash
# Prepara los recursos de las pruebas de Android a partir de iOS y de apps/pruebas:
#   - src/test/resources/paridad.txt: los .swift de apps/ios/Caigo/Pantallas (lo lee ParidadTest);
#   - src/test/resources/pruebas/respuestas.json: la API simulada que comparten iOS y Android (PantallasTest).
# Correrlo al sumar una pantalla en iOS o al tocar respuestas.json (el CI lo corre antes de los tests).
set -euo pipefail
aca="$(cd "$(dirname "$0")/.." && pwd)"
apps="$(cd "$aca/.." && pwd)"
recursos="$aca/app/src/test/resources"
mkdir -p "$recursos/pruebas"
(cd "$apps/ios/Caigo/Pantallas" && find . -name '*.swift' | sed 's|^\./||' | LC_ALL=C sort) > "$recursos/paridad.txt"
cp "$apps/pruebas/respuestas.json" "$recursos/pruebas/respuestas.json"
echo "paridad.txt: $(wc -l < "$recursos/paridad.txt" | tr -d ' ') pantallas · respuestas.json copiado"
