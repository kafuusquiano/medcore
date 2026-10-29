-- ============================================================
-- appointment_db V1: horarios de atencion de los medicos.
--
-- id_medico apunta a un usuario de auth_db. No hay clave foranea:
-- es otra base de datos y otro servicio (regla MC1).
-- ============================================================

CREATE TABLE doctor_schedule (
    id               BIGSERIAL PRIMARY KEY,
    id_medico        BIGINT       NOT NULL,
    nombre_medico    VARCHAR(120) NOT NULL,
    especialidad     VARCHAR(80),
    -- Dias en que atiende, como lista separada por comas de nombres de
    -- java.time.DayOfWeek: "MONDAY,TUESDAY,FRIDAY".
    dias_disponibles VARCHAR(120) NOT NULL,
    hora_inicio      TIME         NOT NULL,
    hora_fin         TIME         NOT NULL,
    activo           BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT chk_schedule_horas CHECK (hora_fin > hora_inicio)
);

-- Un medico tiene un solo horario vigente. El indice es parcial para que
-- queden historicos desactivados sin bloquear el alta de uno nuevo.
CREATE UNIQUE INDEX ux_schedule_medico_activo
    ON doctor_schedule (id_medico) WHERE activo = TRUE;

CREATE INDEX ix_schedule_especialidad ON doctor_schedule (especialidad)
    WHERE especialidad IS NOT NULL;
