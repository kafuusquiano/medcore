package com.medcore.appointment.client.fallback;

import com.medcore.appointment.client.PatientClient;
import com.medcore.appointment.client.PatientDto;
import com.medcore.appointment.exception.AppointmentException;
import com.medcore.commons.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Respuesta cuando patient-service no contesta o el circuito esta abierto.
 *
 * <p>Decision: NO se reserva a ciegas. Sin poder comprobar que el paciente
 * existe y que su ficha admite atencion, crear la cita generaria una reserva
 * contra un paciente que quiza no existe, y habria que deshacerla a mano.
 * Se prefiere rechazar con 503 y que recepcion reintente.
 *
 * <p>La excepcion se lanza aqui, en el fallback, y no se deja propagar la
 * original: asi el usuario recibe un mensaje en espanol en lugar de un error
 * de Feign.
 */
@Component
public class PatientClientFallback implements FallbackFactory<PatientClient> {

    private static final Logger log = LoggerFactory.getLogger(PatientClientFallback.class);

    @Override
    public PatientClient create(Throwable causa) {
        return (id, userId, rol, requestId) -> {
            log.error("patient-service no respondio al consultar el paciente {} [{}]: {}",
                    id, requestId, causa.toString());
            throw AppointmentException.pacienteNoVerificable();
        };
    }

    /** Solo para que el compilador acepte el tipo de retorno del lambda. */
    @SuppressWarnings("unused")
    private ApiResponse<PatientDto> nunca() {
        return null;
    }
}
