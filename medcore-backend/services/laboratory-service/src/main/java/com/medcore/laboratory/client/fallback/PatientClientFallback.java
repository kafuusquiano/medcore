package com.medcore.laboratory.client.fallback;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.laboratory.client.PatientClient;
import com.medcore.laboratory.client.PatientDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Respuesta de reserva cuando patient-service no contesta.
 *
 * <p>Devuelve datos nulos en lugar de propagar el error: que el padron de
 * pacientes este caido no debe impedir recibir un tubo de sangre. La muestra
 * se registra con lo que trajo el evento y sin sexo ni edad se aplica el
 * rango de referencia generico, que es menos preciso pero utilizable.
 *
 * <p>Queda en el log con nivel WARN para que la degradacion sea visible y no
 * se confunda con un paciente sin esos datos registrados.
 */
@Component
public class PatientClientFallback implements FallbackFactory<PatientClient> {

    private static final Logger log = LoggerFactory.getLogger(PatientClientFallback.class);

    @Override
    public PatientClient create(Throwable causa) {
        return (id, userId, rol, requestId) -> {
            log.warn("patient-service no responde al pedir el paciente {}: {}. "
                            + "La muestra se registrara sin sexo ni edad y se usara "
                            + "el rango generico.",
                    id, causa.getMessage());
            return ApiResponse.ok("patient-service no disponible", null);
        };
    }
}
