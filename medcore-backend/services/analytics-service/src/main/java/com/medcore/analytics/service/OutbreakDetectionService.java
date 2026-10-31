package com.medcore.analytics.service;

import com.medcore.analytics.domain.document.OutbreakAlert;
import com.medcore.analytics.domain.enums.AlertStatus;
import com.medcore.analytics.domain.enums.RiskLevel;

import java.util.List;

/**
 * Deteccion de brotes.
 *
 * <p>La regla es deliberadamente simple: si los casos de una misma prueba
 * anormal en una zona superan el umbral dentro de la ventana, se abre una
 * alerta. No pretende ser un modelo estadistico, sino una senal que haga que
 * una persona mire los datos; afinar el umbral con el uso es parte del
 * procedimiento, y por eso vive en la configuracion.
 *
 * <p>Se cuentan PACIENTES DISTINTOS y no resultados: a una persona a la que se
 * repite la prueba tres veces en la semana no se la puede contar como tres
 * casos, o cualquier seguimiento pareceria un brote.
 */
public interface OutbreakDetectionService {

    /**
     * Barre todas las zonas y pruebas y abre o actualiza las alertas.
     *
     * <p>Lo llama el scheduler y tambien el consumidor, tras registrar un caso
     * anormal: un brote detectado doce horas despues sirve de poco.
     *
     * @return las alertas abiertas o actualizadas en este barrido
     */
    List<OutbreakAlert> detectar(String requestId);

    /**
     * Evalua solo una zona y una prueba.
     *
     * <p>Es lo que usa el consumidor al registrar un caso: barrer el almacen
     * entero por cada resultado seria desproporcionado.
     */
    List<OutbreakAlert> evaluar(String zona, String codigoPrueba, String requestId);

    /**
     * Cierra una alerta como CONFIRMADA o DESCARTADA.
     *
     * <p>El motivo es obligatorio: una alerta cerrada sin explicacion no sirve
     * para revisar despues si el umbral estaba bien puesto.
     */
    /**
     * Barrido de brotes por codigo CIE-10, sobre los diagnosticos.
     *
     * <p>Es la otra mitad de la vigilancia: {@link #detectar} solo ve lo que
     * pasa por el laboratorio, asi que una enfermedad que se diagnostica por
     * clinica y no por analitica no aparecia por ningun sitio.
     *
     * <p>Las alertas van a la misma coleccion y se distinguen por su fuente:
     * un epidemiologo quiere ver las dos juntas, no en dos pantallas.
     */
    List<OutbreakAlert> detectarClinicos(String requestId);

    /**
     * Evalua una zona y un codigo CIE-10 concretos.
     *
     * <p>Se llama al registrar cada diagnostico, en lugar de esperar al
     * barrido: un brote detectado doce horas despues sirve de poco.
     */
    List<OutbreakAlert> evaluarDiagnostico(String zona, String codigoCie10,
                                           String requestId);

    OutbreakAlert resolver(String id, AlertStatus desenlace, String usuario, String motivo);

    List<OutbreakAlert> alertasAbiertas();

    List<OutbreakAlert> alertasPorNivel(RiskLevel nivel);

    List<OutbreakAlert> alertasDeZona(String zona);

    OutbreakAlert obtenerPorId(String id);

    /** Cuantas alertas hay abiertas y de que gravedad, para el panel. */
    Resumen resumen();

    record Resumen(long abiertas, long riesgoAlto, long riesgoMedio, long riesgoBajo) {
    }
}
