package com.medcore.analytics;

import com.medcore.analytics.config.AnalyticsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * analytics-service (:8087): epidemiologia, deteccion de brotes e indicadores.
 *
 * <p>Solo MongoDB (MC1). No hay JPA ni Flyway: los datos son series y
 * agregados, no entidades relacionadas, y los indices los declara
 * {@code infra/mongo/init/01-init-collections.js}.
 *
 * <p>Consume {@code result.registered} y {@code result.critical}. Es su unica
 * entrada de datos: no consulta a laboratory-service ni comparte su base, asi
 * que lo que no llegue por esos topicos no existe para la vigilancia.
 *
 * <p>La vigilancia es por PRUEBA DE LABORATORIO con resultado anormal y no por
 * codigo CIE-10, porque medical-service todavia no publica eventos de
 * diagnostico. Cuando lo haga, bastara anadir el codigo al documento y una
 * segunda regla de deteccion.
 *
 * <p>@EnableScheduling por el barrido periodico de brotes y el archivado de
 * indicadores.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AnalyticsProperties.class)
public class AnalyticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
