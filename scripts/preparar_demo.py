"""Crea por la API lo que el seed SQL no puede crear.

Los horarios medicos no se pueden sembrar con un INSERT. Crear un horario
genera a la vez los huecos de las proximas semanas en time_slots, y sin esos
huecos no se puede reservar ninguna cita: el sistema parece configurado y no
lo esta.

Uso, con el stack levantado y demo_seed.sql ya aplicado:

    python scripts/preparar_demo.py <dni-admin> <contrasena>

Lo lanza aplicar-demo.sh despues del SQL. Es idempotente: si el medico ya
tiene horario, lo deja como esta.
"""
import json
import sys
import urllib.error
import urllib.request

BASE = 'http://localhost:8080'

# Los dos medicos del seed. Los horarios no se solapan del todo a proposito:
# con dos agendas distintas se ve que los huecos se calculan por medico y que
# ocupar uno no afecta al otro.
HORARIOS = [
    {
        'idMedico': 1,
        'nombreMedico': 'Dr. Ricardo Vega Campos',
        'especialidad': 'Medicina interna',
        'dias': ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY'],
        'horaInicio': '08:00',
        'horaFin': '14:00',
    },
    {
        'idMedico': 2,
        'nombreMedico': 'Dra. Carmen Rojas Ibanez',
        'especialidad': 'Medicina general',
        'dias': ['MONDAY', 'WEDNESDAY', 'FRIDAY'],
        'horaInicio': '14:00',
        'horaFin': '20:00',
    },
]


def pedir(metodo, url, cuerpo=None, token=None):
    datos = None
    h = {}
    if cuerpo is not None:
        datos = json.dumps(cuerpo).encode('utf-8')
        h['Content-Type'] = 'application/json'
    if token:
        h['Authorization'] = 'Bearer ' + token

    peticion = urllib.request.Request(url, data=datos, headers=h, method=metodo)
    try:
        with urllib.request.urlopen(peticion, timeout=40) as r:
            return r.status, json.loads(r.read().decode('utf-8') or '{}')
    except urllib.error.HTTPError as e:
        bruto = e.read().decode('utf-8')
        try:
            return e.code, json.loads(bruto or '{}')
        except ValueError:
            return e.code, bruto
    except Exception as e:                                  # noqa: BLE001
        return 0, {'error': str(e)}


usuario = sys.argv[1] if len(sys.argv) > 1 else '00000001'
clave = sys.argv[2] if len(sys.argv) > 2 else None

codigo, cuerpo = pedir('POST', BASE + '/api/auth/login',
                       {'identificador': usuario, 'password': clave})

if codigo != 200 or not cuerpo.get('success'):
    print('No se pudo entrar como administrador: HTTP {} {}'.format(
            codigo, str(cuerpo.get('message', ''))[:80]))
    print('Comprueba ADMIN_INITIAL_PASSWORD del .env y que el stack este arriba.')
    sys.exit(1)

token = cuerpo['data']['accessToken']

creados = 0
existentes = 0

for horario in HORARIOS:
    codigo, cuerpo = pedir('GET', BASE + '/api/schedules/medico/'
                           + str(horario['idMedico']), token=token)

    if codigo == 200 and cuerpo.get('data'):
        print('  {} ya tenia horario'.format(horario['nombreMedico']))
        existentes += 1
        continue

    codigo, cuerpo = pedir('POST', BASE + '/api/schedules', horario, token=token)

    if codigo == 201:
        print('  {}: {} a {}, {}'.format(
                horario['nombreMedico'], horario['horaInicio'],
                horario['horaFin'], ', '.join(horario['dias'])))
        creados += 1
    else:
        print('  FALLO al crear el horario de {}: HTTP {} {}'.format(
                horario['nombreMedico'], codigo,
                str(cuerpo.get('message', ''))[:80]))
        sys.exit(1)

# Lo que de verdad importa: que haya huecos. Un horario sin huecos no sirve.
codigo, cuerpo = pedir('GET', BASE + '/api/schedules', token=token)
horarios = (cuerpo.get('data') or []) if codigo == 200 else []

if not horarios:
    print('\nNo hay ningun horario. Algo fallo.')
    sys.exit(1)

print('\n{} horario(s) creado(s), {} ya existian.'.format(creados, existentes))
print('Los huecos de las proximas semanas quedan generados en time_slots.')
