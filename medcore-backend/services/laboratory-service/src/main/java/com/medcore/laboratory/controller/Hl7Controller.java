package com.medcore.laboratory.controller;

import com.medcore.commons.security.GatewayHeaders;
import com.medcore.laboratory.domain.dto.response.ResultResponse;
import com.medcore.laboratory.exception.Hl7ParseException;
import com.medcore.laboratory.exception.LaboratoryException;
import com.medcore.laboratory.hl7.adapter.EquipmentAdapter;
import com.medcore.laboratory.hl7.builder.AckBuilder;
import com.medcore.laboratory.hl7.mapper.Hl7ResultMapper;
import com.medcore.laboratory.hl7.parser.Hl7ParsedResult;
import com.medcore.laboratory.service.ResultService;
import com.medcore.laboratory.service.SampleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Interfaz HL7 con los equipos de laboratorio (regla MC6).
 *
 * <p>Es el unico controller que no responde JSON ni envuelve el resultado en
 * ApiResponse: devuelve un ACK en texto plano, que es lo que el analizador
 * sabe leer. Tampoco devuelve un error HTTP ante una trama ilegible, porque
 * un equipo que no recibe ACK reintenta y acaba bloqueando su cola de salida.
 * Un fallo se responde con 200 y un ACK con codigo AE o AR en el segmento
 * MSA, y queda en el log de MedCore.
 *
 * <p>Sobre la autenticacion: los analizadores no saben enviar un JWT. La ruta
 * {@code /api/hl7/oru} se declara publica en SecurityConfig y debe protegerse
 * por red, no por token: en produccion solo la VLAN del laboratorio alcanza
 * el puerto del servicio, y el gateway no expone esta ruta al exterior.
 */
@Tag(name = "HL7", description = "Recepcion de resultados desde los equipos")
@RestController
@RequestMapping("/api/hl7")
public class Hl7Controller {

    private static final Logger log = LoggerFactory.getLogger(Hl7Controller.class);

    private final EquipmentAdapter equipmentAdapter;
    private final AckBuilder ackBuilder;
    private final ResultService resultService;
    private final SampleService sampleService;
    private final Hl7ResultMapper hl7ResultMapper;

    public Hl7Controller(EquipmentAdapter equipmentAdapter,
                         AckBuilder ackBuilder,
                         ResultService resultService,
                         SampleService sampleService,
                         Hl7ResultMapper hl7ResultMapper) {
        this.equipmentAdapter = equipmentAdapter;
        this.ackBuilder = ackBuilder;
        this.resultService = resultService;
        this.sampleService = sampleService;
        this.hl7ResultMapper = hl7ResultMapper;
    }

    @Operation(summary = "Recibe una trama de resultados de un equipo",
            description = """
                    Acepta HL7 ORU^R01 y ASTM E1394; el formato se detecta por
                    el primer segmento de la trama. Responde siempre un ACK en
                    texto plano, con AA si se proceso, AE si no se pudo
                    interpretar y AR si la muestra no existe. Nunca devuelve un
                    error HTTP: un equipo sin ACK reintenta indefinidamente.
                    """)
    @PostMapping(
            value = "/oru",
            consumes = {MediaType.TEXT_PLAIN_VALUE, "application/hl7-v2", MediaType.ALL_VALUE},
            produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> recibirOru(
            @RequestBody String trama,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        // El control id y el equipo se necesitan para armar el ACK, pero solo
        // se conocen si la trama se pudo parsear. Mientras no sea asi se
        // responde con los valores de reserva del AckBuilder.
        String mensajeId = null;
        String equipo = null;

        try {
            Hl7ParsedResult parseado = equipmentAdapter.interpretar(trama);
            mensajeId = parseado.mensajeId();
            equipo = parseado.equipo();

            List<ResultResponse> registrados =
                    resultService.registrarDesdeEquipo(parseado, trama, requestId);

            log.info("Trama de {} procesada: {} resultados en la muestra {} [{}]",
                    equipo, registrados.size(), parseado.codigoMuestra(), requestId);

            return ResponseEntity.ok(ackBuilder.aceptado(mensajeId, equipo));

        } catch (Hl7ParseException e) {
            // La trama no se entiende. El equipo puede reintentar, pero
            // enviando lo mismo obtendra el mismo resultado; aun asi se
            // responde AE y no AR, porque el fallo puede estar en una trama
            // truncada por la red y el reenvio si tiene sentido.
            log.warn("Trama ilegible [{}]: {}", requestId, e.getMessage());
            return ResponseEntity.ok(ackBuilder.error(mensajeId, equipo, e.getMessage()));

        } catch (LaboratoryException e) {
            // La trama es correcta pero algo del dominio no cuadra: la
            // muestra no existe o ya se cerro. Reintentar no lo va a
            // arreglar, de modo que se rechaza con AR.
            log.warn("Trama rechazada [{}]: {}", requestId, e.getMessage());
            return ResponseEntity.ok(ackBuilder.rechazado(mensajeId, equipo, e.getMessage()));

        } catch (RuntimeException e) {
            // Fallo inesperado de MedCore, no del equipo. Se responde AE para
            // que reintente: el problema puede ser transitorio, como la base
            // de datos momentaneamente inaccesible.
            log.error("Error procesando la trama [{}]: {}", requestId, e.getMessage(), e);
            return ResponseEntity.ok(ackBuilder.error(mensajeId, equipo,
                    "Error interno de MedCore; reintente"));
        }
    }

    @Operation(summary = "Exporta los resultados de una muestra como trama HL7",
            description = """
                    Camino inverso al de la recepcion: entrega un ORU^R01 para
                    un sistema externo que hable HL7, como el historico del
                    hospital o una aseguradora.
                    """)
    @GetMapping(value = "/oru/muestra/{codigo}", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<String> exportarOru(
            @PathVariable String codigo,
            @RequestParam(required = false) String destino) {

        var muestra = sampleService.buscarEntidadPorCodigo(codigo);

        // El mapper trabaja con entidades y no con DTOs porque necesita el
        // codigo HL7 de la interpretacion y la marca de correccion. Las pide
        // a traves del servicio, no de un repositorio: el acceso a datos se
        // queda en la capa de servicio (regla G5).
        var resultados = sampleService.resultadosDe(muestra.getId());

        log.debug("Exportando {} resultados de la muestra {} hacia {}",
                resultados.size(), codigo, destino);

        return ResponseEntity.ok(hl7ResultMapper.aOruR01(muestra, resultados, destino));
    }
}
