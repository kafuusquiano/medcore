package com.medcore.appointment.service;

import com.medcore.appointment.domain.dto.response.SlotResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Disponibilidad de la agenda.
 */
public interface AvailabilityService {

    /** Franjas libres de un medico en una fecha. */
    List<SlotResponse> slotsDisponibles(Long idMedico, LocalDate fecha);

    /** Todas las franjas de la fecha, libres y ocupadas, para la vista de agenda. */
    List<SlotResponse> agendaDelDia(Long idMedico, LocalDate fecha);

    /**
     * Genera los slots de un medico desde una fecha y durante el horizonte
     * configurado. Es idempotente: no duplica los que ya existen.
     *
     * @return cuantos slots nuevos se crearon
     */
    int generarSlots(Long idMedico, LocalDate desde);

    /** Invalida la disponibilidad cacheada de un medico en una fecha. */
    void invalidarCache(Long idMedico, LocalDate fecha);
}
