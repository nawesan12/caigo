"""Cliente mínimo de la API de App Store Connect con la clave de equipo (asc.env).

Uso: asc.py certificados | perfiles | crear <dir_salida>
`crear` genera una clave privada y un CSR, crea un certificado de distribución, arma un .p12 y crea
los perfiles de App Store de ar.com.caigo.app y ar.com.caigo.app.Clip. Nunca imprime secretos: deja
todo en <dir_salida> (600).
"""
import base64, os, sys, time, json, secrets, subprocess
import jwt, requests

env = {}
for linea in open(os.path.expanduser('~/Trabajo/vimo/credenciales-apple/asc.env')):
    linea = linea.strip()
    if linea and not linea.startswith('#') and '=' in linea:
        k, v = linea.split('=', 1)
        env[k.strip().removeprefix('export ').strip()] = v.strip().strip('"').strip("'")
clave = open(os.path.expanduser(env['ASC_KEY_PATH'])).read()

def token():
    ahora = int(time.time())
    return jwt.encode({'iss': env['ASC_ISSUER_ID'], 'iat': ahora, 'exp': ahora + 1100, 'aud': 'appstoreconnect-v1'},
                      clave, algorithm='ES256', headers={'kid': env['ASC_KEY_ID'], 'typ': 'JWT'})

def api(metodo, ruta, cuerpo=None):
    r = requests.request(metodo, 'https://api.appstoreconnect.apple.com' + ruta,
                         headers={'Authorization': 'Bearer ' + token()}, json=cuerpo, timeout=60)
    if r.status_code >= 400:
        sys.exit(f'{metodo} {ruta}: {r.status_code} {r.text[:600]}')
    return r.json() if r.text else {}

def certificados():
    for c in api('GET', '/v1/certificates?limit=50')['data']:
        a = c['attributes']
        print(c['id'], a['certificateType'], a.get('displayName'), a.get('name'), a.get('expirationDate'))

def perfiles():
    for p in api('GET', '/v1/profiles?limit=50')['data']:
        a = p['attributes']
        print(p['id'], a['profileType'], a['name'], a['profileState'], a.get('expirationDate'))

def bundle(identificador):
    d = api('GET', f'/v1/bundleIds?filter[identifier]={identificador}')['data']
    return [b for b in d if b['attributes']['identifier'] == identificador][0]['id']

def crear(salida):
    os.makedirs(salida, mode=0o700, exist_ok=True)
    k, csr = os.path.join(salida, 'dist.key'), os.path.join(salida, 'dist.csr')
    subprocess.run(['openssl', 'genrsa', '-out', k, '2048'], check=True, capture_output=True)
    os.chmod(k, 0o600)
    subprocess.run(['openssl', 'req', '-new', '-key', k, '-out', csr, '-subj', '/CN=Caigo CI/C=AR'], check=True)
    pem = open(csr).read()
    contenido = ''.join(l for l in pem.splitlines() if 'CERTIFICATE REQUEST' not in l)
    c = api('POST', '/v1/certificates', {'data': {'type': 'certificates',
        'attributes': {'certificateType': 'DISTRIBUTION', 'csrContent': contenido}}})
    cid = c['data']['id']
    der = base64.b64decode(c['data']['attributes']['certificateContent'])
    cer = os.path.join(salida, 'dist.cer')
    open(cer, 'wb').write(der)
    subprocess.run(['openssl', 'x509', '-inform', 'DER', '-in', cer, '-out', cer + '.pem'], check=True)
    clave_p12 = secrets.token_urlsafe(24)
    open(os.path.join(salida, 'p12.clave'), 'w').write(clave_p12)
    p12 = os.path.join(salida, 'dist.p12')
    subprocess.run(['openssl', 'pkcs12', '-export', '-legacy', '-inkey', k, '-in', cer + '.pem', '-out', p12,
                    '-passout', 'pass:' + clave_p12], check=True)
    for f in os.listdir(salida):
        os.chmod(os.path.join(salida, f), 0o600)
    print('certificado', cid)
    for ident, nombre in [('ar.com.caigo.app', 'Caigo App Store'), ('ar.com.caigo.app.Clip', 'Caigo Clip App Store')]:
        p = api('POST', '/v1/profiles', {'data': {'type': 'profiles',
            'attributes': {'name': nombre, 'profileType': 'IOS_APP_STORE'},
            'relationships': {'bundleId': {'data': {'type': 'bundleIds', 'id': bundle(ident)}},
                              'certificates': {'data': [{'type': 'certificates', 'id': cid}]}}}})
        ruta = os.path.join(salida, ident + '.mobileprovision')
        open(ruta, 'wb').write(base64.b64decode(p['data']['attributes']['profileContent']))
        os.chmod(ruta, 0o600)
        print('perfil', nombre, p['data']['id'])

if __name__ == '__main__' and sys.argv[1] in ('certificados', 'perfiles', 'crear'):
    {'certificados': certificados, 'perfiles': perfiles}.get(sys.argv[1], lambda: crear(sys.argv[2]))()

def grupos(app_id):
    for g in api('GET', f'/v1/apps/{app_id}/betaGroups')['data']:
        a = g['attributes']
        print(g['id'], a['name'], 'interno' if a.get('isInternalGroup') else 'externo', 'todos' if a.get('hasAccessToAllBuilds') else '')
        for t in api('GET', f"/v1/betaGroups/{g['id']}/betaTesters")['data']:
            print('   tester', t['attributes'].get('email'))

def grupo_equipo(app_id, email, nombre, apellido):
    g = api('POST', '/v1/betaGroups', {'data': {'type': 'betaGroups',
        'attributes': {'name': 'Equipo', 'isInternalGroup': True, 'hasAccessToAllBuilds': True},
        'relationships': {'app': {'data': {'type': 'apps', 'id': app_id}}}}})
    gid = g['data']['id']
    api('POST', '/v1/betaTesters', {'data': {'type': 'betaTesters',
        'attributes': {'email': email, 'firstName': nombre, 'lastName': apellido},
        'relationships': {'betaGroups': {'data': [{'type': 'betaGroups', 'id': gid}]}}}})
    print('grupo', gid, 'con', email)

def builds(app_id):
    for b in api('GET', f'/v1/builds?filter[app]={app_id}&sort=-uploadedDate&limit=5')['data']:
        a = b['attributes']
        print(b['id'], a['version'], a['processingState'], a['uploadedDate'])

if __name__ == '__main__' and sys.argv[1] in ('grupos', 'grupo_equipo', 'builds'):
    globals()[sys.argv[1]](*sys.argv[2:])

def invitar(app_id, email):
    """Reenvía por mail la invitación de TestFlight de la app a un tester ya cargado."""
    t = [x for g in api('GET', f'/v1/apps/{app_id}/betaGroups')['data']
         for x in api('GET', f"/v1/betaGroups/{g['id']}/betaTesters")['data'] if x['attributes'].get('email') == email]
    if not t:
        sys.exit(f'{email} no es tester')
    api('POST', '/v1/betaTesterInvitations', {'data': {'type': 'betaTesterInvitations',
        'relationships': {'app': {'data': {'type': 'apps', 'id': app_id}},
                          'betaTester': {'data': {'type': 'betaTesters', 'id': t[0]['id']}}}}})
    print('invitación enviada a', email, '(tester', t[0]['id'] + ')', t[0]['attributes'].get('inviteType'), t[0]['attributes'].get('state'))

if __name__ == '__main__' and sys.argv[1] == 'invitar':
    invitar(*sys.argv[2:])
