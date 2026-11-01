# API de App Store Connect

`asc.py` habla con la API de App Store Connect con la clave del equipo
(`~/Trabajo/vimo/credenciales-apple/asc.env`). No imprime secretos.

```bash
python3 -m venv /tmp/venv-asc && /tmp/venv-asc/bin/pip install pyjwt cryptography requests
/tmp/venv-asc/bin/python -I infra/apple/asc.py certificados
/tmp/venv-asc/bin/python -I infra/apple/asc.py perfiles
/tmp/venv-asc/bin/python -I infra/apple/asc.py builds 6829274265
/tmp/venv-asc/bin/python -I infra/apple/asc.py grupos 6829274265
```

`crear <carpeta>` hace un certificado de distribución nuevo con los perfiles de App Store de la app
y el Clip: usarlo sólo si el actual (93F3KP32L3) vence o se revoca (Apple limita cuántos hay por equipo),
y después volver a cargar los secretos del repo (ver docs/estado.md, punto 4 de «Cómo retomar»).
