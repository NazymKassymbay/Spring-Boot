package com.example.shop.order.infra;

import java.util.Optional;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.example.shop.common.exception.ResourceNotFoundException;
import com.example.shop.order.app.OrderQueries;
import com.example.shop.order.domain.Order;
import com.example.shop.order.domain.OrderRepository;
import com.example.shop.order.domain.OrderStatus;

/**
 * Adapter (lecture weeks 6-7, slide 26): implements the domain port and the read port
 * with Spring Data JPA and the mapper. The service never sees OrderEntity.
 */
@Repository
class JpaOrderRepository implements OrderRepository, OrderQueries {

    private final SpringDataOrders jpa;
    private final OrderEntityMapper mapper;

    JpaOrderRepository(SpringDataOrders jpa, OrderEntityMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    public Optional<Order> findById(long id) {
        return jpa.findById(id).map(mapper::toDomain);
    }

    @Override
    public Order save(Order order) {
        if (order.getId() == null) {
            // New order: cascade = ALL inserts the order and all its lines
            OrderEntity saved = jpa.save(mapper.toNewEntity(order));
            return mapper.toDomain(saved);
        }

        // Existing order: the entity is MANAGED, so dirty checking sends the UPDATE at commit
        OrderEntity entity = jpa.findById(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", order.getId()));
        if (entity.getVersion() != order.getVersion()) {
            // The domain copy was loaded before someone else changed the row
            throw new OptimisticLockingFailureException("Order " + order.getId() + " was changed by another request");
        }
        mapper.copyState(order, entity);
        return mapper.toDomain(entity);
    }

    @Override
    public void deleteById(long id) {
        jpa.deleteById(id);          // cascade = ALL also deletes the lines
    }

    @Override
    public Page<Order> page(OrderStatus status, Pageable pageable) {
        Page<OrderEntity> page = (status == null)
                ? jpa.findAll(pageable)
                : jpa.findByStatus(status, pageable);
        // Mapping reads the lazy items; default_batch_fetch_size loads them with one IN (...) query
        return page.map(mapper::toDomain);
    }
}
