#!/bin/sh
# Pruebas del núcleo (lógica, modelos, API) en la Mac, sin Xcode: alcanza con las Command Line Tools.
# swift-testing de las CLT vive en Library/Developer/Frameworks y no trae el overlay con Foundation.
set -e
cd "$(dirname "$0")/Nucleo"
F=/Library/Developer/CommandLineTools/Library/Developer/Frameworks
if xcode-select -p | grep -q CommandLineTools; then
  exec swift test -Xswiftc -F -Xswiftc $F -Xswiftc -Xfrontend -Xswiftc -disable-cross-import-overlays \
    -Xlinker -F -Xlinker $F -Xlinker -rpath -Xlinker $F "$@"
fi
exec swift test "$@"
