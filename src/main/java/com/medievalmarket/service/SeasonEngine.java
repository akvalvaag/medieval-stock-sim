package com.medievalmarket.service;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class SeasonEngine {

    private static final int TICKS_PER_SEASON = 60;
    private static final String[] SEASONS = {"SPRING", "SUMMER", "AUTUMN", "WINTER"};

    // Raw seasonal strengths (fraction, e.g. 0.08 = 8%).
    // MarketEngine divides by 100 when applying per-tick to scale the nudge.
    private static final Map<String, Map<String, Double>> SEASON_MODIFIERS = Map.of(
        "SPRING", Map.of(
            "Grain", 0.08, "Wool", 0.08, "Livestock", 0.08, "Fish", 0.05,
            "Coal", -0.10, "Candles", -0.10),
        "SUMMER", Map.of(
            "Spices", 0.10, "Herbs", 0.12, "Ale", 0.05, "Cloth", 0.08,
            "Grain", -0.10),
        "AUTUMN", Map.of(
            "Grain", 0.08, "Timber", 0.10, "Ale", 0.10, "Coal", 0.08, "Leather", 0.05),
        "WINTER", Map.of(
            "Coal", 0.20, "Candles", 0.15, "Salt", 0.10, "Spices", 0.08, "Grain", 0.12,
            "Fish", -0.10, "Livestock", -0.08)
    );

    private int seasonIndex = 0;
    private int tickInSeason = 0;

    public synchronized void advanceTick() {
        tickInSeason++;
        if (tickInSeason >= TICKS_PER_SEASON) {
            tickInSeason = 0;
            seasonIndex = (seasonIndex + 1) % SEASONS.length;
        }
    }

    public synchronized String getCurrentSeason() {
        return SEASONS[seasonIndex];
    }

    public synchronized int getTicksRemaining() {
        return TICKS_PER_SEASON - tickInSeason;
    }

    /** Per-tick modifier strengths (divide by 100 when applying as a nudge). */
    public synchronized Map<String, Double> getModifiers() {
        return SEASON_MODIFIERS.get(getCurrentSeason());
    }
}
