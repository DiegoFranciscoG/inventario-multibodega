package io.github.diegofranciscog.inventory.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.config.AppProperties;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.Role;
import io.github.diegofranciscog.inventory.repository.AppUserRepository;

/**
 * Crea el primer administrador a partir de ADMIN_EMAIL y ADMIN_PASSWORD (solo si no existe). No hay credenciales por
 * defecto en el código: sin esas variables no se crea ningún administrador.
 */
@Component
@Order(1)
public class AdminBootstrap implements ApplicationRunner {

    static final int MIN_PASSWORD_LENGTH = 12;
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppProperties properties;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(AppProperties properties, AppUserRepository users, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap bootstrap = properties.bootstrap();
        if (!bootstrap.hasAdmin()) {
            return;
        }
        String email = AppUser.normalizeEmail(bootstrap.adminEmail());
        if (users.existsByEmail(email)) {
            return;
        }
        String password = bootstrap.adminPassword();
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("ADMIN_PASSWORD debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        users.save(new AppUser(email, "Administrador", passwordEncoder.encode(password), Role.ADMIN));
        log.info("Administrador inicial creado");
    }
}
