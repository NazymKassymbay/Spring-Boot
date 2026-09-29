package com.example.shop.repository.inmemory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.example.shop.domain.Category;
import com.example.shop.repository.CategoryRepository;

// Temporary storage until PostgreSQL + Spring Data JPA arrive in Practice 4
@Repository
public class InMemoryCategoryRepository implements CategoryRepository {

    private final Map<Long, Category> storage = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Category save(Category category) {
        if (category.getId() == null) {
            category.setId(sequence.incrementAndGet());
        }
        storage.put(category.getId(), category);
        return category;
    }

    @Override
    public Optional<Category> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Category> findAll() {
        List<Category> result = new ArrayList<>(storage.values());
        result.sort(Comparator.comparing(Category::getId));
        return result;
    }

    @Override
    public Optional<Category> findByNameIgnoreCase(String name) {
        return storage.values().stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    @Override
    public void deleteById(Long id) {
        storage.remove(id);
    }
}
