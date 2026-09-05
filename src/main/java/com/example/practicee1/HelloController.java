package com.example.practicee1;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String hello() {
        return "Hello, Spring Boot!";
    }

    @GetMapping("/greet/{name}")
    public String greetByTime(@PathVariable String name) {
        int hour = LocalDateTime.now().getHour();
        String greeting = getGreeting(hour);

        return greeting + ", " + name + "!";
    }

    private String getGreeting(int hour) {
        if (hour >= 5 && hour < 12) {
            return "Good morning";
        } else if (hour >= 12 && hour < 18) {
            return "Good afternoon";
        } else if (hour >= 18 && hour < 22) {
            return "Good evening";
        } else {
            return "Good night";
        }
    }
}

