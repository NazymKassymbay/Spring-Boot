package com.example.shop.repository;

import java.util.List;
import java.util.Optional;

import com.example.shop.domain.Order;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    List<Order> findAll();

    void deleteById(Long id);
}
