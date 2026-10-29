-- ============================================================
-- patient_db V1: fichas de pacientes.
-- ============================================================

CREATE TABLE patients (
    id                           BIGSERIAL PRIMARY KEY,
    dni                          VARCHAR(8)   NOT NULL UNIQUE,
    nombres                      VARCHAR(80)  NOT NULL,
    apellidos                    VARCHAR(80)  NOT NULL,
    fecha_nacimiento             DATE         NOT NULL,
    sexo                         VARCHAR(20)  NOT NULL,
    tipo_sangre                  VARCHAR(15)  NOT NULL DEFAULT 'DESCONOCIDO',
    telefono                     VARCHAR(15),
    correo                       VARCHAR(120),
    direccion                    VARCHAR(200),
    zona                         VARCHAR(80),
    contacto_emergencia_nombre   VARCHAR(120),
    contacto_emergencia_telefono VARCHAR(15),
    alergias                     VARCHAR(500),
    estado                       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    fecha_creacion               TIMESTAMPTZ  NOT NULL,
    fecha_actualizacion          TIMESTAMPTZ  NOT NULL,
    version                      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_patients_dni    CHECK (dni ~ '^[0-9]{8}$'),
    CONSTRAINT chk_patients_sexo   CHECK (sexo IN ('MASCULINO', 'FEMENINO', 'NO_ESPECIFICADO')),
    CONSTRAINT chk_patients_estado CHECK (estado IN ('ACTIVO', 'INACTIVO', 'FALLECIDO')),
    -- Una fecha de nacimiento futura solo puede venir de un error de captura.
    CONSTRAINT chk_patients_nacimiento CHECK (fecha_nacimiento <= CURRENT_DATE)
);

-- Busqueda por apellidos y nombres sin distinguir mayusculas ni tildes,
-- que es como el personal de recepcion busca a un paciente.
CREATE INDEX ix_patients_apellidos ON patients (LOWER(apellidos), LOWER(nombres));
CREATE INDEX ix_patients_estado    ON patients (estado);
-- analytics-service agrupa los casos por zona.
CREATE INDEX ix_patients_zona      ON patients (zona) WHERE zona IS NOT NULL;
