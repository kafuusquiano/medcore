-- ============================================================
-- lab_db V5: alinea el codigo de la proteina C reactiva.
--
-- El catalogo de V1 la registro como INM-001, por area, pero el resto del
-- sistema la pide como PCR-001: asi la emiten las ordenes de
-- medical-service, y asi se detecto al recibir la primera orden real, que
-- llegaba con una prueba marcada como no reconocida por el laboratorio.
--
-- El dueno del catalogo es laboratory-service (MC1), de modo que lo que
-- corresponde es que el catalogo tenga el codigo con el que el sistema la
-- pide, no que cada servicio traduzca por su cuenta.
--
-- No se hace con UPDATE del codigo porque es clave primaria y las tablas de
-- rangos y de limites criticos la referencian sin ON UPDATE CASCADE: el
-- UPDATE fallaria. Se inserta la nueva, se mueven las filas que apuntaban a
-- la anterior y se borra la anterior.
-- ============================================================

INSERT INTO test_catalog (codigo, nombre, area, unidad, tipo_muestra, cuantitativa,
                          indicaciones, tiempo_entrega_horas, activa)
SELECT 'PCR-001', nombre, area, unidad, tipo_muestra, cuantitativa,
       indicaciones, tiempo_entrega_horas, activa
FROM test_catalog
WHERE codigo = 'INM-001'
  AND NOT EXISTS (SELECT 1 FROM test_catalog WHERE codigo = 'PCR-001');

UPDATE reference_ranges SET codigo_prueba = 'PCR-001' WHERE codigo_prueba = 'INM-001';
UPDATE critical_values  SET codigo_prueba = 'PCR-001' WHERE codigo_prueba = 'INM-001';

-- Si ya se hubiera emitido algun resultado con el codigo antiguo, se
-- reapunta: results guarda el codigo como texto, sin clave ajena, para que
-- un resultado antiguo siga siendo legible aunque la prueba cambie.
UPDATE results SET codigo_prueba = 'PCR-001' WHERE codigo_prueba = 'INM-001';

-- Las ordenes ya encoladas que pidieron INM-001 tambien se reapuntan.
UPDATE pending_lab_order_tests SET codigo_prueba = 'PCR-001' WHERE codigo_prueba = 'INM-001';

DELETE FROM test_catalog WHERE codigo = 'INM-001';
