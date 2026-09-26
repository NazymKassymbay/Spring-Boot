package com.example.practicee1;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    // Registers a system clock as a bean so services never call LocalDateTime.now() directly
    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of("Asia/Almaty"));
    }
}
