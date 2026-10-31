package com.medcore.medical.domain.dto.response;

import com.medcore.medical.domain.enums.AttentionStatus;

import java.time.Instant;
import java.util.List;

/**
 * Atencion medica completa.
 *
 * @param imc indice de masa corporal, calculado al responder; nulo si falta
 *            peso o talla
 */
public record AttentionResponse(
        Long id,
        Long idCita,
        Long idPaciente,
        String dniPaciente,
        String nombrePaciente,

        /*
         * Zona, edad y sexo tal como se registraron en la atencion.
         *
         * Se devuelven porque son los que vale para la vigilancia
         * epidemiologica: son una foto del dia de la atencion, no lo que diga
         * patient-service hoy. Sin verlos no hay forma de comprobar que el
         * dato llego, y un caso contado en la zona equivocada no se detecta
         * mirando el informe.
         */
        String zonaPaciente,
        Integer edadPaciente,
        String sexoPaciente,

        Long idMedico,
        String nombreMedico,
        String motivoConsulta,
        String anamnesis,
        String examenFisico,
        String tratamiento,
        String observaciones,
        Double temperatura,
        Integer presionSistolica,
        Integer presionDiastolica,
        Integer frecuenciaCardiaca,
        Double pesoKg,
        Double tallaCm,
        Double imc,
        AttentionStatus estado,
        String motivoAnulacion,
        List<DiagnosisResponse> diagnosticos,
        Instant fechaAtencion,
        Instant fechaCierre
) {
}
