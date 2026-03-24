package com.medievalmarket.dto;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class SessionUpdateFieldsTest {

    @Test
    void newFields_roundTripThroughBuilder() {
        SessionUpdate update = SessionUpdate.builder()
            .holdings(Map.of("Grain", 5))
            .costBasis(Map.of("Grain", 12.0))
            .ticksUntilProduction(3)
            .build();
        assertThat(update.getHoldings()).containsEntry("Grain", 5);
        assertThat(update.getCostBasis()).containsEntry("Grain", 12.0);
        assertThat(update.getTicksUntilProduction()).isEqualTo(3);
    }
}
