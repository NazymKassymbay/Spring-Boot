package com.example.shop.order.infra;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shop.order.domain.OrderStatus;

// The Spring Data repository from Practice 4, now an implementation detail of the order adapters
public interface SpringDataOrders extends JpaRepository<OrderEntity, Long> {

    // Derived query with paging: where o.status = ?1
    Page<OrderEntity> findByStatus(OrderStatus status, Pageable pageable);

    // JPQL: is this product part of an order in one of the given statuses?
    @Query("""
            select count(o) > 0 from OrderEntity o join o.items i
            where o.status in :statuses and i.productId = :productId""")
    boolean existsByStatusInAndProductId(@Param("statuses") Collection<OrderStatus> statuses,
                                         @Param("productId") Long productId);
}
