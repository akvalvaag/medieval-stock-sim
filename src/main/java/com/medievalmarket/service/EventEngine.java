// src/main/java/com/medievalmarket/service/EventEngine.java
package com.medievalmarket.service;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class EventEngine {

    public record FiredEvent(String message, Map<String, Double> modifiers) {}

    private record EventDef(String message, Map<String, double[]> goodRanges) {}

    private static final List<EventDef> EVENTS = List.of(
        new EventDef("Iron vein discovered! Mining prices collapse.",
            Map.of("Iron", new double[]{-0.35, -0.20},
                   "Coal", new double[]{-0.35, -0.20},
                   "Stone", new double[]{-0.35, -0.20})),
        new EventDef("Bad harvest! Agricultural prices surge.",
            Map.of("Grain", new double[]{0.25, 0.40},
                   "Livestock", new double[]{0.25, 0.40})),
        new EventDef("Spice ship arrives! Spice prices drop.",
            Map.of("Spices", new double[]{-0.25, -0.15})),
        new EventDef("War declared! Weapons and lumber in demand.",
            Map.of("Iron", new double[]{0.30, 0.40},
                   "Coal", new double[]{0.30, 0.40},
                   "Timber", new double[]{0.30, 0.40},
                   "Rope", new double[]{0.30, 0.40})),
        new EventDef("Gem smugglers caught! Gem prices rise.",
            Map.of("Gems", new double[]{0.15, 0.25})),
        new EventDef("Wool shortage grips the kingdom!",
            Map.of("Wool", new double[]{0.20, 0.30},
                   "Cloth", new double[]{0.20, 0.30}))
    );

    private static final List<String> ALL_GOODS = List.of(
        "Grain","Wool","Livestock","Ale","Spices",
        "Iron","Coal","Stone","Gems","Salt",
        "Timber","Rope","Cloth","Leather","Candles"
    );
    private static final Set<String> PLAGUE_PRIMARY = Set.of("Livestock", "Grain");

    public FiredEvent maybeFireEvent() {
        if (ThreadLocalRandom.current().nextDouble() > 0.04) return null;

        if (ThreadLocalRandom.current().nextInt(7) == 0) {
            return buildPlagueEvent();
        }

        EventDef def = EVENTS.get(ThreadLocalRandom.current().nextInt(EVENTS.size()));
        Map<String, Double> modifiers = new HashMap<>();
        def.goodRanges().forEach((good, range) -> {
            double min = range[0], max = range[1];
            modifiers.put(good, min + ThreadLocalRandom.current().nextDouble() * (max - min));
        });
        return new FiredEvent(def.message(), modifiers);
    }

    private FiredEvent buildPlagueEvent() {
        Map<String, Double> modifiers = new HashMap<>();
        for (String good : ALL_GOODS) {
            if (PLAGUE_PRIMARY.contains(good)) {
                modifiers.put(good, 0.15 + ThreadLocalRandom.current().nextDouble() * 0.10);
            } else {
                modifiers.put(good, -0.10 + ThreadLocalRandom.current().nextDouble() * 0.20);
            }
        }
        return new FiredEvent("Plague outbreak! Fear grips the marketplace.", modifiers);
    }
}
