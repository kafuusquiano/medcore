package com.medcore.laboratory.service;

import com.medcore.commons.event.LabOrderEvent;
import com.medcore.laboratory.domain.dto.response.PendingOrderResponse;

import java.util.List;

/**
 * Cola de ordenes que el laboratorio espera recibir.
 *
 * <p>Entre que el medico emite la orden y el paciente trae la muestra pasa
 * tiempo, y a veces el paciente no acude. La cola es lo que el tecnico ve en
 * recepcion para saber a quien corresponde el tubo que le entregan.
 */
public interface LabOrderQueueService {

    /**
     * Guarda la orden que llego en {@code lab-order.created}.
     *
     * <p>Es idempotente: la clave primaria es el identificador de la orden en
     * medical-service, asi que recibir el mismo evento dos veces, que la
     * garantia "al menos una vez" de Kafka permite, no crea dos filas.
     */
    void encolar(LabOrderEvent evento);

    /** Ordenes en espera, urgentes primero. */
    List<PendingOrderResponse> pendientes(int limite);

    /** Ordenes en espera de un paciente, para buscarlo por su documento. */
    List<PendingOrderResponse> pendientesDeDni(String dni);

    PendingOrderResponse obtenerPorId(Long idOrden);

    /** Cuantas ordenes esperan y cuantas de ellas son urgentes. */
    Resumen resumen();

    record Resumen(long pendientes, long urgentes) {
    }
}
