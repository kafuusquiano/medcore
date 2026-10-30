package com.medcore.notification.controller;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.notification.domain.dto.response.NotificationResponse;
import com.medcore.notification.domain.enums.NotificationStatus;
import com.medcore.notification.service.NotificationService;
import com.medcore.notification.service.impl.NotificationDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bandeja de avisos y panel de entrega.
 *
 * <p>No hay endpoint para CREAR avisos. Los avisos nacen de eventos, nunca de
 * una peticion: si se pudieran crear por API, cualquier cliente podria mandar
 * un correo en nombre de la clinica, y el aviso dejaria de corresponder con un
 * hecho ocurrido en el sistema.
 */
@Tag(name = "Avisos", description = "Bandeja de avisos y panel de entrega")
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationDispatcher dispatcher;

    public NotificationController(NotificationService notificationService,
                                  NotificationDispatcher dispatcher) {
        this.notificationService = notificationService;
        this.dispatcher = dispatcher;
    }

    @Operation(summary = "Bandeja de avisos de un paciente",
            description = "Solo los avisos in-app: los correos no se listan aqui.")
    @GetMapping("/paciente/{idPaciente}")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO', 'PACIENTE')")
    public ResponseEntity<ApiResponse<PageableResponse<NotificationResponse>>> bandejaPaciente(
            @PathVariable Long idPaciente,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Avisos del paciente.",
                PageableResponse.de(
                        notificationService.bandejaDePaciente(idPaciente, pageable))));
    }

    @Operation(summary = "Avisos sin leer de un paciente",
            description = "Para el contador de la campana en la interfaz.")
    @GetMapping("/paciente/{idPaciente}/sin-leer")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'MEDICO', 'PACIENTE')")
    public ResponseEntity<ApiResponse<Long>> sinLeerPaciente(@PathVariable Long idPaciente) {
        return ResponseEntity.ok(ApiResponse.ok("Avisos sin leer.",
                notificationService.sinLeerDePaciente(idPaciente)));
    }

    @Operation(summary = "Mi bandeja de avisos",
            description = """
                    Los avisos del usuario que hace la peticion, segun la
                    cabecera X-User-Id que propaga el gateway. Es la que usa el
                    personal: medico, laboratorio y almacen.
                    """)
    @GetMapping("/mios")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageableResponse<NotificationResponse>>> misAvisos(
            @org.springframework.web.bind.annotation.RequestHeader(GatewayHeaders.USER_ID)
            Long idUsuario,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Mis avisos.",
                PageableResponse.de(
                        notificationService.bandejaDeUsuario(idUsuario, pageable))));
    }

    @Operation(summary = "Mis avisos sin leer")
    @GetMapping("/mios/sin-leer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Long>> misAvisosSinLeer(
            @org.springframework.web.bind.annotation.RequestHeader(GatewayHeaders.USER_ID)
            Long idUsuario) {

        return ResponseEntity.ok(ApiResponse.ok("Mis avisos sin leer.",
                notificationService.sinLeerDeUsuario(idUsuario)));
    }

    @Operation(summary = "Obtiene un aviso")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Aviso encontrado.",
                notificationService.obtenerPorId(id)));
    }

    @Operation(summary = "Marca un aviso como leido",
            description = """
                    Solo los avisos in-app. Un correo no se puede marcar leido:
                    el servicio no sabe si el destinatario lo abrio, y fingir
                    que lo sabe daria una cifra falsa.
                    """)
    @PatchMapping("/{id}/leido")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<NotificationResponse>> marcarLeido(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Aviso marcado como leido.",
                notificationService.marcarLeido(id)));
    }

    @Operation(summary = "Marca como leidos todos los avisos de un paciente")
    @PatchMapping("/paciente/{idPaciente}/leidos")
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA', 'PACIENTE')")
    public ResponseEntity<ApiResponse<Integer>> marcarTodosLeidos(
            @PathVariable Long idPaciente) {

        return ResponseEntity.ok(ApiResponse.ok("Avisos marcados como leidos.",
                notificationService.marcarTodosLeidosDePaciente(idPaciente)));
    }

    // ---- Panel de entrega --------------------------------------------------

    @Operation(summary = "Avisos que nunca llegaron",
            description = """
                    Los que agotaron sus intentos. Es la consulta que de verdad
                    importa en un sistema de avisos: si el correo de un valor
                    critico no salio, alguien tiene que verlo aqui y llamar por
                    telefono. Cada uno lleva el motivo del fallo.
                    """)
    @GetMapping("/no-entregados")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> noEntregados() {
        return ResponseEntity.ok(ApiResponse.ok("Avisos no entregados.",
                notificationService.noEntregados()));
    }

    @Operation(summary = "Avisos por estado")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageableResponse<NotificationResponse>>> porEstado(
            @RequestParam NotificationStatus estado,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Avisos en estado " + estado + ".",
                PageableResponse.de(
                        notificationService.listarPorEstado(estado, pageable))));
    }

    @Operation(summary = "Cifras de entrega",
            description = "Pendientes, enviados, fallidos, omitidos y perdidos.")
    @GetMapping("/resumen")
    @PreAuthorize("hasAnyRole('ADMIN', 'LABORATORISTA')")
    public ResponseEntity<ApiResponse<NotificationService.Resumen>> resumen() {
        return ResponseEntity.ok(ApiResponse.ok("Resumen de avisos.",
                notificationService.resumen()));
    }

    @Operation(summary = "Procesa la cola de envio a demanda",
            description = """
                    Lo mismo que hace el proceso periodico: recoge lo pendiente
                    y lo que fallo, y lo intenta de nuevo. Util para no esperar
                    al siguiente ciclo tras arreglar el servidor de correo.
                    """)
    @PostMapping("/cola")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<NotificationDispatcher.Resumen>> procesarCola() {
        return ResponseEntity.ok(ApiResponse.ok("Cola procesada.",
                dispatcher.procesarCola()));
    }
}
