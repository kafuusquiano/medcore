package com.medcore.appointment.exception;

import com.medcore.commons.exception.MedCoreException;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Errores propios de appointment-service.
 */
public class AppointmentException extends MedCoreException {

    @Serial
    private static final long serialVersionUID = 1L;

    public AppointmentException(String codigo, HttpStatus estado, String mensaje) {
        super(codigo, estado, mensaje);
    }

    public AppointmentException(String codigo, HttpStatus estado, String mensaje,
                                Map<String, Object> detalles) {
        super(codigo, estado, mensaje, detalles);
    }

    public static AppointmentException citaNoEncontrada(Long id) {
        return new AppointmentException("CITA_NO_ENCONTRADA", HttpStatus.NOT_FOUND,
                "No existe una cita con el identificador indicado.", Map.of("id", id));
    }

    public static AppointmentException horarioNoEncontrado(Long idMedico) {
        return new AppointmentException("HORARIO_NO_ENCONTRADO", HttpStatus.NOT_FOUND,
                "El medico no tiene un horario de atencion configurado.",
                Map.of("idMedico", idMedico));
    }

    public static AppointmentException slotNoEncontrado(Long idSlot) {
        return new AppointmentException("HORARIO_NO_DISPONIBLE", HttpStatus.NOT_FOUND,
                "El horario solicitado no existe en la agenda.", Map.of("idSlot", idSlot));
    }

    /** Alguien reservo ese mismo horario primero. */
    public static AppointmentException slotOcupado(LocalDateTime fechaHora) {
        return new AppointmentException("HORARIO_OCUPADO", HttpStatus.CONFLICT,
                "Ese horario acaba de ser reservado. Elige otro.",
                Map.of("fechaHora", fechaHora.toString()));
    }

    public static AppointmentException slotEnElPasado() {
        return new AppointmentException("HORARIO_PASADO", HttpStatus.CONFLICT,
                "No se puede reservar un horario que ya paso.");
    }

    public static AppointmentException slotsConsecutivosInsuficientes() {
        return new AppointmentException("HORARIOS_INSUFICIENTES", HttpStatus.CONFLICT,
                "Una urgencia necesita dos horarios seguidos libres y no los hay a esa hora.");
    }

    public static AppointmentException pacienteNoAdmiteAtencion() {
        return new AppointmentException("PACIENTE_NO_ACTIVO", HttpStatus.CONFLICT,
                "La ficha del paciente no esta activa y no admite nuevas citas.");
    }

    /**
     * patient-service no respondio. No se reserva a ciegas: una cita contra
     * un paciente sin verificar habria que deshacerla a mano.
     */
    public static AppointmentException pacienteNoVerificable() {
        return new AppointmentException("PACIENTE_NO_VERIFICABLE", HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo verificar los datos del paciente en este momento. "
                        + "Intentalo de nuevo en unos minutos.");
    }

    public static AppointmentException pacienteYaTieneCita(LocalDateTime fechaHora) {
        return new AppointmentException("CITA_DUPLICADA", HttpStatus.CONFLICT,
                "El paciente ya tiene una cita vigente ese mismo dia.",
                Map.of("fechaHora", fechaHora.toString()));
    }

    public static AppointmentException cancelacionFueraDePlazo(int horasMinimas) {
        return new AppointmentException("CANCELACION_FUERA_DE_PLAZO", HttpStatus.CONFLICT,
                "Una cita debe cancelarse con al menos " + horasMinimas
                        + " horas de antelacion.");
    }

    public static AppointmentException horarioYaConfigurado(Long idMedico) {
        return new AppointmentException("HORARIO_YA_CONFIGURADO", HttpStatus.CONFLICT,
                "El medico ya tiene un horario activo. Actualizalo en lugar de crear otro.",
                Map.of("idMedico", idMedico));
    }

    public static AppointmentException rangoFechasInvalido() {
        return new AppointmentException("RANGO_FECHAS_INVALIDO", HttpStatus.BAD_REQUEST,
                "La fecha de inicio no puede ser posterior a la de fin.");
    }
}
