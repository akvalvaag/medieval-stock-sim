# Rumour–Event System Redesign

## Goal

Rumours influence global event probability, fire less frequently, are removed when their event fires, and the catalogue of events/rumours is expanded.

## Scope

- `EventEngine.java` — new events, weighted selection
- `RumourService.java` — frequency, removal on fire, new rumour texts, expanded EVENT_KEYS
- `Portfolio.java` — add `removeRumoursByEventKey(String key)`
- `MarketEngine.java` — collect boosted keys, pass to EventEngine
- `EventEngineTest.java`, `RumourServiceTest.java` — updated tests

---

## 1. Event Engine — Weighted Selection

`maybeFireEvent()` gains a parameter: `Set<String> boostedKeys`.

**Selection algorithm:**
Build a candidate list by adding every `EventDef` once. For each event whose key is in `boostedKeys`, add it **3 additional times** (total 4×). Pick uniformly from the candidate list. Base fire probability (4% per tick) does not change.

**Effect (after adding 6 new events, giving 14 entries in the EVENTS list):**
- No boost: each event has 1/14 ≈ 7.1% share of draws
- One event boosted: boosted event has 4/17 ≈ 23.5% share; others share 1/17 each
- Two events boosted: each boosted event has 4/20 = 20% share; others share 1/20 each

**Plague is excluded from boosting.** Plague is handled by a separate branch before the EVENTS list is consulted (`if (ThreadLocalRandom.current().nextInt(7) == 0) return buildPlagueEvent()`). The "plague" key will appear in `boostedKeys` if a player holds a true plague rumour, but `EventEngine` simply ignores unknown keys in `boostedKeys` — plague's 1-in-7 branch is unaffected.

**New signature:**
```java
public FiredEvent maybeFireEvent(Set<String> boostedKeys)
```

---

## 2. New Events (6 added to EVENTS list)

After adding these, the EVENTS list contains **14 entries** (8 existing + 6 new). Plague remains a separate branch.

| Key | Message | Goods affected |
|-----|---------|---------------|
| `drought` | Drought strikes! Crops wither across the kingdom. | Grain +25% to +40%, Livestock +25% to +40%, Ale +20% to +30%, Honey +15% to +25% |
| `fire` | Great fire tears through the timber district! | Timber +30% to +45%, Rope +25% to +35%, Pitch +30% to +45%, Wax +20% to +30% |
| `silver_vein` | Massive silver lode discovered near the capital! | Silver −35% to −20%, Copper −25% to −15% |
| `guild_strike` | Craftsmen's guilds down tools across the city! | Cloth +25% to +35%, Leather +25% to +35%, Weapons +20% to +30%, Soap +15% to +25% |
| `salt_shortage` | Salt caravans seized at the border! | Salt +30% to +45%, Fish +20% to +30%, Leather +15% to +25% |
| `alchemist` | Royal Alchemist announces miraculous cure! | Elixir +35% to +50%, Herbs +25% to +35%, Honey +20% to +30%, Wax +15% to +20% |

**Note:** The `alchemist` event has a maximum modifier of 0.50 (Elixir). The existing test assertion `Math.abs(modifier) <= 0.45` in `EventEngineTest` must be updated to `<= 0.50`.

---

## 3. Rumour Service Changes

### Frequency
Change from every **10 ticks** to every **20 ticks**. First rumour appears at tick 20, second at 40, third at 60. Cap of 3 slots unchanged.

The existing tip-cost tests call `processTick` 20 times — these remain valid because tick 20 is exactly when the first rumour appears, and those tests are guarded by `assumeThat(p.getRumours()).isNotEmpty()`.

### Removal on Event Fire
`onEventFired(String eventKey, Collection<Portfolio> portfolios)` currently marks matching rumours `confirmed=true`. Change to call `p.removeRumoursByEventKey(eventKey)` for each portfolio instead. This frees the slot and signals to the player the rumour played out.

The `confirmed` boolean field on `Rumour` and its setter become dead code after this change. Leave them in place for now (removing would be a separate refactor with no gameplay benefit).

### EVENT_RUMOURS and EVENT_KEYS — both must be extended
`RumourService` has two separate data structures that both need the 6 new keys:

**`EVENT_RUMOURS`** (hint texts — **must migrate from `Map.of()` to `Map.ofEntries(Map.entry(…), …)`** since 15 entries exceed `Map.of()`'s 10-pair limit):
```
"drought"       → "Farmers near the river report cracked earth and dying livestock..."
"fire"          → "Smoke has been seen rising from the carpenter's quarter at night..."
"silver_vein"   → "A cartographer's apprentice was caught mapping the northern ridge in secret..."
"guild_strike"  → "Masters of the weavers' hall have cancelled their apprentice intake this season..."
"salt_shortage" → "Fishmongers are paying twice the usual rate for preserving salt at the docks..."
"alchemist"     → "The palace physician has been requesting unusual quantities of rare herbs..."
```

**`EVENT_KEYS`** (selection pool — currently 9 entries):
Add all 6 new keys. New list: `"war", "harvest", "spice", "iron_vein", "gems", "wool", "banquet", "embargo", "plague", "drought", "fire", "silver_vein", "guild_strike", "salt_shortage", "alchemist"` (15 entries).

### Duplicate Avoidance
Already implemented (checks `usedKeys` against currently-held rumour event keys). No change needed.

---

## 4. Portfolio — New Method

Add to `Portfolio.java`:

```java
public synchronized void removeRumoursByEventKey(String eventKey) {
    rumours.removeIf(r -> r.getEventKey().equals(eventKey));
}
```

This modifies the internal list directly (not a defensive copy), so removal is reliable.

---

## 5. MarketEngine Wiring

Before calling `eventEngine.maybeFireEvent()`, collect the set of event keys that have at least one `isTrue=true` rumour across all human portfolios:

```java
Set<String> boostedKeys = humans.stream()
    .flatMap(p -> p.getRumours().stream())
    .filter(Rumour::isTrue)
    .map(Rumour::getEventKey)
    .collect(Collectors.toSet());

FiredEvent event = eventEngine.maybeFireEvent(boostedKeys);
```

---

## 6. Testing

### EventEngineTest
- Update all `maybeFireEvent()` calls to pass `Set.of()` (no boost)
- Update upper-bound modifier assertion from `<= 0.45` to `<= 0.50` (due to `alchemist` Elixir at 50%)
- Add: statistical boost test — extract the selection logic into a testable helper, or call `maybeFireEvent(boostedKeys)` in a tight loop until 200 non-null results are collected (bypassing the 4% gate). With one key boosted among 14 events, assert that key appears in at least 40 of the 200 results (vs the expected ~14 without boosting). Use `double` arithmetic: `200.0 / 14 * 1.5 ≈ 21`, so a threshold of 40 is safely above baseline noise.

### RumourServiceTest
- Update tick counts for new 20-tick frequency:
  - Fill 3 slots: 60 ticks (was 30)
  - Single rumour at tick 20 (was 10); two rumours at tick 40 (was 20)
  - Expiry test: add rumour at tick 20, run 31 more ticks to expire
- `onEventFired` positive case: assert rumour **is removed** (list size decreases), not confirmed
- `onEventFired` negative case: assert non-matching rumour is **not removed** (list size unchanged)
- Remove the old `onEventFired_marksMatchingRumoursAsConfirmed` and `onEventFired_doesNotMarkNonMatchingRumours` tests; replace with removal-semantics equivalents
