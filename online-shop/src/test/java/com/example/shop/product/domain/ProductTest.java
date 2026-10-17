package com.example.shop.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

// The aggregate protects its rules by itself. Plain JUnit: no Spring, no database, no mocks.
class ProductTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final CategoryRef LAPTOPS = new CategoryRef(1, "Laptops");

    private Product macBook(int stock) {
        return Product.create("LAP-MBA-13", "MacBook Air 13", "Apple M3", new BigDecimal("649990.00"),
                stock, LAPTOPS, NOW);
    }

    @Test
    void newProductHasNoIdAndStartsAtVersionZero() {
        Product product = macBook(5);

        assertThat(product.getId()).isNull();
        assertThat(product.getVersion()).isZero();
        assertThat(product.getCreatedAt()).isEqualTo(NOW);
        assertThat(product.isInStock()).isTrue();
    }

    @Test
    void reserveTakesItemsOutOfStock() {
        Product product = macBook(5);
        Instant later = NOW.plusSeconds(60);

        product.reserve(2, later);

        assertThat(product.getStockQuantity()).isEqualTo(3);
        assertThat(product.getUpdatedAt()).isEqualTo(later);
    }

    @Test
    void cannotReserveMoreThanInStock() {
        Product product = macBook(1);

        assertThatThrownBy(() -> product.reserve(2, NOW))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 2, available 1");
        assertThat(product.getStockQuantity()).isEqualTo(1);   // nothing changed
    }

    @Test
    void releasePutsItemsBack() {
        Product product = macBook(0);

        product.release(4, NOW);

        assertThat(product.getStockQuantity()).isEqualTo(4);
        assertThat(product.isInStock()).isTrue();
    }

    @Test
    void quantityMustBePositive() {
        Product product = macBook(5);

        assertThatThrownBy(() -> product.reserve(0, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> product.release(-1, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void priceMustBeAboveZero() {
        assertThatThrownBy(() -> Product.create("BK-1", "Book", null, BigDecimal.ZERO, 1, LAPTOPS, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("price");
    }

    @Test
    void changeDetailsChecksTheRulesAgain() {
        Product product = macBook(5);

        assertThatThrownBy(() -> product.changeDetails("LAP-MBA-13", "MacBook", null,
                new BigDecimal("1.00"), -1, LAPTOPS, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stockQuantity");
    }
}
