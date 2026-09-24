package com.example.practicee1;

import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class GreetingService {

    private final GreetingProperties greetingProperties;

    public GreetingService(GreetingProperties greetingProperties) {
        this.greetingProperties = greetingProperties;
    }

    public String greetByTime(String name) {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();

        String timeOfDay = getTimeOfDay(hour);
        String formattedTime = formatTime(now);

        return "Hello " + name + ", " + timeOfDay + ", right now it's "
                + formattedTime + " (server: " + greetingProperties.getDefaultName() + ")";
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
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm");
            return time.format(formatter) + period;
        }
    }
}