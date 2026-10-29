package com.medcore.patient.domain.dto.request;

import com.medcore.patient.domain.enums.Gender;
import com.medcore.patient.domain.enums.PatientStatus;

/**
 * Criterios de busqueda de pacientes. Todos opcionales: los nulos no filtran.
 *
 * @param texto     busca en DNI, nombres y apellidos a la vez
 * @param sexo      filtra por sexo
 * @param estado    filtra por estado de la ficha
 * @param zona      filtra por distrito o zona
 * @param edadMin   edad minima en anos cumplidos
 * @param edadMax   edad maxima en anos cumplidos
 */
public record PatientSearchRequest(
        String texto,
        Gender sexo,
        PatientStatus estado,
        String zona,
        Integer edadMin,
        Integer edadMax
) {
    /** Busqueda sin criterios: devuelve todas las fichas activas. */
    public static PatientSearchRequest vacia() {
        return new PatientSearchRequest(null, null, null, null, null, null);
    }
}
