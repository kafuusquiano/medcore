package com.medcore.auth.config;

import com.medcore.auth.domain.entity.Role;
import com.medcore.auth.domain.entity.User;
import com.medcore.auth.domain.enums.RoleType;
import com.medcore.auth.repository.RoleRepository;
import com.medcore.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Crea el usuario ADMIN inicial la primera vez que arranca el servicio.
 *
 * <p>No se hace con un script SQL en el repositorio a proposito: eso obligaria
 * a versionar un hash de contrasena conocido, y cualquiera que clone el
 * proyecto podria entrar como administrador en un despliegue real.
 *
 * <p>La contrasena se toma de {@code ADMIN_INITIAL_PASSWORD}. Si no esta
 * definida, se genera una aleatoria y se escribe UNA sola vez en el log, para
 * que el entorno de desarrollo siga siendo usable sin configuracion previa.
 *
 * <p>Es idempotente: si ya existe algun usuario con rol ADMIN, no hace nada.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminSeedProperties properties;

    public AdminBootstrap(UserRepository userRepository,
                          RoleRepository roleRepository,
                          PasswordEncoder passwordEncoder,
                          AdminSeedProperties properties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean yaHayAdmin = !userRepository
                .buscarPorRol(RoleType.ADMIN, PageRequest.of(0, 1))
                .isEmpty();

        if (yaHayAdmin) {
            log.debug("Ya existe un usuario ADMIN; no se crea ninguno");
            return;
        }

        Role rolAdmin = roleRepository.findByNombre(RoleType.ADMIN).orElseThrow(
                () -> new IllegalStateException(
                        "Falta el rol ADMIN. Revisa que V1__create_roles_users.sql se haya aplicado."));

        boolean generada = properties.password() == null || properties.password().isBlank();
        String password = generada ? passwordAleatoria() : properties.password();

        User admin = new User(
                properties.dni(),
                properties.correo().toLowerCase(),
                passwordEncoder.encode(password),
                properties.nombres(),
                properties.apellidos(),
                null,
                rolAdmin);

        userRepository.save(admin);

        if (generada) {
            log.warn("""

                    ============================================================
                     ADMIN inicial creado
                       DNI        : {}
                       Correo     : {}
                       Contrasena : {}
                     Se genero al azar porque ADMIN_INITIAL_PASSWORD no estaba
                     definida. Cambiala tras el primer acceso; no volvera a
                     mostrarse.
                    ============================================================
                    """, properties.dni(), properties.correo(), password);
        } else {
            log.info("ADMIN inicial creado con DNI {} y la contrasena de ADMIN_INITIAL_PASSWORD",
                    properties.dni());
        }
    }

    private static String passwordAleatoria() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
