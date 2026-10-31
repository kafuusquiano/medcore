package com.medcore.laboratory.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.laboratory.domain.dto.request.SampleRequest;
import com.medcore.laboratory.domain.dto.response.SampleResponse;
import com.medcore.laboratory.domain.enums.SampleStatus;
import com.medcore.laboratory.service.LabDocumentService;
import com.medcore.laboratory.service.SampleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Muestras del laboratorio.
 *
 * <p>Las gestiona el laboratorista. El medico solo consulta: necesita ver en
 * que punto esta el tubo de su paciente, pero no mueve su estado.
 */
@Tag(name = "Muestras", description = "Recepcion y procesamiento de muestras")
@RestController
@RequestMapping("/api/samples")
public class SampleController {

    private final SampleService sampleService;
    private final LabDocumentService labDocumentService;

    public SampleController(SampleService sampleService,
                            LabDocumentService labDocumentService) {
        this.labDocumentService = labDocumentService;
        this.sampleService = sampleService;
    }

    @Operation(summary = "Registra la muestra recibida para una orden",
            description = """
                    Toma los datos del paciente de la orden que dejo
                    lab-order.created, no de la peticion, para que el tubo no
                    pueda quedar a nombre de otra persona. Publica
                    sample.registered y devuelve el codigo de la etiqueta.
                    """)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<SampleResponse>> registrar(
            @Valid @RequestBody SampleRequest request,
            @RequestHeader(value = GatewayHeaders.USER_ID, required = false) String usuario,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Muestra registrada correctamente.",
                        sampleService.registrar(request, usuario, requestId)));
    }

    @Operation(summary = "Marca que la muestra entro al equipo",
            description = "Desde este momento la muestra admite resultados.")
    @PatchMapping("/{id}/proceso")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<SampleResponse>> iniciarProceso(
            @PathVariable Long id,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Muestra en proceso.",
                sampleService.iniciarProceso(id, requestId)));
    }

    @Operation(summary = "Cierra la muestra",
            description = """
                    Solo se permite cuando todas las pruebas de la orden
                    tienen resultado: cerrarla antes dejaria pruebas sin
                    informar y el medico creeria que el laboratorio termino.
                    """)
    @PatchMapping("/{id}/procesada")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<SampleResponse>> marcarProcesada(
            @PathVariable Long id,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Muestra procesada.",
                sampleService.marcarProcesada(id, requestId)));
    }

    @Operation(summary = "Rechaza la muestra",
            description = """
                    Para tubos hemolizados, mal rotulados o con volumen
                    insuficiente. La orden vuelve a la cola para que se tome
                    otra muestra, y el motivo llega al medico.
                    """)
    @PatchMapping("/{id}/rechazo")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<SampleResponse>> rechazar(
            @PathVariable Long id,
            @Valid @RequestBody RechazoRequest request,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId) {

        return ResponseEntity.ok(ApiResponse.ok("Muestra rechazada.",
                sampleService.rechazar(id, request.motivo(), requestId)));
    }

    @Operation(summary = "Obtiene una muestra con sus resultados")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<SampleResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Muestra encontrada.",
                sampleService.obtenerPorId(id)));
    }

    @Operation(summary = "Busca una muestra por el codigo de su etiqueta",
            description = "Es la busqueda que usa el laboratorista con el tubo en la mano.")
    @GetMapping("/codigo/{codigo}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<SampleResponse>> obtenerPorCodigo(
            @PathVariable String codigo) {

        return ResponseEntity.ok(ApiResponse.ok("Muestra encontrada.",
                sampleService.obtenerPorCodigo(codigo)));
    }

    @Operation(summary = "Etiqueta de la muestra, en PNG, con el QR del codigo",
            description = "La pega el laboratorista en el tubo. El tecnico "
                    + "escanea en lugar de teclear: un digito mal teclado en un "
                    + "codigo de muestra adjunta el resultado al paciente "
                    + "equivocado.")
    @GetMapping(value = "/{id}/etiqueta", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<byte[]> etiqueta(
            @PathVariable Long id,
            @RequestParam(defaultValue = "300") int lado) {

        byte[] png = labDocumentService.etiquetaPng(id, lado);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                // inline y no attachment: la etiqueta se ve en pantalla antes
                // de imprimirla, para comprobar que es la muestra correcta.
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=etiqueta.png")
                .body(png);
    }

    @Operation(summary = "Informe de resultados de la muestra, en PDF",
            description = "Solo de una muestra PROCESADA: una en proceso tiene "
                    + "resultados a medias, y un informe con la mitad de las "
                    + "pruebas parece completo. Lo puede pedir tambien el "
                    + "paciente, que es quien se lo lleva.")
    @GetMapping(value = "/{id}/informe", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA', 'PACIENTE')")
    public ResponseEntity<byte[]> informe(@PathVariable Long id) {

        byte[] pdf = labDocumentService.informePdf(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=informe-" + id + ".pdf")
                .body(pdf);
    }

    @Operation(summary = "Cola de trabajo del laboratorio",
            description = """
                    Muestras registradas o en proceso, con las urgentes
                    primero y, dentro de cada grupo, por orden de llegada. Es
                    el orden en que hay que atender los tubos.
                    """)
    @GetMapping("/cola")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<SampleResponse>>> cola(
            @RequestParam(defaultValue = "20") int limite) {

        return ResponseEntity.ok(ApiResponse.ok("Cola de trabajo.",
                sampleService.colaDeTrabajo(limite)));
    }

    @Operation(summary = "Muestras de un paciente")
    @GetMapping("/paciente/{idPaciente}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEDICO', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<SampleResponse>>> porPaciente(
            @PathVariable Long idPaciente,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Muestras del paciente.",
                PageableResponse.de(sampleService.listarPorPaciente(idPaciente, pageable))));
    }

    @Operation(summary = "Muestras por estado")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<PageableResponse<SampleResponse>>> porEstado(
            @RequestParam SampleStatus estado,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Muestras en estado " + estado + ".",
                PageableResponse.de(sampleService.listarPorEstado(estado, pageable))));
    }

    /**
     * Motivo del rechazo.
     *
     * <p>Va en el cuerpo y no como parametro de consulta: puede contener
     * tildes y espacios, y es un dato clinico que no debe quedar en la URL ni
     * en los registros del servidor.
     */
    public record RechazoRequest(
            @NotBlank(message = "Indique por que se rechaza la muestra")
            @Size(max = 300, message = "El motivo no puede pasar de 300 caracteres")
            String motivo
    ) {
    }
}
