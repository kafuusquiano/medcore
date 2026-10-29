package com.medcore.patient.domain.enums;

/**
 * Sexo registrado en la ficha del paciente.
 *
 * <p>Se usa en los reportes epidemiologicos que consume analytics-service,
 * por lo que los valores no deben renombrarse sin migrar los datos.
 */
public enum Gender {

    MASCULINO,
    FEMENINO,
    /** Para fichas antiguas o cuando el paciente prefiere no declararlo. */
    NO_ESPECIFICADO
}
