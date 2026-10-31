package com.medcore.medical.mapper;

import com.medcore.medical.domain.dto.response.AttentionResponse;
import com.medcore.medical.domain.dto.response.DiagnosisResponse;
import com.medcore.medical.domain.entity.Diagnosis;
import com.medcore.medical.domain.entity.MedicalAttention;
import org.springframework.stereotype.Component;

/**
 * Conversion de la atencion a su vista.
 *
 * <p>Se escribe a mano y no con MapStruct porque el IMC se deriva de dos
 * campos y la coleccion de diagnosticos necesita su propio mapeo.
 */
@Component
public class AttentionMapper {

    public AttentionResponse aRespuesta(MedicalAttention atencion) {
        return new AttentionResponse(
                atencion.getId(),
                atencion.getIdCita(),
                atencion.getIdPaciente(),
                atencion.getDniPaciente(),
                atencion.getNombrePaciente(),
                atencion.getZonaPaciente(),
                atencion.getEdadPaciente(),
                atencion.getSexoPaciente(),
                atencion.getIdMedico(),
                atencion.getNombreMedico(),
                atencion.getMotivoConsulta(),
                atencion.getAnamnesis(),
                atencion.getExamenFisico(),
                atencion.getTratamiento(),
                atencion.getObservaciones(),
                atencion.getTemperatura(),
                atencion.getPresionSistolica(),
                atencion.getPresionDiastolica(),
                atencion.getFrecuenciaCardiaca(),
                atencion.getPesoKg(),
                atencion.getTallaCm(),
                // Calculado al responder: una talla o un peso nuevos lo
                // cambian, asi que almacenarlo lo dejaria desactualizado.
                atencion.imc(),
                atencion.getEstado(),
                atencion.getMotivoAnulacion(),
                atencion.getDiagnosticos().stream().map(this::aRespuesta).toList(),
                atencion.getFechaAtencion(),
                atencion.getFechaCierre());
    }

    public DiagnosisResponse aRespuesta(Diagnosis diagnostico) {
        return new DiagnosisResponse(
                diagnostico.getId(),
                diagnostico.getCodigoCie10(),
                diagnostico.getDescripcionCie10(),
                diagnostico.isPrincipal(),
                diagnostico.isCasoNuevo(),
                diagnostico.isNotificable(),
                diagnostico.getObservaciones(),
                diagnostico.getFechaRegistro());
    }
}
