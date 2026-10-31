package com.medcore.commons.exception;

import com.medcore.commons.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que cada clase de fallo sale con su codigo HTTP.
 *
 * <p>Existe por un fallo real: una URL que no existia caia en el manejador
 * generico y el cliente recibia un 500 con "ocurrio un error inesperado". El
 * frontend no podia distinguir "no existe" de "se rompio", y cada peticion a
 * una ruta equivocada dejaba una traza en ERROR, que es justo lo que esconde
 * los errores de verdad.
 *
 * <p>Lo encontro el recorrido clinico de punta a punta, pidiendo una ruta que
 * el script se habia inventado.
 */
class BaseGlobalExceptionHandlerTest {

    /**
     * La clase base es abstracta: cada servicio la extiende para anadir sus
     * propias excepciones de dominio. Aqui se extiende sin anadir nada, para
     * probar solo el comportamiento comun.
     */
    private static final class HandlerDePrueba extends BaseGlobalExceptionHandler {
    }

    private final BaseGlobalExceptionHandler handler = new HandlerDePrueba();

    @Test
    @DisplayName("Una URL inexistente da 404, no 500")
    void urlInexistenteDa404() throws Exception {
        var request = peticion("GET", "/api/schedules/2/slots");

        // La que lanza Spring Boot 4 por defecto: el manejador de recursos
        // estaticos, no el de controladores.
        ResponseEntity<ApiResponse<Void>> respuesta = handler.manejarNoEncontrado(
                new NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET,
                        "api/schedules/2/slots", "api/schedules/2/slots"),
                request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().success()).isFalse();
        assertThat(respuesta.getBody().error().codigo()).isEqualTo("RECURSO_NO_ENCONTRADO");
    }

    @Test
    @DisplayName("La excepcion antigua de Spring tambien da 404")
    void laExcepcionAntiguaTambienDa404() {
        // Spring la lanza solo si se configura
        // throw-exception-if-no-handler-found, y se siguen manejando las dos
        // porque cual llega depende de esa configuracion.
        ResponseEntity<ApiResponse<Void>> respuesta = handler.manejarNoEncontrado(
                new NoHandlerFoundException("GET", "/api/nada",
                        new org.springframework.http.HttpHeaders()),
                peticion("GET", "/api/nada"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Un metodo no admitido da 405, no 500")
    void metodoNoAdmitidoDa405() {
        var request = peticion("POST", "/api/patients/1");

        // El recurso existe; lo que no se puede es hacerle un POST.
        ResponseEntity<ApiResponse<Void>> respuesta = handler.manejarMetodoNoAdmitido(
                new HttpRequestMethodNotSupportedException("POST", List.of("GET", "PUT")),
                request);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(respuesta.getBody().error().codigo()).isEqualTo("METODO_NO_ADMITIDO");
        assertThat(respuesta.getBody().message()).contains("POST");
    }

    @Test
    @DisplayName("La respuesta lleva la ruta y el requestId para poder rastrearla")
    void laRespuestaLlevaRutaYRequestId() {
        var request = peticion("GET", "/api/nada");
        request.addHeader("X-Request-Id", "req-abc-123");

        var respuesta = handler.manejarNoEncontrado(
                new NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "api/nada", "api/nada"),
                request);

        var error = respuesta.getBody().error();
        assertThat(error.ruta()).isEqualTo("/api/nada");
        // Sin el requestId no se puede cruzar la respuesta con el log del
        // servicio que la produjo.
        assertThat(error.requestId()).isEqualTo("req-abc-123");
    }

    @Test
    @DisplayName("Un fallo de verdad sigue dando 500 con su mensaje generico")
    void falloDeVerdadDa500() {
        var respuesta = handler.manejarInesperado(
                new IllegalStateException("se rompio algo de dentro"),
                peticion("GET", "/api/patients"));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        // Al cliente no se le cuenta que fallo por dentro, solo el codigo con
        // el que puede reportarlo.
        assertThat(respuesta.getBody().message())
                .doesNotContain("se rompio algo de dentro")
                .contains("Reporta el codigo");
    }

    @Test
    @DisplayName("Las dos excepciones de ruta inexistente estan declaradas juntas")
    void lasDosExcepcionesEstanDeclaradas() throws Exception {
        var metodo = BaseGlobalExceptionHandler.class.getMethod(
                "manejarNoEncontrado", Exception.class,
                jakarta.servlet.http.HttpServletRequest.class);

        var anotacion = metodo.getAnnotation(
                org.springframework.web.bind.annotation.ExceptionHandler.class);

        // Si alguien quita NoResourceFoundException, las URL inexistentes
        // vuelven a salir como 500 y nada falla al compilar.
        assertThat(Arrays.asList(anotacion.value()))
                .containsExactlyInAnyOrder(
                        NoHandlerFoundException.class, NoResourceFoundException.class);
    }

    private MockHttpServletRequest peticion(String metodo, String ruta) {
        var request = new MockHttpServletRequest(metodo, ruta);
        request.setRequestURI(ruta);
        return request;
    }
}
