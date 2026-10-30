"""Recorrido de punta a punta de MedCore, TODO por el gateway (:8080).

Uso, con el stack levantado con "docker compose up -d":

    python scripts/e2e_gateway.py <dni-admin> <contrasena>

El DNI del administrador por defecto es 00000001, y la contrasena es la de
ADMIN_INITIAL_PASSWORD del .env.

Comprueba lo que ninguna prueba anterior ha comprobado: que los ocho
servicios funcionan juntos, que el gateway enruta y propaga las cabeceras, y
que una cadena de eventos atraviesa cuatro servicios hasta acabar en un
correo.

No usa dependencias externas: solo urllib de la biblioteca estandar.
"""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = 'http://localhost:8080'
LAB_DIRECTO = 'http://localhost:8085'   # los equipos no pasan por el gateway
MAILPIT = 'http://localhost:8025'

fallos = []
estado = {}


def pedir(metodo, url, cuerpo=None, token=None, cabeceras=None, texto=False):
    datos = None
    h = {}
    if cuerpo is not None:
        if texto:
            datos = cuerpo.encode('utf-8')
            h['Content-Type'] = 'text/plain'
        else:
            datos = json.dumps(cuerpo).encode('utf-8')
            h['Content-Type'] = 'application/json'
    if token:
        h['Authorization'] = 'Bearer ' + token
    h.update(cabeceras or {})

    peticion = urllib.request.Request(url, data=datos, headers=h, method=metodo)
    try:
        with urllib.request.urlopen(peticion, timeout=40) as r:
            bruto = r.read().decode('utf-8')
            return r.status, (bruto if texto else json.loads(bruto or '{}'))
    except urllib.error.HTTPError as e:
        bruto = e.read().decode('utf-8')
        try:
            return e.code, json.loads(bruto or '{}')
        except ValueError:
            return e.code, bruto
    except Exception as e:                                  # noqa: BLE001
        return 0, {'error': str(e)}


def paso(titulo):
    print('\n' + '-' * 70)
    print(titulo)
    print('-' * 70)


def comprobar(descripcion, condicion, detalle=''):
    marca = '[ok]  ' if condicion else '[FALLO]'
    print('  {} {}{}'.format(marca, descripcion, (' | ' + str(detalle)) if detalle else ''))
    if not condicion:
        fallos.append(descripcion)
    return condicion


def esperar(segundos, motivo):
    print('  ... esperando {}s: {}'.format(segundos, motivo))
    time.sleep(segundos)


# ============================================================
paso('1. El gateway esta en pie y enruta a auth-service')

codigo, _ = pedir('GET', BASE + '/actuator/health')
comprobar('el gateway responde al health', codigo == 200, 'HTTP ' + str(codigo))

# Sin token, una ruta protegida debe dar 401 y no 404: significa que el
# gateway la conoce y la esta protegiendo, no que no exista.
codigo, _ = pedir('GET', BASE + '/api/patients')
comprobar('una ruta protegida sin token da 401', codigo == 401, 'HTTP ' + str(codigo))


# ============================================================
paso('2. Login del administrador')

usuario = sys.argv[1] if len(sys.argv) > 1 else '00000001'
clave = sys.argv[2] if len(sys.argv) > 2 else None

codigo, cuerpo = pedir('POST', BASE + '/api/auth/login',
                       {'identificador': usuario, 'password': clave})
ok = comprobar('login correcto', codigo == 200 and cuerpo.get('success'),
               'HTTP ' + str(codigo) + ' ' + str(cuerpo.get('message', ''))[:60])

if not ok:
    print('\nSin token no se puede seguir: el resto del recorrido va firmado.')
    sys.exit(1)

token = cuerpo['data']['accessToken']
estado['token'] = token
comprobar('devuelve un accessToken', bool(token), str(len(token)) + ' caracteres')
comprobar('devuelve el refreshToken', bool(cuerpo['data'].get('refreshToken')))

# El token debe servir para una ruta de otro servicio: prueba de que el
# gateway valida el JWT y propaga las cabeceras X-User-* (MC4).
codigo, _ = pedir('GET', BASE + '/api/patients?page=0&size=1', token=token)
comprobar('el token vale en patient-service', codigo == 200, 'HTTP ' + str(codigo))


# ============================================================
paso('3. Alta de paciente (patient-service -> patient.created)')

sufijo = str(int(time.time()))[-6:]
dni = '7' + sufijo[-7:].rjust(7, '0')
codigo, cuerpo = pedir('POST', BASE + '/api/patients', {
    'dni': dni,
    'nombres': 'Elena',
    'apellidos': 'Soto Vargas',
    'fechaNacimiento': '1989-03-22',
    'sexo': 'FEMENINO',
    'tipoSangre': 'O_POS',
    'telefono': '987000111',
    'correo': 'elena.soto.' + sufijo + '@correo.local',
    'direccion': 'Av. Los Proceres 123',
    'zona': 'San Juan de Lurigancho',
}, token=token)

ok = comprobar('paciente creado', codigo == 201, 'HTTP ' + str(codigo)
               + ' ' + str(cuerpo.get('message', ''))[:70])
if ok:
    estado['idPaciente'] = cuerpo['data']['id']
    estado['correoPaciente'] = cuerpo['data']['correo']
    comprobar('calcula la edad', cuerpo['data'].get('edad') is not None,
              'edad ' + str(cuerpo['data'].get('edad')))
    comprobar('guarda la zona', cuerpo['data'].get('zona') == 'San Juan de Lurigancho')


# ============================================================
paso('4. El aviso de bienvenida llega por la cadena de eventos')

if 'idPaciente' in estado:
    esperar(12, 'patient.created -> notification-service -> Mailpit')

    codigo, cuerpo = pedir('GET', MAILPIT + '/api/v1/search?query='
                           + urllib.request.quote(estado['correoPaciente']))
    mensajes = cuerpo.get('messages', []) if isinstance(cuerpo, dict) else []
    comprobar('hay correo de bienvenida para el paciente', len(mensajes) >= 1,
              str(len(mensajes)) + ' mensaje(s)')
    if mensajes:
        comprobar('el asunto es el de bienvenida',
                  'Bienvenido' in (mensajes[0].get('Subject') or ''),
                  mensajes[0].get('Subject'))


# ============================================================
paso('5. Catalogo del laboratorio (laboratory-service por el gateway)')

codigo, cuerpo = pedir('GET', BASE + '/api/lab-catalog/areas', token=token)
comprobar('el gateway enruta a laboratory-service', codigo == 200,
          'HTTP ' + str(codigo))
if codigo == 200:
    comprobar('devuelve las areas del laboratorio',
              len(cuerpo.get('data') or []) >= 3, cuerpo.get('data'))


# ============================================================
paso('6. Almacen: reactivos bajo minimo (inventory-service)')

codigo, cuerpo = pedir('GET', BASE + '/api/reagents/bajo-minimo', token=token)
comprobar('el gateway enruta a inventory-service', codigo == 200,
          'HTTP ' + str(codigo))
if codigo == 200:
    comprobar('devuelve la lista de reposicion',
              isinstance(cuerpo.get('data'), list),
              str(len(cuerpo.get('data') or [])) + ' reactivo(s)')


# ============================================================
paso('7. Epidemiologia e informes (analytics-service)')

codigo, cuerpo = pedir('GET', BASE + '/api/reports?periodo=MENSUAL', token=token)
comprobar('el gateway enruta a analytics-service', codigo == 200,
          'HTTP ' + str(codigo))
if codigo == 200 and cuerpo.get('data'):
    d = cuerpo['data']
    comprobar('el informe trae sus cifras',
              'positividad' in d and 'totalResultados' in d,
              'resultados=' + str(d.get('totalResultados'))
              + ' positividad=' + str(round(d.get('positividad', 0), 1)) + '%')

codigo, cuerpo = pedir('GET', BASE + '/api/epidemiology/heatmap?periodo=MENSUAL',
                       token=token)
comprobar('el mapa de calor responde', codigo == 200, 'HTTP ' + str(codigo))


# ============================================================
paso('8. Mis avisos (notification-service por el gateway)')

codigo, cuerpo = pedir('GET', BASE + '/api/notifications/resumen', token=token)
comprobar('el gateway enruta a notification-service', codigo == 200,
          'HTTP ' + str(codigo))
if codigo == 200 and cuerpo.get('data'):
    d = cuerpo['data']
    comprobar('el resumen de entrega trae sus cifras',
              'enviados' in d,
              'enviados=' + str(d.get('enviados'))
              + ' pendientes=' + str(d.get('pendientes'))
              + ' perdidos=' + str(d.get('sinEntregar')))


# ============================================================
paso('9. Bandeja del paciente: los avisos de su alta')

if 'idPaciente' in estado:
    codigo, cuerpo = pedir('GET', BASE + '/api/notifications/paciente/'
                           + str(estado['idPaciente']), token=token)
    comprobar('la bandeja del paciente responde', codigo == 200,
              'HTTP ' + str(codigo))


# ============================================================
paso('10. La interfaz HL7 no pasa por el gateway')

# Los analizadores escriben al puerto del servicio. Una trama ilegible debe
# devolver 200 con un ACK de error, nunca un error HTTP: un equipo sin ACK
# reintenta y bloquea su cola de salida.
codigo, cuerpo = pedir('POST', LAB_DIRECTO + '/api/hl7/oru',
                       'esto no es una trama', texto=True)
comprobar('el equipo recibe 200 aunque la trama sea mala', codigo == 200,
          'HTTP ' + str(codigo))
if codigo == 200:
    comprobar('el ACK lleva el codigo de error en el MSA',
              'MSA|AE|' in str(cuerpo), str(cuerpo).replace('\r', ' ')[:70])

# Y la misma ruta por el gateway NO debe estar publicada.
codigo, _ = pedir('POST', BASE + '/api/hl7/oru', 'MSH|...', texto=True)
comprobar('el gateway no publica /api/hl7 al exterior', codigo in (401, 403, 404),
          'HTTP ' + str(codigo))


# ============================================================
paso('RESUMEN')

if fallos:
    print('  {} comprobacion(es) FALLARON:'.format(len(fallos)))
    for f in fallos:
        print('    - ' + f)
    sys.exit(1)

print('  Todas las comprobaciones pasaron.')
print('  El recorrido atraveso: gateway -> auth -> patient -> kafka ->')
print('  notification -> mailpit, y consulto laboratory, inventory,')
print('  analytics y notification por el gateway.')
