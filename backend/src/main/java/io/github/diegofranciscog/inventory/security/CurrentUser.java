package io.github.diegofranciscog.inventory.security;

import java.util.Optional;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Acceso al usuario autenticado a partir de los claims del JWT. */
@Component
public class CurrentUser {

    public Long id() {
        return jwt().map(token -> {
            Object claim = token.getToken().getClaim(TokenService.CLAIM_USER_ID);
            return claim instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(claim));
        }).orElseThrow(() -> new AuthenticationCredentialsNotFoundException("No hay un usuario autenticado"));
    }

    /** Correo del usuario autenticado o {@code "system"} para procesos internos (semilla de demo). */
    public String emailOrSystem() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null ? "system" : authentication.getName();
    }

    private Optional<JwtAuthenticationToken> jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken token ? Optional.of(token) : Optional.empty();
    }
}
