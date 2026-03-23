package com.medievalmarket.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SeasonEngineTest {

    @Test
    void startsInSpring() {
        SeasonEngine engine = new SeasonEngine();
        assertThat(engine.getCurrentSeason()).isEqualTo("SPRING");
    }

    @Test
    void advancesSeasonAfter60Ticks() {
        SeasonEngine engine = new SeasonEngine();
        for (int i = 0; i < 60; i++) engine.advanceTick();
        assertThat(engine.getCurrentSeason()).isEqualTo("SUMMER");
    }

    @Test
    void wrapsAroundAfterWinter() {
        SeasonEngine engine = new SeasonEngine();
        for (int i = 0; i < 240; i++) engine.advanceTick();
        assertThat(engine.getCurrentSeason()).isEqualTo("SPRING");
    }

    @Test
    void ticksRemainingDecreasesEachTick() {
        SeasonEngine engine = new SeasonEngine();
        int initial = engine.getTicksRemaining();
        engine.advanceTick();
        assertThat(engine.getTicksRemaining()).isEqualTo(initial - 1);
    }

    @Test
    void springBoostsGrainAndSuppressesCoal() {
        SeasonEngine engine = new SeasonEngine(); // starts in SPRING
        assertThat(engine.getModifiers().get("Grain")).isPositive();
        assertThat(engine.getModifiers().get("Coal")).isNegative();
    }

    @Test
    void winterBoostsCoalHeavily() {
        SeasonEngine engine = new SeasonEngine();
        for (int i = 0; i < 180; i++) engine.advanceTick(); // advance to WINTER
        assertThat(engine.getModifiers().get("Coal"))
            .isGreaterThan(engine.getModifiers().get("Grain"));
    }

    @Test
    void modifiersNullForUnaffectedGoods() {
        SeasonEngine engine = new SeasonEngine(); // SPRING
        // Gems not mentioned in Spring modifiers
        assertThat(engine.getModifiers()).doesNotContainKey("Gems");
    }
}
