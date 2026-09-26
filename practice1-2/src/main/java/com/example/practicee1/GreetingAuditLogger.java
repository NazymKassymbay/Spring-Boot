package com.example.practicee1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// Conditional bean: registered only when app.greeting.audit.enabled=true
@Component
@ConditionalOnProperty(prefix = "app.greeting.audit", name = "enabled", havingValue = "true")
public class GreetingAuditLogger implements GreetingListener {

    private static final Logger log = LoggerFactory.getLogger(GreetingAuditLogger.class);

    public GreetingAuditLogger() {
        log.info("Greeting audit is ENABLED");
    }

    @Override
    public void onGreeting(String name) {
        log.info("[AUDIT] Greeted user: {}", name);
    }
}
