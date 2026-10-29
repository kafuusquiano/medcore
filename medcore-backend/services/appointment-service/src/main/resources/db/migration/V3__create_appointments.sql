-- ============================================================
-- appointment_db V3: citas.
--
-- Guarda una copia del nombre y el contacto del paciente tal como
-- estaban al reservar. Es duplicacion deliberada: evita llamar a
-- patient-service para listar la agenda del dia, que es la consulta
-- mas frecuente, y deja constancia de con que datos se reservo.
-- ============================================================

CREATE TABLE appointments (
    id                  BIGSERIAL PRIMARY KEY,
    id_paciente         BIGINT       NOT NULL,
    dni_paciente        VARCHAR(8)   NOT NULL,
    nombre_paciente     VARCHAR(170) NOT NULL,
    correo_paciente     VARCHAR(120),
    telefono_paciente   VARCHAR(15),
    id_medico           BIGINT       NOT NULL,
    nombre_medico       VARCHAR(120) NOT NULL,
    id_slot             BIGINT       NOT NULL REFERENCES time_slots (id),
    fecha_hora          TIMESTAMP    NOT NULL,
    tipo                VARCHAR(20)  NOT NULL,
    estado              VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    motivo              VARCHAR(300),
    motivo_cancelacion  VARCHAR(300),
    fecha_creacion      TIMESTAMPTZ  NOT NULL,
    fecha_actualizacion TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_appointment_tipo CHECK (
        tipo IN ('CONSULTATION', 'LAB_FOLLOW_UP', 'EMERGENCY')),
    CONSTRAINT chk_appointment_estado CHECK (
        estado IN ('SCHEDULED', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    -- Una cita anulada debe decir por que.
    CONSTRAINT chk_appointment_cancelacion CHECK (
        estado <> 'CANCELLED' OR motivo_cancelacion IS NOT NULL)
);

-- Un slot solo puede tener una cita viva. El indice es parcial sobre los
-- estados no finales, de modo que al anular una cita el slot quede libre
-- para reservarse otra vez sin chocar con la anulada.
CREATE UNIQUE INDEX ux_appointment_slot_activa
    ON appointments (id_slot)
    WHERE estado IN ('SCHEDULED', 'CONFIRMED');

CREATE INDEX ix_appointment_paciente ON appointments (id_paciente, fecha_hora DESC);
-- Agenda del dia de un medico, que es la consulta mas frecuente.
CREATE INDEX ix_appointment_medico_fecha ON appointments (id_medico, fecha_hora);
CREATE INDEX ix_appointment_estado ON appointments (estado, fecha_hora);
