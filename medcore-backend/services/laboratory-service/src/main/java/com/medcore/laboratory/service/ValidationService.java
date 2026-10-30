package com.medcore.laboratory.service;

import com.medcore.laboratory.domain.enums.ResultInterpretation;

/**
 * Interpreta un valor medido frente a los rangos del catalogo.
 *
 * <p>Es el nucleo clinico del servicio: decide si un numero es normal,
 * anormal o critico. Se separa en su propio servicio porque esa decision se
 * toma en dos caminos distintos, la entrada manual y la trama del equipo, y
 * en ambos debe dar el mismo resultado.
 */
public interface ValidationService {

    /**
     * Interpretacion de un valor.
     *
     * <p>El orden de comprobacion importa: primero los limites criticos y
     * despues el rango de referencia. Un valor critico siempre esta tambien
     * fuera del rango, y lo que debe informarse es lo mas grave.
     *
     * @param sexo y {@code edad} pueden ser nulos si patient-service no
     *             respondio. Entonces se aplica el rango generico, que es
     *             menos preciso pero utilizable.
     */
    Interpretacion interpretar(String codigoPrueba, Double valor, String sexo, Integer edad);

    /**
     * Interpretacion de un flag que ya envio el equipo.
     *
     * <p>Se usa cuando el analizador informa el campo OBX-8. Aun asi se
     * consultan los limites criticos propios: el flag del equipo esta
     * calculado con los rangos que tenga configurados de fabrica, que no son
     * necesariamente los del laboratorio.
     */
    Interpretacion interpretarConFlag(String codigoPrueba, Double valor, String flagHl7,
                                      String sexo, Integer edad);

    /**
     * Resultado de interpretar un valor.
     *
     * @param rangoReferencia etiqueta del rango aplicado, por ejemplo
     *                        "13.5 - 17.5 g/dL". Se copia en el resultado
     *                        porque los rangos del catalogo pueden cambiar y
     *                        el informe debe conservar contra que se
     *                        interpreto. Es nulo si no habia rango aplicable.
     */
    record Interpretacion(ResultInterpretation interpretacion, String rangoReferencia) {

        public static Interpretacion sinRango() {
            return new Interpretacion(ResultInterpretation.NO_APLICA, null);
        }

        public boolean esCritica() {
            return interpretacion.esCritico();
        }
    }
}
