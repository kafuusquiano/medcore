package com.medcore.laboratory.hl7.config;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.validation.impl.NoValidation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contexto de HAPI HL7 v2 (regla MC6).
 *
 * <p>La validacion estricta de HAPI se desactiva a proposito. Los equipos de
 * laboratorio reales envian tramas que incumplen el estandar en detalles
 * menores: campos opcionales vacios, fechas sin segundos, separadores de
 * sub-componente de mas. Con la validacion activa HAPI rechazaria el mensaje
 * entero y se perderia un resultado que es perfectamente legible.
 *
 * <p>La validacion propia la hace {@link com.medcore.laboratory.hl7.parser.Hl7MessageValidator},
 * que comprueba lo que de verdad necesita el proceso: que haya segmento MSH,
 * que el tipo de mensaje sea el esperado y que exista al menos un OBX con
 * valor.
 */
@Configuration
public class HapiConfig {

    /**
     * El contexto es caro de crear y seguro para usar desde varios hilos, de
     * modo que se comparte como singleton en lugar de construirlo por mensaje.
     */
    @Bean(destroyMethod = "close")
    public HapiContext hapiContext() {
        HapiContext context = new DefaultHapiContext();
        context.setValidationContext(new NoValidation());
        return context;
    }

    /**
     * Parser del formato de barras verticales, que es el que usan los equipos
     * por cable serie o por MLLP sobre TCP.
     */
    @Bean
    public PipeParser pipeParser(HapiContext context) {
        return context.getPipeParser();
    }
}
