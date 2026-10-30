package com.medcore.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que el gateway publica las rutas que los servicios atienden.
 *
 * <p>Esta prueba existe por un fallo real: el gateway se escribio antes que los
 * servicios y declaraba rutas que ningun controlador atendia
 * ({@code /api/lab}, {@code /api/inventory}, {@code /api/analytics}) mientras
 * omitia seis que si existian. El sintoma era un 404 que parecia del servicio,
 * no del gateway, y no habia nada que fallara al arrancar: una ruta sin
 * controlador se enruta igual, y el servicio responde 404 como si el endpoint
 * no existiera.
 *
 * <p>Los prefijos de abajo se mantienen a mano a proposito. Un test que los
 * leyera del mismo sitio que el codigo no comprobaria nada.
 */
class RouteConfigTest {

    /**
     * Prefijo de cada servicio, tal como lo declara el @RequestMapping de sus
     * controladores.
     *
     * <p>{@code /api/hl7} NO esta aqui: los analizadores escriben al puerto del
     * servicio y no saben enviar un JWT, asi que esa ruta se protege por red y
     * nunca debe salir por el gateway. Tiene su propia prueba mas abajo.
     */
    private static final Map<String, List<String>> ESPERADAS = Map.of(
            "auth-service", List.of("/api/auth/login", "/api/users/1"),
            "patient-service", List.of("/api/patients/1"),
            "appointment-service", List.of("/api/appointments/1", "/api/schedules/1"),
            "medical-service", List.of("/api/medical/1", "/api/diagnoses/1",
                    "/api/lab-orders/1"),
            "laboratory-service", List.of("/api/lab-catalog/areas", "/api/lab-queue",
                    "/api/samples/1", "/api/results/1"),
            "inventory-service", List.of("/api/reagents/bajo-minimo", "/api/stock/1",
                    "/api/movements/1"),
            "analytics-service", List.of("/api/reports", "/api/trends/serie",
                    "/api/epidemiology/heatmap"),
            "notification-service", List.of("/api/notifications/resumen"));

    /**
     * El RouteLocator, construido con un contexto que tiene solo lo que el
     * builder usa.
     *
     * <p>No vale pasarle null: sus predicados piden beans al contexto y se
     * rompe con un NullPointer que no dice nada de las rutas. Tampoco vale
     * levantar GatewayAutoConfiguration, que arrastra el servidor web, el SSL y
     * media docena de beans mas que esta prueba no necesita.
     */
    private final RouteLocator rutas = construirRutas();

    private static RouteLocator construirRutas() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.registerBean(WebFluxProperties.class);
        ctx.registerBean(ConversionService.class,
                ApplicationConversionService::getSharedInstance);
        ctx.registerBean(ConfigurationService.class);
        ctx.registerBean(PathRoutePredicateFactory.class);
        ctx.refresh();

        return new RouteConfig().rutas(new RouteLocatorBuilder(ctx));
    }

    @Test
    @DisplayName("Cada ruta de un servicio llega a ese servicio")
    void cadaRutaLlegaASuServicio() {
        ESPERADAS.forEach((servicio, caminos) ->
                caminos.forEach(camino ->
                        assertThat(servicioQueAtiende(camino))
                                .as("la ruta %s deberia ir a %s", camino, servicio)
                                .isEqualTo(servicio)));
    }

    @Test
    @DisplayName("La interfaz HL7 no sale por el gateway")
    void laInterfazHl7NoSalePorElGateway() {
        // Si alguien la publica aqui, queda abierta sin token: el filtro del
        // gateway no puede exigir uno que los analizadores no envian.
        assertThat(servicioQueAtiende("/api/hl7/oru")).isNull();
    }

    /** El id de la ruta que atiende un camino, o null si ninguna lo hace. */
    private String servicioQueAtiende(String camino) {
        MockServerWebExchange intercambio = MockServerWebExchange.from(
                MockServerHttpRequest.get(camino).build());

        return rutas.getRoutes()
                .filterWhen(ruta -> ruta.getPredicate().apply(intercambio))
                .next()
                .map(Route::getId)
                .block();
    }
}
