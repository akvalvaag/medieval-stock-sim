package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void processTick_fillsUpTo2RumourSlots() {
        for (int i = 0; i < 60; i++) service.processTick();
        assertThat(service.getRumours().size()).isEqualTo(2);
    }

    @Test
    void processTick_addsSingleRumourPer15Ticks() {
        for (int i = 0; i < 15; i++) service.processTick();
        assertThat(service.getRumours().size()).isEqualTo(1);
        for (int i = 0; i < 15; i++) service.processTick();
        assertThat(service.getRumours().size()).isEqualTo(2);
    }

    @Test
    void processTick_removesExpiredRumours() {
        // Inject a rumour with a known short TTL and verify it is removed after expiry
        Rumour r = new Rumour(java.util.UUID.randomUUID().toString(), "Test", "war", true, 5);
        service.injectRumourForTesting(r);
        assertThat(service.getRumours().size()).isEqualTo(1);
        // Run 4 ticks — rumour should still be alive (ticksRemaining=1)
        for (int i = 0; i < 4; i++) service.processTick();
        assertThat(service.getRumours().size()).isEqualTo(1);
        // 5th tick brings ticksRemaining to 0 — removed
        service.processTick();
        assertThat(service.getRumours().size()).isEqualTo(0);
    }

    @Test
    void processTick_noDuplicateEventKeys() {
        for (int i = 0; i < 60; i++) service.processTick();
        long distinctKeys = service.getRumours().stream()
            .map(Rumour::getEventKey).distinct().count();
        assertThat(distinctKeys).isEqualTo(service.getRumours().size());
    }

    @Test
    void tip_deductsGoldAndReturnsTipResult() {
        Portfolio p = merchant();
        p.setGold(100.0);
        for (int i = 0; i < 20; i++) service.processTick();
        assumeThat(service.getRumours()).isNotEmpty();
        String id = service.getRumours().get(0).getId();
        String result = service.tip(p, id);
        assertThat(result).isIn("RELIABLE", "DUBIOUS");
        assertThat(p.getTipResult(id)).isEqualTo(result);
        assertThat(p.getGold()).isCloseTo(90.0, within(0.01));
    }

    @Test
    void tip_scholarsGuildCosts5g() {
        Portfolio p = merchant();
        p.setGold(100.0);
        p.setGuild(Guild.SCHOLARS_GUILD);
        for (int i = 0; i < 20; i++) service.processTick();
        assumeThat(service.getRumours()).isNotEmpty();
        String id = service.getRumours().get(0).getId();
        service.tip(p, id);
        assertThat(p.getGold()).isCloseTo(95.0, within(0.01));
    }

    @Test
    void tip_failsWhenInsufficientGold() {
        Portfolio p = merchant();
        p.setGold(5.0);
        for (int i = 0; i < 20; i++) service.processTick();
        assumeThat(service.getRumours()).isNotEmpty();
        String id = service.getRumours().get(0).getId();
        assertThatThrownBy(() -> service.tip(p, id))
            .isInstanceOf(RumourService.RumourException.class)
            .hasMessageContaining("funds");
    }

    @Test
    void tip_failsWhenAlreadyTipped() {
        Portfolio p = merchant();
        p.setGold(100.0);
        for (int i = 0; i < 20; i++) service.processTick();
        assumeThat(service.getRumours()).isNotEmpty();
        String id = service.getRumours().get(0).getId();
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
    void onEventFired_removesMatchingRumour() {
        Rumour r = new Rumour(java.util.UUID.randomUUID().toString(), "Test", "war", true, 30);
        service.injectRumourForTesting(r);
        assertThat(service.getRumours().size()).isEqualTo(1);
        service.onEventFired("war");
        assertThat(service.getRumours().size()).isEqualTo(0);
    }

    @Test
    void onEventFired_doesNotRemoveNonMatchingRumour() {
        Rumour r = new Rumour(java.util.UUID.randomUUID().toString(), "Test", "spice", true, 30);
        service.injectRumourForTesting(r);
        service.onEventFired("war");
        assertThat(service.getRumours().size()).isEqualTo(1);
    }
}
