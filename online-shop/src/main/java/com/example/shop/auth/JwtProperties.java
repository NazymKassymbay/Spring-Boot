package com.example.shop.auth;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

/**
 * Practice 7: typed JWT settings (lecture week 12, slides 21 and 27).
 *
 * @param issuer          the {@code iss} claim written at login and required when a token is validated
 * @param accessTokenTtl  how long an access token lives; short, because a JWT cannot be revoked (slide 22)
 * @param publicKey       PEM file (X.509) that verifies signatures, e.g. {@code file:/run/secrets/jwt-public.pem}
 * @param privateKey      PEM file (PKCS#8) that signs tokens; a secret, so it comes from the environment
 *
 * <p>Both keys missing (dev, test): a fresh key pair is generated at start-up, so no key is ever committed,
 * and tokens simply stop working after a restart. The prod profile requires both (no default there).
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        Duration accessTokenTtl,
        Resource publicKey,
        Resource privateKey) {

    public JwtProperties {
        accessTokenTtl = accessTokenTtl != null ? accessTokenTtl : Duration.ofMinutes(15);
    }
}
