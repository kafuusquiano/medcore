package com.medcore.laboratory.service;

import com.medcore.laboratory.domain.dto.request.SampleRequest;
import com.medcore.laboratory.domain.dto.response.SampleResponse;
import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;
import com.medcore.laboratory.domain.enums.SampleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Ciclo de vida del tubo, desde que llega al laboratorio hasta que se cierra. */
public interface SampleService {

    /**
     * Registra la muestra recibida para una orden pendiente.
     *
     * <p>Copia del evento los datos del paciente y consulta a patient-service
     * el sexo, la edad y la zona, que el evento no trae. Publica
     * {@code sample.registered} tras el commit.
     */
    SampleResponse registrar(SampleRequest peticion, String usuario, String requestId);

    /** Marca que el tubo entro al equipo: desde aqui admite resultados. */
    SampleResponse iniciarProceso(Long id, String requestId);

    /**
     * Cierra la muestra.
     *
     * <p>Solo se permite cuando todas las pruebas de la orden tienen
     * resultado: cerrarla antes dejaria pruebas sin informar y el medico
     * creeria que el laboratorio ya termino.
     */
    SampleResponse marcarProcesada(Long id, String requestId);

    /**
     * Rechaza la muestra con su motivo.
     *
     * <p>Tras rechazarla, la orden vuelve a la cola para que se registre otro
     * tubo: el indice unico parcial de V2 permite una segunda muestra viva.
     */
    SampleResponse rechazar(Long id, String motivo, String requestId);

    SampleResponse obtenerPorId(Long id);

    SampleResponse obtenerPorCodigo(String codigo);

    /** Busca la entidad sin convertirla, para uso interno del servicio. */
    Sample buscarEntidadPorCodigo(String codigo);

    /**
     * Resultados de una muestra como entidad.
     *
     * <p>Lo necesita Hl7ResultMapper al exportar una trama: del resultado
     * requiere el codigo HL7 de la interpretacion y la marca de correccion,
     * que el DTO no lleva. Se expone aqui para que el controller no tenga
     * que inyectar un repositorio (regla G5).
     */
    List<Result> resultadosDe(Long idMuestra);

    Page<SampleResponse> listarPorPaciente(Long idPaciente, Pageable pageable);

    Page<SampleResponse> listarPorEstado(SampleStatus estado, Pageable pageable);

    /** Cola de trabajo: urgentes primero y, dentro de cada grupo, por llegada. */
    List<SampleResponse> colaDeTrabajo(int limite);
}
