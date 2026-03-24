# Rumour–Event System Redesign

## Goal

Rumours are global (all players see the same rumours), influence global event probability, fire less frequently, are removed when their event fires, and the catalogue of events/rumours is expanded. Tipping is per-player — each player independently pays to get their own private RELIABLE/DUBIOUS verdict.

## Scope

- `EventEngine.java` — new events, weighted selection
- `RumourService.java` — global rumour list, frequency, removal on fire, drop `processAll`, new rumour texts, expanded EVENT_KEYS
- `Rumour.java` — remove `tipResult`, `setTipResult()`, `getTipResult()`, `confirmed`, `isConfirmed()`, `setConfirmed()` fields/methods; retain `isTrue`, `id`, `text`, `eventKey`, `ticksRemaining`
- `Portfolio.java` — remove `List<Rumour> rumours`, `addRumour()`, `getRumours()`, `removeExpiredRumours()`, dead `import Rumour`; add `Map<String, String> tipResults` with synchronized accessors
- `MarketEngine.java` — simplified boosted-key collection, pass to EventEngine
- `SessionUpdate.java` / `RumourDTO` — remove `confirmed` field, add per-player `tipResult`
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

**Plague is excluded from boosting.** Plague is handled by a separate branch before the EVENTS list is consulted (`if (ThreadLocalRandom.current().nextInt(7) == 0) return buildPlagueEvent()`). The "plague" key will appear in `boostedKeys` if a true plague rumour is active, but `EventEngine` simply ignores unknown keys in `boostedKeys` — plague's 1-in-7 branch is unaffected.

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

## 3. Global Rumour Model

### Rumours are now global, not per-player

`RumourService` owns a single `List<Rumour> activeRumours` (max 3 slots). All players see the same rumours. `Portfolio` no longer stores a rumour list.

**`RumourService` state:**
```java
private final List<Rumour> activeRumours = new ArrayList<>();
private int tickCount = 0;
```

**`processAll(Collection<Portfolio>)` is deleted.** The single method is now `processTick()` (no arguments), called once per market tick by `MarketEngine`. Tests continue to call `processTick()` directly.

**`processTick()` logic:**
- Increment `tickCount`
- Decrement `ticksRemaining` on all active rumours
- Remove expired rumours (ticksRemaining ≤ 0)
- If `tickCount % 20 == 0` and `activeRumours.size() < 3`, add one new rumour (avoiding duplicate event keys already in the list — `usedKeys` check)

**`onEventFired(String eventKey)`** — signature change: no longer takes a portfolio collection (behavioural change from "mark confirmed" to "remove from global list"). Removes any rumour whose `eventKey` matches from `activeRumours`.

**`getRumours()`** — returns `Collections.unmodifiableList(activeRumours)` for MarketEngine and controllers to read.

### Rumour model changes

`Rumour.java` retains: `id`, `text`, `eventKey`, `isTrue`, `ticksRemaining`, `decrementTick()`.

`Rumour.java` removes: `tipResult`, `setTipResult()`, `getTipResult()`, `confirmed`, `isConfirmed()`, `setConfirmed()`. These fields were per-player concepts that no longer belong on the shared object.

### Tipping remains per-player

**`Portfolio` changes:**
- Remove: `List<Rumour> rumours`, `addRumour()`, `getRumours()`, `removeExpiredRumours()`, dead `import com.medievalmarket.model.Rumour`
- Add:
  ```java
  private final Map<String, String> tipResults = new HashMap<>();

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

**`tip(Portfolio p, String rumourId)` in `RumourService`:**
- Looks up rumour by id in `activeRumours` — throws `RumourException("Rumour not found")` if absent
- Checks `p.getTipResult(rumourId) == null` — throws `RumourException("already tipped this rumour")` if already tipped
- Checks `p.getGold() >= cost` — throws `RumourException("Insufficient funds")` if insufficient
- Deducts gold (10g standard, 5g for Scholars' Guild)
- Rolls verdict (70% chance verdict matches `rumour.isTrue()`) and calls `p.setTipResult(rumourId, verdict)`
- Returns verdict string (`"RELIABLE"` or `"DUBIOUS"`)

### Frequency
One new rumour every **20 ticks** (was 10). First appears at tick 20, second at 40, third at 60.

---

## 4. EVENT_RUMOURS and EVENT_KEYS — both must be extended

**`EVENT_RUMOURS`** (hint texts — **must migrate from `Map.of()` to `Map.ofEntries(Map.entry(…), …)`** since 15 entries exceed `Map.of()`'s 10-pair limit):
```
"drought"       → "Farmers near the river report cracked earth and dying livestock..."
"fire"          → "Smoke has been seen rising from the carpenter's quarter at night..."
"silver_vein"   → "A cartographer's apprentice was caught mapping the northern ridge in secret..."
"guild_strike"  → "Masters of the weavers' hall have cancelled their apprentice intake this season..."
"salt_shortage" → "Fishmongers are paying twice the usual rate for preserving salt at the docks..."
"alchemist"     → "The palace physician has been requesting unusual quantities of rare herbs..."
```

**`EVENT_KEYS`** (selection pool — currently 9 entries, must grow to 15):
`"war", "harvest", "spice", "iron_vein", "gems", "wool", "banquet", "embargo", "plague", "drought", "fire", "silver_vein", "guild_strike", "salt_shortage", "alchemist"`

---

## 5. MarketEngine Wiring

Replace `rumourService.processAll(humans)` with `rumourService.processTick()` (once per tick, no arguments).

Boosted keys come directly from the global rumour list — no portfolio scanning needed:

```java
Set<String> boostedKeys = rumourService.getRumours().stream()
    .filter(Rumour::isTrue)
    .map(Rumour::getEventKey)
    .collect(Collectors.toSet());

FiredEvent event = eventEngine.maybeFireEvent(boostedKeys);
```

Clean up stale tip entries after ticking rumours:
```java
rumourService.processTick();   // advances tick, expires/adds rumours

Set<String> activeIds = rumourService.getRumours().stream()
    .map(Rumour::getId)
    .collect(Collectors.toSet());
humans.forEach(p -> p.removeTipResultsNotIn(activeIds));
```

`onEventFired` call simplifies to:
```java
if (firedEventKey != null) {
    rumourService.onEventFired(firedEventKey);
}
```

---

## 6. SessionUpdate / Frontend

The `SessionUpdate` payload currently includes `List<RumourDTO>` built per-portfolio. With global rumours:
- The rumour list is the same for all players — built once from `rumourService.getRumours()`
- Each `RumourDTO` includes: `id`, `text`, `eventKey`, `ticksRemaining` (no `isTrue` — client must not know), `tipResult` (populated per-player from `p.getTipResult(r.getId())`, null if not tipped)
- `confirmed` field is removed from `RumourDTO`

Frontend rumour panel behaviour is unchanged: shows text, ticksRemaining, tip button (disabled if `tipResult != null`), and tipResult label when present.

---

## 7. Testing

### EventEngineTest
- Update all `maybeFireEvent()` calls to pass `Set.of()` (no boost)
- Update upper-bound modifier assertion from `<= 0.45` to `<= 0.50` (due to `alchemist` Elixir at 50%)
- Add statistical boost test: call `maybeFireEvent(Set.of("war"))` in a loop, collecting results until 200 non-null events are produced (discard nulls from the 4% gate). Assert `"war"` appears in at least **30** of the 200 results (theoretical expectation ~47; threshold of 30 is ~2.8σ below mean giving <0.3% false-failure rate).

### RumourServiceTest
- `processTick()` takes no arguments
- Fill 3 slots: 60 ticks (was 30)
- Single rumour at tick 20 (was 10); two at tick 40 (was 20)
- `tip` tests: verify `p.getTipResult(id)` is set and gold is deducted; verify `"already tipped this rumour"` exception on second tip
- `onEventFired(eventKey)` positive case: assert matching rumour is removed from the service's active list (`getRumours().size()` decreases)
- `onEventFired(eventKey)` negative case: assert non-matching rumour is NOT removed
- Remove old `onEventFired_marksMatchingRumoursAsConfirmed` and `onEventFired_doesNotMarkNonMatchingRumours`
