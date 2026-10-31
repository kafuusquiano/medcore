package com.medcore.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Rutas de {@code /api/**} hacia cada microservicio.
 *
 * <p>Se declaran de forma explicita en lugar de dejar que el
 * {@code DiscoveryClient} las genere desde Eureka: asi el gateway solo expone
 * los prefijos previstos y no publica por accidente un servicio nuevo que
 * alguien registre.
 *
 * <p>{@code lb://} delega el balanceo en Spring Cloud LoadBalancer, que
 * resuelve el nombre contra el registro de Eureka.
 */
@Configuration
public class RouteConfig {

    @Bean
    public RouteLocator rutas(RouteLocatorBuilder builder) {
        return builder.routes()

                .route("auth-service", r -> r
                        .path("/api/auth/**", "/api/users/**")
                        .uri("lb://auth-service"))

                .route("patient-service", r -> r
                        .path("/api/patients/**")
                        .uri("lb://patient-service"))

                .route("appointment-service", r -> r
                        .path("/api/appointments/**", "/api/schedules/**")
                        .uri("lb://appointment-service"))

                .route("medical-service", r -> r
                        .path("/api/medical/**", "/api/diagnoses/**", "/api/lab-orders/**")
                        .uri("lb://medical-service"))

                // Las rutas tienen que coincidir con el @RequestMapping de cada
                // controlador. Una ruta que ningun controlador atiende no da
                // error al arrancar: el gateway la acepta, enruta, y el
                // servicio responde 404 como si el endpoint no existiera.
                //
                // /api/hl7 NO se publica aqui a proposito: los analizadores
                // escriben al puerto del servicio y no saben enviar un JWT, asi
                // que esa ruta se protege por red y nunca por el gateway.
                .route("laboratory-service", r -> r
                        .path("/api/lab-catalog/**", "/api/lab-queue/**",
                                "/api/samples/**", "/api/results/**")
                        .uri("lb://laboratory-service"))

                .route("inventory-service", r -> r
                        .path("/api/reagents/**", "/api/stock/**",
                                "/api/movements/**")
                        .uri("lb://inventory-service"))

                .route("analytics-service", r -> r
                        .path("/api/reports/**", "/api/trends/**",
                                "/api/epidemiology/**", "/api/surveillance/**")
                        .uri("lb://analytics-service"))

                .route("notification-service", r -> r
                        .path("/api/notifications/**")
                        .uri("lb://notification-service"))

                .build();
    }
}
