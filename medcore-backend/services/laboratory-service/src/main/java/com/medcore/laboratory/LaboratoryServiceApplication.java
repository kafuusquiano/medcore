package com.medcore.laboratory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * laboratory-service (:8085): muestras, resultados e interfaz HL7 con los
 * equipos.
 *
 * <p>Usa dos bases de datos a proposito (MC1). En lab_db, que es Postgres,
 * van las muestras y los resultados ya interpretados, que tienen forma fija y
 * se consultan de forma relacional. En MongoDB va la trama cruda que emitio
 * el equipo, cuya estructura cambia con cada fabricante: guardarla en
 * columnas obligaria a una migracion por cada analizador nuevo.
 *
 * <p>Consume {@code lab-order.created} para saber que muestras espera, y
 * publica {@code sample.registered}, {@code result.registered} y
 * {@code result.critical}.
 */
@SpringBootApplication
@EnableFeignClients
public class LaboratoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LaboratoryServiceApplication.class, args);
    }
}
