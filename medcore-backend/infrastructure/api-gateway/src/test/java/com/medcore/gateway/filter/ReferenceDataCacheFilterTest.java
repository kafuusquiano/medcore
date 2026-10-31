package com.medcore.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.util.AntPathMatcher;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que la cache alcanza a los catalogos y NO a los datos clinicos.
 *
 * <p>Es la prueba que de verdad importa de este filtro, y existe por un error
 * concreto: la primera version modificaba la cadena de Spring Security de cada
 * servicio, y el resultado fue que {@code /api/lab-queue} y
 * {@code /api/samples} se quedaron sin {@code no-store}. Datos clinicos
 * cacheables.
 */
class ReferenceDataCacheFilterTest {

    private final ReferenceDataCacheFilter filtro = new ReferenceDataCacheFilter();
    private final AntPathMatcher matcher = new AntPathMatcher();

    @Test
    @DisplayName("El catalogo de pruebas se puede cachear")
    void elCatalogoSeCachea() {
        var cabeceras = pasar("GET", "/api/lab-catalog", HttpStatus.OK);

        assertThat(cabeceras.getFirst(HttpHeaders.CACHE_CONTROL))
                .contains("max-age=600")
                .contains("private");
    }

    @Test
    @DisplayName("Las areas, una prueba concreta y el CIE-10 tambien")
    void lasDemasRutasDeCatalogo() {
        for (String ruta : new String[]{
                "/api/lab-catalog/areas",
                "/api/lab-catalog/HEM-001",
                "/api/diagnoses/cie10",
                "/api/diagnoses/cie10/A09"}) {

            assertThat(pasar("GET", ruta, HttpStatus.OK)
                    .getFirst(HttpHeaders.CACHE_CONTROL))
                    .as("la ruta %s deberia ser cacheable", ruta)
                    .contains("max-age");
        }
    }

    @Test
    @DisplayName("El no-store de los datos clinicos se queda como vino")
    void elNoStoreClinicoSeQueda() {
        // Lo esencial. El servicio pone no-store y el gateway NO lo levanta:
        // una analitica no debe quedarse en la cache del navegador de un
        // ordenador compartido.
        for (String ruta : new String[]{
                "/api/patients/4",
                "/api/patients",
                "/api/samples",
                "/api/lab-queue",
                "/api/results/muestra/70",
                "/api/medical/attentions/5",
                "/api/notifications/paciente/4",
                "/api/reports"}) {

            var cabeceras = pasar("GET", ruta, HttpStatus.OK);

            assertThat(cabeceras.getFirst(HttpHeaders.CACHE_CONTROL))
                    .as("la ruta clinica %s debe conservar su no-store", ruta)
                    .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
        }
    }

    @Test
    @DisplayName("Un POST no se cachea, aunque sea a una ruta de catalogo")
    void elPostNoSeCachea() {
        assertThat(pasar("POST", "/api/lab-catalog", HttpStatus.OK)
                .getFirst(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("no-cache, no-store, max-age=0, must-revalidate");
    }

    @Test
    @DisplayName("Un error no se cachea")
    void elErrorNoSeCachea() {
        for (HttpStatus estado : new HttpStatus[]{
                HttpStatus.NOT_FOUND, HttpStatus.CONFLICT,
                HttpStatus.INTERNAL_SERVER_ERROR}) {

            assertThat(pasar("GET", "/api/lab-catalog", estado)
                    .getFirst(HttpHeaders.CACHE_CONTROL))
                    .as("un %s no debe quedar cacheado", estado)
                    .doesNotContain("max-age=600");
        }
    }

    @Test
    @DisplayName("Declara Vary por Authorization")
    void declaraVaryPorAuthorization() {
        assertThat(pasar("GET", "/api/lab-catalog", HttpStatus.OK)
                .get(HttpHeaders.VARY))
                .contains(HttpHeaders.AUTHORIZATION);
    }

    @Test
    @DisplayName("La cache es privada, no de proxies compartidos")
    void laCacheEsPrivada() {
        assertThat(ReferenceDataCacheFilter.valorCacheControl())
                .startsWith("private")
                .doesNotContain("public");
    }

    @Test
    @DisplayName("Ninguna ruta de datos clinicos esta en la lista de cacheables")
    void ningunaRutaClinicaEstaEnLaLista() {
        var cacheables = ReferenceDataCacheFilter.rutasCacheables();

        assertThat(cacheables).hasSizeLessThan(10);

        // Un /api/** o un /** dejaria cacheable todo el sistema. Esta
        // comprobacion existe para que anadirlo falle aqui y no en produccion.
        assertThat(cacheables).noneMatch(r -> r.equals("/api/**")
                || r.equals("/**") || r.equals("/api/*"));

        for (String clinica : new String[]{
                "/api/patients/4", "/api/samples/70", "/api/results/muestra/70",
                "/api/medical/attentions/5", "/api/lab-queue", "/api/reports",
                "/api/notifications/paciente/4", "/api/appointments/1"}) {

            assertThat(cacheables)
                    .as("la ruta clinica %s no debe coincidir con ningun patron",
                            clinica)
                    .noneMatch(patron -> matcher.match(patron, clinica));
        }
    }

    /**
     * Pasa una peticion por el filtro simulando lo que hace el gateway: el
     * servicio responde con su no-store y el filtro decide si lo levanta.
     */
    private HttpHeaders pasar(String metodo, String ruta, HttpStatus estado) {
        var request = MockServerHttpRequest
                .method(org.springframework.http.HttpMethod.valueOf(metodo), ruta)
                .build();
        var exchange = MockServerWebExchange.from(request);

        // Lo que pone Spring Security en cada servicio.
        exchange.getResponse().getHeaders().set(HttpHeaders.CACHE_CONTROL,
                "no-cache, no-store, max-age=0, must-revalidate");
        exchange.getResponse().setStatusCode(estado);

        filtro.filter(exchange, intercambio -> Mono.empty()).block();

        // Fuerza el beforeCommit, que es donde el filtro trabaja.
        exchange.getResponse().setComplete().block();

        return exchange.getResponse().getHeaders();
    }
}
