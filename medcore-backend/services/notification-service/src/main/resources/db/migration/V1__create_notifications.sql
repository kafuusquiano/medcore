-- ============================================================
-- notification_db V1: avisos y sus plantillas.
--
-- Un mismo hecho produce VARIAS filas en notifications, una por canal: el
-- aviso de cita reservada va por correo y tambien in-app, y cada uno se
-- envia, falla y se reintenta por separado. Con una sola fila y varios
-- canales habria que llevar un estado por canal dentro del registro, y
-- entonces "esta enviada" dejaria de tener una respuesta.
--
-- Los avisos NO se borran. Uno que no llego es justamente el que hay que
-- poder consultar: si el correo de un valor critico fallo, alguien tiene que
-- enterarse y llamar por telefono.
-- ============================================================

CREATE TABLE notifications (
    id                  BIGSERIAL     PRIMARY KEY,
    tipo                VARCHAR(30)   NOT NULL,
    canal               VARCHAR(20)   NOT NULL,
    estado              VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    -- Identificador del evento que lo origino. Con el tipo y el canal forma
    -- la clave de idempotencia.
    event_id            VARCHAR(60)   NOT NULL,
    destinatario        VARCHAR(150)  NOT NULL,
    id_paciente         BIGINT,
    id_usuario          BIGINT,
    asunto              VARCHAR(200)  NOT NULL,
    mensaje             VARCHAR(1000) NOT NULL,
    enlace              VARCHAR(300),
    -- Datos del evento, en JSON, para rellenar la plantilla HTML del correo.
    -- Se guardan porque un reintento puede ocurrir horas despues, incluso
    -- tras reiniciar el servicio, y entonces el consumidor que los tenia en
    -- memoria ya no existe. Sin esto, el correo reintentado saldria con los
    -- huecos vacios.
    datos               TEXT,
    intentos            INTEGER       NOT NULL DEFAULT 0,
    motivo_fallo        VARCHAR(500),
    -- Normalmente ahora mismo; el recordatorio de cita se programa para el
    -- dia anterior.
    fecha_programada    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_envio         TIMESTAMPTZ,
    fecha_lectura       TIMESTAMPTZ,
    fecha_creacion      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT chk_notif_tipo CHECK (tipo IN (
        'PACIENTE_REGISTRADO', 'CITA_RESERVADA', 'CITA_CANCELADA',
        'CITA_RECORDATORIO', 'ORDEN_LABORATORIO', 'MUESTRA_REGISTRADA',
        'MUESTRA_RECHAZADA', 'RESULTADO_LISTO', 'VALOR_CRITICO',
        'STOCK_BAJO', 'LOTE_VENCIDO')),
    CONSTRAINT chk_notif_canal CHECK (canal IN ('EMAIL', 'IN_APP', 'PUSH', 'SMS')),
    CONSTRAINT chk_notif_estado CHECK (estado IN (
        'PENDIENTE', 'ENVIADA', 'FALLIDA', 'OMITIDA', 'LEIDA')),
    -- Un aviso enviado tiene su hora de envio, y uno leido su hora de
    -- lectura. Sin esto, un estado podria quedar sin la fecha que lo
    -- justifica y el historial no cuadraria.
    CONSTRAINT chk_notif_envio CHECK (
        estado NOT IN ('ENVIADA', 'LEIDA') OR fecha_envio IS NOT NULL),
    CONSTRAINT chk_notif_lectura CHECK (
        estado <> 'LEIDA' OR fecha_lectura IS NOT NULL),
    -- Si fallo o se omitio, tiene que decir por que: un aviso que no llego
    -- sin motivo no sirve para averiguar nada.
    CONSTRAINT chk_notif_motivo CHECK (
        estado NOT IN ('FALLIDA', 'OMITIDA') OR motivo_fallo IS NOT NULL),
    CONSTRAINT chk_notif_intentos CHECK (intentos >= 0)
);

-- Idempotencia: Kafka entrega al menos una vez, y sin esto un reenvio
-- mandaria el mismo correo dos veces al paciente. La clave es el evento con
-- el tipo y el canal, porque un hecho genera un aviso por canal.
CREATE UNIQUE INDEX ux_notif_evento ON notifications (event_id, tipo, canal);

-- Bandeja del usuario: sus avisos in-app, los no leidos primero.
CREATE INDEX ix_notif_paciente
    ON notifications (id_paciente, fecha_creacion DESC)
    WHERE id_paciente IS NOT NULL;
CREATE INDEX ix_notif_usuario
    ON notifications (id_usuario, fecha_creacion DESC)
    WHERE id_usuario IS NOT NULL;

-- Cola de envio: lo pendiente cuya hora ya llego. Indice PARCIAL porque los
-- avisos ya enviados son la inmensa mayoria y no interesan aqui.
CREATE INDEX ix_notif_pendientes
    ON notifications (fecha_programada)
    WHERE estado IN ('PENDIENTE', 'FALLIDA');

CREATE INDEX ix_notif_estado ON notifications (estado, fecha_creacion DESC);


-- ============================================================
-- Plantillas de los textos.
--
-- Viven en la base porque son lo que se corrige con el uso: una redaccion
-- confusa, una falta, un cambio de tono. El cuerpo HTML de los correos va en
-- resources/templates/email/ con Thymeleaf; maquetar HTML dentro de una
-- columna de texto seria ilegible.
--
-- Los marcadores son {nombre} y se sustituyen al enviar. Uno sin valor se
-- deja visible en lugar de quedar como "null": asi se ve que falta un dato.
-- ============================================================

CREATE TABLE notification_templates (
    tipo                VARCHAR(30)   PRIMARY KEY,
    asunto              VARCHAR(200)  NOT NULL,
    mensaje             VARCHAR(1000) NOT NULL,
    -- Nombre de la plantilla HTML, sin extension. Nulo en los tipos que solo
    -- van in-app.
    plantilla_html      VARCHAR(80),
    enlace              VARCHAR(300),
    activa              BOOLEAN       NOT NULL DEFAULT TRUE,
    fecha_actualizacion TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT chk_tpl_tipo CHECK (tipo IN (
        'PACIENTE_REGISTRADO', 'CITA_RESERVADA', 'CITA_CANCELADA',
        'CITA_RECORDATORIO', 'ORDEN_LABORATORIO', 'MUESTRA_REGISTRADA',
        'MUESTRA_RECHAZADA', 'RESULTADO_LISTO', 'VALOR_CRITICO',
        'STOCK_BAJO', 'LOTE_VENCIDO'))
);


INSERT INTO notification_templates (tipo, asunto, mensaje, plantilla_html, enlace) VALUES
    ('PACIENTE_REGISTRADO',
     'Bienvenido a MedCore',
     'Hola {nombrePaciente}, su historia clinica se ha creado correctamente. Ya puede reservar citas y consultar sus resultados.',
     'welcome',
     '/mi-perfil'),

    ('CITA_RESERVADA',
     'Cita confirmada para el {fecha}',
     'Su cita con {nombreMedico} queda confirmada para el {fecha} a las {hora}. Acuda con 15 minutos de antelacion.',
     'appointment-confirmed',
     '/mis-citas/{idCita}'),

    ('CITA_CANCELADA',
     'Su cita del {fecha} ha sido cancelada',
     'La cita con {nombreMedico} del {fecha} a las {hora} ha sido cancelada. Motivo: {motivo}. Puede reservar otra cuando lo desee.',
     'appointment-cancelled',
     '/mis-citas'),

    ('CITA_RECORDATORIO',
     'Recordatorio: su cita es manana',
     'Le recordamos su cita con {nombreMedico} manana {fecha} a las {hora}. Si no puede asistir, cancelela para que otra persona pueda ocupar el horario.',
     'appointment-reminder',
     '/mis-citas/{idCita}'),

    ('ORDEN_LABORATORIO',
     'Analisis solicitados por su medico',
     '{nombreMedico} ha solicitado los siguientes analisis: {pruebas}. Indicaciones: {indicaciones}. Acuda al laboratorio con su documento de identidad.',
     'lab-order',
     '/mis-ordenes/{idOrden}'),

    ('MUESTRA_REGISTRADA',
     'Hemos recibido su muestra',
     'Su muestra {codigoMuestra} ha sido recibida en el laboratorio. Le avisaremos cuando el resultado este disponible.',
     NULL,
     '/mis-resultados'),

    ('MUESTRA_RECHAZADA',
     'Muestra rechazada: {codigoMuestra}',
     'La muestra {codigoMuestra} del paciente {nombrePaciente} ha sido rechazada. Motivo: {motivo}. Es necesario repetir la toma.',
     'sample-rejected',
     '/laboratorio/muestras/{idMuestra}'),

    ('RESULTADO_LISTO',
     'Su resultado de laboratorio esta disponible',
     'El resultado de {nombrePrueba} ya esta disponible. Consultelo en la aplicacion o solicitelo en recepcion.',
     'result-ready',
     '/mis-resultados/{idResultado}'),

    ('VALOR_CRITICO',
     'VALOR CRITICO: {nombrePrueba} en {nombrePaciente}',
     'El paciente {nombrePaciente} ({dniPaciente}) presenta un valor critico en {nombrePrueba}: {valor} {unidad} (rango {rango}). Requiere atencion inmediata.',
     'critical-value',
     '/laboratorio/resultados/{idResultado}'),

    ('STOCK_BAJO',
     'Stock bajo: {nombreReactivo}',
     'El reactivo {codigoReactivo} ({nombreReactivo}) tiene {cantidad} unidades, por debajo del minimo de {minimo}. Cantidad sugerida de pedido: {sugerido}.',
     NULL,
     '/almacen/reactivos/{idReactivo}'),

    ('LOTE_VENCIDO',
     'Lote vencido: {nombreReactivo} ({lote})',
     'El lote {lote} de {nombreReactivo} vencio el {fechaVencimiento} y quedan {cantidad} unidades en {ubicacion}. Retirelo del almacen.',
     'expired-lot',
     '/almacen/stock/{idStock}');
