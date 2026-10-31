package com.medcore.medical.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Apertura de una atencion medica.
 *
 * @param idCita nulo si el paciente llega por urgencia, sin cita previa
 */
public record AttentionRequest(

        Long idCita,

        @NotNull(message = "El paciente es obligatorio")
        Long idPaciente,

        @NotBlank(message = "El DNI del paciente es obligatorio")
        String dniPaciente,

        @NotBlank(message = "El nombre del paciente es obligatorio")
        @Size(max = 170, message = "El nombre no puede superar los 170 caracteres")
        String nombrePaciente,

        String correoPaciente,

        /*
         * Zona, edad y sexo del paciente: los pide la vigilancia
         * epidemiologica, que agrupa los diagnosticos por distrito y por grupo
         * de edad. Van en la peticion y no se consultan a patient-service para
         * que una atencion se pueda abrir aunque ese servicio este caido: en
         * urgencias eso no es negociable.
         *
         * Opcionales a proposito. Un dato que no se conoce se queda nulo, y el
         * informe cuenta el caso en "sin registrar", que es la verdad.
         */
        @Size(max = 80, message = "La zona no puede superar los 80 caracteres")
        String zonaPaciente,

        Integer edadPaciente,

        @Size(max = 10, message = "El sexo no puede superar los 10 caracteres")
        String sexoPaciente,

        @NotNull(message = "El medico es obligatorio")
        Long idMedico,

        @NotBlank(message = "El nombre del medico es obligatorio")
        @Size(max = 120, message = "El nombre del medico no puede superar los 120 caracteres")
        String nombreMedico,

        @NotBlank(message = "El motivo de consulta es obligatorio")
        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivoConsulta
) {
}
