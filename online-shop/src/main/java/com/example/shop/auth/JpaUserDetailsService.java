package com.example.shop.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Practice 7: plugs the {@code app_users} table into Spring Security's authentication pipeline
 * (lecture week 12, slides 10 and 13).
 *
 * <p>At login, {@code DaoAuthenticationProvider} asks this service for the user by name and then compares
 * the submitted password with the stored hash through the {@code PasswordEncoder}. Because this is the only
 * {@link UserDetailsService} bean and there is one {@code PasswordEncoder} bean, Spring Boot wires that
 * provider by itself. An unknown user and a wrong password both end as the same 401 (no user enumeration).
 */
@Service
public class JpaUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;

    public JpaUserDetailsService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)   // the roles are a lazy collection, read inside this transaction
    public UserDetails loadUserByUsername(String username) {
        AppUser user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRoles().stream().map(Role::name).toArray(String[]::new))   // adds ROLE_
                .disabled(!user.isEnabled())
                .build();
    }
}
