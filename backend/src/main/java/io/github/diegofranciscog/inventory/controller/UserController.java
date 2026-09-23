package io.github.diegofranciscog.inventory.controller;

import java.util.List;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.github.diegofranciscog.inventory.dto.auth.UserCreateRequest;
import io.github.diegofranciscog.inventory.dto.auth.UserResponse;
import io.github.diegofranciscog.inventory.dto.auth.UserUpdateRequest;
import io.github.diegofranciscog.inventory.security.CurrentUser;
import io.github.diegofranciscog.inventory.service.UserService;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Usuarios")
public class UserController {

    private final UserService userService;
    private final CurrentUser currentUser;

    public UserController(UserService userService, CurrentUser currentUser) {
        this.userService = userService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.users();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody UserCreateRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request, currentUser.id());
    }
}
