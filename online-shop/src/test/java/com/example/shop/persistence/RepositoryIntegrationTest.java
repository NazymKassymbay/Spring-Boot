package com.example.shop.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.shop.category.Category;
import com.example.shop.category.CategoryRepository;
import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderLine;
import com.example.shop.order.domain.OrderRepository;
import com.example.shop.order.domain.OrderStatus;
import com.example.shop.product.app.ProductQueries;
import com.example.shop.product.domain.CategoryRef;
import com.example.shop.product.domain.Product;
import com.example.shop.product.domain.ProductRepository;

/**
 * Repository integration test against the database we ship (lecture weeks 6-7, slide 16):
 * a real PostgreSQL 16 in Docker. Flyway migrates the container first, so every run also proves V1 works.
 * The test goes through the ports, so it checks the adapters and both mappers together.
 * Each test runs in a transaction that is rolled back at the end, so tests do not see each other's rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Testcontainers
@Transactional
class RepositoryIntegrationTest {

    @Container
    @ServiceConnection                          // container URL and credentials go into the DataSource
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    @Autowired
    private ProductRepository products;
    @Autowired
    private ProductQueries productQueries;
    @Autowired
    private OrderRepository orders;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private EntityManager em;
    @Autowired
    private JdbcTemplate jdbc;

    private CategoryRef laptops;
    private CategoryRef books;

    @BeforeEach
    void twoCategories() {
        laptops = category("Laptops");
        books = category("Books");
    }

    private CategoryRef category(String name) {
        Category category = new Category(name, null);
        category.setCreatedAt(NOW);
        category.setUpdatedAt(NOW);
        Category saved = categories.save(category);
        return new CategoryRef(saved.getId(), saved.getName());
    }

    private Product saveProduct(String sku, String price, int stock, CategoryRef category) {
        return products.save(Product.create(sku, "Product " + sku, null, new BigDecimal(price), stock, category, NOW));
    }

    // Forces the SQL out now and empties the persistence context, so the next read really hits PostgreSQL
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    @Test
    void productSurvivesARoundTripThroughBothMappers() {
        Product saved = saveProduct("LAP-MBA-13", "649990.00", 5, laptops);
        flushAndClear();

        Product loaded = products.findById(saved.getId()).orElseThrow();

        assertThat(saved.getId()).isNotNull();                        // PostgreSQL gave the id (IDENTITY)
        assertThat(loaded.getSku()).isEqualTo("LAP-MBA-13");
        assertThat(loaded.getPrice()).isEqualByComparingTo("649990.00");
        assertThat(loaded.getStockQuantity()).isEqualTo(5);
        assertThat(loaded.getCategory()).isEqualTo(laptops);           // value object: equal by value
    }

    @Test
    void skuLookupIgnoresLetterCase() {
        Product saved = saveProduct("LAP-MBA-13", "649990.00", 5, laptops);

        assertThat(products.findIdBySku("lap-mba-13")).contains(saved.getId());
        assertThat(products.findIdBySku("NOPE")).isEmpty();
    }

    @Test
    void pagingFilteringAndSortingHappenInPostgres() {
        saveProduct("LAP-1", "300000.00", 1, laptops);
        saveProduct("LAP-2", "900000.00", 1, laptops);
        saveProduct("LAP-3", "500000.00", 1, laptops);
        saveProduct("BK-1", "10000.00", 1, books);
        flushAndClear();

        Page<Product> page = productQueries.page(laptops.id(), PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "price")));

        assertThat(page.getContent()).extracting(Product::getSku).containsExactly("LAP-2", "LAP-3");
        assertThat(page.getTotalElements()).isEqualTo(3);              // the COUNT query
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void staleCopyOfAProductIsRejected() {
        long id = saveProduct("LAP-MBA-13", "649990.00", 5, laptops).getId();
        flushAndClear();
        Product first = products.findById(id).orElseThrow();
        Product stale = products.findById(id).orElseThrow();       // both copies carry version 0

        first.reserve(1, NOW);
        products.save(first);
        em.flush();                                                // UPDATE ... version 0 -> 1

        stale.reserve(1, NOW);
        assertThatThrownBy(() -> products.save(stale))              // still carries version 0
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void databaseRejectsNegativeStockEvenIfJavaIsSkipped() {
        long id = saveProduct("LAP-MBA-13", "649990.00", 5, laptops).getId();
        flushAndClear();

        // Product.reserve() would never allow this; the CHECK constraint from V1 is the last line of defence
        assertThatThrownBy(() -> jdbc.update("UPDATE products SET stock_quantity = -1 WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void orderAndItsLinesAreSavedTogether() {
        long macBookId = saveProduct("LAP-MBA-13", "649990.00", 5, laptops).getId();
        OrderLine line = new OrderLine(macBookId, "MacBook Air 13", new BigDecimal("649990.00"), 2);

        Order saved = orders.save(Order.place("Nazym", "nazym@example.com", "Almaty", List.of(line), NOW));
        flushAndClear();

        Order loaded = orders.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(loaded.getLines()).containsExactly(line);          // cascade = ALL inserted the line
        assertThat(jdbc.queryForObject("select count(*) from order_items where order_id = ?", Long.class,
                saved.getId())).isEqualTo(1L);
    }

    @Test
    void statusChangeKeepsTheLines() {
        long macBookId = saveProduct("LAP-MBA-13", "649990.00", 5, laptops).getId();
        OrderLine line = new OrderLine(macBookId, "MacBook Air 13", new BigDecimal("649990.00"), 1);
        long orderId = orders.save(Order.place("Nazym", "nazym@example.com", "Almaty", List.of(line), NOW)).getId();
        flushAndClear();

        Order order = orders.findById(orderId).orElseThrow();
        order.changeStatus(OrderStatus.PAID, NOW);
        orders.save(order);
        flushAndClear();

        Order reloaded = orders.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(reloaded.getVersion()).isEqualTo(1);                // @Version went up
        assertThat(reloaded.getLines()).containsExactly(line);
    }
}
