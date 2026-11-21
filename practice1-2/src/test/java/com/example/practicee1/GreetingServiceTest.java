package com.example.practicee1;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

// Plain unit test: no Spring context, dependencies are passed by hand
class GreetingServiceTest {

    private GreetingProperties properties() {
        GreetingProperties props = new GreetingProperties();
        props.setDefaultName("Test-Server");
        return props;
    }

    @Test
    void saysGoodMorningAtNineAndNotifiesListeners() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-26T09:00:00Z"), ZoneOffset.UTC);
        List<String> audited = new ArrayList<>();
        GreetingListener fakeListener = audited::add;

        GreetingService service = new GreetingService(properties(), fixedClock, List.of(fakeListener));
        String result = service.greetByTime("Nazym");

        assertThat(result).contains("good morning").contains("9am").contains("Test-Server");
        assertThat(audited).containsExactly("Nazym");
    }

    @Test
    void worksWithoutAnyListeners() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-09-26T19:30:00Z"), ZoneOffset.UTC);

        GreetingService service = new GreetingService(properties(), fixedClock, List.of());
        String result = service.greetByTime("Nazym");

        assertThat(result).contains("good evening").contains("7:30pm");
    }
}