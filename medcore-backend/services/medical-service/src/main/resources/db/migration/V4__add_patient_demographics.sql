-- ============================================================
-- medical_db V4: zona, edad y sexo del paciente en la atencion.
--
-- Hacen falta para la vigilancia epidemiologica por codigo CIE-10. El evento
-- diagnosis.registered los lleva dentro, porque analytics-service agrupa por
-- zona y por grupo de edad: pedirlos despues a patient-service acoplaria los
-- dos servicios y haria que la vigilancia dejara de funcionar cuando
-- patient-service esta caido.
--
-- Son una FOTO del momento de la atencion, igual que el dni, el nombre y el
-- correo que ya se guardaban. Esa es la razon de guardarlos y no consultarlos:
--
--   - La edad cambia. Un caso de 2026 en un paciente que hoy tiene 40 anios
--     se conto cuando tenia 38, y el grupo de edad del informe de 2026 no
--     debe moverse porque el paciente cumpla anios.
--   - La zona cambia. Un paciente que se muda no traslada su caso al nuevo
--     distrito: el brote ocurrio donde vivia entonces.
--
-- Son nulos porque las atenciones ya registradas no los tienen y no se pueden
-- inventar. Un informe sobre datos antiguos contara esos casos en la zona
-- "sin registrar", que es la verdad, en lugar de repartirlos a ciegas.
-- ============================================================

ALTER TABLE medical_attentions ADD COLUMN zona_paciente  VARCHAR(80);
ALTER TABLE medical_attentions ADD COLUMN edad_paciente  INTEGER;
ALTER TABLE medical_attentions ADD COLUMN sexo_paciente  VARCHAR(10);

COMMENT ON COLUMN medical_attentions.zona_paciente IS
    'Distrito del paciente el dia de la atencion. Foto, no referencia: si el '
    'paciente se muda, el caso sigue contando en la zona donde ocurrio.';

COMMENT ON COLUMN medical_attentions.edad_paciente IS
    'Edad en anios cumplidos el dia de la atencion. Nulo si no se conocia la '
    'fecha de nacimiento; un cero diria "recien nacido".';

COMMENT ON COLUMN medical_attentions.sexo_paciente IS
    'MASCULINO o FEMENINO. Nulo en las atenciones registradas antes de V4.';

-- La vigilancia consulta los diagnosticos de un periodo agrupando por zona.
-- Sin este indice, cada informe recorre la tabla entera de atenciones.
CREATE INDEX ix_attention_zona_fecha
    ON medical_attentions (zona_paciente, fecha_atencion)
    WHERE zona_paciente IS NOT NULL;
