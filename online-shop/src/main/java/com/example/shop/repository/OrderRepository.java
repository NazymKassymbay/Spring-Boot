package com.example.shop.repository;

import java.util.Collection;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.shop.domain.Order;
import com.example.shop.domain.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    // Locks the order row, so two concurrent status changes cannot both return stock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(o) > 0 from Order o join o.items i
            where o.status in :statuses and i.productId = :productId""")
    boolean existsByStatusInAndProductId(@Param("statuses") Collection<OrderStatus> statuses,
                                         @Param("productId") Long productId);
}
