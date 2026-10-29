-- ============================================================
-- patient_db V2: linea de tiempo clinica del paciente.
--
-- Guarda el resumen de cada episodio, no el detalle clinico: ese
-- vive en medical_db y lab_db (regla MC1). referencia_externa
-- apunta al registro original en el servicio de origen.
-- ============================================================

CREATE TABLE medical_history (
    id                 BIGSERIAL PRIMARY KEY,
    patient_id         BIGINT       NOT NULL REFERENCES patients (id),
    origen             VARCHAR(40)  NOT NULL,
    referencia_externa VARCHAR(60),
    titulo             VARCHAR(150) NOT NULL,
    descripcion        VARCHAR(1000),
    codigo_cie10       VARCHAR(10),
    fecha_episodio     TIMESTAMPTZ  NOT NULL,
    fecha_registro     TIMESTAMPTZ  NOT NULL
);

-- La consulta habitual es el historial de un paciente, del mas reciente
-- al mas antiguo.
CREATE INDEX ix_history_paciente ON medical_history (patient_id, fecha_episodio DESC);
CREATE INDEX ix_history_cie10    ON medical_history (codigo_cie10) WHERE codigo_cie10 IS NOT NULL;

-- Evita duplicar el mismo episodio si el servicio de origen reintenta.
CREATE UNIQUE INDEX ux_history_origen_referencia
    ON medical_history (origen, referencia_externa)
    WHERE referencia_externa IS NOT NULL;
