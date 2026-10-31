package com.medcore.analytics.domain.document;

import com.medcore.commons.event.DiagnosisEvent;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Un diagnostico CIE-10, para la vigilancia epidemiologica.
 *
 * <p>Va en su propia coleccion y no en {@code epidemiological_records}, que
 * guarda resultados de laboratorio. Son dos fuentes distintas con campos
 * distintos: un diagnostico no tiene valor numerico ni unidad ni prueba, y un
 * resultado no tiene codigo CIE-10. Meterlos en el mismo documento dejaria la
 * mitad de los campos nulos en cada caso, y las consultas tendrian que
 * distinguirlos por un campo de tipo.
 *
 * <p>Las dos fuentes se complementan: el laboratorio ve lo que se confirma por
 * analitica, y el diagnostico ve lo que el medico determina por clinica. Un
 * brote de una enfermedad que no se confirma en laboratorio solo aparece aqui.
 *
 * @param zona           distrito del paciente el dia de la atencion, no el de
 *                       hoy: el caso cuenta donde ocurrio
 * @param grupoEdad      franja de edad, calculada al guardar; agrupar por edad
 *                       exacta da grupos de un caso y no deja ver nada
 * @param notificable    enfermedad de notificacion obligatoria al MINSA, que
 *                       exige aviso con un solo caso, sin esperar un umbral
 */
@Document(collection = "diagnosis_records")
public record DiagnosisRecord(
        @Id String id,
        Long idDiagnostico,
        Long idAtencion,
        Long idPaciente,
        String dniPaciente,
        String zona,
        Integer edad,
        String grupoEdad,
        String sexo,
        Long idMedico,
        String codigoCie10,
        String descripcionCie10,
        boolean principal,
        boolean notificable,
        LocalDate fecha,
        Instant registradoEn
) {

    /** Cuando no se conoce la zona. No se reparte a ciegas entre distritos. */
    public static final String ZONA_SIN_REGISTRAR = "SIN REGISTRAR";

    /** Cuando no se conoce la edad. */
    public static final String EDAD_SIN_REGISTRAR = "SIN REGISTRAR";

    /**
     * Construye el registro desde el evento.
     *
     * <p>El id del documento es el del diagnostico en medical_db, no uno
     * generado: es lo que hace idempotente el consumo. Kafka entrega al menos
     * una vez, y sin esto un reenvio contaria el mismo caso dos veces y podria
     * disparar un brote que no existe.
     */
    public static DiagnosisRecord de(DiagnosisEvent evento) {
        return new DiagnosisRecord(
                String.valueOf(evento.idDiagnostico()),
                evento.idDiagnostico(),
                evento.idAtencion(),
                evento.idPaciente(),
                evento.dniPaciente(),
                zonaDe(evento.zona()),
                evento.edadPaciente(),
                grupoDeEdad(evento.edadPaciente()),
                evento.sexoPaciente(),
                evento.idMedico(),
                evento.codigoCie10(),
                evento.descripcionCie10(),
                evento.esDiagnosticoPrincipal(),
                evento.exigeAvisoInmediato(),
                evento.fechaDiagnostico(),
                Instant.now());
    }

    private static String zonaDe(String zona) {
        return zona == null || zona.isBlank() ? ZONA_SIN_REGISTRAR : zona.trim();
    }

    /**
     * Franjas de edad del MINSA para la vigilancia.
     *
     * <p>No son tramos iguales: separan los grupos que de verdad se comportan
     * distinto ante una enfermedad. El menor de cinco anios y el mayor de
     * sesenta son los que concentran la mortalidad, y meterlos en tramos de
     * diez anios los diluye.
     */
    public static String grupoDeEdad(Integer edad) {
        if (edad == null || edad < 0) {
            return EDAD_SIN_REGISTRAR;
        }
        if (edad < 1) {
            return "MENOR DE 1";
        }
        if (edad < 5) {
            return "1 A 4";
        }
        if (edad < 12) {
            return "5 A 11";
        }
        if (edad < 18) {
            return "12 A 17";
        }
        if (edad < 30) {
            return "18 A 29";
        }
        if (edad < 60) {
            return "30 A 59";
        }
        return "60 Y MAS";
    }
}
