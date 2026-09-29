package com.example.shop.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.shop.config.ShopProperties;
import com.example.shop.domain.Order;
import com.example.shop.domain.OrderItem;
import com.example.shop.domain.OrderStatus;
import com.example.shop.domain.Product;
import com.example.shop.exception.BusinessRuleException;
import com.example.shop.exception.ConflictException;
import com.example.shop.exception.InsufficientStockException;
import com.example.shop.exception.ResourceNotFoundException;
import com.example.shop.mapper.OrderMapper;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.web.dto.CreateOrderRequest;
import com.example.shop.web.dto.OrderItemRequest;
import com.example.shop.web.dto.OrderResponse;
import com.example.shop.web.dto.PageResponse;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderMapper orderMapper;
    private final ShopProperties shopProperties;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        OrderMapper orderMapper,
                        ShopProperties shopProperties,
                        Clock clock) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.orderMapper = orderMapper;
        this.shopProperties = shopProperties;
        this.clock = clock;
    }

    public PageResponse<OrderResponse> search(OrderStatus status, String customerEmail, int page, int size) {
        List<Order> matching = orderRepository.findAll().stream()
                .filter(o -> status == null || o.getStatus() == status)
                .filter(o -> customerEmail == null || o.getCustomerEmail().equalsIgnoreCase(customerEmail.trim()))
                .sorted(Comparator.comparing(Order::getCreatedAt).reversed())
                .toList();
        return PageResponse.of(matching, page, size, orderMapper::toResponse);
    }

    public OrderResponse findById(Long id) {
        return orderMapper.toResponse(getOrder(id));
    }

    // Creates an order and reserves stock for every line. Either all lines are reserved or none.
    // The lock stands in for a database transaction until JPA + @Transactional arrive.
    public OrderResponse create(CreateOrderRequest request) {
        Map<Long, Integer> quantities = mergeQuantities(request.items());
        if (quantities.size() > shopProperties.getMaxItemsPerOrder()) {
            throw new BusinessRuleException("An order may contain at most " + shopProperties.getMaxItemsPerOrder()
                    + " different products, got " + quantities.size());
        }

        synchronized (productRepository) {
            Map<Product, Integer> lines = new LinkedHashMap<>();
            quantities.forEach((productId, quantity) -> {
                Product product = productRepository.findById(productId)
                        .orElseThrow(() -> new BusinessRuleException("Product " + productId + " does not exist"));
                if (product.getStockQuantity() < quantity) {
                    throw new InsufficientStockException(productId, product.getName(), quantity,
                            product.getStockQuantity());
                }
                lines.put(product, quantity);
            });

            Order order = new Order(request.customerName().trim(), request.customerEmail().trim(),
                    request.shippingAddress().trim());
            Instant now = Instant.now(clock);
            lines.forEach((product, quantity) -> {
                product.reserve(quantity);
                product.setUpdatedAt(now);
                productRepository.save(product);
                order.addItem(new OrderItem(product.getId(), product.getName(), product.getPrice(), quantity));
            });
            order.setCreatedAt(now);
            order.setUpdatedAt(now);

            Order saved = orderRepository.save(order);
            log.info("Order created: id={}, items={}, total={}", saved.getId(), saved.getItems().size(),
                    saved.getTotalAmount());
            return orderMapper.toResponse(saved);
        }
    }

    public OrderResponse changeStatus(Long id, OrderStatus newStatus) {
        synchronized (productRepository) {
            Order order = getOrder(id);
            OrderStatus previous = order.getStatus();
            order.changeStatus(newStatus);

            if (newStatus == OrderStatus.CANCELLED) {
                returnItemsToStock(order);
            }
            order.setUpdatedAt(Instant.now(clock));
            log.info("Order {} status: {} -> {}", id, previous, newStatus);
            return orderMapper.toResponse(orderRepository.save(order));
        }
    }

    // Only finished orders (cancelled or delivered) can be removed from history
    public void delete(Long id) {
        Order order = getOrder(id);
        if (order.getStatus() != OrderStatus.CANCELLED && order.getStatus() != OrderStatus.DELIVERED) {
            throw new ConflictException("Order " + id + " is " + order.getStatus()
                    + "; only CANCELLED or DELIVERED orders can be deleted");
        }
        orderRepository.deleteById(id);
        log.info("Order deleted: id={}", id);
    }

    private Order getOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    // The same product listed twice becomes one line with the summed quantity
    private Map<Long, Integer> mergeQuantities(List<OrderItemRequest> items) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            result.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return result;
    }

    private void returnItemsToStock(Order order) {
        Instant now = Instant.now(clock);
        for (OrderItem item : order.getItems()) {
            // The product may have been deleted after the order was finished; nothing to return then
            productRepository.findById(item.getProductId()).ifPresent(product -> {
                product.release(item.getQuantity());
                product.setUpdatedAt(now);
                productRepository.save(product);
            });
        }
    }
}
