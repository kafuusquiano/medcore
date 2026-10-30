package com.medcore.laboratory.mapper;

import com.medcore.laboratory.domain.dto.response.SampleResponse;
import com.medcore.laboratory.domain.entity.Result;
import com.medcore.laboratory.domain.entity.Sample;
import org.springframework.stereotype.Component;

import java.util.List;

/** Conversion de una muestra a su vista. */
@Component
public class SampleMapper {

    private final ResultMapper resultMapper;

    public SampleMapper(ResultMapper resultMapper) {
        this.resultMapper = resultMapper;
    }

    /** Vista de la muestra sin sus resultados, para los listados. */
    public SampleResponse aRespuesta(Sample muestra) {
        return aRespuesta(muestra, List.of());
    }

    /**
     * Vista de la muestra con sus resultados.
     *
     * <p>Los resultados se reciben como parametro en lugar de leerlos de la
     * entidad: Sample no mantiene la coleccion para no arrastrarla en cada
     * consulta de la cola de trabajo, donde solo interesan los datos del tubo.
     */
    public SampleResponse aRespuesta(Sample muestra, List<Result> resultados) {
        return new SampleResponse(
                muestra.getId(),
                muestra.getCodigo(),
                muestra.getIdOrden(),
                muestra.getCodigoOrden(),
                muestra.getIdPaciente(),
                muestra.getDniPaciente(),
                muestra.getNombrePaciente(),
                muestra.getSexoPaciente(),
                muestra.getEdadPaciente(),
                muestra.getIdMedico(),
                muestra.getTipoMuestra(),
                muestra.getEstado(),
                muestra.isUrgente(),
                // Calculado, para que el frontend no replique la maquina de
                // estados al decidir si muestra el formulario de resultados.
                muestra.admiteResultados(),
                muestra.getObservaciones(),
                muestra.getMotivoRechazo(),
                muestra.getTomadaPor(),
                muestra.getFechaToma(),
                muestra.getFechaProceso(),
                resultados.stream().map(resultMapper::aRespuesta).toList());
    }
}
