package com.example.shop.product.app;

// Port: a product cannot be deleted while an active order holds it, but orders live in another context.
// order.infra.JpaProductUsage implements it, so the dependency points from order to product.
public interface ProductUsage {

    boolean isInActiveOrder(long productId);
}
