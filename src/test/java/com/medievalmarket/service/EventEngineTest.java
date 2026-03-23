// src/test/java/com/medievalmarket/service/EventEngineTest.java
package com.medievalmarket.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventEngineTest {

    @Test
    void maybeFireEventReturnsNullMostOfTheTime() {
        EventEngine engine = new EventEngine();
        long nullCount = java.util.stream.IntStream.range(0, 1000)
            .mapToObj(i -> engine.maybeFireEvent())
            .filter(e -> e == null)
            .count();
        // ~96% chance of null per tick; over 1000 ticks expect at least 900 nulls
        assertThat(nullCount).isGreaterThan(900);
    }

    @Test
    void firedEventHasNonNullMessageAndModifiers() {
        EventEngine engine = new EventEngine();
        EventEngine.FiredEvent event = null;
        for (int i = 0; i < 200 && event == null; i++) {
            event = engine.maybeFireEvent();
        }
        assertThat(event).isNotNull();
        assertThat(event.message()).isNotBlank();
        assertThat(event.modifiers()).isNotEmpty();
    }

    @Test
    void firedEventModifiersAreWithinExpectedRange() {
        EventEngine engine = new EventEngine();
        for (int attempt = 0; attempt < 500; attempt++) {
            EventEngine.FiredEvent event = engine.maybeFireEvent();
            if (event != null) {
                event.modifiers().values().forEach(modifier ->
                    assertThat(Math.abs(modifier)).isLessThanOrEqualTo(0.45)
                );
                return;
            }
        }
    }
}
