package com.medcore.medical.domain.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Hallazgos y signos vitales de la consulta.
 *
 * <p>Los rangos replican las restricciones CHECK de la base: fuera de ellos
 * solo cabe un error de captura, y conviene rechazarlo con un mensaje claro
 * antes de que la base lo rechace con un error tecnico.
 */
public record AttentionUpdateRequest(

        @Size(max = 2000, message = "La anamnesis no puede superar los 2000 caracteres")
        String anamnesis,

        @Size(max = 2000, message = "El examen fisico no puede superar los 2000 caracteres")
        String examenFisico,

        @Size(max = 2000, message = "El tratamiento no puede superar los 2000 caracteres")
        String tratamiento,

        @Size(max = 1000, message = "Las observaciones no pueden superar los 1000 caracteres")
        String observaciones,

        @DecimalMin(value = "30.0", message = "La temperatura debe estar entre 30 y 45 grados")
        @DecimalMax(value = "45.0", message = "La temperatura debe estar entre 30 y 45 grados")
        Double temperatura,

        @Min(value = 40, message = "La presion sistolica debe estar entre 40 y 300")
        @Max(value = 300, message = "La presion sistolica debe estar entre 40 y 300")
        Integer presionSistolica,

        @Min(value = 20, message = "La presion diastolica debe estar entre 20 y 200")
        @Max(value = 200, message = "La presion diastolica debe estar entre 20 y 200")
        Integer presionDiastolica,

        @Min(value = 20, message = "La frecuencia cardiaca debe estar entre 20 y 250")
        @Max(value = 250, message = "La frecuencia cardiaca debe estar entre 20 y 250")
        Integer frecuenciaCardiaca,

        @DecimalMin(value = "0.5", message = "El peso debe estar entre 0.5 y 500 kg")
        @DecimalMax(value = "500.0", message = "El peso debe estar entre 0.5 y 500 kg")
        Double pesoKg,

        @DecimalMin(value = "20.0", message = "La talla debe estar entre 20 y 250 cm")
        @DecimalMax(value = "250.0", message = "La talla debe estar entre 20 y 250 cm")
        Double tallaCm
) {
    /** La coherencia entre sistolica y diastolica cruza dos campos. */
    public boolean presionCoherente() {
        return presionSistolica == null || presionDiastolica == null
                || presionSistolica > presionDiastolica;
    }
}
