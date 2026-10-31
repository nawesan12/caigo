#!/bin/sh
# Chequeo de tipos rápido en la Mac sin Xcode: compila la app contra el SDK de macOS (que trae
# SwiftUI). Lo propio de iOS (UIKit, Core NFC, PassKit UI…) va con `#if os(iOS)` y acá no se ve:
# eso lo verifica el job `ios` de CI con el SDK del simulador.
set -e
cd "$(dirname "$0")"
(cd Nucleo && swift build -q 2>&1 | grep -v "^$" || true)
MOD=$(cd Nucleo && swift build --show-bin-path)/Modules
ARCH=$(uname -m)
find Caigo -name '*.swift' -print0 | xargs -0 swiftc -typecheck -sdk "$(xcrun --show-sdk-path --sdk macosx)" \
  -target "$ARCH-apple-macos14" -swift-version 5 -I "$MOD" -D REVISION_LOCAL "$@"
# El App Clip: sus archivos y los compartidos con la app (los mismos que project.yml).
swiftc -typecheck -sdk "$(xcrun --show-sdk-path --sdk macosx)" -target "$ARCH-apple-macos14" -swift-version 5 -I "$MOD" -D REVISION_LOCAL \
  $(find CaigoClip Caigo/Tema Caigo/Piezas -name '*.swift') Caigo/App/Contenedor.swift Caigo/App/Ir.swift Caigo/App/Push.swift \
  Caigo/Pantallas/Pagos/PagosComun.swift Caigo/Pantallas/Pagos/Adentro.swift Caigo/Pantallas/Ingreso/IngresoClerk.swift "$@"
