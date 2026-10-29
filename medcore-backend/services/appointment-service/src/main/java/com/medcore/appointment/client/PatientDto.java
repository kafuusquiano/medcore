package com.medcore.appointment.client;

/**
 * Proyeccion de la ficha del paciente, con solo lo que la agenda necesita.
 *
 * <p>No se reutiliza el DTO de patient-service: compartir clases de respuesta
 * entre servicios los acopla, y cualquier campo nuevo alla obligaria a
 * recompilar aqui. Los campos que no figuren se ignoran al deserializar.
 *
 * @param estado estado de la ficha: ACTIVO, INACTIVO o FALLECIDO
 */
public record PatientDto(
        Long id,
        String dni,
        String nombreCompleto,
        String correo,
        String telefono,
        String estado
) {
    public boolean admiteAtencion() {
        return "ACTIVO".equals(estado);
    }
}
