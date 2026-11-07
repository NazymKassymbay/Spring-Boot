package com.example.shop.common.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Practice 5: a logging and execution-time aspect for the service layer (lecture weeks 8-9, slide 24).
 *
 * <p>It wraps every public method of any {@code @Service} bean, measures how long it takes and logs
 * the method signature and the elapsed milliseconds. The business code stays free of timing code
 * (no tangling), and the same rule covers every service at once (no scattering).
 *
 * <p>Spring AOP advises only external calls to public methods that arrive through the proxy, so a
 * service method called from inside the same bean ({@code this.method()}) is not timed.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // run outside the transaction advice, so the time includes commit
public class ServiceTimingAspect {

    private static final Logger log = LoggerFactory.getLogger(ServiceTimingAspect.class);

    // Any public method of a bean annotated with @Service (ProductService, OrderService, CategoryService)
    @Around("@within(org.springframework.stereotype.Service) && execution(public * *(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        // nanoTime is a monotonic clock; currentTimeMillis can jump when the OS adjusts the time
        long startNanos = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            // Log the signature only, never the arguments: they may hold customer or order data
            log.info("{} took {} ms", joinPoint.getSignature().toShortString(), elapsedMs);
        }
    }
}
