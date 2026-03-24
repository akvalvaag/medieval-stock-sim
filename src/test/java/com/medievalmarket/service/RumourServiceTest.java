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
        // Run enough ticks to trigger generation (every ~20)
        for (int i = 0; i < 20; i++) service.processTick(p);
        assertThat(p.getRumours().size()).isLessThanOrEqualTo(3);
        assertThat(p.getRumours().size()).isGreaterThan(0);
    }

    @Test
    void processTick_expiresRumoursAfter30Ticks() {
        Portfolio p = merchant();
        // Force generation by calling processTick 20 times
        for (int i = 0; i < 20; i++) service.processTick(p);
        int count = p.getRumours().size();
        assertThat(count).isGreaterThan(0);
        // Run 30 more ticks — original rumours should expire
        for (int i = 0; i < 31; i++) service.processTick(p);
        // Expired rumours removed (new ones may have replaced them)
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
