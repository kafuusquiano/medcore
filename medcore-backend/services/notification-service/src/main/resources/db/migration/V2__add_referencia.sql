-- ============================================================
-- notification_db V2: referencia al hecho al que se refiere el aviso.
--
-- Hace falta para poder cancelar un aviso programado cuando el hecho que lo
-- motivo desaparece. El caso concreto: el recordatorio de cita se crea al
-- reservar y sale el dia antes; si la cita se anula entretanto, el paciente
-- recibia un recordatorio de una cita que ya no existe.
--
-- No se puede localizar por event_id: la confirmacion y la cancelacion son
-- eventos DISTINTOS, con identificadores distintos, asi que desde la
-- cancelacion no hay forma de derivar el identificador del recordatorio.
--
-- Tampoco se busca dentro de la columna datos: su contenido es JSON libre, y
-- una consulta con LIKE sobre el texto se rompe en cuanto cambia el formato.
-- Una columna propia con indice dice explicitamente a que se refiere el
-- aviso.
--
-- Formato: "tipo:identificador", por ejemplo "cita:42" o "muestra:701".
-- ============================================================

ALTER TABLE notifications ADD COLUMN referencia VARCHAR(60);

-- Indice PARCIAL sobre lo que todavia puede cancelarse: un aviso ya enviado
-- no se puede retirar, asi que buscarlo no sirve de nada. Los pendientes son
-- una fraccion minima de la tabla.
CREATE INDEX ix_notif_referencia
    ON notifications (referencia)
    WHERE referencia IS NOT NULL AND estado IN ('PENDIENTE', 'FALLIDA');

COMMENT ON COLUMN notifications.referencia IS
    'Hecho al que se refiere el aviso, como "cita:42". Permite cancelar un '
    'aviso programado cuando ese hecho desaparece.';
