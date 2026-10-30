package com.medcore.laboratory.client;

/**
 * Vista reducida del paciente que necesita el laboratorio.
 *
 * <p>Declara solo tres datos de los veintiuno que devuelve patient-service.
 * Jackson ignora el resto, asi que anadir un campo alla no obliga a tocar
 * nada aqui.
 *
 * @param edad  la calcula patient-service desde la fecha de nacimiento; se
 *              pide calculada para no recalcularla en dos sitios con riesgo
 *              de discrepar
 * @param sexo  selecciona el rango de referencia aplicable
 * @param zona  la usa analytics-service para agrupar por distrito
 */
public record PatientDto(
        Long id,
        String dni,
        String nombreCompleto,
        String correo,
        Integer edad,
        String sexo,
        String zona
) {
}
