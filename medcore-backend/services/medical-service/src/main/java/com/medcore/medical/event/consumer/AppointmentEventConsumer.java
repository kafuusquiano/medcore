package com.medcore.medical.event.consumer;

import com.medcore.commons.event.AppointmentEvent;
import com.medcore.commons.kafka.KafkaTopics;
import com.medcore.medical.domain.entity.MedicalAttention;
import com.medcore.medical.repository.MedicalAttentionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Escucha {@code appointment.cancelled}.
 *
 * <p>Si la cita cancelada tenia una atencion abierta, la anula: el paciente
 * no va a acudir y dejarla abierta ensuciaria la lista de atenciones
 * pendientes del medico indefinidamente.
 *
 * <p>Una atencion ya FINALIZADA no se toca. Que la cita se cancele despues de
 * atender al paciente es una incoherencia administrativa, pero el registro
 * clinico de lo que ocurrio debe conservarse intacto.
 *
 * <p>El offset se confirma siempre: un fallo aqui no debe bloquear la
 * particion ni impedir que notification-service reciba el mismo evento.
 *
 * <p>No se declara un parametro Acknowledgment: con ack-mode: record el
 * contenedor confirma el offset al terminar el listener sin excepcion, y ese
 * parametro solo se inyecta con ack-mode MANUAL. Como el metodo atrapa toda
 * RuntimeException, un fallo queda en el log y el offset avanza igualmente,
 * de modo que un mensaje problematico no bloquea la particion.
 */
@Component
public class AppointmentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AppointmentEventConsumer.class);

    private final MedicalAttentionRepository attentionRepository;

    public AppointmentEventConsumer(MedicalAttentionRepository attentionRepository) {
        this.attentionRepository = attentionRepository;
    }

    @KafkaListener(topics = KafkaTopics.APPOINTMENT_CANCELLED, groupId = "medical-service")
    @Transactional
    public void alCancelarseCita(AppointmentEvent evento) {
        try {
            if (evento == null || evento.idCita() == null) {
                log.warn("Evento appointment.cancelled incompleto; se descarta");
                return;
            }

            Optional<MedicalAttention> encontrada =
                    attentionRepository.atencionVivaDeCita(evento.idCita());

            if (encontrada.isEmpty()) {
                log.debug("La cita {} no tiene atencion viva; nada que anular", evento.idCita());
                return;
            }

            MedicalAttention atencion = encontrada.get();

            if (!atencion.getEstado().admiteCambios()) {
                log.info("La atencion {} de la cita {} ya esta {}; no se modifica",
                        atencion.getId(), evento.idCita(), atencion.getEstado());
                return;
            }

            atencion.anular("Cita cancelada: "
                    + (evento.motivoCancelacion() == null ? "sin motivo" : evento.motivoCancelacion()));
            attentionRepository.save(atencion);

            log.info("Atencion {} anulada por cancelacion de la cita {} [{}]",
                    atencion.getId(), evento.idCita(), evento.requestId());

        } catch (RuntimeException e) {
            log.error("Error procesando appointment.cancelled: {}", e.getMessage(), e);
        }
    }
}
