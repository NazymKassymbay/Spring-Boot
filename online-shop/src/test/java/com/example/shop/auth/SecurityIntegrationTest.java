package com.example.shop.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.shop.product.app.ProductService;
import com.jayway.jsonpath.JsonPath;

/**
 * Practice 7: proof of the security rules with the REAL filter chain, the REAL login and REAL signed JWTs
 * against a real PostgreSQL (lecture week 12, slides 16 and 30: "tests showing 401, 403 and 2xx").
 * Nothing is mocked: a token is obtained from POST /api/v1/auth/login exactly as a client would.
 *
 * <pre>
 *   request                    anonymous   CUSTOMER   ADMIN
 *   GET  /api/v1/products         200        200       200
 *   POST /api/v1/orders           401        201        -
 *   GET  /api/v1/orders           401        403       200
 *   DELETE /api/v1/products/{id}  401        403       204
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecurityIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ProductService productService;

    private long productId;

    @BeforeEach
    void usersAndCatalogue() throws Exception {
        jdbc.execute("TRUNCATE order_items, orders, products, categories, user_roles, app_users "
                + "RESTART IDENTITY CASCADE");
        // The test profile has no demo users, so each test creates its own, hashed by the real encoder
        createUser("customer", "customer123", true, Role.CUSTOMER);
        createUser("admin", "admin123", true, Role.ADMIN);
        createUser("blocked", "blocked123", false, Role.CUSTOMER);

        String admin = token("admin", "admin123");
        String category = mvc.perform(post("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Books\",\"description\":\"Printed books\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String product = mvc.perform(post("/api/v1/products").header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"BK-001","name":"Spring in Action","description":"5th edition",
                                 "price":18500.00,"stockQuantity":10,"categoryId":%d}
                                """.formatted(((Number) JsonPath.read(category, "$.id")).longValue())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        productId = ((Number) JsonPath.read(product, "$.id")).longValue();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ---- the user store and the login endpoint ----

    @Test
    void passwordsAreStoredOnlyAsBcryptHashes() {
        String stored = jdbc.queryForObject("SELECT password_hash FROM app_users WHERE username = 'admin'",
                String.class);

        assertThat(stored).startsWith("{bcrypt}$2a$10$").doesNotContain("admin123");
        assertThat(passwordEncoder.matches("admin123", stored)).isTrue();
    }

    @Test
    void loginReturnsASignedTokenThatCarriesTheRoles() throws Exception {
        String body = login("admin", "admin123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        assertThat(token.split("\\.")).hasSize(3);   // header.payload.signature
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
    }

    @Test
    void usernameIsCaseInsensitive() throws Exception {
        login("ADMIN", "admin123").andExpect(status().isOk());
    }

    // Wrong password, unknown user and disabled account look exactly the same from outside (slide 10)
    @Test
    void everyFailedLoginGetsTheSameGeneric401() throws Exception {
        for (String[] attempt : new String[][] {
                {"admin", "wrong-password"}, {"nobody", "admin123"}, {"blocked", "blocked123"}}) {
            login(attempt[0], attempt[1])
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.message").value("Invalid username or password"));
        }
    }

    @Test
    void loginWithoutPasswordIs400() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("password"));
    }

    // ---- the rule matrix ----

    @Test
    void catalogueIsPublicForReading() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/{id}", productId)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    }

    // The role-protected endpoint of Practice 7
    @Test
    void deletingAProductIsForAdminsOnly() throws Exception {
        mvc.perform(delete("/api/v1/products/{id}", productId))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/v1/products/" + productId));

        mvc.perform(delete("/api/v1/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("customer", "customer123"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You do not have permission to do this"));

        mvc.perform(delete("/api/v1/products/{id}", productId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("admin", "admin123"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void placingAnOrderNeedsAnySignedInUser() throws Exception {
        String order = """
                {"customerName":"Aigerim","customerEmail":"aigerim@example.com","shippingAddress":"Almaty",
                 "items":[{"productId":%d,"quantity":1}]}
                """.formatted(productId);

        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(order))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Authentication is required")));

        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(order)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("customer", "customer123"))))
                .andExpect(status().isCreated());
    }

    @Test
    void onlyAdminsSeeAllOrders() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("customer", "customer123"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token("admin", "admin123"))))
                .andExpect(status().isOk());
    }

    // ---- the token itself ----

    // A JWT is signed, not encrypted: anyone can READ the payload, but changing it breaks the signature (slide 19)
    @Test
    void aTokenWhosePayloadWasEditedIsRejected() throws Exception {
        String[] parts = token("customer", "customer123").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(payload).contains("ROLE_CUSTOMER");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                payload.replace("ROLE_CUSTOMER", "ROLE_ADMIN").getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        mvc.perform(delete("/api/v1/products/{id}", productId).header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void garbageInTheAuthorizationHeaderIs401() throws Exception {
        mvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    // ---- method security: the rule holds even without the web layer (slide 15) ----

    @Test
    void theServiceRuleHoldsWithoutTheWebLayer() {
        assertThatThrownBy(() -> productService.delete(productId))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("customer", null, "ROLE_CUSTOMER"));
        assertThatThrownBy(() -> productService.delete(productId)).isInstanceOf(AccessDeniedException.class);
    }

    // ---- helpers ----

    private void createUser(String username, String password, boolean enabled, Role role) {
        Long id = jdbc.queryForObject(
                "INSERT INTO app_users (username, password_hash, enabled) VALUES (?, ?, ?) RETURNING id",
                Long.class, username, passwordEncoder.encode(password), enabled);
        jdbc.update("INSERT INTO user_roles (user_id, role) VALUES (?, ?)", id, role.name());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)));
    }

    private String token(String username, String password) throws Exception {
        String body = login(username, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
