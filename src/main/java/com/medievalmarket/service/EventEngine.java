package com.medievalmarket.service;

import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class EventEngine {

    public record FiredEvent(String key, String message, Map<String, Double> modifiers) {}

    private record EventDef(String key, String message, Map<String, double[]> goodRanges) {}

    private static final List<EventDef> EVENTS = List.of(
        new EventDef("iron_vein", "Iron vein discovered! Mining prices collapse.",
            Map.of("Iron",  new double[]{-0.35, -0.20},
                   "Coal",  new double[]{-0.35, -0.20},
                   "Stone", new double[]{-0.35, -0.20})),
        new EventDef("harvest", "Bad harvest! Agricultural prices surge.",
            Map.of("Grain",    new double[]{0.25, 0.40},
                   "Livestock",new double[]{0.25, 0.40},
                   "Bread",    new double[]{0.20, 0.30},
                   "Wine",     new double[]{0.15, 0.25})),
        new EventDef("spice", "Spice ship arrives! Spice prices drop.",
            Map.of("Spices", new double[]{-0.25, -0.15})),
        new EventDef("war", "War declared! Weapons and lumber in demand.",
            Map.of("Iron",    new double[]{0.30, 0.40},
                   "Coal",    new double[]{0.30, 0.40},
                   "Timber",  new double[]{0.30, 0.40},
                   "Rope",    new double[]{0.30, 0.40},
                   "Weapons", new double[]{0.30, 0.40},
                   "Bread",   new double[]{0.15, 0.25})),
        new EventDef("gems", "Gem smugglers caught! Gem prices rise.",
            Map.of("Gems", new double[]{0.15, 0.25})),
        new EventDef("wool", "Wool shortage grips the kingdom!",
            Map.of("Wool",  new double[]{0.20, 0.30},
                   "Cloth", new double[]{0.20, 0.30})),
        new EventDef("banquet", "Royal Banquet announced! Food and drink prices surge.",
            Map.of("Bread",   new double[]{0.20, 0.30},
                   "Wine",    new double[]{0.20, 0.30},
                   "Spices",  new double[]{0.15, 0.25},
                   "Candles", new double[]{0.10, 0.20})),
        new EventDef("embargo", "Trade Embargo declared! Craft goods flood the market.",
            Map.of("Soap",      new double[]{-0.30, -0.20},
                   "Cloth",     new double[]{-0.25, -0.15},
                   "Dye",       new double[]{-0.25, -0.15},
                   "Parchment", new double[]{-0.25, -0.15})),
        new EventDef("drought", "Drought strikes! Crops wither across the kingdom.",
            Map.of("Grain",     new double[]{0.25, 0.40},
                   "Livestock", new double[]{0.25, 0.40},
                   "Ale",       new double[]{0.20, 0.30},
                   "Honey",     new double[]{0.15, 0.25})),
        new EventDef("fire", "Great fire tears through the timber district!",
            Map.of("Timber", new double[]{0.30, 0.45},
                   "Rope",   new double[]{0.25, 0.35},
                   "Pitch",  new double[]{0.30, 0.45},
                   "Wax",    new double[]{0.20, 0.30})),
        new EventDef("silver_vein", "Massive silver lode discovered near the capital!",
            Map.of("Silver", new double[]{-0.35, -0.20},
                   "Copper", new double[]{-0.25, -0.15})),
        new EventDef("guild_strike", "Craftsmen's guilds down tools across the city!",
            Map.of("Cloth",   new double[]{0.25, 0.35},
                   "Leather", new double[]{0.25, 0.35},
                   "Weapons", new double[]{0.20, 0.30},
                   "Soap",    new double[]{0.15, 0.25})),
        new EventDef("salt_shortage", "Salt caravans seized at the border!",
            Map.of("Salt",    new double[]{0.30, 0.45},
                   "Fish",    new double[]{0.20, 0.30},
                   "Leather", new double[]{0.15, 0.25})),
        new EventDef("alchemist", "Royal Alchemist announces miraculous cure!",
            Map.of("Elixir", new double[]{0.35, 0.50},
                   "Herbs",  new double[]{0.25, 0.35},
                   "Honey",  new double[]{0.20, 0.30},
                   "Wax",    new double[]{0.15, 0.20}))
    );

    private static final List<String> ALL_GOODS = List.of(
        "Grain","Wool","Livestock","Ale","Spices","Fish","Honey","Herbs",
        "Iron","Coal","Stone","Gems","Salt","Silver","Copper",
        "Timber","Rope","Cloth","Leather","Candles","Pitch","Wax","Parchment","Dye",
        "Bread","Weapons","Wine","Soap","Elixir"
    );
    private static final Set<String> PLAGUE_PRIMARY = Set.of("Livestock", "Grain", "Fish", "Elixir");

    public FiredEvent maybeFireEvent(Set<String> boostedKeys) {
        if (ThreadLocalRandom.current().nextDouble() > 0.04) return null;
        if (ThreadLocalRandom.current().nextInt(7) == 0) return buildPlagueEvent();

        List<EventDef> candidates = new ArrayList<>();
        for (EventDef def : EVENTS) {
            candidates.add(def);
            if (boostedKeys.contains(def.key())) {
                candidates.add(def);
                candidates.add(def);
                candidates.add(def);
            }
        }

        EventDef def = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Map<String, Double> modifiers = new HashMap<>();
        def.goodRanges().forEach((good, range) -> {
            double min = range[0], max = range[1];
            modifiers.put(good, min + ThreadLocalRandom.current().nextDouble() * (max - min));
        });
        return new FiredEvent(def.key(), def.message(), modifiers);
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
        return new FiredEvent("plague", "Plague outbreak! Fear grips the marketplace.", modifiers);
    }
}
