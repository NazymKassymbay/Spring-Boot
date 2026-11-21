package com.example.shop.product.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.shop.common.exception.BadRequestException;
import com.example.shop.common.exception.BusinessRuleException;
import com.example.shop.common.exception.ConflictException;
import com.example.shop.common.exception.ResourceNotFoundException;
import com.example.shop.product.domain.CategoryRef;
import com.example.shop.product.domain.InsufficientStockException;
import com.example.shop.product.domain.Product;

/**
 * Unit test of the application service. Everything is wired by hand:
 * an in-memory repository, lambdas for the small ports and a fixed clock.
 * No Spring context and no database, so it runs in milliseconds.
 */
class ProductServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");
    private static final CategoryRef LAPTOPS = new CategoryRef(1, "Laptops");

    private final InMemoryProductRepository products = new InMemoryProductRepository();
    private final CategoryCatalog categories = id -> id == 1 ? Optional.of(LAPTOPS) : Optional.empty();
    private final Set<Long> productsInActiveOrders = new HashSet<>();
    private final ProductUsage usage = productsInActiveOrders::contains;
    private final ProductQueries queries = (categoryId, pageable) -> {
        throw new UnsupportedOperationException("not used in these tests");
    };
    private final ProductService service = new ProductService(products, queries, categories, usage,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private ProductDetails macBook(String sku, int stock) {
        return new ProductDetails(sku, "MacBook Air 13", "Apple M3", new BigDecimal("649990.00"), stock, 1);
    }

    @Test
    void createsAProductInAnExistingCategory() {
        Product created = service.create(macBook("LAP-MBA-13", 5));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getCategory()).isEqualTo(LAPTOPS);
        assertThat(created.getCreatedAt()).isEqualTo(NOW);   // time comes from the injected clock
    }

    @Test
    void rejectsAnUnknownCategory() {
        ProductDetails details = new ProductDetails("BK-1", "Book", null, BigDecimal.TEN, 1, 99);

        assertThatThrownBy(() -> service.create(details))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Category 99 does not exist");
        assertThat(products.all()).isEmpty();
    }

    @Test
    void rejectsADuplicateSkuIgnoringLetterCase() {
        service.create(macBook("LAP-MBA-13", 5));

        assertThatThrownBy(() -> service.create(macBook("lap-mba-13", 1)))
                .isInstanceOf(ConflictException.class);
        assertThat(products.all()).hasSize(1);
    }

    @Test
    void updateMayKeepItsOwnSku() {
        Product created = service.create(macBook("LAP-MBA-13", 5));

        Product updated = service.update(created.getId(), macBook("LAP-MBA-13", 7));

        assertThat(updated.getStockQuantity()).isEqualTo(7);
    }

    @Test
    void adjustStockAddsAndWritesOff() {
        long id = service.create(macBook("LAP-MBA-13", 5)).getId();

        assertThat(service.adjustStock(id, 3, "delivery").getStockQuantity()).isEqualTo(8);
        assertThat(service.adjustStock(id, -6, "write-off").getStockQuantity()).isEqualTo(2);
    }

    @Test
    void stockCannotGoBelowZero() {
        long id = service.create(macBook("LAP-MBA-13", 2)).getId();

        assertThatThrownBy(() -> service.adjustStock(id, -3, "write-off"))
                .isInstanceOf(InsufficientStockException.class);
        assertThat(products.findById(id).orElseThrow().getStockQuantity()).isEqualTo(2);
    }

    @Test
    void zeroDeltaIsABadRequest() {
        long id = service.create(macBook("LAP-MBA-13", 2)).getId();

        assertThatThrownBy(() -> service.adjustStock(id, 0, null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void cannotDeleteAProductHeldByAnActiveOrder() {
        long id = service.create(macBook("LAP-MBA-13", 2)).getId();
        productsInActiveOrders.add(id);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ConflictException.class);
        assertThat(products.findById(id)).isPresent();
    }

    @Test
    void deletesAFreeProduct() {
        long id = service.create(macBook("LAP-MBA-13", 2)).getId();

        service.delete(id);

        assertThat(products.findById(id)).isEmpty();
    }

    @Test
    void unknownProductIsNotFound() {
        assertThatThrownBy(() -> service.findById(42)).isInstanceOf(ResourceNotFoundException.class);
    }
}
