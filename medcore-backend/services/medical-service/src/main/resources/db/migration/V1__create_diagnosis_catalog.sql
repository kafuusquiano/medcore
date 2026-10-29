-- ============================================================
-- medical_db V1: catalogo CIE-10.
--
-- Es un catalogo de solo lectura. La clave primaria es el propio
-- codigo y no un id autogenerado: el codigo ya es unico y estable
-- a nivel internacional, asi que los diagnosticos lo referencian
-- directamente sin una union adicional.
--
-- Las filas completas las carga infra/postgres/seeds/cie10_seed.sql.
-- Aqui se insertan solo unos pocos codigos frecuentes para que el
-- servicio sea usable nada mas arrancar.
-- ============================================================

CREATE TABLE cie10_codes (
    codigo      VARCHAR(10)  PRIMARY KEY,
    descripcion VARCHAR(300) NOT NULL,
    capitulo    VARCHAR(150),
    -- Enfermedades de notificacion obligatoria a la DIRESA.
    notificable BOOLEAN      NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_cie10_formato CHECK (codigo ~ '^[A-Z][0-9]{2}(\.[0-9X])?$')
);

-- Busqueda por texto de la descripcion, que es como el medico localiza
-- un codigo cuando no lo recuerda.
CREATE INDEX ix_cie10_descripcion ON cie10_codes (LOWER(descripcion));
CREATE INDEX ix_cie10_notificable ON cie10_codes (notificable) WHERE notificable = TRUE;

INSERT INTO cie10_codes (codigo, descripcion, capitulo, notificable) VALUES
    ('A09',   'Diarrea y gastroenteritis de presunto origen infeccioso', 'Enfermedades infecciosas y parasitarias', TRUE),
    ('A90',   'Dengue clasico',                                          'Enfermedades infecciosas y parasitarias', TRUE),
    ('A91',   'Fiebre del dengue hemorragico',                           'Enfermedades infecciosas y parasitarias', TRUE),
    ('B01',   'Varicela',                                                'Enfermedades infecciosas y parasitarias', TRUE),
    ('E11',   'Diabetes mellitus tipo 2',                                'Enfermedades endocrinas y metabolicas',   FALSE),
    ('I10',   'Hipertension esencial primaria',                          'Enfermedades del sistema circulatorio',   FALSE),
    ('J00',   'Rinofaringitis aguda, resfriado comun',                   'Enfermedades del sistema respiratorio',   FALSE),
    ('J02.9', 'Faringitis aguda, no especificada',                       'Enfermedades del sistema respiratorio',   FALSE),
    ('J03.9', 'Amigdalitis aguda, no especificada',                      'Enfermedades del sistema respiratorio',   FALSE),
    ('J18.9', 'Neumonia, no especificada',                               'Enfermedades del sistema respiratorio',   TRUE),
    ('J45',   'Asma',                                                    'Enfermedades del sistema respiratorio',   FALSE),
    ('K29.7', 'Gastritis, no especificada',                              'Enfermedades del sistema digestivo',      FALSE),
    ('M54.5', 'Lumbago no especificado',                                 'Enfermedades osteomusculares',            FALSE),
    ('N39.0', 'Infeccion de vias urinarias, sitio no especificado',      'Enfermedades del sistema genitourinario', FALSE),
    ('R50.9', 'Fiebre, no especificada',                                 'Sintomas y signos',                       FALSE),
    ('R51',   'Cefalea',                                                 'Sintomas y signos',                       FALSE),
    ('Z00.0', 'Examen medico general',                                   'Factores que influyen en la salud',       FALSE);
