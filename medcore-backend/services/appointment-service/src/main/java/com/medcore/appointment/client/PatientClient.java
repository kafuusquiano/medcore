package com.medcore.appointment.client;

import com.medcore.appointment.client.fallback.PatientClientFallback;
import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.security.GatewayHeaders;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Consulta sincrona a patient-service.
 *
 * <p>Se llama por el nombre registrado en Eureka, no por una URL fija, para
 * que el balanceo lo resuelva Spring Cloud LoadBalancer.
 *
 * <p>Las cabeceras de identidad se reenvian explicitamente: la llamada sale de
 * este servicio, no del gateway, asi que patient-service no las recibiria y
 * respondria 403.
 */
@FeignClient(
        name = "patient-service",
        contextId = "patientClient",
        fallbackFactory = PatientClientFallback.class)
public interface PatientClient {

    @GetMapping("/api/patients/{id}")
    ApiResponse<PatientDto> obtenerPorId(
            @PathVariable("id") Long id,
            @RequestHeader(GatewayHeaders.USER_ID) String userId,
            @RequestHeader(GatewayHeaders.USER_ROLE) String rol,
            @RequestHeader(value = GatewayHeaders.REQUEST_ID, required = false) String requestId);
}
