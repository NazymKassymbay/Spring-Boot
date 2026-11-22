package com.example.shop.auth;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Practice 7: Spring Security for the shop (lecture week 12, slides 7-15, 18, 26).
 *
 * <p>The outer class holds what every context needs: the password encoder, the authentication manager used by
 * the login endpoint, and method security ({@code @PreAuthorize}). The nested {@link FilterChain} holds the
 * HTTP part, created only in a web application (a test with {@code WebEnvironment.NONE} has no filters).
 */
@Configuration
@EnableMethodSecurity   // @PreAuthorize on service methods (slide 15), e.g. ProductService.delete
public class SecurityConfig {

    /**
     * BCrypt by default, stored with an {id} prefix such as {bcrypt}, so the algorithm can be upgraded later
     * (e.g. to Argon2) without resetting anyone's password (slide 14).
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * The pipeline the login endpoint runs (slide 10): ProviderManager -> DaoAuthenticationProvider ->
     * JpaUserDetailsService loads the user -> PasswordEncoder compares the hash. Built explicitly so it is
     * visible in one place. The provider reports an unknown user as BadCredentials, like a wrong password,
     * so the two cannot be told apart from outside.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    /**
     * The security filter chain. Rules are checked top-down and the first match wins; the catch-all comes
     * last, so everything that is not opened by name needs a valid token (deny by default, slide 6):
     *
     * <pre>
     *   anyone            POST /api/v1/auth/login, GET catalogue (categories, products), GET exchange rates, API docs
     *   ADMIN             any write to the catalogue, reading and managing orders
     *   any signed-in     POST /api/v1/orders (place an order), GET /api/v1/auth/me
     * </pre>
     *
     * <p>Stateless: every request carries {@code Authorization: Bearer <JWT>} and the server keeps no session.
     * CSRF protection is off because no credential travels in a cookie (slide 26).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    static class FilterChain {

        private static final String CATEGORIES = "/api/v1/categories/**";
        private static final String PRODUCTS = "/api/v1/products/**";
        private static final String ORDERS = "/api/v1/orders/**";

        @Bean
        SecurityFilterChain apiSecurity(HttpSecurity http,
                                        AuthenticationEntryPoint unauthorized,
                                        AccessDeniedHandler forbidden) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .authorizeHttpRequests(auth -> auth
                            // public
                            .requestMatchers(POST, "/api/v1/auth/login").permitAll()
                            .requestMatchers(GET, CATEGORIES, PRODUCTS, "/api/v1/exchange-rates/**").permitAll()
                            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error")
                                    .permitAll()
                            // the role-protected part: only staff change the catalogue and manage orders
                            .requestMatchers(CATEGORIES, PRODUCTS).hasRole(Role.ADMIN.name())
                            .requestMatchers(POST, "/api/v1/orders").authenticated()
                            .requestMatchers(ORDERS).hasRole(Role.ADMIN.name())
                            // deny by default: anything else needs a valid token
                            .anyRequest().authenticated())
                    // Validates the Bearer JWT with the JwtDecoder and maps roles with the converter (JwtConfig)
                    .oauth2ResourceServer(oauth -> oauth
                            .jwt(withDefaults())
                            .authenticationEntryPoint(unauthorized)
                            .accessDeniedHandler(forbidden))
                    .exceptionHandling(errors -> errors
                            .authenticationEntryPoint(unauthorized)
                            .accessDeniedHandler(forbidden))
                    .build();
        }

        /**
         * 401 from the filter chain. Filters run before Spring MVC, so @RestControllerAdvice never sees these
         * exceptions (slide 7). The standard Bearer entry point sets the status and the WWW-Authenticate header;
         * then the exception is handed to MVC's exception resolvers, so GlobalExceptionHandler writes the same
         * ErrorResponse body as every other error of the API.
         */
        @Bean
        AuthenticationEntryPoint unauthorized(
                @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
            BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
            return (request, response, exception) -> {
                bearer.commence(request, response, exception);
                resolver.resolveException(request, response, null, exception);
            };
        }

        /** 403 from the filter chain: a valid token without the needed role. Same idea as {@link #unauthorized}. */
        @Bean
        AccessDeniedHandler forbidden(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
            BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
            return (request, response, exception) -> {
                bearer.handle(request, response, exception);
                resolver.resolveException(request, response, null, exception);
            };
        }
    }
}
