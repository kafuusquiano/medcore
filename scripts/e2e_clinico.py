"""Recorrido CLINICO completo de MedCore, de la cita al aviso.

Lo que el recorrido de e2e_gateway.py no cubre: la cadena larga que atraviesa
cinco servicios y cuatro topicos de Kafka.

    cita -> atencion -> diagnostico -> orden de laboratorio -> muestra ->
    resultado critico -> aviso al medico -> descuento de reactivo ->
    vigilancia epidemiologica

Uso, con el stack levantado y el seed de demostracion aplicado:

    bash infra/postgres/seeds/aplicar-demo.sh
    python scripts/e2e_clinico.py <dni-admin> <contrasena>

El DNI del administrador por defecto es 00000001, y la contrasena la de
ADMIN_INITIAL_PASSWORD del .env.

No usa dependencias externas: solo urllib de la biblioteca estandar.
"""
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta

BASE = 'http://localhost:8080'
LAB_DIRECTO = 'http://localhost:8085'
MAILPIT = 'http://localhost:8025'

fallos = []
estado = {}


def pedir(metodo, url, cuerpo=None, token=None, texto=False, binario=False,
          timeout=40):
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

    peticion = urllib.request.Request(url, data=datos, headers=h, method=metodo)
    try:
        with urllib.request.urlopen(peticion, timeout=timeout) as r:
            # Un PNG o un PDF no son UTF-8: decodificarlos rompe la lectura y
            # el fallo parece del endpoint.
            if binario:
                return r.status, r.read()
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
    print('\n' + '=' * 72)
    print(titulo)
    print('=' * 72)


def comprobar(descripcion, condicion, detalle=''):
    marca = '[ok]   ' if condicion else '[FALLO]'
    print('  {} {}{}'.format(marca, descripcion,
                             (' | ' + str(detalle)) if detalle else ''))
    if not condicion:
        fallos.append(descripcion)
    return condicion


def esperar(segundos, motivo):
    print('  ... esperando {}s: {}'.format(segundos, motivo))
    time.sleep(segundos)


def abandonar(motivo):
    print('\n' + motivo)
    print('El recorrido es una cadena: sin este paso no hay nada que seguir.')
    if fallos:
        print('\nFallos hasta aqui:')
        for f in fallos:
            print('  - ' + f)
    sys.exit(1)


# ============================================================
paso('0. Entrar como administrador')

usuario = sys.argv[1] if len(sys.argv) > 1 else '00000001'
clave = sys.argv[2] if len(sys.argv) > 2 else None

codigo, cuerpo = pedir('POST', BASE + '/api/auth/login',
                       {'identificador': usuario, 'password': clave})

if not comprobar('login del administrador', codigo == 200 and cuerpo.get('success'),
                 'HTTP ' + str(codigo) + ' ' + str(cuerpo.get('message', ''))[:60]):
    abandonar('Sin token no se puede seguir.')

token = cuerpo['data']['accessToken']


# ============================================================
paso('1. El seed de demostracion esta aplicado')

codigo, cuerpo = pedir('GET', BASE + '/api/schedules', token=token)
horarios = (cuerpo.get('data') or []) if codigo == 200 else []

if not comprobar('hay horarios medicos', len(horarios) >= 1,
                 str(len(horarios)) + ' horario(s)'):
    abandonar('Falta el seed. Ejecuta: bash infra/postgres/seeds/aplicar-demo.sh')

medico = horarios[0]
estado['idMedico'] = medico.get('idMedico')
estado['nombreMedico'] = medico.get('nombreMedico')
print('  medico: {} (id {})'.format(estado['nombreMedico'], estado['idMedico']))

codigo, cuerpo = pedir('GET', BASE + '/api/patients?page=0&size=50', token=token)
pacientes = ((cuerpo.get('data') or {}).get('contenido') or []) if codigo == 200 else []

if not comprobar('hay pacientes', len(pacientes) >= 1,
                 str(len(pacientes)) + ' paciente(s)'):
    abandonar('Falta el seed de pacientes.')

# Se elige un adulto: un lactante tiene otros rangos de referencia y el
# recorrido quedaria menos claro.
# Se usan adultos: un lactante tiene otros rangos de referencia y el
# recorrido quedaria menos claro. El paciente concreto se elige al reservar,
# porque uno que ya tenga cita ese dia no puede tener otra.
candidatos = [p for p in pacientes if (p.get('edad') or 0) >= 18] or pacientes
comprobar('hay pacientes adultos', len(candidatos) >= 1,
          str(len(candidatos)) + ' candidato(s)')


# ============================================================
paso('2. Reservar una cita (appointment.created -> aviso al paciente)')

# Se busca una combinacion libre de dia y paciente. Al repetir el recorrido,
# los pacientes del intento anterior ya tienen cita en los dias de antes, y el
# sistema las rechaza con un 409 que es lo correcto: un paciente no puede
# tener dos citas el mismo dia.
nombres_dia = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY',
               'FRIDAY', 'SATURDAY', 'SUNDAY']

# Los dias que atiende el medico, como los devuelve su horario.
dias = medico.get('dias') or []

dias_atiende = [date.today() + timedelta(days=d) for d in range(1, 21)]
dias_atiende = [d for d in dias_atiende if nombres_dia[d.weekday()] in dias]

comprobar('el medico atiende algun dia de las proximas 3 semanas',
          len(dias_atiende) >= 1, medico.get('dias'))

if not dias_atiende:
    abandonar('Sin dia de atencion no hay cita que reservar.')

reservada = None
motivo_ultimo = 'no se probo ninguna combinacion'

for fecha_candidata in dias_atiende:
    codigo, cuerpo = pedir('GET', BASE + '/api/appointments/slots?idMedico='
                           + str(estado['idMedico']) + '&fecha='
                           + fecha_candidata.isoformat(), token=token)
    libres = [h for h in (cuerpo.get('data') or []) if h.get('disponible')]

    if not libres:
        motivo_ultimo = 'sin huecos el ' + fecha_candidata.isoformat()
        continue

    for candidato in candidatos:
        codigo, cuerpo = pedir('POST', BASE + '/api/appointments', {
            'idPaciente': candidato['id'],
            'idSlot': libres[0]['id'],
            'tipo': 'CONSULTATION',
            'motivo': 'Fiebre y dolor abdominal de tres dias',
        }, token=token)

        if codigo == 201:
            reservada = (candidato, cuerpo, fecha_candidata, libres)
            break

        motivo_ultimo = 'HTTP ' + str(codigo) + ' ' + str(cuerpo.get('message', ''))[:50]

    if reservada:
        break

if not comprobar('cita reservada', reservada is not None,
                 'HTTP 201' if reservada else motivo_ultimo):
    abandonar('Sin cita no hay atencion. Ningun paciente tiene hueco libre en '
              'las proximas tres semanas: reinicia las bases con '
              '"docker compose down -v" y vuelve a aplicar el seed.')

elegido, cuerpo, fecha_cita, libres = reservada
hueco = libres[0]

# El listado es resumido; el correo y el sexo solo vienen en el detalle.
codigo, detalle = pedir('GET', BASE + '/api/patients/' + str(elegido['id']),
                        token=token)
if not comprobar('el detalle del paciente responde', codigo == 200,
                 'HTTP ' + str(codigo)):
    abandonar('Sin los datos del paciente no se puede abrir la atencion.')

paciente = detalle['data']
estado['idPaciente'] = paciente['id']
estado['dniPaciente'] = paciente['dni']
estado['nombrePaciente'] = paciente.get('nombreCompleto') or (
        (paciente.get('nombres', '') + ' ' + paciente.get('apellidos', '')).strip())
estado['correoPaciente'] = paciente.get('correo')
estado['zonaPaciente'] = paciente.get('zona')
estado['edadPaciente'] = paciente.get('edad')
estado['sexoPaciente'] = paciente.get('sexo')

print('  paciente: {} ({}), {} anios, {}'.format(
        estado['nombrePaciente'], estado['dniPaciente'],
        estado['edadPaciente'], estado['zonaPaciente']))
print('  cita el {} a las {}'.format(fecha_cita, hueco.get('horaInicio')))

# El mismo hueco no se puede reservar dos veces.
codigo, _ = pedir('POST', BASE + '/api/appointments', {
    'idPaciente': estado['idPaciente'],
    'idSlot': hueco['id'],
    'tipo': 'CONSULTATION',
    'motivo': 'Intento de doble reserva',
}, token=token)
comprobar('el mismo hueco no se reserva dos veces', codigo == 409,
          'HTTP ' + str(codigo))

estado['idCita'] = cuerpo['data']['id']
print('  cita {} el {} a las {}'.format(
        estado['idCita'], fecha_cita, hueco.get('horaInicio')))

# El mismo hueco no se puede reservar dos veces.
codigo, _ = pedir('POST', BASE + '/api/appointments', {
    'idPaciente': estado['idPaciente'],
    'idSlot': hueco['id'],
    'tipo': 'CONSULTATION',
    'motivo': 'Intento de doble reserva',
}, token=token)
comprobar('el mismo hueco no se reserva dos veces', codigo == 409,
          'HTTP ' + str(codigo))


# ============================================================
paso('3. Abrir la atencion medica')

codigo, cuerpo = pedir('POST', BASE + '/api/medical/attentions', {
    'idCita': estado['idCita'],
    'idPaciente': estado['idPaciente'],
    'dniPaciente': estado['dniPaciente'],
    'nombrePaciente': estado['nombrePaciente'],
    'correoPaciente': estado['correoPaciente'],
    'zonaPaciente': estado['zonaPaciente'],
    'edadPaciente': estado['edadPaciente'],
    'sexoPaciente': estado['sexoPaciente'],
    'idMedico': estado['idMedico'],
    'nombreMedico': estado['nombreMedico'],
    'motivoConsulta': 'Fiebre y dolor abdominal de tres dias',
}, token=token)

if not comprobar('atencion abierta', codigo == 201,
                 'HTTP ' + str(codigo) + ' ' + str(cuerpo.get('message', ''))[:70]):
    abandonar('Sin atencion no hay orden de laboratorio.')

estado['idAtencion'] = cuerpo['data']['id']
comprobar('guarda la zona del paciente para la vigilancia',
          cuerpo['data'].get('zonaPaciente') == estado['zonaPaciente'],
          cuerpo['data'].get('zonaPaciente'))


# ============================================================
paso('4. Registrar un diagnostico CIE-10')

codigo, cuerpo = pedir('GET', BASE + '/api/diagnoses/cie10?texto=A09', token=token)
codigos = ((cuerpo.get('data') or {}).get('contenido') or []) if codigo == 200 else []

if not codigos:
    codigo, cuerpo = pedir('GET', BASE + '/api/diagnoses/cie10?texto=a', token=token)
    codigos = ((cuerpo.get('data') or {}).get('contenido') or []) if codigo == 200 else []

comprobar('el catalogo CIE-10 responde', len(codigos) >= 1,
          str(len(codigos)) + ' codigo(s)')

if not codigos:
    abandonar('Sin catalogo CIE-10 no hay diagnostico.')

cie10 = codigos[0]
estado['codigoCie10'] = cie10['codigo']

codigo, cuerpo = pedir('POST', BASE + '/api/medical/attentions/' + str(estado['idAtencion'])
                       + '/diagnosticos', {
    'codigoCie10': estado['codigoCie10'],
    'esPrincipal': True,
    'esCasoNuevo': True,
    'observaciones': 'Cuadro compatible, se solicita analitica',
}, token=token)

comprobar('diagnostico registrado', codigo in (200, 201),
          'HTTP ' + str(codigo) + ' ' + estado['codigoCie10']
          + ' ' + str(cie10.get('descripcion', ''))[:40])

# El mismo codigo dos veces en la misma atencion no tiene sentido.
codigo, _ = pedir('POST', BASE + '/api/medical/attentions/' + str(estado['idAtencion'])
                  + '/diagnosticos', {
    'codigoCie10': estado['codigoCie10'],
    'esPrincipal': False,
    'esCasoNuevo': False,
}, token=token)
comprobar('el mismo codigo no se repite en la atencion', codigo == 409,
          'HTTP ' + str(codigo))


# ============================================================
paso('5. Emitir la orden de laboratorio (lab-order.created)')

codigo, cuerpo = pedir('GET', BASE + '/api/lab-catalog', token=token)
pruebas = (cuerpo.get('data') or []) if codigo == 200 else []
comprobar('el catalogo del laboratorio responde', len(pruebas) >= 1,
          str(len(pruebas)) + ' prueba(s)')

# Se piden pruebas que SI declaran consumo de reactivo, para poder comprobar
# despues que el almacen se descuenta solo.
deseadas = [p['codigo'] for p in pruebas
            if p['codigo'] in ('HEM-001', 'HEM-003', 'BIO-004')]
if not deseadas:
    deseadas = [p['codigo'] for p in pruebas[:2]]

estado['pruebas'] = deseadas

por_codigo = {p['codigo']: p for p in pruebas}

codigo, cuerpo = pedir('POST', BASE + '/api/lab-orders/atencion/'
                       + str(estado['idAtencion']), {
    'prioridad': 'URGENTE',
    'observaciones': 'Paciente con fiebre, descartar infeccion',
    'pruebas': [{
        'codigo': c,
        'nombre': por_codigo.get(c, {}).get('nombre', c),
        'indicaciones': None,
    } for c in deseadas],
}, token=token)

if not comprobar('orden emitida', codigo == 201,
                 'HTTP ' + str(codigo) + ' ' + str(cuerpo.get('message', ''))[:70]):
    abandonar('Sin orden no hay muestra.')

estado['codigoOrden'] = cuerpo['data']['codigo']
print('  orden {} con {}'.format(estado['codigoOrden'], ', '.join(deseadas)))


# ============================================================
paso('6. La orden llega al laboratorio por Kafka')

esperar(10, 'lab-order.created -> laboratory-service')

codigo, cuerpo = pedir('GET', BASE + '/api/lab-queue', token=token)
cola = (cuerpo.get('data') or []) if codigo == 200 else []
mia = [o for o in cola if o.get('codigoOrden') == estado['codigoOrden']]

if not comprobar('la orden esta en la cola del laboratorio', len(mia) == 1,
                 str(len(cola)) + ' orden(es) en cola'):
    abandonar('La orden no llego al laboratorio: revisa el consumidor de Kafka.')

comprobar('conserva la prioridad urgente', mia[0].get('urgente') is True)
comprobar('trae las pruebas solicitadas',
          len(mia[0].get('pruebas') or []) == len(deseadas),
          mia[0].get('pruebas'))


# ============================================================
paso('7. Registrar la muestra (sample.registered)')

codigo, cuerpo = pedir('POST', BASE + '/api/samples', {
    'idOrden': mia[0]['idOrden'],
    'tipoMuestra': 'SANGRE',
    'urgente': True,
    'tomadaPor': 'lab01',
    'observaciones': 'Ayuno de 8 horas',
}, token=token)

if not comprobar('muestra registrada', codigo == 201,
                 'HTTP ' + str(codigo) + ' ' + str(cuerpo.get('message', ''))[:70]):
    abandonar('Sin muestra no hay resultados.')

estado['idMuestra'] = cuerpo['data']['id']
estado['codigoMuestra'] = cuerpo['data']['codigo']
print('  muestra {}'.format(estado['codigoMuestra']))

# La etiqueta con el QR: es lo que se pega en el tubo.
codigo, datos = pedir('GET', BASE + '/api/samples/' + str(estado['idMuestra'])
                      + '/etiqueta?lado=200', token=token, binario=True)
comprobar('la etiqueta con el QR se genera',
          codigo == 200 and isinstance(datos, bytes) and datos[1:4] == b'PNG',
          'HTTP ' + str(codigo) + ', '
          + (str(len(datos)) + ' bytes' if isinstance(datos, bytes) else str(datos)[:60]))

# Una muestra en proceso todavia no tiene informe.
codigo, cuerpo = pedir('GET', BASE + '/api/samples/' + str(estado['idMuestra'])
                       + '/informe', token=token)
comprobar('una muestra sin procesar no tiene informe', codigo == 409,
          'HTTP ' + str(codigo))


# ============================================================
paso('8. El equipo envia los resultados por HL7, con un valor critico')

codigo, _ = pedir('PATCH', BASE + '/api/samples/' + str(estado['idMuestra'])
                  + '/proceso', {}, token=token)
comprobar('la muestra pasa a EN_PROCESO', codigo == 200, 'HTTP ' + str(codigo))

# Potasio de 7.2 mmol/L: es un valor critico de verdad, de los que obligan a
# llamar al medico por telefono.
ahora = time.strftime('%Y%m%d%H%M%S')
trama = '\r'.join([
    'MSH|^~\\&|ANALIZADOR|LAB_CENTRAL|MEDCORE|MEDCORE_LAB|' + ahora
    + '||ORU^R01|MSG' + ahora + '|P|2.5.1',
    'PID|1||' + estado['dniPaciente'] + '||'
    + estado['nombrePaciente'].replace(' ', '^'),
    'OBR|1||' + estado['codigoMuestra'] + '|PANEL^Panel solicitado',
    'OBX|1|NM|BIO-004^Potasio||7.2|mmol/L|3.5-5.1|HH|||F',
    'OBX|2|NM|HEM-001^Hemoglobina||13.4|g/dL|12.0-16.0|N|||F',
    # Las TRES pruebas de la orden: mientras falte una, la muestra no
    # pasa a PROCESADA, y eso es lo correcto.
    'OBX|3|NM|HEM-003^Leucocitos||8.2|x10^3/uL|4.0-10.0|N|||F',
]) + '\r'

codigo, ack = pedir('POST', LAB_DIRECTO + '/api/hl7/oru', trama, texto=True)
comprobar('el equipo recibe un ACK', codigo == 200, 'HTTP ' + str(codigo))
comprobar('el ACK es de aceptacion (AA)', 'MSA|AA|' in str(ack),
          str(ack).replace('\r', ' ')[:60])

esperar(8, 'la trama se procesa y se publican result.registered y result.critical')

codigo, cuerpo = pedir('GET', BASE + '/api/results/muestra/'
                       + str(estado['idMuestra']), token=token)
resultados = (cuerpo.get('data') or []) if codigo == 200 else []

comprobar('los resultados quedaron registrados',
          len(resultados) >= len(estado['pruebas']),
          str(len(resultados)) + ' de ' + str(len(estado['pruebas']))
          + ' prueba(s) pedida(s)')

criticos = [r for r in resultados if r.get('critico')]
comprobar('el potasio se marco como critico', len(criticos) >= 1,
          [r.get('codigoPrueba') + '=' + str(r.get('valorNumerico'))
           for r in criticos])

normales = [r for r in resultados if r.get('codigoPrueba') == 'HEM-001']
if normales:
    comprobar('la hemoglobina normal NO se marco critica',
              not normales[0].get('critico'),
              'interpretacion=' + str(normales[0].get('etiquetaInterpretacion')))

leucos = [r for r in resultados if r.get('codigoPrueba') == 'HEM-003']
if leucos:
    # 8.2 x10^3/uL son 8200 leucocitos: normal. El mismo numero informado en
    # /uL se leeria como 8.200.000 y saldria critico, que es lo que ocurre si
    # el equipo manda la unidad equivocada.
    comprobar('los leucocitos en la unidad del catalogo NO son criticos',
              not leucos[0].get('critico'),
              str(leucos[0].get('valorNumerico')) + ' '
              + str(leucos[0].get('unidad')) + ', '
              + str(leucos[0].get('etiquetaInterpretacion')))


# ============================================================
paso('9. El aviso del valor critico llega al medico, no al paciente')

esperar(12, 'result.critical -> notification-service -> Mailpit')

codigo, cuerpo = pedir('GET', MAILPIT + '/api/v1/search?query='
                       + urllib.parse.quote('critico'))
mensajes = cuerpo.get('messages', []) if isinstance(cuerpo, dict) else []
comprobar('hay correo de valor critico', len(mensajes) >= 1,
          str(len(mensajes)) + ' correo(s)')

# La decision clinica principal del sistema: un valor critico NO va al
# paciente. Se lo tiene que explicar su medico.
codigo, cuerpo = pedir('GET', BASE + '/api/notifications/paciente/'
                       + str(estado['idPaciente']), token=token)
bandeja = ((cuerpo.get('data') or {}).get('contenido') or []) if codigo == 200 else []
avisos_criticos = [a for a in bandeja if a.get('tipo') == 'VALOR_CRITICO']

comprobar('el valor critico NO aparece en la bandeja del paciente',
          len(avisos_criticos) == 0,
          str(len(bandeja)) + ' aviso(s) en su bandeja, '
          + str(len(avisos_criticos)) + ' criticos')


# ============================================================
paso('10. El almacen se descuenta solo')

codigo, cuerpo = pedir('GET', BASE + '/api/movements/consumo-automatico/pruebas',
                       token=token)
con_consumo = (cuerpo.get('data') or []) if codigo == 200 else []
comprobar('hay pruebas con consumo automatico declarado', len(con_consumo) >= 1,
          str(len(con_consumo)) + ' prueba(s)')

# Los movimientos de esta muestra: uno por reactivo de cada prueba hecha.
codigo, cuerpo = pedir('GET', BASE + '/api/movements?page=0&size=50', token=token)
movimientos = ((cuerpo.get('data') or {}).get('contenido') or []) if codigo == 200 else []
mios = [m for m in movimientos
        if m.get('codigoMuestra') == estado['codigoMuestra']]

comprobar('se descontaron reactivos por la muestra', len(mios) >= 1,
          str(len(mios)) + ' movimiento(s): '
          + ', '.join(sorted({m.get('codigoReactivo') for m in mios})))

if mios:
    comprobar('los descuentos son salidas registradas por el sistema',
              all(m.get('tipo') == 'SALIDA' for m in mios)
              and all(m.get('registradoPor') == 'sistema' for m in mios))


# ============================================================
paso('11. El informe en PDF, ya con la muestra procesada')

codigo, _ = pedir('PATCH', BASE + '/api/samples/' + str(estado['idMuestra'])
                  + '/procesada', {}, token=token)
comprobar('la muestra pasa a PROCESADA', codigo == 200, 'HTTP ' + str(codigo))

codigo, pdf = pedir('GET', BASE + '/api/samples/' + str(estado['idMuestra'])
                    + '/informe', token=token, binario=True)
comprobar('el informe en PDF se emite',
          codigo == 200 and isinstance(pdf, bytes) and pdf[:5] == b'%PDF-',
          'HTTP ' + str(codigo) + ', '
          + (str(len(pdf)) + ' bytes' if isinstance(pdf, bytes) else str(pdf)[:60]))


# ============================================================
paso('12. Cerrar la atencion (diagnosis.registered -> vigilancia CIE-10)')

codigo, cuerpo = pedir('PATCH', BASE + '/api/medical/attentions/' + str(estado['idAtencion'])
                       + '/finalizar', {}, token=token)
comprobar('atencion finalizada', codigo == 200,
          'HTTP ' + str(codigo) + ' ' + str(cuerpo.get('message', ''))[:60])

esperar(12, 'diagnosis.registered -> analytics-service')

codigo, cuerpo = pedir('GET', BASE + '/api/surveillance/morbilidad?periodo=MENSUAL',
                       token=token)
morbilidad = (cuerpo.get('data') or []) if codigo == 200 else []
mio = [m for m in morbilidad if m.get('clave') == estado['codigoCie10']]

comprobar('el diagnostico llego a la vigilancia por CIE-10', len(mio) == 1,
          estado['codigoCie10'] + ': '
          + (str(mio[0].get('total')) + ' caso(s)' if mio
             else 'no aparece entre ' + str(len(morbilidad)) + ' codigo(s)'))

codigo, cuerpo = pedir('GET', BASE + '/api/surveillance/grupos-edad?periodo=MENSUAL',
                       token=token)
grupos = (cuerpo.get('data') or []) if codigo == 200 else []
comprobar('el caso se agrupo por edad', len(grupos) >= 1,
          [g.get('clave') for g in grupos])

codigo, cuerpo = pedir('GET', BASE + '/api/surveillance/zonas?periodo=MENSUAL',
                       token=token)
zonas = (cuerpo.get('data') or []) if codigo == 200 else []
mi_zona = [z for z in zonas if z.get('clave') == estado['zonaPaciente']]
comprobar('el caso se agrupo por la zona del paciente', len(mi_zona) == 1,
          estado['zonaPaciente'])


# ============================================================
paso('13. Anular una cita retira su recordatorio')

# Se busca otra combinacion libre de dia y paciente, sin repetir la del
# recorrido: la cita que se va a anular tiene que ser distinta de la que lo
# recorrio.
reservada_prueba = None

for fecha_candidata in dias_atiende:
    codigo, cuerpo = pedir('GET', BASE + '/api/appointments/slots?idMedico='
                           + str(estado['idMedico']) + '&fecha='
                           + fecha_candidata.isoformat(), token=token)
    libres_otro = [h for h in (cuerpo.get('data') or [])
                   if h.get('disponible') and h['id'] != hueco['id']]

    for candidato in candidatos:
        if not libres_otro:
            break
        codigo, cuerpo = pedir('POST', BASE + '/api/appointments', {
            'idPaciente': candidato['id'],
            'idSlot': libres_otro[0]['id'],
            'tipo': 'CONSULTATION',
            'motivo': 'Control, se va a anular',
        }, token=token)
        if codigo == 201:
            reservada_prueba = cuerpo
            break

    if reservada_prueba:
        break

if comprobar('cita de prueba reservada', reservada_prueba is not None,
             'HTTP 201' if reservada_prueba else 'ninguna combinacion libre'):

    id_anular = reservada_prueba['data']['id']
    esperar(8, 'se programa su recordatorio')

    codigo, cuerpo = pedir('PATCH', BASE + '/api/appointments/'
                           + str(id_anular) + '/cancelar',
                           {'motivo': 'El paciente no puede asistir'},
                           token=token)
    comprobar('cita anulada', codigo == 200, 'HTTP ' + str(codigo))

    esperar(10, 'appointment.cancelled -> se retira el recordatorio')

    # El recordatorio sale el dia antes de la cita; si no se retira, el
    # paciente recibe el recordatorio de una cita que ya no existe.
    codigo, cuerpo = pedir('GET', BASE
                           + '/api/notifications?estado=OMITIDA&page=0&size=50',
                           token=token)
    omitidos = ((cuerpo.get('data') or {}).get('contenido')
                or []) if codigo == 200 else []
    retirados = [o for o in omitidos
                 if o.get('referencia') == 'cita:' + str(id_anular)]

    comprobar('el recordatorio de la cita anulada quedo retirado',
              len(retirados) >= 1,
              str(len(retirados)) + ' aviso(s) retirado(s) de '
              + str(len(omitidos)) + ' omitido(s)')

    if retirados:
        comprobar('el motivo del retiro queda escrito',
                  'anulo' in str(retirados[0].get('motivoFallo', '')),
                  str(retirados[0].get('motivoFallo'))[:60])


# ============================================================
paso('RESUMEN')

print('  Recorrido:')
print('    cita {} -> atencion {} -> diagnostico {} -> orden {} ->'.format(
        estado.get('idCita'), estado.get('idAtencion'),
        estado.get('codigoCie10'), estado.get('codigoOrden')))
print('    muestra {} -> resultado critico -> aviso al medico ->'.format(
        estado.get('codigoMuestra')))
print('    descuento de reactivo -> vigilancia por CIE-10')
print()

if fallos:
    print('  {} comprobacion(es) FALLARON:'.format(len(fallos)))
    for f in fallos:
        print('    - ' + f)
    sys.exit(1)

print('  Todas las comprobaciones pasaron.')
print('  Cinco servicios y cuatro topicos de Kafka, de punta a punta.')
