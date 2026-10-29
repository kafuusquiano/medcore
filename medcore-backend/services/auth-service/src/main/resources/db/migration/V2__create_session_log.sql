-- ============================================================
-- auth_db V2: bitacora de accesos, para auditoria.
-- Registra tanto los inicios de sesion correctos como los fallidos:
-- una racha de fallos desde una misma IP delata un ataque de fuerza bruta.
-- ============================================================

CREATE TABLE session_log (
    id            BIGSERIAL PRIMARY KEY,
    -- Nulo cuando el intento fallo porque el identificador no existe.
    user_id       BIGINT       REFERENCES users (id) ON DELETE SET NULL,
    identificador VARCHAR(120) NOT NULL,
    exitoso       BOOLEAN      NOT NULL,
    motivo        VARCHAR(60),
    ip_origen     VARCHAR(45),
    dispositivo   VARCHAR(255),
    request_id    VARCHAR(40),
    fecha_acceso  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_session_log_user   ON session_log (user_id, fecha_acceso DESC);
CREATE INDEX ix_session_log_fecha  ON session_log (fecha_acceso DESC);
-- Soporta la consulta de intentos fallidos recientes por IP.
CREATE INDEX ix_session_log_fallos ON session_log (ip_origen, fecha_acceso DESC) WHERE exitoso = FALSE;
