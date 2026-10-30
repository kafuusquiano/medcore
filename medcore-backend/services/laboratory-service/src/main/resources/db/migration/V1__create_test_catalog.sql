-- ============================================================
-- lab_db V1: catalogo de pruebas, rangos de referencia y limites criticos.
--
-- laboratory-service es el dueno de este catalogo (MC1). medical-service
-- solo guarda el codigo y el nombre con el que se solicito la prueba.
--
-- Las filas completas las carga infra/postgres/seeds/lab_catalog_seed.sql.
-- Aqui se insertan las pruebas mas frecuentes para que el servicio sea
-- usable nada mas arrancar.
-- ============================================================

CREATE TABLE test_catalog (
    codigo               VARCHAR(20)  PRIMARY KEY,
    nombre               VARCHAR(150) NOT NULL,
    area                 VARCHAR(60)  NOT NULL,
    unidad               VARCHAR(20),
    tipo_muestra         VARCHAR(30)  NOT NULL,
    -- False en las cualitativas, como un cultivo: su resultado es un texto
    -- y no admite comparacion con rangos numericos.
    cuantitativa         BOOLEAN      NOT NULL DEFAULT TRUE,
    indicaciones         VARCHAR(300),
    tiempo_entrega_horas INTEGER,
    activa               BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE INDEX ix_catalog_area   ON test_catalog (area) WHERE activa = TRUE;
CREATE INDEX ix_catalog_nombre ON test_catalog (LOWER(nombre));


-- ============================================================
-- Rangos de normalidad.
--
-- Una misma prueba tiene varias filas porque el rango depende del sexo y la
-- edad: la hemoglobina normal de un varon adulto no es la de una mujer ni la
-- de un lactante. sexo NULL significa "cualquiera"; edad_min o edad_max NULL
-- significan sin tope por ese lado.
-- ============================================================

CREATE TABLE reference_ranges (
    id             BIGSERIAL PRIMARY KEY,
    codigo_prueba  VARCHAR(20)      NOT NULL REFERENCES test_catalog (codigo),
    sexo           VARCHAR(20),
    edad_min_anios INTEGER,
    edad_max_anios INTEGER,
    valor_min      DOUBLE PRECISION NOT NULL,
    valor_max      DOUBLE PRECISION NOT NULL,

    CONSTRAINT chk_range_valores CHECK (valor_max > valor_min),
    CONSTRAINT chk_range_sexo CHECK (sexo IS NULL OR sexo IN ('MASCULINO', 'FEMENINO')),
    CONSTRAINT chk_range_edades CHECK (
        edad_min_anios IS NULL OR edad_max_anios IS NULL
        OR edad_max_anios >= edad_min_anios)
);

CREATE INDEX ix_range_prueba ON reference_ranges (codigo_prueba);


-- ============================================================
-- Limites criticos: los valores que obligan a avisar al medico de inmediato.
--
-- Son distintos del rango de referencia. Fuera del rango el valor es
-- anormal; fuera del limite critico es una urgencia. Entre ambos hay una
-- franja de valores anormales que no requieren aviso inmediato.
--
-- Ambos limites son opcionales: hay pruebas con un solo extremo critico,
-- como las plaquetas, peligrosas solo cuando estan muy bajas.
-- ============================================================

CREATE TABLE critical_values (
    id              BIGSERIAL PRIMARY KEY,
    codigo_prueba   VARCHAR(20)      NOT NULL UNIQUE REFERENCES test_catalog (codigo),
    limite_inferior DOUBLE PRECISION,
    limite_superior DOUBLE PRECISION,
    indicacion      VARCHAR(300),

    -- Una fila sin ningun limite no sirve para nada.
    CONSTRAINT chk_critical_algun_limite CHECK (
        limite_inferior IS NOT NULL OR limite_superior IS NOT NULL)
);


INSERT INTO test_catalog (codigo, nombre, area, unidad, tipo_muestra, cuantitativa, indicaciones, tiempo_entrega_horas) VALUES
    ('HEM-001', 'Hemoglobina',                 'Hematologia',   'g/dL',     'SANGRE', TRUE,  NULL,                      4),
    ('HEM-002', 'Hematocrito',                 'Hematologia',   '%',        'SANGRE', TRUE,  NULL,                      4),
    ('HEM-003', 'Leucocitos',                  'Hematologia',   'x10^3/uL', 'SANGRE', TRUE,  NULL,                      4),
    ('HEM-004', 'Plaquetas',                   'Hematologia',   'x10^3/uL', 'SANGRE', TRUE,  NULL,                      4),
    ('BIO-001', 'Glucosa',                     'Bioquimica',    'mg/dL',    'SANGRE', TRUE,  'Ayuno de 8 horas',        6),
    ('BIO-002', 'Creatinina',                  'Bioquimica',    'mg/dL',    'SANGRE', TRUE,  NULL,                      6),
    ('BIO-003', 'Colesterol total',            'Bioquimica',    'mg/dL',    'SANGRE', TRUE,  'Ayuno de 12 horas',       6),
    ('BIO-004', 'Potasio',                     'Bioquimica',    'mEq/L',    'SANGRE', TRUE,  NULL,                      6),
    ('INM-001', 'Proteina C reactiva',         'Inmunologia',   'mg/L',     'SANGRE', TRUE,  NULL,                     12),
    ('MIC-001', 'Urocultivo',                  'Microbiologia', NULL,       'ORINA',  FALSE, 'Primera orina de la manana', 72),
    ('MIC-002', 'Coprocultivo',                'Microbiologia', NULL,       'HECES',  FALSE, NULL,                     72),
    ('ORI-001', 'Examen completo de orina',    'Bioquimica',    NULL,       'ORINA',  FALSE, 'Primera orina de la manana', 6);

-- Rangos: la hemoglobina y el hematocrito cambian con el sexo; la glucosa no.
INSERT INTO reference_ranges (codigo_prueba, sexo, edad_min_anios, edad_max_anios, valor_min, valor_max) VALUES
    ('HEM-001', 'MASCULINO', 15, NULL, 13.5, 17.5),
    ('HEM-001', 'FEMENINO',  15, NULL, 12.0, 15.5),
    ('HEM-001', NULL,         0,   14, 11.0, 14.0),
    ('HEM-002', 'MASCULINO', 15, NULL, 41.0, 53.0),
    ('HEM-002', 'FEMENINO',  15, NULL, 36.0, 46.0),
    ('HEM-003', NULL,      NULL, NULL,  4.5, 11.0),
    ('HEM-004', NULL,      NULL, NULL, 150.0, 450.0),
    ('BIO-001', NULL,      NULL, NULL, 70.0, 100.0),
    ('BIO-002', 'MASCULINO', 15, NULL, 0.7, 1.3),
    ('BIO-002', 'FEMENINO',  15, NULL, 0.6, 1.1),
    ('BIO-003', NULL,      NULL, NULL,  0.0, 200.0),
    ('BIO-004', NULL,      NULL, NULL,  3.5, 5.1),
    ('INM-001', NULL,      NULL, NULL,  0.0, 5.0);

INSERT INTO critical_values (codigo_prueba, limite_inferior, limite_superior, indicacion) VALUES
    ('HEM-001',   7.0,  20.0, 'Anemia severa o policitemia: avisar al medico tratante de inmediato'),
    -- Las plaquetas solo son criticas cuando estan muy bajas.
    ('HEM-004',  50.0, NULL,  'Riesgo de sangrado espontaneo'),
    ('HEM-003',   2.0,  30.0, 'Neutropenia severa o posible leucemia'),
    ('BIO-001',  50.0, 400.0, 'Hipoglucemia o hiperglucemia severa'),
    ('BIO-004',   2.5,   6.5, 'Riesgo de arritmia cardiaca'),
    ('BIO-002', NULL,    5.0, 'Posible falla renal aguda');
