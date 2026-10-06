package com.example.shop;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.jayway.jsonpath.JsonPath;

// End-to-end check of the main business flow through the real HTTP layer and a real PostgreSQL.
// The database is started in Docker once per class; Flyway creates the schema exactly as in production.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class ShopApiIntegrationTest {

    // @ServiceConnection points spring.datasource.* at this container
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Every test starts with empty tables and ids from 1
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE order_items, orders, products, categories RESTART IDENTITY CASCADE");
    }

    @Test
    void orderReservesStockAndCancellationReturnsIt() throws Exception {
        long categoryId = createCategory("Laptops");
        long productId = createProduct("LAP-001", "150000.00", 5, categoryId);

        MvcResult orderResult = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"Nazym","customerEmail":"nazym@example.com",
                                 "shippingAddress":"Almaty, Tole bi 59",
                                 "items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":1}]}
                                """.formatted(productId, productId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/orders/")))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.totalAmount").value(450000.00))
                .andReturn();
        long orderId = idOf(orderResult);

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(jsonPath("$.stockQuantity").value(2));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(jsonPath("$.stockQuantity").value(5));
    }

    @Test
    void notEnoughStockReturns409AndReservesNothing() throws Exception {
        long categoryId = createCategory("Phones");
        long phone = createProduct("PHN-001", "300000.00", 10, categoryId);
        long rare = createProduct("PHN-002", "500000.00", 1, categoryId);

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"A","customerEmail":"a@example.com","shippingAddress":"Astana",
                                 "items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":3}]}
                                """.formatted(phone, rare)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("Not enough stock")));

        mockMvc.perform(get("/api/v1/products/{id}", phone))
                .andExpect(jsonPath("$.stockQuantity").value(10));
    }

    @Test
    void illegalStatusJumpReturns409() throws Exception {
        long categoryId = createCategory("Books");
        long productId = createProduct("BK-001", "5000.00", 3, categoryId);
        MvcResult order = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerName":"B","customerEmail":"b@example.com","shippingAddress":"Shymkent",
                                 "items":[{"productId":%d,"quantity":1}]}
                                """.formatted(productId)))
                .andReturn();

        mockMvc.perform(patch("/api/v1/orders/{id}/status", idOf(order))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"));
    }

    @Test
    void invalidBodyReturnsFieldViolations() throws Exception {
        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"bad sku","name":"","price":-1,"stockQuantity":-5}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/v1/products"))
                .andExpect(jsonPath("$.violations", hasSize(5)));
    }

    @Test
    void duplicateSkuReturns409AndUnknownIdReturns404() throws Exception {
        long categoryId = createCategory("Misc");
        createProduct("DUP-001", "100.00", 1, categoryId);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("DUP-001", "100.00", 1, categoryId)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/products/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product with id 999 not found"));
    }

    @Test
    void categoryWithProductsCannotBeDeleted() throws Exception {
        long categoryId = createCategory("Audio");
        long productId = createProduct("AUD-001", "20000.00", 2, categoryId);

        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/v1/products/{id}", productId))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/categories/{id}", categoryId))
                .andExpect(status().isNoContent());
    }

    @Test
    void unsupportedAcceptHeaderReturns406InsteadOf500() throws Exception {
        mockMvc.perform(get("/api/v1/categories").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(jsonPath("$.status").value(406));
    }

    @Test
    void hugePageNumberReturnsEmptyPageInsteadOf500() throws Exception {
        mockMvc.perform(get("/api/v1/products").param("page", "30000000").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void productsAreFilteredSortedAndPagedByTheDatabase() throws Exception {
        long books = createCategory("Books");
        long other = createCategory("Other");
        createProduct("BK-001", "3000.00", 1, books);
        createProduct("BK-002", "1000.00", 0, books);
        createProduct("BK-003", "2000.00", 4, books);
        createProduct("OT-001", "500.00", 9, other);

        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", String.valueOf(books))
                        .param("sort", "price,desc")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].sku").value("BK-001"))
                .andExpect(jsonPath("$.content[1].sku").value("BK-003"))
                .andExpect(jsonPath("$.content[0].categoryName").value("Books"));

        mockMvc.perform(get("/api/v1/products")
                        .param("categoryId", String.valueOf(books))
                        .param("sort", "price,desc")
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].sku").value("BK-002"));

        mockMvc.perform(get("/api/v1/products").param("q", "bk-00").param("inStock", "true").param("maxPrice", "2500"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("BK-003"));
    }

    private long createCategory(String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long createProduct(String sku, String price, int stock, long categoryId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(sku, price, stock, categoryId)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private String productJson(String sku, String price, int stock, long categoryId) {
        return """
                {"sku":"%s","name":"Product %s","price":%s,"stockQuantity":%d,"categoryId":%d}
                """.formatted(sku, sku, price, stock, categoryId);
    }

    private long idOf(MvcResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
