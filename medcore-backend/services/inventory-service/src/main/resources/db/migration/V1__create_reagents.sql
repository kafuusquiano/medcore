-- ============================================================
-- inventory_db V1: catalogo de reactivos e insumos.
--
-- Es el articulo en abstracto, no el material fisico. Las unidades
-- concretas, con su lote y su vencimiento, van en la tabla stock de V2.
--
-- Esa separacion existe porque el mismo reactivo llega en varios lotes con
-- vencimientos distintos, y el laboratorio tiene que gastar primero el que
-- caduca antes. Con una sola tabla no se sabria que parte del total esta por
-- vencer.
-- ============================================================

CREATE TABLE reagents (
    id                     BIGSERIAL    PRIMARY KEY,
    -- Codigo interno del laboratorio, el que figura en la estanteria.
    codigo                 VARCHAR(30)  NOT NULL UNIQUE,
    nombre                 VARCHAR(150) NOT NULL,
    categoria              VARCHAR(60)  NOT NULL,
    -- Caja, frasco, vial, unidad.
    unidad_medida          VARCHAR(30)  NOT NULL,
    proveedor              VARCHAR(120),
    -- Minimo por reactivo, no global: de uno de uso diario hay que tener
    -- mucho mas que de uno que se usa una vez al mes.
    stock_minimo           INTEGER      NOT NULL DEFAULT 0,
    -- Referencia para quien hace el pedido, no un maximo que el sistema
    -- imponga.
    stock_optimo           INTEGER,
    requiere_refrigeracion BOOLEAN      NOT NULL DEFAULT FALSE,
    observaciones          VARCHAR(300),
    -- Un reactivo descatalogado se desactiva, no se borra: sus movimientos
    -- historicos deben seguir siendo legibles.
    activo                 BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Con DEFAULT para que los INSERT de siembra de esta misma migracion no
    -- tengan que repetir la hora en cada fila. La aplicacion las fija en
    -- @PrePersist, asi que el default solo actua en las cargas por SQL.
    fecha_creacion         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    fecha_actualizacion    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version                BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_reagent_minimo CHECK (stock_minimo >= 0),
    CONSTRAINT chk_reagent_optimo CHECK (
        stock_optimo IS NULL OR stock_optimo >= stock_minimo)
);

CREATE INDEX ix_reagent_categoria ON reagents (categoria) WHERE activo = TRUE;
CREATE INDEX ix_reagent_nombre    ON reagents (LOWER(nombre));


-- Reactivos de las pruebas que el laboratorio ya tiene en catalogo, mas los
-- fungibles de uso diario. Las cantidades no se cargan aqui: entran como
-- lotes, con su vencimiento, por la API de stock.
INSERT INTO reagents (codigo, nombre, categoria, unidad_medida, proveedor,
                      stock_minimo, stock_optimo, requiere_refrigeracion, observaciones) VALUES
    ('REA-HEM-01', 'Reactivo de hemoglobina (Drabkin)',  'Hematologia',   'frasco', 'Wiener Lab',  4, 12, TRUE,  'Caja de 250 mL'),
    ('REA-HEM-02', 'Diluyente para contador hematologico', 'Hematologia', 'bidon',  'Sysmex',      2,  6, FALSE, 'Bidon de 20 L'),
    ('REA-HEM-03', 'Lisante para contador hematologico', 'Hematologia',   'frasco', 'Sysmex',      3,  9, FALSE, NULL),
    ('REA-BIO-01', 'Reactivo de glucosa (GOD-PAP)',      'Bioquimica',    'caja',   'Wiener Lab',  5, 15, TRUE,  'Caja de 100 pruebas'),
    ('REA-BIO-02', 'Reactivo de creatinina',             'Bioquimica',    'caja',   'Wiener Lab',  4, 12, TRUE,  NULL),
    ('REA-BIO-03', 'Reactivo de colesterol',             'Bioquimica',    'caja',   'Wiener Lab',  3, 10, TRUE,  NULL),
    ('REA-BIO-04', 'Electrodo selectivo de potasio',     'Bioquimica',    'unidad', 'Roche',       1,  3, TRUE,  'Vida util limitada por numero de lecturas'),
    ('REA-INM-01', 'Reactivo de proteina C reactiva',    'Inmunologia',   'caja',   'Roche',       3,  8, TRUE,  'Para PCR-001 del catalogo de pruebas'),
    ('REA-MIC-01', 'Agar sangre (placas preparadas)',    'Microbiologia', 'caja',   'Britania',    4, 10, TRUE,  'Caja de 20 placas'),
    ('REA-MIC-02', 'Agar MacConkey (placas preparadas)', 'Microbiologia', 'caja',   'Britania',    4, 10, TRUE,  'Caja de 20 placas'),
    ('REA-MIC-03', 'Tiras de identificacion bioquimica', 'Microbiologia', 'caja',   'bioMerieux',  2,  6, TRUE,  NULL),
    ('FUN-TUB-01', 'Tubo con EDTA 4 mL',                 'Fungible',      'caja',   'BD',         10, 30, FALSE, 'Caja de 100 tubos'),
    ('FUN-TUB-02', 'Tubo sin anticoagulante 5 mL',       'Fungible',      'caja',   'BD',          8, 25, FALSE, 'Caja de 100 tubos'),
    ('FUN-TUB-03', 'Frasco esteril para orina',          'Fungible',      'caja',   'Generico',    6, 20, FALSE, 'Caja de 50 frascos'),
    ('FUN-PUN-01', 'Puntas de pipeta 1000 uL',           'Fungible',      'caja',   'Generico',    8, 24, FALSE, 'Caja de 1000 puntas'),
    ('FUN-PUN-02', 'Puntas de pipeta 200 uL',            'Fungible',      'caja',   'Generico',    8, 24, FALSE, 'Caja de 1000 puntas'),
    ('FUN-GUA-01', 'Guantes de nitrilo talla M',         'Fungible',      'caja',   'Generico',   12, 40, FALSE, 'Caja de 100 unidades'),
    ('FUN-AGU-01', 'Aguja de extraccion 21G',            'Fungible',      'caja',   'BD',          6, 20, FALSE, 'Caja de 100 unidades');
