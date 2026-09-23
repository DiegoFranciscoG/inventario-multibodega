package io.github.diegofranciscog.inventory.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.dto.auth.LoginRequest;
import io.github.diegofranciscog.inventory.dto.auth.TokenResponse;
import io.github.diegofranciscog.inventory.dto.auth.UserResponse;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.exception.NotFoundException;
import io.github.diegofranciscog.inventory.mapper.UserMapper;
import io.github.diegofranciscog.inventory.repository.AppUserRepository;
import io.github.diegofranciscog.inventory.security.LoginRateLimiter;
import io.github.diegofranciscog.inventory.security.TokenService;

/**
 * Login con BCrypt, límite de intentos y respuesta genérica (no revela si el correo existe). Si el correo no existe se
 * compara igual contra un hash ficticio para que el tiempo de respuesta no delate a los usuarios válidos.
 */
@Service
public class AuthService {

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final LoginRateLimiter rateLimiter;
    private final AuditService audit;
    private final String dummyHash;

    public AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokens,
                       LoginRateLimiter rateLimiter, AuditService audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request, String clientIp) {
        String email = AppUser.normalizeEmail(request.email());
        rateLimiter.check(clientIp, email);
        Optional<AppUser> user = users.findByEmail(email);
        boolean matches = passwordEncoder.matches(request.password(), user.map(AppUser::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches || !user.get().isActive()) {
            audit.recordSecurityEvent(email, "LOGIN_FAILED", Map.of("reason", user.isEmpty() ? "unknown_user"
                    : !matches ? "bad_password" : "inactive_user"));
            throw new BadCredentialsException("Credenciales inválidas");
        }
        TokenService.IssuedToken token = tokens.issue(user.get());
        audit.recordSecurityEvent(email, "LOGIN_SUCCESS", Map.of());
        return new TokenResponse(token.value(), "Bearer", token.expiresAt(), UserMapper.toResponse(user.get()));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return users.findWithWarehousesById(userId)
                .map(UserMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Usuario", userId));
    }
}
