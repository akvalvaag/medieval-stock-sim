# Rumour–Event System Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make rumours global (shared across all players), let true rumours boost the corresponding event's probability 4×, add 6 new events with matching rumour hints, reduce rumour frequency to every 20 ticks, and remove rumours when their event fires.

**Architecture:** `EventEngine` gains a weighted-selection algorithm driven by a `Set<String> boostedKeys`. `RumourService` owns a single global `List<Rumour>` (max 3 slots) and is called once per tick with no arguments. Per-player tip verdicts move from `Rumour` to `Portfolio.tipResults`. `MarketEngine` gathers boosted keys from the global list and passes them to `EventEngine`.

**Tech Stack:** Java 21, Spring Boot 3.2, JUnit 5, AssertJ. Run tests with `mvn test` from `D:\OSC\Repos\stock_sim`. All source files under `src/main/java/com/medievalmarket/` and tests under `src/test/java/com/medievalmarket/`.

---

### Task 1: EventEngine — weighted selection + 6 new events

**Files:**
- Modify: `src/main/java/com/medievalmarket/service/EventEngine.java`
- Modify: `src/test/java/com/medievalmarket/service/EventEngineTest.java`

- [ ] **Step 1: Update EventEngineTest — fix existing calls and add boost test**

Replace the entire content of `src/test/java/com/medievalmarket/service/EventEngineTest.java` with:

```java
package com.medievalmarket.service;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class EventEngineTest {

    @Test
    void maybeFireEventReturnsNullMostOfTheTime() {
        EventEngine engine = new EventEngine();
        long nullCount = java.util.stream.IntStream.range(0, 1000)
            .mapToObj(i -> engine.maybeFireEvent(Set.of()))
            .filter(e -> e == null)
            .count();
        assertThat(nullCount).isGreaterThan(900);
    }

    @Test
    void firedEventHasNonNullMessageAndModifiers() {
        EventEngine engine = new EventEngine();
        EventEngine.FiredEvent event = null;
        for (int i = 0; i < 200 && event == null; i++) {
            event = engine.maybeFireEvent(Set.of());
        }
        assertThat(event).isNotNull();
        assertThat(event.message()).isNotBlank();
        assertThat(event.modifiers()).isNotEmpty();
    }

    @Test
    void firedEventModifiersAreWithinExpectedRange() {
        EventEngine engine = new EventEngine();
        for (int attempt = 0; attempt < 500; attempt++) {
            EventEngine.FiredEvent event = engine.maybeFireEvent(Set.of());
            if (event != null) {
                event.modifiers().values().forEach(modifier ->
                    assertThat(Math.abs(modifier)).isLessThanOrEqualTo(0.50)
                );
                return;
            }
        }
    }

    @Test
    void boostedKeyFiresSignificantlyMoreOften() {
        EventEngine engine = new EventEngine();
        Set<String> boosted = Set.of("war");
        int warCount = 0;
        int collected = 0;
        // Collect 200 actual fired events (discard nulls from 4% gate)
        for (int i = 0; collected < 200; i++) {
            EventEngine.FiredEvent event = engine.maybeFireEvent(boosted);
            if (event != null) {
                if ("war".equals(event.key())) warCount++;
                collected++;
            }
        }
        // With 14 events and war boosted 4×, expected share is 4/17 ≈ 23.5% → ~47/200
        // Threshold of 30 is ~2.8σ below mean; <0.3% false-failure rate
        assertThat(warCount).isGreaterThanOrEqualTo(30);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail (EventEngine.maybeFireEvent has wrong signature)**

```bash
cd D:/OSC/Repos/stock_sim && mvn test -pl . -Dtest=EventEngineTest -q 2>&1 | tail -20
```

Expected: FAIL — `maybeFireEvent()` does not accept a `Set` argument.

- [ ] **Step 3: Update EventEngine.java — new signature, 6 events, weighted selection**

Replace the entire content of `src/main/java/com/medievalmarket/service/EventEngine.java` with:

```java
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
        // 6 new events
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

    /**
     * Maybe fire an event. boostedKeys are event keys where a true rumour is active;
     * those events get 4× weight in the selection pool. Plague is handled separately
     * and is unaffected by boosting.
     */
    public FiredEvent maybeFireEvent(Set<String> boostedKeys) {
        if (ThreadLocalRandom.current().nextDouble() > 0.04) return null;
        if (ThreadLocalRandom.current().nextInt(7) == 0) return buildPlagueEvent();

        // Build weighted candidate list: each event once + 3 extra times if boosted
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
```

- [ ] **Step 4: Run EventEngine tests**

```bash
cd D:/OSC/Repos/stock_sim && mvn test -pl . -Dtest=EventEngineTest -q 2>&1 | tail -10
```

Expected: 4 tests pass. Note: `boostedKeyFiresSignificantlyMoreOften` is statistical — if it fails, re-run once before investigating.

- [ ] **Step 5: Temporarily fix MarketEngine to compile (it still calls old `maybeFireEvent()`)**

In `src/main/java/com/medievalmarket/service/MarketEngine.java` line 74, change:
```java
EventEngine.FiredEvent event = eventEngine.maybeFireEvent();
```
to:
```java
EventEngine.FiredEvent event = eventEngine.maybeFireEvent(java.util.Set.of());
```
(This is a temporary placeholder — Task 3 will replace it with the real boosted-key logic.)

- [ ] **Step 6: Run full test suite to confirm nothing else broke**

```bash
cd D:/OSC/Repos/stock_sim && mvn test -q 2>&1 | tail -20
```

Expected: All tests pass (existing tests were not using `maybeFireEvent` outside EventEngineTest).

- [ ] **Step 7: Commit**

```bash
cd D:/OSC/Repos/stock_sim && git add src/main/java/com/medievalmarket/service/EventEngine.java src/test/java/com/medievalmarket/service/EventEngineTest.java src/main/java/com/medievalmarket/service/MarketEngine.java && git commit -m "feat: EventEngine — weighted selection, 6 new events, boostedKeys API"
```

---

### Task 2: Global rumour model — Rumour, Portfolio, RumourService, RumourController

This task refactors the rumour system end-to-end: global list in `RumourService`, per-player tips in `Portfolio`, cleaned-up `Rumour` model. All changes compile together and are validated by rewritten tests.

**Files:**
- Modify: `src/main/java/com/medievalmarket/model/Rumour.java`
- Modify: `src/main/java/com/medievalmarket/model/Portfolio.java`
- Modify: `src/main/java/com/medievalmarket/service/RumourService.java`
- Modify: `src/main/java/com/medievalmarket/controller/RumourController.java`
- Modify: `src/test/java/com/medievalmarket/service/RumourServiceTest.java`

- [ ] **Step 1: Rewrite RumourServiceTest for the new global model**

Replace the entire content of `src/test/java/com/medievalmarket/service/RumourServiceTest.java` with:

```java
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
    void processTick_fillsUpTo3RumourSlots() {
        // One rumour added per 20 ticks; run 60 ticks to fill all 3 slots
        for (int i = 0; i < 60; i++) service.processTick();
        assertThat(service.getRumours().size()).isEqualTo(3);
    }

    @Test
    void processTick_addsSingleRumourPer20Ticks() {
        for (int i = 0; i < 20; i++) service.processTick();
        assertThat(service.getRumours().size()).isEqualTo(1);
        for (int i = 0; i < 20; i++) service.processTick();
        assertThat(service.getRumours().size()).isEqualTo(2);
    }

    @Test
    void processTick_expiresRumoursAfter30Ticks() {
        // Fill a slot at tick 20; the rumour has ticksRemaining=30
        for (int i = 0; i < 20; i++) service.processTick();
        assertThat(service.getRumours().size()).isGreaterThan(0);
        // Run 31 more ticks to expire it
        for (int i = 0; i < 31; i++) service.processTick();
        // All surviving rumours must still have time remaining
        service.getRumours().forEach(r -> assertThat(r.getTicksRemaining()).isGreaterThan(0));
    }

    @Test
    void processTick_noDuplicateEventKeys() {
        for (int i = 0; i < 60; i++) service.processTick();
        long distinctKeys = service.getRumours().stream()
            .map(r -> r.getEventKey()).distinct().count();
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
```

Note: This test uses `service.injectRumourForTesting(r)` — a package-private test helper we'll add to `RumourService`.

- [ ] **Step 2: Run the new tests to confirm they fail**

```bash
cd D:/OSC/Repos/stock_sim && mvn test -pl . -Dtest=RumourServiceTest -q 2>&1 | tail -20
```

Expected: FAIL — `processTick()` requires arguments, `getRumours()` doesn't exist, etc.

- [ ] **Step 3: Update Rumour.java — remove tipResult and confirmed**

Replace the entire content of `src/main/java/com/medievalmarket/model/Rumour.java` with:

```java
package com.medievalmarket.model;

public class Rumour {
    private final String id;
    private final String text;
    private final String eventKey;
    private final boolean isTrue;
    private int ticksRemaining;

    public Rumour(String id, String text, String eventKey, boolean isTrue, int ticksRemaining) {
        this.id = id;
        this.text = text;
        this.eventKey = eventKey;
        this.isTrue = isTrue;
        this.ticksRemaining = ticksRemaining;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public String getEventKey() { return eventKey; }
    public boolean isTrue() { return isTrue; }
    public int getTicksRemaining() { return ticksRemaining; }
    public void decrementTick() { ticksRemaining--; }
}
```

- [ ] **Step 4: Update Portfolio.java — remove rumour list, add tipResults**

**Apply all four sub-steps (a–d) before running the compiler — intermediate states won't compile.**

In `src/main/java/com/medievalmarket/model/Portfolio.java`:

a) Add `import java.util.Set;` alongside the existing imports (the file uses explicit imports, no wildcard):
```java
import java.util.Set;
```

b) Remove this import (same-package import, dead after removing rumour fields):
```java
import com.medievalmarket.model.Rumour;
```

c) Remove the rumour state field (around line 48):
```java
    // Rumour state
    private final List<Rumour> rumours = new ArrayList<>();
```

d) Add tipResults field after the contract state block, before the black market state block:
```java
    // Tip results (rumourId → "RELIABLE" | "DUBIOUS")
    private final Map<String, String> tipResults = new HashMap<>();
```

e) Replace the three rumour methods (around lines 160-162):
```java
    public synchronized List<Rumour> getRumours() { return new ArrayList<>(rumours); }
    public synchronized void addRumour(Rumour r) { rumours.add(r); }
    public synchronized void removeExpiredRumours() { rumours.removeIf(r -> r.getTicksRemaining() <= 0); }
```
with:
```java
    public synchronized String getTipResult(String rumourId) {
        return tipResults.get(rumourId);
    }
    public synchronized void setTipResult(String rumourId, String result) {
        tipResults.put(rumourId, result);
    }
    public synchronized void removeTipResultsNotIn(Set<String> activeIds) {
        tipResults.keySet().retainAll(activeIds);
    }
    public synchronized Map<String, String> getTipResults() {
        return new HashMap<>(tipResults);
    }
```

- [ ] **Step 5: Rewrite RumourService.java — global model**

Replace the entire content of `src/main/java/com/medievalmarket/service/RumourService.java` with:

```java
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

    private static final List<String> EVENT_KEYS = List.of(
        "war", "harvest", "spice", "iron_vein", "gems", "wool", "banquet", "embargo", "plague",
        "drought", "fire", "silver_vein", "guild_strike", "salt_shortage", "alchemist"
    );

    private final List<Rumour> activeRumours = new ArrayList<>();
    private int tickCount = 0;

    /** Called once per market tick by MarketEngine (and directly by tests). */
    public void processTick() {
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
            boolean isTrue = ThreadLocalRandom.current().nextBoolean(); // 50% base; 78% for Scholars not applicable globally
            activeRumours.add(new Rumour(UUID.randomUUID().toString(), EVENT_RUMOURS.get(key), key, isTrue, 30));
            return;
        }
    }

    /** Returns an unmodifiable view of current active rumours. */
    public List<Rumour> getRumours() {
        return Collections.unmodifiableList(activeRumours);
    }

    /** Tips a rumour for a specific player. Verdict is private to that player. */
    public String tip(Portfolio p, String rumourId) {
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
    public void onEventFired(String eventKey) {
        activeRumours.removeIf(r -> r.getEventKey().equals(eventKey));
    }

    /** Test helper — injects a rumour directly into the active list. */
    void injectRumourForTesting(Rumour r) {
        activeRumours.add(r);
    }
}
```

**Note on truth rate:** Since rumours are now global (not per-player), we can't apply Scholars' Guild truth rate at generation time (we don't know who will read it). The 50% base rate keeps things fair globally. The Scholars' Guild benefit now applies only to tip cost (5g vs 10g).

- [ ] **Step 6: Update RumourController.java — use global rumour list**

Replace the entire content of `src/main/java/com/medievalmarket/controller/RumourController.java` with:

```java
package com.medievalmarket.controller;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.model.Rumour;
import com.medievalmarket.service.RumourService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/rumours")
public class RumourController {

    private final RumourService rumourService;
    private final SessionRegistry sessionRegistry;

    public RumourController(RumourService rumourService, SessionRegistry sessionRegistry) {
        this.rumourService = rumourService;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping
    public ResponseEntity<?> getRumours(@RequestHeader("X-Session-Id") String sessionId) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        Portfolio p = opt.get();
        List<Map<String, Object>> rumours = rumourService.getRumours().stream()
            .map(r -> {
                Map<String, Object> dto = new java.util.HashMap<>();
                dto.put("id", r.getId());
                dto.put("text", r.getText());
                dto.put("eventKey", r.getEventKey());
                dto.put("ticksRemaining", r.getTicksRemaining());
                String tip = p.getTipResult(r.getId());
                dto.put("tipResult", tip == null ? "" : tip);
                return dto;
            }).toList();
        return ResponseEntity.ok(rumours);
    }

    @PostMapping("/{id}/tip")
    public ResponseEntity<?> tip(@RequestHeader("X-Session-Id") String sessionId,
                                 @PathVariable String id) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        try {
            String result = rumourService.tip(opt.get(), id);
            return ResponseEntity.ok(Map.of("tipResult", result, "gold", opt.get().getGold()));
        } catch (RumourService.RumourException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
```

- [ ] **Step 7: Run RumourServiceTest**

```bash
cd D:/OSC/Repos/stock_sim && mvn test -pl . -Dtest=RumourServiceTest -q 2>&1 | tail -20
```

Expected: All tests pass.

- [ ] **Step 8: Run full test suite**

```bash
cd D:/OSC/Repos/stock_sim && mvn test -q 2>&1 | tail -20
```

Expected: All tests pass. If MarketEngine fails to compile due to `p.getRumours()` still being called there, fix it in the next step first.

- [ ] **Step 9: Commit**

```bash
cd D:/OSC/Repos/stock_sim && git add src/main/java/com/medievalmarket/model/Rumour.java src/main/java/com/medievalmarket/model/Portfolio.java src/main/java/com/medievalmarket/service/RumourService.java src/main/java/com/medievalmarket/controller/RumourController.java src/test/java/com/medievalmarket/service/RumourServiceTest.java && git commit -m "feat: global rumour model — shared list, per-player tips, 15 event keys"
```

---

### Task 3: MarketEngine wiring — boosted keys + global rumour DTOs

**Files:**
- Modify: `src/main/java/com/medievalmarket/service/MarketEngine.java`

- [ ] **Step 1: Update MarketEngine.java**

In `src/main/java/com/medievalmarket/service/MarketEngine.java`, make the following changes:

**a) Replace the event firing block (around line 74) — change from placeholder to real boosted-key logic:**

Find:
```java
EventEngine.FiredEvent event = eventEngine.maybeFireEvent(java.util.Set.of());
```
Replace with:
```java
// Collect event keys where a true rumour is currently active (boosts that event 4×)
Set<String> boostedKeys = rumourService.getRumours().stream()
    .filter(r -> r.isTrue())
    .map(r -> r.getEventKey())
    .collect(java.util.stream.Collectors.toSet());
EventEngine.FiredEvent event = eventEngine.maybeFireEvent(boostedKeys);
```

**b) Replace `rumourService.processAll(humans)` and the old `onEventFired` call (around lines 109–117), including the stale comment above it:**

Find:
```java
        // FacilityService, ContractService, RumourService use processAll() so their internal
        // tick counters increment exactly once per market tick
        facilityService.processAll(humans);
        contractService.processAll(humans);
        rumourService.processAll(humans);
        // Notify RumourService of any fired event
        if (firedEventKey != null) {
            rumourService.onEventFired(firedEventKey, humans);
        }
```
Replace with:
```java
        facilityService.processAll(humans);
        contractService.processAll(humans);
        rumourService.processTick();
        // Clean up stale tip entries for expired/removed rumours
        Set<String> activeRumourIds = rumourService.getRumours().stream()
            .map(r -> r.getId())
            .collect(java.util.stream.Collectors.toSet());
        humans.forEach(p -> p.removeTipResultsNotIn(activeRumourIds));
        if (firedEventKey != null) {
            rumourService.onEventFired(firedEventKey);
        }
```

**c) Replace the per-portfolio rumour DTO building (around lines 136–140):**

Find (the `.toList()` is on its own line in the actual file):
```java
                // Build rumour DTOs (omit isTrue — client must not know)
                List<SessionUpdate.RumourDTO> rumourDTOs = p.getRumours().stream()
                    .map(r -> new SessionUpdate.RumourDTO(r.getId(), r.getText(), r.getEventKey(),
                                                           r.getTicksRemaining(), r.getTipResult()))
                    .toList();
```
Replace with:
```java
                // Build rumour DTOs from global list; tipResult is per-player
                List<SessionUpdate.RumourDTO> rumourDTOs = rumourService.getRumours().stream()
                    .map(r -> new SessionUpdate.RumourDTO(r.getId(), r.getText(), r.getEventKey(),
                                                           r.getTicksRemaining(), p.getTipResult(r.getId())))
                    .toList();
```

- [ ] **Step 2: Run full test suite**

```bash
cd D:/OSC/Repos/stock_sim && mvn test -q 2>&1 | tail -20
```

Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
cd D:/OSC/Repos/stock_sim && git add src/main/java/com/medievalmarket/service/MarketEngine.java && git commit -m "feat: wire global rumours into MarketEngine — boosted keys + shared DTOs"
```

---

### Task 4: Verify end-to-end and clean up

**Files:**
- Read: `src/main/resources/static/index.html` (verify frontend panel still works — no changes needed since `tipResult` field name and `eventKey` are unchanged)

- [ ] **Step 1: Run full test suite one final time**

```bash
cd D:/OSC/Repos/stock_sim && mvn test 2>&1 | grep -E "Tests run|FAIL|ERROR|BUILD" | tail -20
```

Expected: BUILD SUCCESS, 0 failures.

- [ ] **Step 2: Verify no dead references remain**

```bash
cd D:/OSC/Repos/stock_sim && grep -rn "getRumours\|addRumour\|removeExpiredRumours\|isConfirmed\|setConfirmed" src/main/java/ | grep -v "\.class"
```

Expected: No output (all old Portfolio rumour API calls removed).

- [ ] **Step 3: Verify no `Rumour.getTipResult()` calls remain (must all be `Portfolio.getTipResult()`)**

```bash
cd D:/OSC/Repos/stock_sim && grep -rn "\.getTipResult()" src/main/java/ | grep -v "\.class"
```

Expected: Only `p.getTipResult(r.getId())` patterns — no bare `r.getTipResult()` calls on a `Rumour` object.

- [ ] **Step 3: Verify Scholars' Guild truth-rate note in code comment**

The Scholars' Guild description says "Rumour truth rate raised to 78%". Since rumours are now global, the 78% truth rate no longer applies at generation time. The guild still provides the 5g tip discount. This is an acceptable trade-off — no code change needed, but if the guild description text needs updating, edit `src/main/java/com/medievalmarket/model/Guild.java` line 10:
```java
SCHOLARS_GUILD("Scholars' Guild",
    "Tip cost reduced to 5g. Rumours are more likely to be reliable."),
```

- [ ] **Step 4: Final commit if Guild description was updated**

```bash
cd D:/OSC/Repos/stock_sim && git add src/main/java/com/medievalmarket/model/Guild.java && git commit -m "fix: update Scholars Guild description — tip discount only (rumours now global)"
```

If no change was needed, skip this step.
