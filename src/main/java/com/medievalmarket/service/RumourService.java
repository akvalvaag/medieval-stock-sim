package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class RumourService {

    public static class RumourException extends RuntimeException {
        public RumourException(String msg) { super(msg); }
    }

    private static final Map<String, String> EVENT_RUMOURS = Map.ofEntries(
        Map.entry("war",          "Soldiers have been seen requisitioning horses along the King's road..."),
        Map.entry("harvest",      "A shepherd near Ashford claims his flock is diseased and crops are wilting..."),
        Map.entry("spice",        "Foreign galleys spotted off the coast, heavy with cargo..."),
        Map.entry("iron_vein",    "Miners speak of a rich new vein in the northern hills..."),
        Map.entry("gems",         "Customs officers raided a warehouse near the docks last night..."),
        Map.entry("wool",         "Weavers in three villages report empty looms this season..."),
        Map.entry("banquet",      "The palace kitchens have been burning lights all night..."),
        Map.entry("embargo",      "Merchants' caravans are turning back at the border gates..."),
        Map.entry("plague",       "Physicians are unusually busy near the market district..."),
        Map.entry("drought",      "Farmers near the river report cracked earth and dying livestock..."),
        Map.entry("fire",         "Smoke has been seen rising from the carpenter's quarter at night..."),
        Map.entry("silver_vein",  "A cartographer's apprentice was caught mapping the northern ridge in secret..."),
        Map.entry("guild_strike", "Masters of the weavers' hall have cancelled their apprentice intake this season..."),
        Map.entry("salt_shortage","Fishmongers are paying twice the usual rate for preserving salt at the docks..."),
        Map.entry("alchemist",    "The palace physician has been requesting unusual quantities of rare herbs...")
    );

    private static final List<String> EVENT_KEYS = List.copyOf(EVENT_RUMOURS.keySet());

    private final List<Rumour> activeRumours = new ArrayList<>();
    private int tickCount = 0;

    /** Called once per market tick by MarketEngine (and directly by tests). */
    public synchronized void processTick() {
        tickCount++;
        activeRumours.forEach(Rumour::decrementTick);
        activeRumours.removeIf(r -> r.getTicksRemaining() <= 0);
        if (tickCount % 20 == 0 && activeRumours.size() < 3) {
            addOneRumour();
        }
    }

    private void addOneRumour() {
        Set<String> usedKeys = new HashSet<>();
        activeRumours.forEach(r -> usedKeys.add(r.getEventKey()));
        for (int attempts = 0; attempts < 20; attempts++) {
            String key = EVENT_KEYS.get(ThreadLocalRandom.current().nextInt(EVENT_KEYS.size()));
            if (usedKeys.contains(key)) continue;
            boolean isTrue = ThreadLocalRandom.current().nextBoolean();
            activeRumours.add(new Rumour(UUID.randomUUID().toString(), EVENT_RUMOURS.get(key), key, isTrue, 50));
            return;
        }
    }

    /** Returns an unmodifiable snapshot of current active rumours. */
    public synchronized List<Rumour> getRumours() {
        return Collections.unmodifiableList(new ArrayList<>(activeRumours));
    }

    /** Tips a rumour for a specific player. Verdict is private to that player. */
    public synchronized String tip(Portfolio p, String rumourId) {
        Rumour rumour = activeRumours.stream()
            .filter(r -> r.getId().equals(rumourId))
            .findFirst()
            .orElseThrow(() -> new RumourException("Rumour not found"));
        if (p.getTipResult(rumourId) != null)
            throw new RumourException("already tipped this rumour");
        double cost = (p.getGuild() == Guild.SCHOLARS_GUILD) ? 5.0 : 10.0;
        if (p.getGold() < cost)
            throw new RumourException("Insufficient funds");
        p.setGold(p.getGold() - cost);
        boolean correctVerdict = ThreadLocalRandom.current().nextDouble() < 0.70;
        boolean verdict = correctVerdict ? rumour.isTrue() : !rumour.isTrue();
        String tipResult = verdict ? "RELIABLE" : "DUBIOUS";
        p.setTipResult(rumourId, tipResult);
        return tipResult;
    }

    /** Removes any rumour matching the fired event key from the global list. */
    public synchronized void onEventFired(String eventKey) {
        activeRumours.removeIf(r -> r.getEventKey().equals(eventKey));
    }

    /** Test helper — injects a rumour directly into the active list. */
    synchronized void injectRumourForTesting(Rumour r) {
        activeRumours.add(r);
    }
}
