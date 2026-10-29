package com.medcore.medical.service;

import com.medcore.commons.dto.PageableResponse;
import com.medcore.medical.domain.dto.request.LabOrderRequest;
import com.medcore.medical.domain.dto.response.LabOrderResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Ordenes de laboratorio.
 */
public interface LabOrderService {

    /** Emite la orden dentro de una atencion y publica lab-order.created. */
    LabOrderResponse emitir(Long idAtencion, LabOrderRequest request, String requestId);

    LabOrderResponse obtener(Long id);

    LabOrderResponse obtenerPorCodigo(String codigo);

    List<LabOrderResponse> ordenesDeAtencion(Long idAtencion);

    PageableResponse<LabOrderResponse> ordenesDePaciente(Long idPaciente, Pageable pageable);

    /** Solo se permite mientras el laboratorio no haya tomado la muestra. */
    LabOrderResponse anular(Long id, String motivo);
}
