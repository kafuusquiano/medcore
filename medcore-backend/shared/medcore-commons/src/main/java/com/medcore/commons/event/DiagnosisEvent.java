package com.medcore.commons.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento publicado por medical-service en {@code diagnosis.registered}.
 *
 * <p>Lo consume analytics-service, para vigilar por codigo CIE-10 y no solo
 * por prueba de laboratorio anormal. Hasta ahora la vigilancia epidemiologica
 * solo veia lo que pasaba por el laboratorio, asi que un brote de una
 * enfermedad que se diagnostica por clinica y no por analitica era invisible.
 *
 * <p>Lleva la zona del paciente dentro del evento: analytics agrupa por zona y
 * pedirla despues a patient-service acoplaria los dos servicios y haria que la
 * vigilancia dependiera de que patient-service este en pie.
 *
 * <p>NO lo consume notification-service: un diagnostico registrado no es
 * noticia para nadie en el momento en que se registra. El paciente acaba de
 * estar delante del medico, y el medico es quien lo escribio.
 *
 * @param codigoCie10 codigo de la Clasificacion Internacional de Enfermedades,
 *                    por ejemplo A09 para una gastroenteritis infecciosa
 * @param esPrincipal el diagnostico principal de la atencion, frente a los
 *                    secundarios; una atencion tiene uno solo
 * @param zona        distrito del paciente, para la agrupacion geografica
 */
public record DiagnosisEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String source,
        String requestId,

        Long idDiagnostico,
        Long idAtencion,
        Long idPaciente,
        String dniPaciente,
        String zona,
        Integer edadPaciente,
        String sexoPaciente,
        Long idMedico,
        String codigoCie10,
        String descripcionCie10,
        Boolean esPrincipal,
        Boolean esNotificacionObligatoria,
        LocalDate fechaDiagnostico
) implements BaseEvent {

    public static DiagnosisEvent registrado(String requestId, Long idDiagnostico,
                                            Long idAtencion, Long idPaciente,
                                            String dniPaciente, String zona,
                                            Integer edadPaciente, String sexoPaciente,
                                            Long idMedico, String codigoCie10,
                                            String descripcionCie10, boolean esPrincipal,
                                            boolean esNotificacionObligatoria,
                                            LocalDate fechaDiagnostico) {
        return new DiagnosisEvent(
                UUID.randomUUID().toString(),
                "diagnosis.registered",
                Instant.now(),
                "medical-service",
                requestId,
                idDiagnostico, idAtencion, idPaciente, dniPaciente, zona, edadPaciente,
                sexoPaciente, idMedico, codigoCie10, descripcionCie10, esPrincipal,
                esNotificacionObligatoria,
                fechaDiagnostico == null ? LocalDate.now() : fechaDiagnostico);
    }

    /**
     * Enfermedad de notificacion obligatoria al Ministerio de Salud.
     *
     * <p>Son las que exigen aviso aunque haya un solo caso, sin esperar a que
     * se acumule un umbral: dengue, sarampion, tuberculosis y el resto de la
     * lista del MINSA. La vigilancia las trata distinto de un brote detectado
     * por conteo.
     */
    public boolean exigeAvisoInmediato() {
        return Boolean.TRUE.equals(esNotificacionObligatoria);
    }

    /** El diagnostico principal de la atencion. Null se trata como secundario. */
    public boolean esDiagnosticoPrincipal() {
        return Boolean.TRUE.equals(esPrincipal);
    }
}
