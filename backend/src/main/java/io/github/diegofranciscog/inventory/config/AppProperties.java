package io.github.diegofranciscog.inventory.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuración tipada de la aplicación. Se valida al arrancar: si falta un valor crítico (p. ej. JWT_SECRET)
 * la aplicación no inicia.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid @NotNull Security security,
        @Valid @NotNull Inventory inventory,
        @Valid @NotNull Bootstrap bootstrap,
        @Valid @NotNull Demo demo) {

    public record Security(
            @NotBlank String jwtSecret,
            @NotNull Duration jwtTtl,
            @NotBlank String jwtIssuer,
            @NotEmpty List<String> corsAllowedOrigins,
            @Min(1) @Max(100) int loginAttemptsPerMinute) {
    }

    /**
     * @param timeZone zona horaria para interpretar fechas de filtros y vencimientos (Ecuador continental: UTC−5)
     */
    public record Inventory(
            @Min(1) @Max(365) int expiringDays,
            @Min(100) @Max(100_000) int exportMaxRows,
            @Min(10) @Max(500) int maxPageSize,
            @NotNull ZoneId timeZone) {
    }

    public record Bootstrap(String adminEmail, String adminPassword) {

        public boolean hasAdmin() {
            return adminEmail != null && !adminEmail.isBlank();
        }
    }

    public record Demo(boolean enabled, String password) {
    }
}
