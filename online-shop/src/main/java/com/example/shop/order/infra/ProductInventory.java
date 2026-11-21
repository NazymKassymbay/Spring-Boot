package com.example.shop.order.infra;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.example.shop.common.exception.BusinessRuleException;
import com.example.shop.order.app.Inventory;
import com.example.shop.order.domain.OrderLine;
import com.example.shop.product.domain.Product;
import com.example.shop.product.domain.ProductRepository;

/**
 * Translates between the order context and the product context: the only order class that knows
 * products exist. The stock rule itself stays in Product.reserve(), so it is written once.
 * Runs inside OrderService's transaction, so a failed line rolls back the stock of all earlier lines.
 */
@Component
class ProductInventory implements Inventory {

    private final ProductRepository products;
    private final Clock clock;

    ProductInventory(ProductRepository products, Clock clock) {
        this.products = products;
        this.clock = clock;
    }

    @Override
    public OrderLine reserve(long productId, int quantity) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new BusinessRuleException("Product " + productId + " does not exist"));
        product.reserve(quantity, Instant.now(clock));   // throws InsufficientStockException -> rollback -> 409
        products.save(product);
        return new OrderLine(product.getId(), product.getName(), product.getPrice(), quantity);
    }

    @Override
    public void release(long productId, int quantity) {
        products.findById(productId).ifPresent(product -> {
            product.release(quantity, Instant.now(clock));
            products.save(product);
        });
    }
}
