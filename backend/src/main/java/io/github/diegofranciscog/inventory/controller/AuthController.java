package io.github.diegofranciscog.inventory.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.diegofranciscog.inventory.dto.auth.LoginRequest;
import io.github.diegofranciscog.inventory.dto.auth.TokenResponse;
import io.github.diegofranciscog.inventory.dto.auth.UserResponse;
import io.github.diegofranciscog.inventory.security.CurrentUser;
import io.github.diegofranciscog.inventory.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService authService;
    private final CurrentUser currentUser;

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Inicia sesión y devuelve un JWT de vida corta (máx. N intentos por minuto)")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return authService.login(request, http.getRemoteAddr());
    }

    @GetMapping("/me")
    @Operation(summary = "Datos del usuario autenticado y sus bodegas")
    public UserResponse me() {
        return authService.me(currentUser.id());
    }
}
