package com.medcore.patient.mapper;

import com.medcore.patient.domain.dto.response.MedicalHistoryResponse;
import com.medcore.patient.domain.entity.MedicalHistory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MedicalHistoryMapper {

    @Mapping(target = "idPaciente", source = "paciente.id")
    MedicalHistoryResponse aRespuesta(MedicalHistory episodio);
}
