-- ============================================================
-- appointment_db V2: franjas concretas de la agenda.
--
-- Es el recurso que se disputa al reservar. El indice unico de abajo
-- es la primera defensa contra la doble reserva; la segunda es el
-- bloqueo pesimista al leer el slot antes de ocuparlo.
-- ============================================================

CREATE TABLE time_slots (
    id          BIGSERIAL PRIMARY KEY,
    id_medico   BIGINT  NOT NULL,
    fecha       DATE    NOT NULL,
    hora_inicio TIME    NOT NULL,
    hora_fin    TIME    NOT NULL,
    ocupado     BOOLEAN NOT NULL DEFAULT FALSE,
    id_cita     BIGINT,
    version     BIGINT  NOT NULL DEFAULT 0,

    CONSTRAINT chk_slot_horas CHECK (hora_fin > hora_inicio),
    -- Un slot ocupado siempre apunta a su cita, y uno libre nunca.
    CONSTRAINT chk_slot_coherencia CHECK (
        (ocupado = TRUE  AND id_cita IS NOT NULL) OR
        (ocupado = FALSE AND id_cita IS NULL)
    )
);

-- Impide que existan dos franjas identicas para el mismo medico. Sin esto,
-- el generador de slots podria duplicarlas al reejecutarse y dos pacientes
-- acabarian con una "franja propia" a la misma hora.
CREATE UNIQUE INDEX ux_slot_medico_fecha_hora
    ON time_slots (id_medico, fecha, hora_inicio);

-- Consulta principal: disponibilidad de un medico en una fecha.
CREATE INDEX ix_slot_disponibilidad
    ON time_slots (id_medico, fecha, ocupado, hora_inicio);

-- Usada por el generador para saber hasta que fecha ya hay slots.
CREATE INDEX ix_slot_fecha ON time_slots (fecha);
