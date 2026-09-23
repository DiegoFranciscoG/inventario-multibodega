package io.github.diegofranciscog.inventory.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableResilientMethods
public class AppConfig {

    /** Reloj inyectable: permite fijar la fecha en los tests (vencimientos, cronología del kárdex). */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** BCrypt con prefijo {@code {bcrypt}} para poder migrar de algoritmo sin romper contraseñas existentes. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
