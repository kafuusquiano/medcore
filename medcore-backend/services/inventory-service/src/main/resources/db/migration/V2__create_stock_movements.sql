-- ============================================================
-- inventory_db V2: lotes en almacen y auditoria de movimientos.
-- ============================================================

CREATE TABLE stock (
    id                  BIGSERIAL   PRIMARY KEY,
    reagent_id          BIGINT      NOT NULL REFERENCES reagents (id),
    -- Numero de lote impreso por el fabricante. Es como el proveedor
    -- identifica el material cuando hay que reclamarlo.
    lote                VARCHAR(60) NOT NULL,
    cantidad_actual     INTEGER     NOT NULL DEFAULT 0,
    -- Con que cantidad entro el lote. Permite saber cuanto se consumio sin
    -- sumar todos sus movimientos.
    cantidad_inicial    INTEGER     NOT NULL DEFAULT 0,
    -- Nulo en los insumos que no caducan, como gradillas o puntas.
    fecha_vencimiento   DATE,
    fecha_ingreso       DATE        NOT NULL DEFAULT CURRENT_DATE,
    ubicacion           VARCHAR(80),
    observaciones       VARCHAR(300),
    -- El lote retirado no se borra: su historial debe seguir legible.
    retirado            BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Mismo motivo que en reagents: la aplicacion la fija en @PrePersist y
    -- el default cubre las cargas por SQL.
    fecha_actualizacion TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT      NOT NULL DEFAULT 0,

    -- El inventario no puede quedar en negativo: seria material que no
    -- existe. La entidad lo comprueba antes, y esta restriccion es la
    -- garantia de ultimo recurso.
    CONSTRAINT chk_stock_cantidad CHECK (cantidad_actual >= 0),
    CONSTRAINT chk_stock_inicial  CHECK (cantidad_inicial >= 0),
    -- Un lote no puede vencer antes de haber ingresado.
    CONSTRAINT chk_stock_fechas CHECK (
        fecha_vencimiento IS NULL OR fecha_vencimiento >= fecha_ingreso)
);

-- Un lote de un reactivo es unico mientras no se retire. Indice PARCIAL: si
-- se retira y el proveedor vuelve a enviar material con el mismo numero de
-- lote, se puede registrar de nuevo.
CREATE UNIQUE INDEX ux_stock_reactivo_lote
    ON stock (reagent_id, lote) WHERE retirado = FALSE;

-- Consumo por orden de vencimiento: el lote que caduca antes se gasta
-- primero. Es la consulta que hace el laboratorio al tomar material.
CREATE INDEX ix_stock_consumo
    ON stock (reagent_id, fecha_vencimiento NULLS LAST)
    WHERE retirado = FALSE AND cantidad_actual > 0;

-- Revision diaria de vencimientos. Los lotes que no caducan quedan fuera del
-- indice: no tiene sentido recorrerlos cada madrugada.
CREATE INDEX ix_stock_vencimiento
    ON stock (fecha_vencimiento)
    WHERE retirado = FALSE AND fecha_vencimiento IS NOT NULL;

CREATE INDEX ix_stock_reactivo ON stock (reagent_id);


-- ============================================================
-- Auditoria de movimientos.
--
-- Las filas no se modifican ni se borran. Un movimiento mal registrado se
-- corrige con un AJUSTE que deja constancia de la diferencia, nunca
-- editando el original: es un inventario de material sanitario y tiene que
-- poder auditarse.
--
-- Se guardan la cantidad antes y despues, no solo el delta. Con eso, un
-- recuento que no cuadra se rastrea hasta el movimiento exacto en que la
-- cuenta se desvio.
-- ============================================================

CREATE TABLE movement_logs (
    id                  BIGSERIAL    PRIMARY KEY,
    stock_id            BIGINT       NOT NULL REFERENCES stock (id),
    -- Copia del codigo, el nombre y el lote: el historial debe poder leerse
    -- sin cargar el lote ni el reactivo, y seguir legible si el reactivo se
    -- da de baja del catalogo.
    codigo_reactivo     VARCHAR(30)  NOT NULL,
    nombre_reactivo     VARCHAR(150) NOT NULL,
    lote                VARCHAR(60)  NOT NULL,
    tipo                VARCHAR(20)  NOT NULL,
    -- Delta con signo: negativo en salidas, mermas y devoluciones.
    cantidad            INTEGER      NOT NULL,
    cantidad_anterior   INTEGER      NOT NULL,
    cantidad_resultante INTEGER      NOT NULL,
    motivo              VARCHAR(300),
    registrado_por      VARCHAR(120) NOT NULL,
    -- Muestra que consumio el material. Identificador suelto, sin clave
    -- ajena: la muestra vive en lab_db, que es de otro servicio (MC1).
    codigo_muestra      VARCHAR(25),
    fecha_movimiento    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_movement_tipo CHECK (
        tipo IN ('ENTRADA', 'SALIDA', 'MERMA', 'AJUSTE', 'DEVOLUCION')),
    -- Un movimiento de cero unidades no mueve nada y solo ensucia la
    -- auditoria.
    CONSTRAINT chk_movement_cantidad CHECK (cantidad <> 0),
    -- La cuenta tiene que cuadrar: si no, la fila no sirve para auditar.
    CONSTRAINT chk_movement_coherencia CHECK (
        cantidad_resultante = cantidad_anterior + cantidad),
    CONSTRAINT chk_movement_no_negativo CHECK (cantidad_resultante >= 0),
    -- Solo el AJUSTE corrige en cualquier direccion; en el resto el signo lo
    -- determina el tipo.
    CONSTRAINT chk_movement_signo CHECK (
        (tipo = 'ENTRADA'    AND cantidad > 0) OR
        (tipo IN ('SALIDA', 'MERMA', 'DEVOLUCION') AND cantidad < 0) OR
        (tipo = 'AJUSTE')),
    -- Una merma, un ajuste o una devolucion sin motivo no sirven para
    -- auditar nada. La salida por consumo no lo exige: su motivo es el
    -- trabajo diario, y se identifica con el codigo de la muestra.
    CONSTRAINT chk_movement_motivo CHECK (
        tipo IN ('ENTRADA', 'SALIDA') OR motivo IS NOT NULL)
);

CREATE INDEX ix_movement_stock    ON movement_logs (stock_id, fecha_movimiento DESC);
CREATE INDEX ix_movement_fecha    ON movement_logs (fecha_movimiento DESC);
CREATE INDEX ix_movement_reactivo ON movement_logs (codigo_reactivo, fecha_movimiento DESC);
-- Trazabilidad inversa: que material consumio una muestra concreta.
CREATE INDEX ix_movement_muestra
    ON movement_logs (codigo_muestra) WHERE codigo_muestra IS NOT NULL;
