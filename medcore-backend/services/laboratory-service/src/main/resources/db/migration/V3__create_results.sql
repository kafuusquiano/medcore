-- ============================================================
-- lab_db V3: resultados.
--
-- Un resultado validado no se modifica: es un registro clinico.
-- Corregirlo consiste en anadir otro que lo rectifique, apuntando al
-- anterior en id_resultado_corregido, de modo que el historial conserve
-- lo que se informo en su momento.
--
-- El documento crudo del equipo y la trama HL7 original viven en MongoDB:
-- tienen forma variable segun el fabricante y no encajan en columnas.
-- ============================================================

CREATE TABLE results (
    id                     BIGSERIAL PRIMARY KEY,
    sample_id              BIGINT       NOT NULL REFERENCES samples (id),
    codigo_prueba          VARCHAR(20)  NOT NULL,
    nombre_prueba          VARCHAR(150) NOT NULL,
    -- Nulo en las pruebas cualitativas, como un cultivo.
    valor_numerico         DOUBLE PRECISION,
    valor_texto            VARCHAR(500),
    unidad                 VARCHAR(20),
    -- Copia del rango aplicado: los del catalogo pueden cambiar y el
    -- resultado debe conservar contra que se interpreto.
    rango_referencia       VARCHAR(60),
    interpretacion         VARCHAR(20)  NOT NULL DEFAULT 'NO_APLICA',
    critico                BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Identificador del documento de MongoDB con la trama original.
    id_documento_crudo     VARCHAR(40),
    origen                 VARCHAR(20)  NOT NULL,
    registrado_por         VARCHAR(120),
    id_resultado_corregido BIGINT       REFERENCES results (id),
    observaciones          VARCHAR(500),
    fecha_resultado        TIMESTAMPTZ  NOT NULL,

    CONSTRAINT chk_result_interpretacion CHECK (
        interpretacion IN ('NORMAL', 'BAJO', 'ALTO', 'CRITICO_BAJO', 'CRITICO_ALTO', 'NO_APLICA')),
    CONSTRAINT chk_result_origen CHECK (origen IN ('EQUIPO', 'MANUAL')),
    -- Todo resultado tiene un valor, numerico o de texto.
    CONSTRAINT chk_result_valor CHECK (
        valor_numerico IS NOT NULL OR valor_texto IS NOT NULL),
    -- La marca de critico debe corresponder con la interpretacion.
    CONSTRAINT chk_result_critico CHECK (
        critico = (interpretacion IN ('CRITICO_BAJO', 'CRITICO_ALTO')))
);

-- Una prueba tiene un solo resultado vigente por muestra. Indice PARCIAL
-- sobre los no corregidos, para que una correccion pueda convivir con el
-- resultado que rectifica.
CREATE UNIQUE INDEX ux_result_muestra_prueba_vigente
    ON results (sample_id, codigo_prueba)
    WHERE id_resultado_corregido IS NULL;

CREATE INDEX ix_result_muestra ON results (sample_id);
CREATE INDEX ix_result_prueba  ON results (codigo_prueba, fecha_resultado DESC);
-- Consulta de los valores criticos del dia, que el laboratorio revisa.
CREATE INDEX ix_result_critico ON results (fecha_resultado DESC) WHERE critico = TRUE;
