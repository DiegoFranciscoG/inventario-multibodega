package io.github.diegofranciscog.inventory.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_code", nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Version
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_warehouse",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "warehouse_id"))
    private Set<Warehouse> warehouses = new HashSet<>();

    protected AppUser() {
    }

    public AppUser(String email, String fullName, String passwordHash, Role role) {
        this.email = normalizeEmail(email);
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean canOperateIn(Long warehouseId) {
        if (!role.isRestrictedToAssignedWarehouses()) {
            return true;
        }
        return warehouses.stream().anyMatch(warehouse -> warehouse.getId().equals(warehouseId));
    }

    public void update(String fullName, Role role, boolean active) {
        this.fullName = fullName;
        this.role = role;
        this.active = active;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void assignWarehouses(Set<Warehouse> warehouses) {
        this.warehouses.clear();
        this.warehouses.addAll(warehouses);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Warehouse> getWarehouses() {
        return warehouses;
    }
}
