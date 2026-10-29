package com.medcore.medical.mapper;

import com.medcore.medical.domain.dto.response.LabOrderResponse;
import com.medcore.medical.domain.entity.LabOrder;
import com.medcore.medical.domain.entity.LabOrderTest;
import org.springframework.stereotype.Component;

/** Conversion de la orden de laboratorio a su vista. */
@Component
public class LabOrderMapper {

    public LabOrderResponse aRespuesta(LabOrder orden) {
        return new LabOrderResponse(
                orden.getId(),
                orden.getCodigo(),
                orden.getAtencion().getId(),
                orden.getIdPaciente(),
                orden.getIdMedico(),
                orden.getPrioridad(),
                orden.getEstado(),
                orden.getObservaciones(),
                orden.getMotivoAnulacion(),
                // Para que el frontend muestre o no el boton de anular.
                orden.getEstado().admiteAnulacion(),
                orden.getPruebas().stream().map(this::aRespuesta).toList(),
                orden.getFechaEmision());
    }

    private LabOrderResponse.PruebaResponse aRespuesta(LabOrderTest prueba) {
        return new LabOrderResponse.PruebaResponse(
                prueba.getId(),
                prueba.getCodigoPrueba(),
                prueba.getNombrePrueba(),
                prueba.getIndicaciones());
    }
}
