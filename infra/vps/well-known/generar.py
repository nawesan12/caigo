#!/usr/bin/env python3
"""Arma apple-app-site-association y assetlinks.json (los sirve Caddy desde /opt/caigo/well-known) con
apps/pruebas/enlaces.json, lo mismo que leen las pruebas de iOS y Android. Lo corre desplegar-web.sh; con --revisar
sólo avisa si los archivos del repo quedaron viejos."""
import json
import sys
from pathlib import Path

aca = Path(__file__).resolve().parent
e = json.loads((aca.parent.parent.parent / 'apps/pruebas/enlaces.json').read_text())

aasa = {
    'applinks': {'details': [{'appIDs': [e['ios']], 'components': [
        {'/': '*', '?': {'web': '1'}, 'exclude': True, 'comment': 'Forzar la web'},
        *({'/': p, 'exclude': True} for p in e['web']),
        *({'/': p} for p in e['app']),
    ]}]},
    'appclips': {'apps': [e['clip']]},
    'webcredentials': {'apps': [e['ios']]},
}
assetlinks = [{
    'relation': ['delegate_permission/common.handle_all_urls'],
    'target': {'namespace': 'android_app', 'package_name': e['android'], 'sha256_cert_fingerprints': e['huellas']},
}]

viejos = []
for nombre, datos in (('apple-app-site-association', aasa), ('assetlinks.json', assetlinks)):
    texto = json.dumps(datos, ensure_ascii=False, indent=2) + '\n'
    archivo = aca / nombre
    if archivo.read_text() != texto:
        viejos.append(nombre)
        if '--revisar' not in sys.argv:
            archivo.write_text(texto)
if viejos and '--revisar' in sys.argv:
    sys.exit('desactualizados (correr generar.py): ' + ', '.join(viejos))
