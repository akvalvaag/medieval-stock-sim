package com.medievalmarket.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PriceHistoryTest {

    private PriceHistory history() {
        return new PriceHistory(new GoodsCatalogue());
    }

    @Test
    void preSeededWith50BasePriceValues() {
        PriceHistory history = history();
        List<Double> grain = history.getHistory("Grain");
        assertThat(grain).hasSize(50);
        assertThat(grain).allMatch(v -> v == 10.0); // Grain base price = 10g
    }

    @Test
    void appendAddsToEnd() {
        PriceHistory history = history();
        history.append("Grain", 11.0);
        List<Double> h = history.getHistory("Grain");
        assertThat(h).hasSize(50);
        assertThat(h.get(49)).isEqualTo(11.0);
    }

    @Test
    void circularBufferDropsOldestWhenFull() {
        PriceHistory history = history();
        for (int i = 0; i < 50; i++) history.append("Grain", (double) i);
        List<Double> h = history.getHistory("Grain");
        assertThat(h).hasSize(50);
        assertThat(h.get(0)).isEqualTo(0.0);
        assertThat(h.get(49)).isEqualTo(49.0);
    }
}
