package com.example.shop.auth;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Practice 7: the keys and the JWT beans (lecture week 12, slides 20-22).
 *
 * <p>RS256: the private key signs tokens at login, the public key verifies them on every request. Only the
 * shop holds the private key, so another service that is given the public key can check tokens but never
 * forge them.
 */
@Configuration
public class JwtConfig {

    /** Name of the custom claim that carries the user's authorities, e.g. ["ROLE_ADMIN"]. */
    public static final String ROLES_CLAIM = "roles";

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    RSAKey jwtSigningKey(JwtProperties properties) throws JOSEException {
        Resource publicPem = properties.publicKey();
        Resource privatePem = properties.privateKey();
        if (publicPem == null && privatePem == null) {
            KeyPair pair = generateRsaKeyPair();
            log.warn("No app.jwt keys configured: using a key pair generated at start-up. "
                    + "Fine for dev and tests; tokens become invalid after a restart.");
            return rsaKey((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        }
        if (publicPem == null || privatePem == null) {
            throw new IllegalStateException("Set both app.jwt.public-key and app.jwt.private-key, or neither");
        }
        return rsaKey(readPem(publicPem, RsaKeyConverters.x509()), readPem(privatePem, RsaKeyConverters.pkcs8()));
    }

    // Signs tokens at login (slide 20)
    @Bean
    JwtEncoder jwtEncoder(RSAKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    // Verifies every Bearer token (slide 21): signature with the public key, then exp/nbf and our issuer
    @Bean
    JwtDecoder jwtDecoder(RSAKey jwtSigningKey, JwtProperties properties) throws JOSEException {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtSigningKey.toRSAPublicKey()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return decoder;
    }

    /**
     * Reads authorities from our "roles" claim. Without this converter Spring reads the "scope" claim,
     * finds nothing, and every admin gets 403 - "the most common Practice 7 bug" (slide 21).
     * The claim already holds ROLE_*, so no extra prefix is added.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(ROLES_CLAIM);
        roles.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }

    private static RSAKey rsaKey(RSAPublicKey publicKey, RSAPrivateKey privateKey) throws JOSEException {
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        // The kid header tells a verifier which key signed the token, which makes key rotation possible
        return new RSAKey.Builder(key).keyID(key.computeThumbprint().toString()).build();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA is not available", ex);
        }
    }

    private static <K> K readPem(Resource pem, Converter<InputStream, K> converter) {
        try (InputStream in = pem.getInputStream()) {
            return converter.convert(in);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read JWT key " + pem.getDescription(), ex);
        }
    }
}
