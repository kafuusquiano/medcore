package com.medcore.auth.service.impl;

import com.medcore.auth.domain.dto.request.CreateUserRequest;
import com.medcore.auth.domain.dto.response.UserResponse;
import com.medcore.auth.domain.entity.Role;
import com.medcore.auth.domain.entity.User;
import com.medcore.auth.domain.enums.RoleType;
import com.medcore.auth.exception.AuthException;
import com.medcore.auth.mapper.UserMapper;
import com.medcore.auth.repository.RoleRepository;
import com.medcore.auth.repository.UserRepository;
import com.medcore.auth.service.UserService;
import com.medcore.commons.dto.PageableResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public UserResponse crear(CreateUserRequest request) {
        if (userRepository.existsByDni(request.dni())) {
            throw AuthException.dniYaRegistrado(request.dni());
        }
        if (userRepository.existsByCorreo(request.correo())) {
            throw AuthException.correoYaRegistrado(request.correo());
        }

        Role role = roleRepository.findByNombre(request.rol())
                .orElseThrow(() -> AuthException.rolNoExiste(request.rol().name()));

        User usuario = new User(
                request.dni(),
                request.correo().toLowerCase(),
                passwordEncoder.encode(request.password()),
                request.nombres(),
                request.apellidos(),
                request.telefono(),
                role);

        User guardado = userRepository.save(usuario);
        log.info("Usuario {} creado con rol {}", guardado.getId(), role.getNombre());
        return userMapper.aRespuesta(guardado);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse obtenerPorId(Long id) {
        return userRepository.findById(id)
                .map(userMapper::aRespuesta)
                .orElseThrow(() -> AuthException.usuarioNoEncontrado(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageableResponse<UserResponse> listar(RoleType rol, Pageable pageable) {
        var pagina = (rol == null)
                ? userRepository.findAll(pageable)
                : userRepository.buscarPorRol(rol, pageable);
        return PageableResponse.de(pagina, userMapper::aRespuesta);
    }

    @Override
    @Transactional
    public UserResponse activar(Long id) {
        User usuario = userRepository.findById(id)
                .orElseThrow(() -> AuthException.usuarioNoEncontrado(id));
        usuario.activar();
        log.info("Usuario {} activado", id);
        return userMapper.aRespuesta(userRepository.save(usuario));
    }

    @Override
    @Transactional
    public UserResponse desactivar(Long id) {
        User usuario = userRepository.findById(id)
                .orElseThrow(() -> AuthException.usuarioNoEncontrado(id));
        usuario.desactivar();
        log.info("Usuario {} desactivado", id);
        return userMapper.aRespuesta(userRepository.save(usuario));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Idempotente a proposito: Kafka garantiza entrega al menos una vez, de
     * modo que el mismo patient.created puede llegar dos veces y no debe
     * provocar un error ni una segunda cuenta.
     *
     * <p>La contrasena inicial es aleatoria y no se comunica: el paciente la
     * establece por el flujo de recuperacion. Dejar una contrasena previsible,
     * como el DNI, convertiria cada alta en una cuenta adivinable.
     */
    @Override
    @Transactional
    public void crearCuentaPaciente(Long idPaciente, String dni, String correo,
                                    String nombres, String apellidos, String telefono) {

        if (userRepository.existsByIdPaciente(idPaciente)) {
            log.debug("El paciente {} ya tiene cuenta; se ignora el evento", idPaciente);
            return;
        }
        if (userRepository.existsByDni(dni)) {
            log.debug("Ya existe un usuario con DNI {}; se ignora el evento del paciente {}",
                    dni, idPaciente);
            return;
        }

        Role role = roleRepository.findByNombre(RoleType.PACIENTE)
                .orElseThrow(() -> AuthException.rolNoExiste(RoleType.PACIENTE.name()));

        String correoFinal = (correo == null || correo.isBlank())
                // Sin correo no se puede dejar el campo vacio: es NOT NULL y unico.
                ? dni + "@sin-correo.medcore.local"
                : correo.toLowerCase();

        User usuario = new User(dni, correoFinal, passwordEncoder.encode(passwordAleatoria()),
                nombres, apellidos, telefono, role);
        usuario.vincularPaciente(idPaciente);

        userRepository.save(usuario);
        log.info("Cuenta PACIENTE creada para el paciente {}", idPaciente);
    }

    private static String passwordAleatoria() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
