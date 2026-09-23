package io.github.diegofranciscog.inventory.dto.auth;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
}
