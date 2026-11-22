package com.example.shop.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Practice 7: Spring Data access to the user store (a derived query from week 5). */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    // Case-insensitive, matching the unique index on LOWER(username) in V4
    Optional<AppUser> findByUsernameIgnoreCase(String username);
}
