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

    private static final Map<String, String> EVENT_RUMOURS = Map.of(
        "war",       "Soldiers have been seen requisitioning horses along the King's road...",
        "harvest",   "A shepherd near Ashford claims his flock is diseased and crops are wilting...",
        "spice",     "Foreign galleys spotted off the coast, heavy with cargo...",
        "iron_vein", "Miners speak of a rich new vein in the northern hills...",
        "gems",      "Customs officers raided a warehouse near the docks last night...",
        "wool",      "Weavers in three villages report empty looms this season...",
        "banquet",   "The palace kitchens have been burning lights all night...",
        "embargo",   "Merchants' caravans are turning back at the border gates...",
        "plague",    "Physicians are unusually busy near the market district..."
    );

    private static final List<String> EVENT_KEYS = new ArrayList<>(EVENT_RUMOURS.keySet());

    // tickCount represents market ticks elapsed, not per-portfolio calls.
    // processTick() is for tests; production code uses processAll().
    private int tickCount = 0;

    /** For tests — one call = one tick for a single portfolio. */
    public void processTick(Portfolio p) {
        tickCount++;
        tickPortfolio(p, tickCount);
    }

    /** For MarketEngine — one call = one market tick for all portfolios. */
    public void processAll(java.util.Collection<Portfolio> portfolios) {
        tickCount++;
        final int t = tickCount;
        portfolios.forEach(p -> tickPortfolio(p, t));
    }

    private void tickPortfolio(Portfolio p, int t) {
        p.getRumours().forEach(Rumour::decrementTick);
        p.removeExpiredRumours();
        if (t % 20 == 0) {
            fillRumours(p);
        }
    }

    private void fillRumours(Portfolio p) {
        List<Rumour> current = p.getRumours();
        double truthRate = (p.getGuild() == Guild.SCHOLARS_GUILD) ? 0.78 : 0.60;
        Set<String> usedKeys = new HashSet<>();
        current.forEach(r -> usedKeys.add(r.getEventKey()));
        while (current.size() < 3) {
            String key = EVENT_KEYS.get(ThreadLocalRandom.current().nextInt(EVENT_KEYS.size()));
            if (usedKeys.contains(key)) continue;
            usedKeys.add(key);
            boolean isTrue = ThreadLocalRandom.current().nextDouble() < truthRate;
            Rumour r = new Rumour(UUID.randomUUID().toString(), EVENT_RUMOURS.get(key), key, isTrue, 30);
            p.addRumour(r);
            current = p.getRumours(); // refresh reference after defensive copy
        }
    }

    public String tip(Portfolio p, String rumourId) {
        Rumour rumour = p.getRumours().stream()
            .filter(r -> r.getId().equals(rumourId))
            .findFirst()
            .orElseThrow(() -> new RumourException("Rumour not found"));
        if (rumour.getTipResult() != null)
            throw new RumourException("already tipped this rumour");
        double cost = (p.getGuild() == Guild.SCHOLARS_GUILD) ? 5.0 : 10.0;
        if (p.getGold() < cost)
            throw new RumourException("Insufficient funds");
        p.setGold(p.getGold() - cost);
        // 70% chance verdict matches truth
        boolean correctVerdict = ThreadLocalRandom.current().nextDouble() < 0.70;
        boolean verdict = correctVerdict ? rumour.isTrue() : !rumour.isTrue();
        String tipResult = verdict ? "RELIABLE" : "DUBIOUS";
        rumour.setTipResult(tipResult);
        return tipResult;
    }

    public void onEventFired(String eventKey, Collection<Portfolio> portfolios) {
        for (Portfolio p : portfolios) {
            p.getRumours().stream()
                .filter(r -> r.getEventKey().equals(eventKey))
                .forEach(r -> r.setConfirmed(true));
        }
    }
}
