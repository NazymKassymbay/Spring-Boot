package com.example.shop.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    // Services take time from this bean, so tests can replace it with Clock.fixed(...)
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
