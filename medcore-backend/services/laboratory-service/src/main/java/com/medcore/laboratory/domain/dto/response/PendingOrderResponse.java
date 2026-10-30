package com.medcore.laboratory.domain.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * Orden que el laboratorio espera recibir.
 *
 * <p>Es lo que ve el tecnico en la pantalla de recepcion: a quien corresponde
 * el tubo que le estan entregando y que pruebas hay que hacerle.
 *
 * @param horasEnEspera tiempo desde que el medico emitio la orden. Sirve para
 *                      detectar ordenes olvidadas: un paciente que nunca
 *                      llego a dar la muestra.
 */
public record PendingOrderResponse(
        Long idOrden,
        String codigoOrden,
        Long idAtencion,
        Long idPaciente,
        String dniPaciente,
        String nombrePaciente,
        Long idMedico,
        String nombreMedico,
        String prioridad,
        boolean urgente,
        String observaciones,
        List<PruebaPendiente> pruebas,
        long horasEnEspera,
        Instant fechaEmision
) {

    /**
     * Prueba solicitada, enriquecida con lo que el catalogo sabe de ella.
     *
     * @param enCatalogo  false si medical-service pidio un codigo que el
     *                    laboratorio no tiene. Se muestra en lugar de
     *                    ocultarlo: el tecnico debe poder avisar.
     * @param indicaciones condiciones de la toma, por ejemplo el ayuno
     */
    public record PruebaPendiente(
            String codigoPrueba,
            String nombrePrueba,
            String area,
            String tipoMuestra,
            String indicaciones,
            boolean enCatalogo
    ) {
    }
}
