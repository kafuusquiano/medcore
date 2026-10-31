package com.medcore.analytics.domain.document;

import com.medcore.analytics.domain.enums.AlertSource;
import com.medcore.analytics.domain.enums.AlertStatus;
import com.medcore.analytics.domain.enums.RiskLevel;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Alerta de posible brote en una zona.
 *
 * <p>Se abre cuando los casos de una misma prueba anormal en una zona superan
 * el umbral dentro de la ventana configurada. Mientras la alerta sigue
 * ABIERTA no se abre otra igual: se actualizan sus cifras. Sin esa regla, cada
 * caso nuevo del mismo brote abriria una alerta y el panel acabaria con
 * veinte avisos del mismo hecho.
 *
 * <p>Una alerta no se borra. Se cierra como CONFIRMADA o DESCARTADA por quien
 * la revisa, y ese desenlace es lo que permite afinar el umbral despues: si
 * casi todas se descartan, el umbral esta demasiado bajo.
 *
 * @param casos          casos contados al abrir o en la ultima actualizacion
 * @param dnisAfectados  documentos de los pacientes, para no contar dos veces
 *                       a la misma persona y para poder rastrear el brote
 */
@Document(collection = "outbreak_alerts")
public record OutbreakAlert(

        @Id String id,

        String zona,

        /**
         * De donde sale el brote.
         *
         * <p>Las dos fuentes se complementan y no son la misma cosa: el
         * laboratorio ve lo que se confirma por analitica, y el diagnostico lo
         * que el medico determina por clinica. Una enfermedad que no se
         * confirma en laboratorio solo aparece por la segunda, y mezclar las
         * dos en un recuento contaria dos veces al mismo paciente.
         *
         * <p>Nula en las alertas creadas antes de que existiera la vigilancia
         * clinica; se leen como de LABORATORIO, que es lo que eran.
         */
        AlertSource fuente,

        /**
         * Que se esta vigilando: el codigo de la prueba o el CIE-10.
         *
         * <p>Siguen llamandose codigoPrueba y nombrePrueba por las alertas que
         * ya estaban guardadas con esos nombres de campo. En una alerta de
         * fuente DIAGNOSTICO llevan el codigo CIE-10 y su descripcion, y es
         * {@link #fuente} lo que dice como leerlos.
         */
        String codigoPrueba,
        String nombrePrueba,

        int casos,
        int umbral,
        int ventanaDias,

        RiskLevel nivelRiesgo,
        AlertStatus estado,

        /** Casos criticos entre los contados; suben el nivel de riesgo. */
        int casosCriticos,

        List<String> dnisAfectados,

        LocalDate primerCaso,
        LocalDate ultimoCaso,

        /** Quien la cerro y por que. Nulos mientras sigue abierta. */
        String resueltaPor,
        String motivoResolucion,
        Instant fechaResolucion,

        Instant fechaDeteccion,
        Instant fechaActualizacion
) {

    public static OutbreakAlert abrir(String zona, String codigoPrueba, String nombrePrueba,
                                      int casos, int casosCriticos, int umbral, int ventanaDias,
                                      List<String> dnis, LocalDate primerCaso,
                                      LocalDate ultimoCaso) {
        return new OutbreakAlert(
                null, zona, AlertSource.LABORATORIO, codigoPrueba, nombrePrueba,
                casos, umbral, ventanaDias,
                RiskLevel.evaluar(casos, umbral, casosCriticos),
                AlertStatus.ABIERTA,
                casosCriticos,
                dnis == null ? List.of() : List.copyOf(dnis),
                primerCaso, ultimoCaso,
                null, null, null,
                Instant.now(), Instant.now());
    }

    /**
     * Abre una alerta de la vigilancia clinica, por codigo CIE-10.
     *
     * <p>Los casos que llegan aqui son pacientes DISTINTOS, no diagnosticos:
     * un paciente que vuelve a consulta por lo mismo genera otro diagnostico,
     * y contarlo dos veces inventaria la mitad de un brote.
     *
     * @param notificables casos de notificacion obligatoria al MINSA entre los
     *                     contados; suben el nivel de riesgo igual que los
     *                     valores criticos en la vigilancia de laboratorio
     */
    public static OutbreakAlert abrirPorDiagnostico(
            String zona, String codigoCie10, String descripcionCie10,
            int casos, int notificables, int umbral, int ventanaDias,
            List<String> dnis, LocalDate primerCaso, LocalDate ultimoCaso) {

        return new OutbreakAlert(
                null, zona, AlertSource.DIAGNOSTICO, codigoCie10, descripcionCie10,
                casos, umbral, ventanaDias,
                RiskLevel.evaluar(casos, umbral, notificables),
                AlertStatus.ABIERTA,
                notificables,
                dnis == null ? List.of() : List.copyOf(dnis),
                primerCaso, ultimoCaso,
                null, null, null,
                Instant.now(), Instant.now());
    }

    /**
     * La fuente, tratando como LABORATORIO las alertas guardadas antes de que
     * existiera la vigilancia clinica.
     *
     * <p>Es lo que eran: cuando se guardaron, la unica vigilancia que habia
     * era la del laboratorio.
     */
    public AlertSource fuenteSegura() {
        return fuente == null ? AlertSource.LABORATORIO : fuente;
    }

    /** Una alerta de la vigilancia clinica, por codigo CIE-10. */
    public boolean esClinica() {
        return fuenteSegura() == AlertSource.DIAGNOSTICO;
    }

    /**
     * Actualiza las cifras de una alerta que sigue abierta.
     *
     * <p>Conserva la fecha de deteccion original: lo que interesa saber es
     * cuando se detecto el brote, no cuando se recalculo por ultima vez.
     */
    public OutbreakAlert actualizar(int casos, int casosCriticos, List<String> dnis,
                                    LocalDate primerCaso, LocalDate ultimoCaso) {
        return new OutbreakAlert(
                id, zona, fuenteSegura(), codigoPrueba, nombrePrueba,
                casos, umbral, ventanaDias,
                RiskLevel.evaluar(casos, umbral, casosCriticos),
                estado,
                casosCriticos,
                dnis == null ? List.of() : List.copyOf(dnis),
                primerCaso, ultimoCaso,
                resueltaPor, motivoResolucion, fechaResolucion,
                fechaDeteccion, Instant.now());
    }

    /**
     * Cierra la alerta.
     *
     * <p>El estado y el motivo se guardan juntos porque una alerta cerrada sin
     * explicacion no sirve para revisar despues si el umbral estaba bien
     * puesto.
     */
    public OutbreakAlert resolver(AlertStatus desenlace, String usuario, String motivo) {
        return new OutbreakAlert(
                id, zona, fuenteSegura(), codigoPrueba, nombrePrueba,
                casos, umbral, ventanaDias,
                nivelRiesgo,
                desenlace,
                casosCriticos,
                dnisAfectados,
                primerCaso, ultimoCaso,
                usuario, motivo, Instant.now(),
                fechaDeteccion, Instant.now());
    }

    public boolean estaAbierta() {
        return estado == AlertStatus.ABIERTA;
    }

    /** Cuantos casos por encima del umbral, para ordenar por gravedad. */
    public int exceso() {
        return Math.max(0, casos - umbral);
    }
}
