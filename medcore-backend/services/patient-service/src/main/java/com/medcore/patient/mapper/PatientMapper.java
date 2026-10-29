package com.medcore.patient.mapper;

import com.medcore.patient.domain.dto.response.PatientResponse;
import com.medcore.patient.domain.dto.response.PatientSummaryResponse;
import com.medcore.patient.domain.entity.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Conversion de la ficha a sus dos vistas.
 *
 * <p>edad, menorDeEdad y nombreCompleto se derivan de la entidad en cada
 * respuesta; no se almacenan, porque una edad guardada queda obsoleta al dia
 * siguiente del cumpleanos.
 */
@Mapper(componentModel = "spring")
public interface PatientMapper {

    @Mapping(target = "nombreCompleto", expression = "java(paciente.nombreCompleto())")
    @Mapping(target = "edad", expression = "java(paciente.edad())")
    @Mapping(target = "menorDeEdad", expression = "java(paciente.esMenorDeEdad())")
    @Mapping(target = "tipoSangreEtiqueta", expression = "java(paciente.getTipoSangre().etiqueta())")
    PatientResponse aRespuesta(Patient paciente);

    @Mapping(target = "nombreCompleto", expression = "java(paciente.nombreCompleto())")
    @Mapping(target = "edad", expression = "java(paciente.edad())")
    PatientSummaryResponse aResumen(Patient paciente);
}
