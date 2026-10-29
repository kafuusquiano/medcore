package com.medcore.appointment.service;

import com.medcore.appointment.domain.dto.request.ScheduleRequest;
import com.medcore.appointment.domain.dto.response.ScheduleResponse;

import java.util.List;

/**
 * Horarios de atencion de los medicos.
 */
public interface ScheduleService {

    /**
     * Da de alta el horario y genera de inmediato los slots del horizonte
     * configurado, para que el medico quede disponible sin esperar a la
     * tarea nocturna.
     */
    ScheduleResponse crear(ScheduleRequest request);

    ScheduleResponse actualizar(Long id, ScheduleRequest request);

    ScheduleResponse obtenerPorMedico(Long idMedico);

    List<ScheduleResponse> listar(String especialidad);

    void desactivar(Long id);
}
