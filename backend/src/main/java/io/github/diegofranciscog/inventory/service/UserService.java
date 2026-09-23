package io.github.diegofranciscog.inventory.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.diegofranciscog.inventory.dto.auth.UserCreateRequest;
import io.github.diegofranciscog.inventory.dto.auth.UserResponse;
import io.github.diegofranciscog.inventory.dto.auth.UserUpdateRequest;
import io.github.diegofranciscog.inventory.entity.AppUser;
import io.github.diegofranciscog.inventory.entity.Role;
import io.github.diegofranciscog.inventory.entity.Warehouse;
import io.github.diegofranciscog.inventory.exception.BusinessRuleException;
import io.github.diegofranciscog.inventory.exception.ConflictException;
import io.github.diegofranciscog.inventory.exception.NotFoundException;
import io.github.diegofranciscog.inventory.mapper.UserMapper;
import io.github.diegofranciscog.inventory.repository.AppUserRepository;
import io.github.diegofranciscog.inventory.repository.WarehouseRepository;

/** Administración de usuarios (solo ADMIN). Se guardan únicamente correo, nombre y rol (R-25, LOPDP). */
@Service
public class UserService {

    private final AppUserRepository users;
    private final WarehouseRepository warehouses;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    public UserService(AppUserRepository users, WarehouseRepository warehouses, PasswordEncoder passwordEncoder,
                       AuditService audit) {
        this.users = users;
        this.warehouses = warehouses;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> users() {
        return users.findAllByOrderByEmailAsc().stream().map(UserMapper::toResponse).toList();
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        String email = AppUser.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("DUPLICATE_USER", "Ya existe un usuario con ese correo");
        }
        AppUser user = new AppUser(email, request.fullName().strip(), passwordEncoder.encode(request.password()),
                request.role());
        user.assignWarehouses(warehouses(request.role(), request.warehouseIds()));
        users.save(user);
        audit.record("USER_CREATED", "AppUser", user.getId(), Map.of("email", email, "role", request.role().name()));
        return UserMapper.toResponse(user);
    }

    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request, Long actingUserId) {
        AppUser user = users.findWithWarehousesById(id).orElseThrow(() -> new NotFoundException("Usuario", id));
        if (Objects.equals(id, actingUserId) && (!request.active() || request.role() != Role.ADMIN)) {
            throw new BusinessRuleException("SELF_LOCKOUT", "No puedes desactivarte ni quitarte el rol de administrador");
        }
        user.update(request.fullName().strip(), request.role(), request.active());
        user.assignWarehouses(warehouses(request.role(), request.warehouseIds()));
        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            user.changePassword(passwordEncoder.encode(request.newPassword()));
        }
        audit.record("USER_UPDATED", "AppUser", user.getId(), Map.of("email", user.getEmail(),
                "role", request.role().name(), "active", request.active(),
                "passwordChanged", request.newPassword() != null && !request.newPassword().isBlank()));
        return UserMapper.toResponse(user);
    }

    private Set<Warehouse> warehouses(Role role, Set<Long> ids) {
        Set<Long> requested = ids == null ? Set.of() : ids;
        if (role == Role.OPERATOR && requested.isEmpty()) {
            throw new BusinessRuleException("WAREHOUSES_REQUIRED", "Un operador debe tener al menos una bodega asignada");
        }
        Set<Warehouse> found = new HashSet<>(warehouses.findAllById(requested));
        if (found.size() != requested.size()) {
            throw new NotFoundException("Bodega", requested);
        }
        return found;
    }
}
