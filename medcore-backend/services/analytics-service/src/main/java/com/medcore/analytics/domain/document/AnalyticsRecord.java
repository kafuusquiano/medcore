package com.medcore.analytics.domain.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Un resultado de laboratorio, reducido a lo que la vigilancia necesita.
 *
 * <p>Es el hecho individual del que salen todos los agregados. Se guarda uno
 * por cada {@code result.registered} recibido.
 *
 * <p><b>Sobre que se vigila:</b> esta vigilancia es por PRUEBA DE LABORATORIO
 * con resultado anormal, no por diagnostico CIE-10. El motivo es que
 * medical-service no publica todavia ningun evento de diagnostico, asi que
 * analytics solo recibe resultados. Es una senal valida por si misma: "cinco
 * casos de proteina C reactiva alta en la misma zona en siete dias" indica un
 * posible brote sin necesidad del diagnostico. Cuando medical-service
 * publique sus diagnosticos, bastara con anadir el codigo CIE-10 a este
 * documento y una segunda regla de deteccion.
 *
 * <p>No guarda el nombre del paciente. Para contar casos por zona basta el
 * documento, que sirve para no contar dos veces a la misma persona, y la
 * epidemiologia no necesita saber de quien se trata. Reducir lo que se copia
 * limita lo que se expone si esta base se consulta para un informe.
 *
 * @param fecha fecha del resultado sin la hora, para agrupar por dia sin
 *              tener que truncar en cada consulta
 */
@Document(collection = "epidemiological_records")
public record AnalyticsRecord(

        @Id String id,

        /**
         * Identificador del resultado en lab_db. Es la clave natural: hace
         * idempotente el consumo, porque Kafka puede entregar el mismo evento
         * dos veces y un caso contado doble falsea la vigilancia.
         */
        Long idResultado,

        String codigoMuestra,
        Long idPaciente,
        String dniPaciente,

        /** Distrito o zona del paciente. Es el eje de toda la agregacion. */
        String zona,

        String codigoPrueba,
        String nombrePrueba,

        Double valorNumerico,
        String unidad,

        /** NORMAL, BAJO, ALTO, CRITICO_BAJO, CRITICO_ALTO o NO_APLICA. */
        String interpretacion,

        boolean critico,

        /**
         * True cuando la interpretacion no es normal. Se guarda calculado
         * porque es el filtro de casi todas las consultas de vigilancia, y
         * recalcularlo en cada agregacion obligaria a listar los valores
         * anormales en cada una.
         */
        boolean anormal,

        LocalDate fecha,
        Instant fechaResultado,
        Instant fechaRegistro
) {

    /** Interpretaciones que no cuentan como caso para la vigilancia. */
    private static final String NORMAL = "NORMAL";
    private static final String NO_APLICA = "NO_APLICA";

    public static AnalyticsRecord de(Long idResultado, String codigoMuestra, Long idPaciente,
                                     String dniPaciente, String zona, String codigoPrueba,
                                     String nombrePrueba, Double valorNumerico, String unidad,
                                     String interpretacion, boolean critico,
                                     Instant fechaResultado, java.time.ZoneId zonaHoraria) {
        Instant fecha = fechaResultado == null ? Instant.now() : fechaResultado;

        return new AnalyticsRecord(
                null,
                idResultado,
                codigoMuestra,
                idPaciente,
                dniPaciente,
                // Una zona en blanco se normaliza a un valor explicito: en un
                // mapa, "sin zona" es informacion, y un null repartido entre
                // los agregados solo da huecos.
                zona == null || zona.isBlank() ? "SIN ZONA" : zona.trim(),
                codigoPrueba,
                nombrePrueba,
                valorNumerico,
                unidad,
                interpretacion,
                critico,
                esAnormal(interpretacion),
                fecha.atZone(zonaHoraria).toLocalDate(),
                fecha,
                Instant.now());
    }

    /**
     * Un resultado cuenta como caso si su interpretacion no es normal.
     *
     * <p>NO_APLICA queda fuera: son las pruebas cualitativas y las que no
     * tienen rango de referencia, de las que no se puede decir si el valor es
     * anormal. Contarlas inflaria los brotes con resultados que nadie
     * interpreto.
     */
    private static boolean esAnormal(String interpretacion) {
        return interpretacion != null
                && !NORMAL.equals(interpretacion)
                && !NO_APLICA.equals(interpretacion);
    }
}
