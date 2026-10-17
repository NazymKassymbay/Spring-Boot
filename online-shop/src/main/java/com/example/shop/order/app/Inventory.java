package com.example.shop.order.app;

import com.example.shop.order.domain.OrderLine;

/**
 * Port to the product context. The order context needs two things from products:
 * take items out of stock (getting back the name and price to copy into the line),
 * and put them back when an order is cancelled. order.infra.ProductInventory implements it.
 */
public interface Inventory {

    // Throws a business-rule exception (409) if the product does not exist or there is not enough stock
    OrderLine reserve(long productId, int quantity);

    // Does nothing if the product was deleted in the meantime
    void release(long productId, int quantity);
}
