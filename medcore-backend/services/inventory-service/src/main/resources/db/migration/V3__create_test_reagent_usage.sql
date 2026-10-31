-- ============================================================
-- inventory_db V3: que reactivos consume cada prueba de laboratorio.
--
-- Es la pieza que faltaba para que el almacen se descuente solo. Hasta ahora
-- el consumo habia que registrarlo a mano, asi que el stock del sistema se
-- separaba del real en cuanto alguien se olvidaba de apuntarlo, y la alerta de
-- reposicion llegaba tarde o no llegaba.
--
-- El descuento se dispara por EVENTO (result.registered) y no por una llamada
-- del laboratorio: el procesamiento de una muestra no puede depender de que el
-- almacen responda. Si inventory-service esta caido, los mensajes esperan en
-- Kafka y el laboratorio sigue trabajando.
--
-- La tabla vive aqui y no en lab_db porque es informacion de almacen: cuanto
-- reactivo gasta una tecnica. El laboratorio no tiene por que saberlo, y asi
-- cambiar el consumo de una prueba no obliga a migrar su base.
-- ============================================================

CREATE TABLE test_reagent_usage (
    id                  BIGSERIAL    PRIMARY KEY,

    -- Codigo del catalogo de laboratorio (HEM-001, BIO-002...). No hay clave
    -- ajena: el catalogo esta en otra base, y las bases no se cruzan.
    codigo_prueba       VARCHAR(20)  NOT NULL,

    reagent_id          BIGINT       NOT NULL REFERENCES reagents (id),

    -- Unidades que gasta UNA determinacion. Casi siempre 1, pero una tecnica
    -- puede llevar dos reactivos distintos o dos unidades del mismo.
    cantidad_por_prueba INTEGER      NOT NULL CHECK (cantidad_por_prueba > 0),

    activo              BOOLEAN      NOT NULL DEFAULT TRUE,
    observaciones       VARCHAR(300),
    fecha_creacion      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Una prueba no puede declarar dos veces el mismo reactivo: seria
    -- ambiguo cuanto gasta, y el descuento saldria doble.
    CONSTRAINT uq_usage_prueba_reactivo UNIQUE (codigo_prueba, reagent_id)
);

-- La consulta del consumidor: los reactivos activos de una prueba.
CREATE INDEX ix_usage_prueba ON test_reagent_usage (codigo_prueba) WHERE activo;

COMMENT ON TABLE test_reagent_usage IS
    'Reactivos que gasta cada prueba del catalogo de laboratorio. Lo usa el '
    'consumidor de result.registered para descontar el stock solo.';

COMMENT ON COLUMN test_reagent_usage.activo IS
    'Una linea se desactiva en lugar de borrarse: los movimientos ya '
    'registrados la referencian como motivo del descuento.';

-- ------------------------------------------------------------
-- Idempotencia del descuento automatico.
--
-- Kafka entrega al menos una vez. Sin esto, un reenvio de result.registered
-- descontaria el reactivo dos veces, y el stock del sistema quedaria por
-- debajo del real: el almacen pediria reposicion de algo que tiene.
--
-- Se guarda en el propio log de movimientos, que es donde consta el descuento,
-- en lugar de en una tabla aparte de eventos procesados: asi la comprobacion
-- y el hecho no pueden separarse.
-- ------------------------------------------------------------

ALTER TABLE movement_logs ADD COLUMN id_resultado BIGINT;

COMMENT ON COLUMN movement_logs.id_resultado IS
    'Resultado de laboratorio que provoco el descuento automatico. Nulo en '
    'los movimientos registrados a mano.';

-- Indice PARCIAL: solo los movimientos automaticos lo tienen, y son los
-- unicos que hay que proteger del reenvio. Un movimiento a mano repetido es
-- decision de quien lo registra.
CREATE UNIQUE INDEX uq_movement_resultado_reactivo
    ON movement_logs (id_resultado, codigo_reactivo)
    WHERE id_resultado IS NOT NULL;

-- ------------------------------------------------------------
-- Consumos de las pruebas del catalogo actual.
--
-- Las cantidades son de una determinacion. Casi todas gastan un reactivo; las
-- del contador hematologico comparten el diluyente, y los cultivos gastan dos
-- placas y una tira de identificacion.
--
-- ORI-001 (examen completo de orina) NO aparece: se hace con tira reactiva, y
-- la tira no esta en el catalogo de reactivos. Una prueba sin linea aqui no
-- descuenta nada y no es un error: el consumidor lo registra en el log y
-- sigue.
-- ------------------------------------------------------------

INSERT INTO test_reagent_usage (codigo_prueba, reagent_id, cantidad_por_prueba,
                                observaciones)
SELECT v.codigo_prueba, r.id, v.cantidad, v.nota
FROM (VALUES
    ('HEM-001', 'REA-HEM-01', 1, 'Metodo de Drabkin'),
    ('HEM-002', 'REA-HEM-02', 1, 'Diluyente del contador'),
    ('HEM-003', 'REA-HEM-02', 1, 'Diluyente del contador'),
    ('HEM-003', 'REA-HEM-03', 1, 'Lisante para el recuento leucocitario'),
    ('HEM-004', 'REA-HEM-02', 1, 'Diluyente del contador'),
    ('BIO-001', 'REA-BIO-01', 1, 'Metodo GOD-PAP'),
    ('BIO-002', 'REA-BIO-02', 1, NULL),
    ('BIO-003', 'REA-BIO-03', 1, NULL),
    ('BIO-004', 'REA-BIO-04', 1, 'Electrodo selectivo'),
    ('PCR-001', 'REA-INM-01', 1, 'Proteina C reactiva'),
    ('MIC-001', 'REA-MIC-01', 1, 'Agar sangre'),
    ('MIC-001', 'REA-MIC-02', 1, 'Agar MacConkey'),
    ('MIC-001', 'REA-MIC-03', 1, 'Identificacion bioquimica'),
    ('MIC-002', 'REA-MIC-02', 1, 'Agar MacConkey'),
    ('MIC-002', 'REA-MIC-03', 1, 'Identificacion bioquimica')
) AS v (codigo_prueba, codigo_reactivo, cantidad, nota)
JOIN reagents r ON r.codigo = v.codigo_reactivo;
