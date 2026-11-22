package com.example.shop.auth;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Practice 7: one row of the user store (lecture week 12, slides 12-13), mapped to {@code app_users}
 * and {@code user_roles} from V4.
 *
 * <p>It deliberately does NOT implement Spring Security's {@code UserDetails}: the entity stays a plain
 * persistence model, and {@link JpaUserDetailsService} copies what the security pipeline needs. That way the
 * password hash can never leak into a JSON response by accident (slide 13, "keep entity apart").
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String username;

    // Only the hash, e.g. {bcrypt}$2a$10$... - the raw password is never stored
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    // LAZY like every association in this project; loaded inside the transaction of JpaUserDetailsService
    @ElementCollection
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    protected AppUser() {
        // for JPA
    }

    public AppUser(String username, String passwordHash, Set<Role> roles) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void disable() {
        this.enabled = false;
    }

    public Set<Role> getRoles() {
        return Collections.unmodifiableSet(roles);
    }
}
