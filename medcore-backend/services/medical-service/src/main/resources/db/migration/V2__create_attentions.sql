-- ============================================================
-- medical_db V2: atenciones medicas y sus diagnosticos.
--
-- id_paciente, id_cita e id_medico apuntan a otras bases. No hay
-- claves foraneas: son otros servicios (regla MC1).
-- ============================================================

CREATE TABLE medical_attentions (
    id                  BIGSERIAL PRIMARY KEY,
    -- Nulo cuando el paciente llega por urgencia, sin cita previa.
    id_cita             BIGINT,
    id_paciente         BIGINT       NOT NULL,
    dni_paciente        VARCHAR(8)   NOT NULL,
    nombre_paciente     VARCHAR(170) NOT NULL,
    correo_paciente     VARCHAR(120),
    id_medico           BIGINT       NOT NULL,
    nombre_medico       VARCHAR(120) NOT NULL,
    motivo_consulta     VARCHAR(500) NOT NULL,
    anamnesis           VARCHAR(2000),
    examen_fisico       VARCHAR(2000),
    tratamiento         VARCHAR(2000),
    observaciones       VARCHAR(1000),

    -- Signos vitales
    temperatura         DOUBLE PRECISION,
    presion_sistolica   INTEGER,
    presion_diastolica  INTEGER,
    frecuencia_cardiaca INTEGER,
    peso_kg             DOUBLE PRECISION,
    talla_cm            DOUBLE PRECISION,

    estado              VARCHAR(20)  NOT NULL DEFAULT 'EN_CURSO',
    motivo_anulacion    VARCHAR(300),
    fecha_atencion      TIMESTAMPTZ  NOT NULL,
    fecha_cierre        TIMESTAMPTZ,
    fecha_actualizacion TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_attention_estado CHECK (estado IN ('EN_CURSO', 'FINALIZADA', 'ANULADA')),
    -- Una atencion anulada debe decir por que.
    CONSTRAINT chk_attention_anulacion CHECK (
        estado <> 'ANULADA' OR motivo_anulacion IS NOT NULL),
    -- Rangos fisiologicos: fuera de ellos solo cabe un error de captura.
    CONSTRAINT chk_attention_temperatura CHECK (
        temperatura IS NULL OR temperatura BETWEEN 30 AND 45),
    CONSTRAINT chk_attention_presion CHECK (
        presion_sistolica IS NULL OR presion_diastolica IS NULL
        OR presion_sistolica > presion_diastolica)
);

-- Una cita produce como mucho una atencion viva. Indice PARCIAL para que,
-- si la atencion se anula, pueda registrarse otra sobre la misma cita.
CREATE UNIQUE INDEX ux_attention_cita_viva
    ON medical_attentions (id_cita)
    WHERE id_cita IS NOT NULL AND estado <> 'ANULADA';

CREATE INDEX ix_attention_paciente ON medical_attentions (id_paciente, fecha_atencion DESC);
CREATE INDEX ix_attention_medico   ON medical_attentions (id_medico, fecha_atencion DESC);
CREATE INDEX ix_attention_estado   ON medical_attentions (estado, fecha_atencion DESC);


CREATE TABLE diagnoses (
    id                BIGSERIAL PRIMARY KEY,
    attention_id      BIGINT       NOT NULL REFERENCES medical_attentions (id) ON DELETE CASCADE,
    codigo_cie10      VARCHAR(10)  NOT NULL REFERENCES cie10_codes (codigo),
    -- Copia de la descripcion con la que se emitio: el catalogo puede
    -- corregir su redaccion con el tiempo y el diagnostico no debe cambiar.
    descripcion_cie10 VARCHAR(300) NOT NULL,
    principal         BOOLEAN      NOT NULL DEFAULT FALSE,
    caso_nuevo        BOOLEAN      NOT NULL DEFAULT TRUE,
    notificable       BOOLEAN      NOT NULL DEFAULT FALSE,
    observaciones     VARCHAR(500),
    fecha_registro    TIMESTAMPTZ  NOT NULL
);

-- Un mismo codigo no se repite dentro de la misma atencion.
CREATE UNIQUE INDEX ux_diagnosis_atencion_codigo
    ON diagnoses (attention_id, codigo_cie10);

-- Como mucho un diagnostico principal por atencion.
CREATE UNIQUE INDEX ux_diagnosis_principal
    ON diagnoses (attention_id) WHERE principal = TRUE;

CREATE INDEX ix_diagnosis_codigo      ON diagnoses (codigo_cie10, fecha_registro DESC);
-- Consulta de los casos notificables para el reporte a la DIRESA.
CREATE INDEX ix_diagnosis_notificable ON diagnoses (fecha_registro DESC) WHERE notificable = TRUE;
