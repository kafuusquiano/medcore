-- ============================================================
-- medical_db V3: ordenes de laboratorio.
--
-- Es el punto de union con laboratory-service: al crearse una orden
-- se publica lab-order.created y aquel la abre y espera las muestras.
-- El estado que se guarda aqui refleja lo que ese servicio comunica.
-- ============================================================

CREATE TABLE lab_orders (
    id                  BIGSERIAL PRIMARY KEY,
    -- Codigo legible que se imprime y lleva el paciente al laboratorio,
    -- por ejemplo ORD-20261029-0007. El id numerico es interno.
    codigo              VARCHAR(25)  NOT NULL UNIQUE,
    attention_id        BIGINT       NOT NULL REFERENCES medical_attentions (id),
    id_paciente         BIGINT       NOT NULL,
    id_medico           BIGINT       NOT NULL,
    prioridad           VARCHAR(15)  NOT NULL DEFAULT 'RUTINA',
    estado              VARCHAR(20)  NOT NULL DEFAULT 'EMITIDA',
    observaciones       VARCHAR(500),
    motivo_anulacion    VARCHAR(300),
    fecha_emision       TIMESTAMPTZ  NOT NULL,
    fecha_actualizacion TIMESTAMPTZ  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_order_prioridad CHECK (prioridad IN ('RUTINA', 'URGENTE')),
    CONSTRAINT chk_order_estado CHECK (
        estado IN ('EMITIDA', 'EN_PROCESO', 'COMPLETADA', 'ANULADA')),
    CONSTRAINT chk_order_anulacion CHECK (
        estado <> 'ANULADA' OR motivo_anulacion IS NOT NULL)
);

CREATE INDEX ix_order_paciente  ON lab_orders (id_paciente, fecha_emision DESC);
CREATE INDEX ix_order_atencion  ON lab_orders (attention_id);
-- Cola del laboratorio: las urgentes primero, y dentro de cada grupo
-- por orden de emision.
CREATE INDEX ix_order_cola      ON lab_orders (estado, prioridad, fecha_emision);


CREATE TABLE lab_order_tests (
    id            BIGSERIAL PRIMARY KEY,
    lab_order_id  BIGINT       NOT NULL REFERENCES lab_orders (id) ON DELETE CASCADE,
    -- Codigo del catalogo de laboratory-service, que es su dueno (MC1).
    codigo_prueba VARCHAR(20)  NOT NULL,
    -- Copia del nombre con el que se solicito, para que la orden impresa
    -- siga siendo legible aunque el catalogo cambie su redaccion.
    nombre_prueba VARCHAR(150) NOT NULL,
    indicaciones  VARCHAR(300)
);

-- Una prueba no se pide dos veces en la misma orden.
CREATE UNIQUE INDEX ux_order_test ON lab_order_tests (lab_order_id, codigo_prueba);
