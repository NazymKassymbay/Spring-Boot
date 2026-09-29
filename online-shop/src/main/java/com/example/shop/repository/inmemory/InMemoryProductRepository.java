package com.example.shop.repository.inmemory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.example.shop.domain.Product;
import com.example.shop.repository.ProductRepository;

@Repository
public class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> storage = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Product save(Product product) {
        if (product.getId() == null) {
            product.setId(sequence.incrementAndGet());
        }
        storage.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Product> findAll() {
        List<Product> result = new ArrayList<>(storage.values());
        result.sort(Comparator.comparing(Product::getId));
        return result;
    }

    @Override
    public Optional<Product> findBySkuIgnoreCase(String sku) {
        return storage.values().stream()
                .filter(p -> p.getSku().equalsIgnoreCase(sku))
                .findFirst();
    }

    @Override
    public long countByCategoryId(Long categoryId) {
        return storage.values().stream()
                .filter(p -> Objects.equals(p.getCategoryId(), categoryId))
                .count();
    }

    @Override
    public void deleteById(Long id) {
        storage.remove(id);
    }
}
