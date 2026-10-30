package com.medcore.analytics.domain.document;

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
                null, zona, codigoPrueba, nombrePrueba,
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
     * Actualiza las cifras de una alerta que sigue abierta.
     *
     * <p>Conserva la fecha de deteccion original: lo que interesa saber es
     * cuando se detecto el brote, no cuando se recalculo por ultima vez.
     */
    public OutbreakAlert actualizar(int casos, int casosCriticos, List<String> dnis,
                                    LocalDate primerCaso, LocalDate ultimoCaso) {
        return new OutbreakAlert(
                id, zona, codigoPrueba, nombrePrueba,
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
                id, zona, codigoPrueba, nombrePrueba,
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
