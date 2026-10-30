package com.medcore.laboratory.client;

import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.security.GatewayHeaders;
import com.medcore.laboratory.client.fallback.PatientClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Consulta el sexo, la edad y la zona del paciente.
 *
 * <p>Estos tres datos no viajan en {@code lab-order.created} porque
 * medical-service no los guarda, y el laboratorio los necesita: el sexo y la
 * edad seleccionan que rango de referencia aplica, y la zona la consume
 * analytics-service.
 *
 * <p>La llamada se resuelve por Eureka con {@code lb://}, no por host y
 * puerto. Las cabeceras de usuario se propagan a mano porque Feign no hereda
 * las de la peticion entrante (MC4).
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
