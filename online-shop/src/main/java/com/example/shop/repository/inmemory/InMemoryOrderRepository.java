package com.example.shop.repository.inmemory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Repository;

import com.example.shop.domain.Order;
import com.example.shop.repository.OrderRepository;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<Long, Order> storage = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            order.setId(sequence.incrementAndGet());
        }
        storage.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<Order> findAll() {
        List<Order> result = new ArrayList<>(storage.values());
        result.sort(Comparator.comparing(Order::getId));
        return result;
    }

    @Override
    public void deleteById(Long id) {
        storage.remove(id);
    }
}
