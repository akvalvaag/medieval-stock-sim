# Rumour–Event System Redesign

## Goal

Rumours influence global event probability, fire less frequently, are removed when their event fires, and the catalogue of events/rumours is expanded.

## Scope

- `EventEngine.java` — new events, weighted selection
- `RumourService.java` — frequency, removal on fire, new rumour texts
- `MarketEngine.java` — collect boosted keys, pass to EventEngine
- `EventEngineTest.java`, `RumourServiceTest.java` — updated tests

---

## 1. Event Engine — Weighted Selection

`maybeFireEvent()` gains a parameter: `Set<String> boostedKeys`.

**Selection algorithm:**
Build a candidate list by adding every `EventDef` once. For each event whose key is in `boostedKeys`, add it **3 additional times** (total 4×). Pick uniformly from the candidate list. Base fire probability (4% per tick) does not change.

**Effect:** If one event is boosted and there are 8 total events, its share of draws goes from 1/8 (12.5%) to 4/11 (36%). If two events are boosted, each gets 4/14 (28.6%).

**Signature change:**
```java
public FiredEvent maybeFireEvent(Set<String> boostedKeys)
```

---

## 2. New Events (6)

| Key | Message | Goods affected |
|-----|---------|---------------|
| `drought` | Drought strikes! Crops wither across the kingdom. | Grain +25–40%, Livestock +25–40%, Ale +20–30%, Honey +15–25% |
| `fire` | Great fire tears through the timber district! | Timber +30–45%, Rope +25–35%, Pitch +30–45%, Wax +20–30% |
| `silver_vein` | Massive silver lode discovered near the capital! | Silver −35–20%, Copper −25–15% |
| `guild_strike` | Craftsmen's guilds down tools across the city! | Cloth +25–35%, Leather +25–35%, Weapons +20–30%, Soap +15–25% |
| `salt_shortage` | Salt caravans seized at the border! | Salt +30–45%, Fish +20–30%, Leather +15–25% |
| `alchemist` | Royal Alchemist announces miraculous cure! | Elixir +35–50%, Herbs +25–35%, Honey +20–30%, Wax +15–20% |

These add to the existing 8 named events (iron_vein, harvest, spice, war, gems, wool, banquet, embargo) plus plague, giving 15 total event types.

---

## 3. Rumour Service Changes

### Frequency
Change from every **10 ticks** to every **20 ticks**. First rumour appears at tick 20, second at 40, third at 60. Cap of 3 slots unchanged.

### Removal on Event Fire
`onEventFired(String eventKey, Collection<Portfolio> portfolios)` currently marks matching rumours `confirmed=true`. Change to **remove** matching rumours from each portfolio instead. This frees the slot and signals to the player the rumour played out.

Add `Portfolio.removeRumoursByEventKey(String key)` (or equivalent) if not already present.

### New Rumour Texts (6)
```
"drought"       → "Farmers near the river report cracked earth and dying livestock..."
"fire"          → "Smoke has been seen rising from the carpenter's quarter at night..."
"silver_vein"   → "A cartographer's apprentice was caught mapping the northern ridge in secret..."
"guild_strike"  → "Masters of the weavers' hall have cancelled their apprentice intake this season..."
"salt_shortage" → "Fishmongers are paying twice the usual rate for preserving salt at the docks..."
"alchemist"     → "The palace physician has been requesting unusual quantities of rare herbs..."
```

### Duplicate Avoidance
Already implemented (checks `usedKeys`). No change needed.

---

## 4. MarketEngine Wiring

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

## 5. Testing

### EventEngineTest
- Update all `maybeFireEvent()` calls to pass `Set.of()` (no boost)
- Add: statistical test rolling ~200 events with one key boosted — assert that key wins significantly more than 1/N share

### RumourServiceTest
- Update tick counts: fill 3 slots now requires 60 ticks (`3 × 20`), single rumour at 20 ticks
- `onEventFired` test: assert rumour is **removed** (list size decreases), not confirmed
- Keep existing tests for tip cost, Scholars' Guild discount, insufficient-funds guard
