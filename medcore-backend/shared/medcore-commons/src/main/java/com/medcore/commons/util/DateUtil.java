package com.medcore.commons.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Fechas y horas, con los formatos que el sistema usa de verdad.
 *
 * <p>Existe porque los mismos patrones estaban repetidos en cinco servicios:
 * {@code yyyyMMddHHmmss} tres veces, {@code yyyyMMdd} dos. Un patron HL7
 * copiado mal en un sitio produce una trama que el analizador rechaza sin
 * decir por que.
 *
 * <p>Todo lo que se guarda y se publica va en UTC, que es lo que hace
 * comparables los instantes entre servicios. La zona de Lima solo aparece al
 * escribir para una persona: un correo que diga las 14:00 cuando la cita es a
 * las 09:00 es peor que no decir la hora.
 */
public final class DateUtil {

    /** Peru no aplica horario de verano, asi que el desfase es fijo en -05:00. */
    public static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    /**
     * Fecha y hora de HL7 v2, campos TS: {@code yyyyMMddHHmmss}.
     *
     * <p>Sin separadores y sin zona. El estandar admite precision variable, asi
     * que al leer hay que aceptar tambien una fecha sola.
     */
    public static final DateTimeFormatter HL7_FECHA_HORA =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** Fecha de HL7 sin hora: {@code yyyyMMdd}. */
    public static final DateTimeFormatter HL7_FECHA =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Para leer de una persona: 31/10/2026. */
    public static final DateTimeFormatter FECHA_LEGIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Para leer de una persona: 31/10/2026 14:30. */
    public static final DateTimeFormatter FECHA_HORA_LEGIBLE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Solo la hora: 14:30. */
    public static final DateTimeFormatter HORA_LEGIBLE =
            DateTimeFormatter.ofPattern("HH:mm");

    private static final Locale PERU = Locale.of("es", "PE");

    private static final String[] MESES = {
            "enero", "febrero", "marzo", "abril", "mayo", "junio",
            "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    // ------------------------------------------------------------------
    // Edad
    // ------------------------------------------------------------------

    /**
     * Edad en anios cumplidos.
     *
     * <p>Se calcula y no se guarda: una edad guardada queda desactualizada al
     * dia siguiente del cumpleanios, y el rango de referencia de un analisis
     * depende de la edad del paciente el dia de la muestra.
     *
     * @return null si no se conoce la fecha de nacimiento, para que quien
     *         consulte distinga "no se sabe" de "cero anios"
     */
    public static Integer edadEnAnios(LocalDate fechaNacimiento) {
        if (fechaNacimiento == null) {
            return null;
        }
        return edadEnAnios(fechaNacimiento, LocalDate.now(ZONA_LIMA));
    }

    /**
     * Edad en anios cumplidos a una fecha dada.
     *
     * <p>Es la que vale para un resultado de laboratorio: hay que aplicar el
     * rango de referencia de la edad que tenia el paciente cuando se le tomo la
     * muestra, no la de hoy.
     *
     * @return null si falta algun dato, o si la fecha de nacimiento es
     *         posterior a la de referencia
     */
    public static Integer edadEnAnios(LocalDate fechaNacimiento, LocalDate referencia) {
        if (fechaNacimiento == null || referencia == null
                || fechaNacimiento.isAfter(referencia)) {
            return null;
        }
        return Period.between(fechaNacimiento, referencia).getYears();
    }

    /**
     * Edad en meses, para pacientes de menos de dos anios.
     *
     * <p>Los rangos de referencia pediatricos se expresan en meses durante el
     * primer anio: decir "0 anios" de un recien nacido y de un nino de once
     * meses los mete en el mismo rango, y no es el mismo.
     */
    public static Integer edadEnMeses(LocalDate fechaNacimiento) {
        if (fechaNacimiento == null) {
            return null;
        }
        LocalDate hoy = LocalDate.now(ZONA_LIMA);
        if (fechaNacimiento.isAfter(hoy)) {
            return null;
        }
        Period p = Period.between(fechaNacimiento, hoy);
        return p.getYears() * 12 + p.getMonths();
    }

    // ------------------------------------------------------------------
    // HL7
    // ------------------------------------------------------------------

    /** Instante en formato HL7, en la zona de Lima, que es donde esta el equipo. */
    public static String aHl7(Instant instante) {
        if (instante == null) {
            return "";
        }
        return HL7_FECHA_HORA.format(LocalDateTime.ofInstant(instante, ZONA_LIMA));
    }

    /** Ahora mismo en formato HL7. Lo usa el MSH-7 de cada trama que se emite. */
    public static String ahoraHl7() {
        return HL7_FECHA_HORA.format(LocalDateTime.now(ZONA_LIMA));
    }

    /**
     * Lee una fecha y hora HL7, admitiendo la precision variable del estandar.
     *
     * <p>Un equipo puede mandar {@code 20261031}, {@code 2026103114},
     * {@code 202610311430} o los catorce digitos. Rechazar los cortos deja sin
     * leer resultados que son validos.
     *
     * @return null si el valor esta vacio o no se puede leer; un resultado sin
     *         fecha es preferible a descartar el resultado entero
     */
    public static LocalDateTime deHl7(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        // El estandar permite una zona al final (+0500) y decimales en los
        // segundos; para la fecha no aportan nada.
        String limpio = valor.trim();
        int corte = limpio.indexOf("+");
        if (corte < 0) {
            corte = limpio.indexOf("-");
        }
        if (corte > 0) {
            limpio = limpio.substring(0, corte);
        }
        int punto = limpio.indexOf(".");
        if (punto > 0) {
            limpio = limpio.substring(0, punto);
        }

        try {
            return switch (limpio.length()) {
                case 8 -> LocalDate.parse(limpio, HL7_FECHA).atStartOfDay();
                case 10 -> LocalDateTime.parse(limpio + "0000", HL7_FECHA_HORA);
                case 12 -> LocalDateTime.parse(limpio + "00", HL7_FECHA_HORA);
                case 14 -> LocalDateTime.parse(limpio, HL7_FECHA_HORA);
                default -> null;
            };
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Para leer
    // ------------------------------------------------------------------

    /** 31/10/2026, o cadena vacia si no hay fecha. */
    public static String legible(LocalDate fecha) {
        return fecha == null ? "" : FECHA_LEGIBLE.format(fecha);
    }

    /** 31/10/2026 14:30 en hora de Lima. Es lo que va en los correos. */
    public static String legible(Instant instante) {
        if (instante == null) {
            return "";
        }
        return FECHA_HORA_LEGIBLE.format(LocalDateTime.ofInstant(instante, ZONA_LIMA));
    }

    /** 14:30, sin la fecha. */
    public static String hora(Instant instante) {
        if (instante == null) {
            return "";
        }
        return HORA_LEGIBLE.format(LocalDateTime.ofInstant(instante, ZONA_LIMA));
    }

    /**
     * "31 de octubre de 2026", para el encabezado de un informe.
     *
     * <p>Los meses van escritos a mano y no por Locale: el JDK del contenedor
     * puede no traer los datos de la configuracion regional, y entonces un
     * informe clinico saldria con el mes en ingles.
     */
    public static String enPalabras(LocalDate fecha) {
        if (fecha == null) {
            return "";
        }
        return "%d de %s de %d".formatted(
                fecha.getDayOfMonth(), MESES[fecha.getMonthValue() - 1], fecha.getYear());
    }

    /** La configuracion regional del sistema, para quien formatee numeros. */
    public static Locale localePeru() {
        return PERU;
    }

    // ------------------------------------------------------------------
    // Rangos
    // ------------------------------------------------------------------

    /** Primer instante del dia en Lima, en UTC. Para consultar "lo de hoy". */
    public static Instant inicioDelDia(LocalDate fecha) {
        return fecha.atStartOfDay(ZONA_LIMA).toInstant();
    }

    /**
     * Primer instante del dia SIGUIENTE, en UTC.
     *
     * <p>Se devuelve el limite abierto a proposito: una consulta
     * {@code >= inicio AND < fin} no puede perder un registro de las 23:59:59.7
     * como lo haria un {@code <= 23:59:59}.
     */
    public static Instant finDelDia(LocalDate fecha) {
        return fecha.plusDays(1).atStartOfDay(ZONA_LIMA).toInstant();
    }

    /** El dia de Lima al que pertenece un instante. */
    public static LocalDate diaDe(Instant instante) {
        return instante == null ? null : instante.atZone(ZONA_LIMA).toLocalDate();
    }

    private DateUtil() {
        throw new AssertionError("Clase de utilidades, no instanciable");
    }
}
