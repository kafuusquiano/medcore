package com.medcore.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Deja que el navegador guarde los CATALOGOS, y solo los catalogos.
 *
 * <p>Los servicios ponen {@code Cache-Control: no-cache, no-store} en todas
 * sus respuestas, porque lo pone Spring Security. Para los datos de un
 * paciente eso es exactamente lo que hay que hacer: una analitica no debe
 * quedarse en la cache del navegador de un ordenador compartido ni en un
 * proxy intermedio.
 *
 * <p>Pero el catalogo de pruebas, las areas del laboratorio y el catalogo
 * CIE-10 no son datos de nadie: son una tabla de referencia que cambia dos
 * veces al anio, y el frontend los pide en casi cada pantalla. Por un tunel,
 * donde la red cuesta mas que la consulta, repetir esa llamada se nota.
 *
 * <p><b>Vive en el gateway y no en cada servicio.</b> El primer intento fue un
 * filtro en medcore-commons que modificaba la cadena de Spring Security de
 * cada servicio para que no escribiera el {@code no-store} en esas rutas. Y
 * funciono al reves de lo esperado: el escritor global se desactivaba, el que
 * debia reponerlo para el resto no se aplicaba, y
 * {@code /api/lab-queue} y {@code /api/samples} se quedaron SIN
 * {@code no-store}. Datos clinicos cacheables, que es justo lo que no puede
 * pasar.
 *
 * <p>Aqui el reparto de responsabilidades es el seguro: los servicios siguen
 * marcando todo como no cacheable, sin excepciones y sin tocar su seguridad,
 * y el gateway levanta esa marca en una lista corta de rutas. Si este filtro
 * falla o se queda corto, el peor caso es que los catalogos no se cacheen,
 * que cuesta unos milisegundos. Al reves, el peor caso era una analitica en
 * la cache de la sala de espera.
 *
 * <p>Solo afecta a GET con respuesta 200.
 */
@Component
public class ReferenceDataCacheFilter implements GlobalFilter, Ordered {

    private static final Logger log =
            LoggerFactory.getLogger(ReferenceDataCacheFilter.class);

    /**
     * Rutas de datos de referencia, sin informacion de ningun paciente.
     *
     * <p>Antes de anadir una ruta aqui hay que responder a una pregunta: si
     * dos personas distintas la piden, reciben lo mismo? Si la respuesta
     * depende de quien pregunta, no va en esta lista.
     *
     * <p>Corta a proposito. Un patron amplio como {@code /api/**} dejaria
     * cacheables los datos clinicos, y hay una prueba que falla si alguien lo
     * mete.
     */
    private static final List<String> CACHEABLES = List.of(
            "/api/lab-catalog",
            "/api/lab-catalog/areas",
            "/api/lab-catalog/*",
            "/api/diagnoses/cie10",
            "/api/diagnoses/cie10/*");

    /**
     * Diez minutos.
     *
     * <p>Corto a proposito. Un catalogo cambia cuando el laboratorio incorpora
     * una prueba, y entonces conviene que el cambio se vea en la siguiente
     * pantalla, no al dia siguiente. Diez minutos ahorran casi todas las
     * llamadas repetidas de una sesion de trabajo.
     */
    private static final long SEGUNDOS = 600;

    /**
     * {@code private} y no {@code public}.
     *
     * <p>La respuesta la guarda el navegador de quien pregunta, no un proxy
     * compartido: viaja con un token en la cabecera, y una respuesta a una
     * peticion autenticada no debe quedarse en una cache intermedia.
     */
    private static final String VALOR =
            "private, max-age=" + SEGUNDOS + ", must-revalidate";

    private final AntPathMatcher matcher = new AntPathMatcher();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!esCacheable(exchange)) {
            return chain.filter(exchange);
        }

        // beforeCommit, no antes de la cadena: la respuesta del servicio
        // todavia no ha llegado, y es ella la que trae el no-store que hay
        // que levantar. Aqui se ejecuta justo antes de enviar al cliente, con
        // las cabeceras del servicio ya puestas y todavia modificables.
        exchange.getResponse().beforeCommit(() -> {
            var respuesta = exchange.getResponse();

            if (respuesta.getStatusCode() != HttpStatus.OK) {
                // Un 404 o un 500 se dejan como vinieron: cachear un error
                // diez minutos deja al cliente viendolo despues de que se
                // arregle.
                return Mono.empty();
            }

            HttpHeaders cabeceras = respuesta.getHeaders();
            cabeceras.set(HttpHeaders.CACHE_CONTROL, VALOR);
            cabeceras.remove(HttpHeaders.PRAGMA);

            // Vary por Authorization: deja constancia de que la respuesta
            // depende de la cabecera de autenticacion, de modo que ninguna
            // cache la reutilice para otro usuario.
            cabeceras.add(HttpHeaders.VARY, HttpHeaders.AUTHORIZATION);

            if (log.isTraceEnabled()) {
                log.trace("Cacheable {}s: {}", SEGUNDOS,
                        exchange.getRequest().getPath().value());
            }

            return Mono.empty();
        });

        return chain.filter(exchange);
    }

    private boolean esCacheable(ServerWebExchange exchange) {
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
            return false;
        }

        String ruta = exchange.getRequest().getPath().value();
        return CACHEABLES.stream().anyMatch(patron -> matcher.match(patron, ruta));
    }

    /**
     * Despues del enrutado y de la autenticacion.
     *
     * <p>El orden no cambia lo que hace, porque el trabajo va en
     * {@code beforeCommit}, pero asi no se ejecuta para una peticion que el
     * filtro de JWT va a rechazar.
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** Las rutas que este filtro deja cachear. Para poder probarlas. */
    public static List<String> rutasCacheables() {
        return CACHEABLES;
    }

    /** El valor que escribe en Cache-Control. Para poder probarlo. */
    public static String valorCacheControl() {
        return VALOR;
    }
}
