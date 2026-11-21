package com.example.practicee1;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    private static final Logger log = LoggerFactory.getLogger(GreetingService.class);

    private final GreetingProperties greetingProperties;
    private final Clock clock;
    private final List<GreetingListener> listeners;

    // Constructor injection: every dependency is explicit, final and supplied by the container
    public GreetingService(GreetingProperties greetingProperties,
                           Clock clock,
                           List<GreetingListener> listeners) {
        this.greetingProperties = greetingProperties;
        this.clock = clock;
        this.listeners = listeners;
        log.info("GreetingService created with {} greeting listener(s)", listeners.size());
    }

    public String greetByTime(String name) {
        // Time comes from the injected clock, so tests can freeze it
        LocalDateTime now = LocalDateTime.now(clock);
        int hour = now.getHour();

        String timeOfDay = getTimeOfDay(hour);
        String formattedTime = formatTime(now);

        String greeting = "Hello " + name + ", " + timeOfDay + ", right now it's "
                + formattedTime + " (server: " + greetingProperties.getDefaultName() + ")";

        // Notify every registered listener; the list is empty when audit is disabled
        listeners.forEach(listener -> listener.onGreeting(name));

        return greeting;
    }

    private String getTimeOfDay(int hour) {
        if (hour >= 5 && hour < 12) {
            return "good morning";
        } else if (hour >= 12 && hour < 18) {
            return "good afternoon";
        } else if (hour >= 18 && hour < 22) {
            return "good evening";
        } else {
            return "good night";
        }
    }

    private String formatTime(LocalDateTime time) {
        int hour = time.getHour();
        int minute = time.getMinute();

        String period = hour < 12 ? "am" : "pm";

        int hour12 = hour % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }

        if (minute == 0) {
            return hour12 + period;
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm");
        return time.format(formatter) + period;
    }
}