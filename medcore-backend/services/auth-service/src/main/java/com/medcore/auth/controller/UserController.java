package com.medcore.auth.controller;

import com.medcore.auth.domain.dto.request.CreateUserRequest;
import com.medcore.auth.domain.dto.response.UserResponse;
import com.medcore.auth.domain.enums.RoleType;
import com.medcore.auth.service.UserService;
import com.medcore.commons.dto.ApiResponse;
import com.medcore.commons.dto.PageableResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestion de usuarios. Todo el controller exige rol ADMIN.
 *
 * <p>El rol llega en X-User-Role y lo convierte en autoridad el
 * HeaderAuthenticationFilter, asi que @PreAuthorize funciona sin que este
 * servicio toque el JWT.
 */
@Tag(name = "Usuarios", description = "Alta, consulta y estado de los usuarios (solo ADMIN)")
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Crea un usuario")
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> crear(
            @Valid @RequestBody CreateUserRequest request) {

        UserResponse creado = userService.crear(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuario registrado correctamente.", creado));
    }

    @Operation(summary = "Obtiene un usuario por su identificador")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Usuario encontrado.",
                userService.obtenerPorId(id)));
    }

    @Operation(summary = "Lista usuarios", description = "Permite filtrar por rol.")
    @GetMapping
    public ResponseEntity<ApiResponse<PageableResponse<UserResponse>>> listar(
            @RequestParam(required = false) RoleType rol,
            @PageableDefault(size = 20, sort = "apellidos") Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok("Usuarios listados correctamente.",
                userService.listar(rol, pageable)));
    }

    @Operation(summary = "Activa un usuario",
            description = "Tambien reinicia el contador de intentos fallidos, por lo que sirve para desbloquear una cuenta.")
    @PatchMapping("/{id}/activar")
    public ResponseEntity<ApiResponse<UserResponse>> activar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Usuario activado correctamente.",
                userService.activar(id)));
    }

    @Operation(summary = "Desactiva un usuario",
            description = "La cuenta deja de poder iniciar sesion. No se elimina ningun dato.")
    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<ApiResponse<UserResponse>> desactivar(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Usuario desactivado correctamente.",
                userService.desactivar(id)));
    }
}
