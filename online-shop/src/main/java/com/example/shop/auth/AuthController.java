package com.example.shop.auth;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.shop.common.web.ApiSignedIn;
import com.example.shop.common.web.ErrorResponse;

/**
 * Practice 7: the login endpoint (lecture week 12, slide 20) and a "who am I" endpoint.
 *
 * <p>Login reuses the normal authentication pipeline: the AuthenticationManager asks
 * {@link JpaUserDetailsService} for the user and the PasswordEncoder compares the hash (slide 10). On success
 * a signed JWT is returned; on failure Spring throws an AuthenticationException, which the
 * GlobalExceptionHandler turns into one generic 401, whatever the exact reason was.
 */
@Tag(name = "Authentication", description = "Log in to get a JWT; send it as Authorization: Bearer <token>")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    public AuthController(AuthenticationManager authenticationManager, TokenService tokenService) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
    }

    @Operation(summary = "Log in with username and password; returns a 15-minute access token")
    @SecurityRequirements   // public: no token needed to get a token
    @ApiResponse(responseCode = "200", description = "The signed access token")
    @ApiResponse(responseCode = "400", description = "Username or password missing",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "401", description = "Wrong username or password, or a disabled account "
            + "(one message for all, so valid usernames cannot be guessed)",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        return tokenService.issue(authentication);
    }

    // The identity always comes from the validated token, never from the request body (slide 11)
    @Operation(summary = "Who am I: the user and roles inside the current token")
    @ApiResponse(responseCode = "200", description = "The current user")
    @ApiSignedIn
    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(JwtConfig.ROLES_CLAIM);
        return new CurrentUserResponse(jwt.getSubject(), roles != null ? roles : List.of(), jwt.getExpiresAt());
    }
}
