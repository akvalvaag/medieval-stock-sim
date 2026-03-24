package com.medievalmarket.service;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class EventEngineTest {

    @Test
    void maybeFireEventReturnsNullMostOfTheTime() {
        EventEngine engine = new EventEngine();
        long nullCount = java.util.stream.IntStream.range(0, 1000)
            .mapToObj(i -> engine.maybeFireEvent(Set.of()))
            .filter(e -> e == null)
            .count();
        assertThat(nullCount).isGreaterThan(900);
    }

    @Test
    void firedEventHasNonNullMessageAndModifiers() {
        EventEngine engine = new EventEngine();
        EventEngine.FiredEvent event = null;
        for (int i = 0; i < 200 && event == null; i++) {
            event = engine.maybeFireEvent(Set.of());
        }
        assertThat(event).isNotNull();
        assertThat(event.message()).isNotBlank();
        assertThat(event.modifiers()).isNotEmpty();
    }

    @Test
    void firedEventModifiersAreWithinExpectedRange() {
        EventEngine engine = new EventEngine();
        for (int attempt = 0; attempt < 500; attempt++) {
            EventEngine.FiredEvent event = engine.maybeFireEvent(Set.of());
            if (event != null) {
                event.modifiers().values().forEach(modifier ->
                    assertThat(Math.abs(modifier)).isLessThanOrEqualTo(0.50)
                );
                return;
            }
        }
    }

    @Test
    void boostedKeyFiresSignificantlyMoreOften() {
        EventEngine engine = new EventEngine();
        Set<String> boosted = Set.of("war");
        int warCount = 0;
        int collected = 0;
        for (int i = 0; collected < 200; i++) {
            EventEngine.FiredEvent event = engine.maybeFireEvent(boosted);
            if (event != null) {
                if ("war".equals(event.key())) warCount++;
                collected++;
            }
        }
        assertThat(warCount).isGreaterThanOrEqualTo(30);
    }
}
