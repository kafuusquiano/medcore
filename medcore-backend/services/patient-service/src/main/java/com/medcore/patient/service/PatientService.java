package com.medcore.patient.service;

import com.medcore.patient.domain.dto.request.PatientRequest;
import com.medcore.patient.domain.dto.request.PatientSearchRequest;
import com.medcore.patient.domain.dto.response.PatientResponse;
import com.medcore.patient.domain.dto.response.PatientSummaryResponse;
import com.medcore.commons.dto.PageableResponse;
import org.springframework.data.domain.Pageable;

/**
 * Gestion de fichas de pacientes.
 *
 * <p>Las operaciones de escritura publican eventos Kafka, por eso reciben el
 * requestId: permite seguir una misma operacion desde la peticion HTTP hasta
 * el consumidor del evento en otro servicio.
 */
public interface PatientService {

    PatientResponse crear(PatientRequest request, String requestId);

    PatientResponse actualizar(Long id, PatientRequest request, String requestId);

    PatientResponse obtenerPorId(Long id);

    PatientResponse obtenerPorDni(String dni);

    PageableResponse<PatientSummaryResponse> buscar(PatientSearchRequest criterios, Pageable pageable);

    PatientResponse desactivar(Long id);

    PatientResponse reactivar(Long id);

    PatientResponse marcarFallecido(Long id);
}
