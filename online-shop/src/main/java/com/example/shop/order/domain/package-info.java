/**
 * Domain model of the order context (lecture weeks 6-7, slides 18-22).
 *
 * <p>Plain Java: no Spring, no JPA, no Jackson. The Order aggregate guards its own rules
 * (status life cycle, at least one line, only finished orders can be deleted) and is unit-tested
 * with JUnit alone. Persistence lives in {@code order.infra} and depends on this package.
 */
package com.example.shop.order.domain;
