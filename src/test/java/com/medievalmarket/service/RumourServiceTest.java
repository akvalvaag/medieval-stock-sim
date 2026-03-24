package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assumptions.assumeThat;

class RumourServiceTest {

    private RumourService service;

    @BeforeEach
    void setUp() {
        service = new RumourService();
    }

    private Portfolio merchant() {
        return new Portfolio("s1", "Test", PlayerClass.MERCHANT);
    }

    @Test
    void processTick_fillsUpTo3RumourSlots() {
        Portfolio p = merchant();
        // One rumour added per 10 ticks; run 30 ticks to fill all 3 slots
        for (int i = 0; i < 30; i++) service.processTick(p);
        assertThat(p.getRumours().size()).isEqualTo(3);
    }

    @Test
    void processTick_addsSingleRumourPer10Ticks() {
        Portfolio p = merchant();
        for (int i = 0; i < 10; i++) service.processTick(p);
        assertThat(p.getRumours().size()).isEqualTo(1);
        for (int i = 0; i < 10; i++) service.processTick(p);
        assertThat(p.getRumours().size()).isEqualTo(2);
    }

    @Test
    void processTick_expiresRumoursAfter30Ticks() {
        Portfolio p = merchant();
        // Fill a slot at tick 10; the rumour has ticksRemaining=30
        for (int i = 0; i < 10; i++) service.processTick(p);
        assertThat(p.getRumours().size()).isGreaterThan(0);
        // Run 31 more ticks to expire it
        for (int i = 0; i < 31; i++) service.processTick(p);
        // All surviving rumours must still have time remaining
        p.getRumours().forEach(r -> assertThat(r.getTicksRemaining()).isGreaterThan(0));
    }

    @Test
    void tip_deductsGoldAndReturnsTipResult() {
        Portfolio p = merchant();
        p.setGold(100.0);
        for (int i = 0; i < 20; i++) service.processTick(p);
        assumeThat(p.getRumours()).isNotEmpty();
        String id = p.getRumours().get(0).getId();
        String result = service.tip(p, id);
        assertThat(result).isIn("RELIABLE", "DUBIOUS");
        assertThat(p.getGold()).isCloseTo(90.0, within(0.01)); // 10g cost
    }

    @Test
    void tip_scholarsGuildCosts5g() {
        Portfolio p = merchant();
        p.setGold(100.0);
        p.setGuild(Guild.SCHOLARS_GUILD);
        for (int i = 0; i < 20; i++) service.processTick(p);
        assumeThat(p.getRumours()).isNotEmpty();
        String id = p.getRumours().get(0).getId();
        service.tip(p, id);
        assertThat(p.getGold()).isCloseTo(95.0, within(0.01)); // 5g cost
    }

    @Test
    void tip_failsWhenInsufficientGold() {
        Portfolio p = merchant();
        p.setGold(5.0);
        for (int i = 0; i < 20; i++) service.processTick(p);
        assumeThat(p.getRumours()).isNotEmpty();
        String id = p.getRumours().get(0).getId();
        assertThatThrownBy(() -> service.tip(p, id))
            .isInstanceOf(RumourService.RumourException.class)
            .hasMessageContaining("funds");
    }

    @Test
    void tip_failsWhenAlreadyTipped() {
        Portfolio p = merchant();
        p.setGold(100.0);
        for (int i = 0; i < 20; i++) service.processTick(p);
        assumeThat(p.getRumours()).isNotEmpty();
        String id = p.getRumours().get(0).getId();
        service.tip(p, id);
        assertThatThrownBy(() -> service.tip(p, id))
            .isInstanceOf(RumourService.RumourException.class)
            .hasMessageContaining("already");
    }

    @Test
    void tip_failsWhenRumourNotFound() {
        Portfolio p = merchant();
        p.setGold(100.0);
        assertThatThrownBy(() -> service.tip(p, "nonexistent-id"))
            .isInstanceOf(RumourService.RumourException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void onEventFired_marksMatchingRumoursAsConfirmed() {
        Portfolio p = merchant();
        Rumour r = new Rumour(java.util.UUID.randomUUID().toString(), "Test rumour", "war", true, 30);
        p.addRumour(r);
        service.onEventFired("war", List.of(p));
        assertThat(p.getRumours().get(0).isConfirmed()).isTrue();
    }

    @Test
    void onEventFired_doesNotMarkNonMatchingRumours() {
        Portfolio p = merchant();
        Rumour r = new Rumour(java.util.UUID.randomUUID().toString(), "Test rumour", "spice", true, 30);
        p.addRumour(r);
        service.onEventFired("war", List.of(p));
        assertThat(p.getRumours().get(0).isConfirmed()).isFalse();
    }
}
