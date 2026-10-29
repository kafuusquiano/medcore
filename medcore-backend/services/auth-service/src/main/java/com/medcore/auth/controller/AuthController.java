package com.medcore.auth.controller;

import com.medcore.auth.domain.dto.request.LoginRequest;
import com.medcore.auth.domain.dto.request.RefreshTokenRequest;
import com.medcore.auth.domain.dto.response.LoginResponse;
import com.medcore.auth.service.AuthService;
import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.security.GatewayHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticacion", description = "Inicio de sesion, renovacion y cierre")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Inicia sesion",
            description = "Acepta DNI o correo. Devuelve un token de acceso y uno de refresco.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest http) {

        LoginResponse respuesta = authService.login(
                request,
                ipOrigen(http),
                http.getHeader(HttpHeaders.USER_AGENT),
                http.getHeader(GatewayHeaders.REQUEST_ID));

        return ResponseEntity.ok(ApiResponse.ok("Sesion iniciada correctamente.", respuesta));
    }

    @Operation(summary = "Renueva el token de acceso",
            description = "Emite un par de tokens nuevo a partir de un token de refresco vigente.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refrescar(
            @Valid @RequestBody RefreshTokenRequest request) {

        return ResponseEntity.ok(ApiResponse.ok("Token renovado correctamente.",
                authService.refrescar(request)));
    }

    @Operation(summary = "Cierra la sesion",
            description = "Revoca el token de acceso y, si se envia, tambien el de refresco.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) RefreshTokenRequest request) {

        String accessToken = (authorization != null && authorization.startsWith(PREFIJO_BEARER))
                ? authorization.substring(PREFIJO_BEARER.length()).trim()
                : null;

        authService.logout(accessToken, request == null ? null : request.refreshToken());

        // Siempre 200: un logout con un token ya expirado tambien deja la
        // sesion cerrada, que es lo que el cliente pidio.
        return ResponseEntity.ok(ApiResponse.ok("Sesion cerrada correctamente."));
    }

    /**
     * IP real del cliente. Detras del gateway y del tunel, la direccion del
     * socket es la del ultimo salto, no la del usuario; X-Forwarded-For
     * conserva la original en su primer elemento.
     */
    private String ipOrigen(HttpServletRequest http) {
        String forwarded = http.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}
