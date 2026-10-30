-- ============================================================
-- lab_db V2: muestras.
--
-- Guardan una copia de los datos del paciente que llegaron en el evento
-- lab-order.created. Es duplicacion deliberada: el laboratorio debe poder
-- rotular, procesar y emitir el resultado sin depender de que
-- patient-service responda, y el resultado debe conservar a nombre de quien
-- se emitio.
-- ============================================================

CREATE TABLE samples (
    id                  BIGSERIAL PRIMARY KEY,
    -- Codigo de la etiqueta del tubo, por ejemplo MUE-20261029-0012.
    codigo              VARCHAR(25)  NOT NULL UNIQUE,
    id_orden            BIGINT       NOT NULL,
    codigo_orden        VARCHAR(25)  NOT NULL,
    id_paciente         BIGINT       NOT NULL,
    dni_paciente        VARCHAR(8)   NOT NULL,
    nombre_paciente     VARCHAR(170) NOT NULL,
    correo_paciente     VARCHAR(120),
    -- Sexo y edad seleccionan que rango de referencia aplica.
    sexo_paciente       VARCHAR(20),
    edad_paciente       INTEGER,
    -- analytics-service agrupa los brotes por zona.
    zona_paciente       VARCHAR(80),
    id_medico           BIGINT       NOT NULL,
    tipo_muestra        VARCHAR(30)  NOT NULL,
    estado              VARCHAR(20)  NOT NULL DEFAULT 'REGISTRADA',
    urgente             BOOLEAN      NOT NULL DEFAULT FALSE,
    observaciones       VARCHAR(500),
    motivo_rechazo      VARCHAR(300),
    tomada_por          VARCHAR(120),
    fecha_toma          TIMESTAMPTZ  NOT NULL,
    fecha_proceso       TIMESTAMPTZ,
    fecha_actualizacion TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_sample_estado CHECK (
        estado IN ('REGISTRADA', 'EN_PROCESO', 'PROCESADA', 'RECHAZADA')),
    -- Una muestra rechazada debe decir por que: el medico necesita saber
    -- que hay que repetir la toma.
    CONSTRAINT chk_sample_rechazo CHECK (
        estado <> 'RECHAZADA' OR motivo_rechazo IS NOT NULL),
    CONSTRAINT chk_sample_edad CHECK (
        edad_paciente IS NULL OR edad_paciente BETWEEN 0 AND 130)
);

-- Una orden produce como mucho una muestra viva. Indice PARCIAL: si la
-- muestra se rechaza, puede registrarse otra para la misma orden.
CREATE UNIQUE INDEX ux_sample_orden_viva
    ON samples (id_orden) WHERE estado <> 'RECHAZADA';

CREATE INDEX ix_sample_paciente ON samples (id_paciente, fecha_toma DESC);
-- Cola de trabajo: las urgentes primero, y dentro de cada grupo por llegada.
CREATE INDEX ix_sample_cola     ON samples (estado, urgente DESC, fecha_toma);
CREATE INDEX ix_sample_orden    ON samples (id_orden);
