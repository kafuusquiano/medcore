-- ============================================================
-- lab_db V4: cola de ordenes pendientes.
--
-- Cuando medical-service emite una orden publica lab-order.created, pero el
-- tubo llega al laboratorio despues, o no llega nunca si el paciente no
-- acude. Hacen falta por tanto dos registros distintos: la orden en cola, que
-- es lo que el laboratorio espera recibir, y la muestra, que es el tubo que
-- ya tiene en la mano.
--
-- La tabla es una proyeccion local del evento, no la fuente de verdad: el
-- dueno de la orden es medical-service (MC1). Existe para que el laboratorio
-- pueda trabajar aunque medical-service este caido, que es el punto de
-- desacoplar por Kafka.
-- ============================================================

CREATE TABLE pending_lab_orders (
    -- El identificador es el de la orden en medical-service, no uno propio:
    -- asi recibir el mismo evento dos veces no crea dos filas (idempotencia).
    id_orden        BIGINT       PRIMARY KEY,
    codigo_orden    VARCHAR(25)  NOT NULL,
    id_atencion     BIGINT,
    id_paciente     BIGINT       NOT NULL,
    dni_paciente    VARCHAR(8)   NOT NULL,
    nombre_paciente VARCHAR(170) NOT NULL,
    correo_paciente VARCHAR(120),
    id_medico       BIGINT       NOT NULL,
    nombre_medico   VARCHAR(170),
    prioridad       VARCHAR(20)  NOT NULL DEFAULT 'RUTINA',
    observaciones   VARCHAR(500),
    -- Pasa a TRUE al registrarse la muestra. No se borra la fila: deja
    -- constancia de cuanto tardo el tubo en llegar, y de las ordenes que
    -- nunca se atendieron.
    atendida        BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_evento    TIMESTAMPTZ  NOT NULL,
    fecha_recepcion TIMESTAMPTZ  NOT NULL,

    CONSTRAINT chk_pending_prioridad CHECK (prioridad IN ('RUTINA', 'URGENTE'))
);

-- Cola de espera: las urgentes primero, y dentro de cada grupo por antiguedad.
CREATE INDEX ix_pending_cola
    ON pending_lab_orders (prioridad DESC, fecha_evento) WHERE atendida = FALSE;

CREATE INDEX ix_pending_paciente ON pending_lab_orders (id_paciente);
CREATE INDEX ix_pending_dni      ON pending_lab_orders (dni_paciente) WHERE atendida = FALSE;


-- ============================================================
-- Pruebas que pide cada orden.
--
-- Se guarda el codigo tal como lo pidio el medico, sin clave ajena a
-- test_catalog: si una prueba se da de baja del catalogo, las ordenes
-- antiguas deben seguir legibles.
-- ============================================================

CREATE TABLE pending_lab_order_tests (
    id            BIGSERIAL   PRIMARY KEY,
    id_orden      BIGINT      NOT NULL REFERENCES pending_lab_orders (id_orden) ON DELETE CASCADE,
    codigo_prueba VARCHAR(20) NOT NULL,

    CONSTRAINT ux_pending_test UNIQUE (id_orden, codigo_prueba)
);

CREATE INDEX ix_pending_test_orden ON pending_lab_order_tests (id_orden);
