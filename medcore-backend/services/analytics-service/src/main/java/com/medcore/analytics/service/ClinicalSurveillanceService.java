package com.medcore.analytics.service;

import com.medcore.analytics.domain.document.DiagnosisRecord;
import com.medcore.analytics.repository.projection.ConteoPorClave;
import com.medcore.analytics.repository.projection.ConteoPorFecha;
import com.medcore.commons.event.DiagnosisEvent;

import java.time.LocalDate;
import java.util.List;

/**
 * Vigilancia por codigo CIE-10, sobre los diagnosticos del medico.
 *
 * <p>Es la mitad que faltaba. Hasta ahora la vigilancia solo veia lo que pasaba
 * por el laboratorio, asi que un brote de una enfermedad que se diagnostica por
 * clinica y no por analitica era invisible.
 *
 * <p>Las dos fuentes no se suman: el mismo paciente puede tener un resultado
 * anormal y un diagnostico del mismo episodio, y sumarlos contaria dos veces un
 * solo caso.
 */
public interface ClinicalSurveillanceService {

    /**
     * Guarda un diagnostico para la vigilancia.
     *
     * <p>Es idempotente por el id del diagnostico: Kafka entrega al menos una
     * vez, y sin eso un reenvio contaria el mismo caso dos veces y podria
     * disparar un brote que no existe.
     *
     * @return el registro guardado, o el que ya existia si el evento se repitio
     */
    DiagnosisRecord registrar(DiagnosisEvent evento);

    /** Los codigos mas diagnosticados del periodo: la morbilidad del centro. */
    List<ConteoPorClave> morbilidad(LocalDate desde, LocalDate hasta);

    /**
     * Casos por grupo de edad.
     *
     * <p>Distingue una gripe estacional de algo que hay que mirar: la misma
     * cifra concentrada en menores de cinco anios no significa lo mismo que
     * repartida entre adultos.
     */
    List<ConteoPorClave> porGrupoDeEdad(LocalDate desde, LocalDate hasta);

    /** Casos por zona, para el mapa de calor clinico. */
    List<ConteoPorClave> porZona(LocalDate desde, LocalDate hasta);

    /** Casos por dia, para la curva epidemica. */
    List<ConteoPorFecha> curvaEpidemica(LocalDate desde, LocalDate hasta);

    /**
     * Enfermedades de notificacion obligatoria del periodo.
     *
     * <p>Es la lista que hay que mandar al MINSA. Exigen aviso con un solo
     * caso, sin esperar a que se acumule ningun umbral, asi que esta consulta
     * no filtra por numero de casos.
     */
    List<DiagnosisRecord> notificables(LocalDate desde, LocalDate hasta);

    /** Los casos de una zona y un codigo, para revisar un brote detectado. */
    List<DiagnosisRecord> casosDe(String zona, String codigoCie10,
                                  LocalDate desde, LocalDate hasta);

    /** Las zonas con algun diagnostico, para los selectores del panel. */
    List<String> zonas();

    /** Cifras del periodo: cuantos diagnosticos y cuantos notificables. */
    Resumen resumen(LocalDate desde, LocalDate hasta);

    record Resumen(long diagnosticos, long notificables) {
    }
}
