package io.github.diegofranciscog.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.diegofranciscog.inventory.dto.auth.LoginRequest;
import io.github.diegofranciscog.inventory.dto.auth.TokenResponse;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.DomainFixtures;
import io.github.diegofranciscog.inventory.entity.Role;
import io.github.diegofranciscog.inventory.exception.TooManyRequestsException;
import io.github.diegofranciscog.inventory.repository.AppUserRepository;
import io.github.diegofranciscog.inventory.security.LoginRateLimiter;
import io.github.diegofranciscog.inventory.security.TokenService;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String DUMMY_HASH = "{bcrypt}dummy";

    @Mock
    private AppUserRepository users;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokens;
    @Mock
    private LoginRateLimiter rateLimiter;
    @Mock
    private AuditService audit;

    private AuthService service;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn(DUMMY_HASH);
        service = new AuthService(users, passwordEncoder, tokens, rateLimiter, audit);
    }

    @Test
    void issuesATokenForValidCredentialsAndNormalizesTheEmail() {
        AppUser user = DomainFixtures.user(1, Role.SUPERVISOR);
        when(users.findByEmail("user1@demo.local")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secreto", user.getPasswordHash())).thenReturn(true);
        when(tokens.issue(user)).thenReturn(new TokenService.IssuedToken("jwt", Instant.EPOCH));

        TokenResponse response = service.login(new LoginRequest(" USER1@demo.local ", "secreto"), "10.0.0.1");

        assertThat(response.accessToken()).isEqualTo("jwt");
        assertThat(response.user().role()).isEqualTo(Role.SUPERVISOR);
        verify(rateLimiter).check("10.0.0.1", "user1@demo.local");
        verify(audit).recordSecurityEvent(eq("user1@demo.local"), eq("LOGIN_SUCCESS"), anyMap());
    }

    @Test
    void unknownEmailStillComparesAgainstADummyHashToAvoidTimingLeaks() {
        when(users.findByEmail("nadie@demo.local")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("nadie@demo.local", "x"), "10.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);

        verify(passwordEncoder).matches("x", DUMMY_HASH);
        verify(audit).recordSecurityEvent(eq("nadie@demo.local"), eq("LOGIN_FAILED"), anyMap());
        verify(tokens, never()).issue(any());
    }

    @Test
    void inactiveUsersCannotLogInEvenWithTheRightPassword() {
        AppUser user = DomainFixtures.user(1, Role.OPERATOR);
        user.update("Usuario", Role.OPERATOR, false);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secreto", user.getPasswordHash())).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest(user.getEmail(), "secreto"), "10.0.0.1"))
                .isInstanceOf(BadCredentialsException.class);
        verify(tokens, never()).issue(any());
    }

    @Test
    void rateLimitIsCheckedBeforeTouchingTheDatabase() {
        doThrow(new TooManyRequestsException(30)).when(rateLimiter).check(anyString(), anyString());

        assertThatThrownBy(() -> service.login(new LoginRequest("a@demo.local", "x"), "10.0.0.1"))
                .isInstanceOf(TooManyRequestsException.class);
        verify(users, never()).findByEmail(anyString());
    }

    @Test
    void loginRequestNeverPrintsThePassword() {
        assertThat(new LoginRequest("a@demo.local", "secreto").toString()).doesNotContain("secreto");
    }
}
