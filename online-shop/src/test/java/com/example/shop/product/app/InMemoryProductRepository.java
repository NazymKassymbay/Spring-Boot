package com.example.shop.product.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.example.shop.product.domain.Product;
import com.example.shop.product.domain.ProductRepository;

/**
 * The payoff of ports and adapters (lecture weeks 6-7, slide 26): a second adapter for the same port,
 * kept in a map, so the service is tested without Spring or a database.
 */
class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new HashMap<>();
    private final AtomicLong ids = new AtomicLong();

    @Override
    public Optional<Product> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Long> findIdBySku(String sku) {
        return store.values().stream()
                .filter(p -> p.getSku().equalsIgnoreCase(sku))
                .map(Product::getId)
                .findFirst();
    }

    @Override
    public Product save(Product p) {
        long id = p.getId() != null ? p.getId() : ids.incrementAndGet();
        Product saved = Product.restore(id, p.getSku(), p.getName(), p.getDescription(), p.getPrice(),
                p.getStockQuantity(), p.getCategory(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
        store.put(id, saved);
        return saved;
    }

    @Override
    public void deleteById(long id) {
        store.remove(id);
    }

    List<Product> all() {
        return new ArrayList<>(store.values());
    }
}
