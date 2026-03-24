# Facilities Overhaul Design

## Goal

Fix production not updating the client's holdings/goods table, add a demolish action, and redesign the facilities panel with live status cards.

## Scope

- `FacilityService.java` — add `demolish()`, expose `getTicksUntilProduction()`
- `FacilityController.java` — add `POST /api/facility/demolish` endpoint
- `SessionUpdate.java` — add `holdings`, `costBasis`, `ticksUntilProduction` fields
- `MarketEngine.java` — populate new `SessionUpdate` fields
- `index.html` — new facility panel UI + apply holdings from `SessionUpdate`

---

## 1. Bug Fix — Holdings Not Updating

`SessionUpdate` (the per-session WebSocket push sent every tick) does not include `holdings` or `costBasis`. When `FacilityService.processAll()` runs production every 5 ticks it mutates `Portfolio.holdings`, but the frontend never hears about it.

**Fix:** Add three fields to `SessionUpdate`:

```java
private Map<String, Integer> holdings;        // all goods held (name → qty)
private Map<String, Double> costBasis;        // avg paid per good (name → avg price)
private int ticksUntilProduction;             // ticks until next facility cycle
```

Add corresponding getter and builder methods following the existing pattern.

**MarketEngine** populates them inside the per-session loop:

```java
SessionUpdate update = SessionUpdate.builder()
    // ... existing fields ...
    .holdings(p.getHoldings())
    .costBasis(p.getCostBasis())
    .ticksUntilProduction(facilityService.getTicksUntilProduction())
    .build();
```

**Frontend** applies them in the `/user/queue/updates` handler:

```js
if (update.holdings)   session.holdings  = update.holdings;
if (update.costBasis)  session.costBasis = update.costBasis;
if (update.ticksUntilProduction != null)
    ticksUntilProduction.value = update.ticksUntilProduction;
```

---

## 2. FacilityService — New Methods

### `getTicksUntilProduction()`

Returns ticks remaining until the next production cycle fires. After `processAll()` increments `tickCount`:

```java
public int getTicksUntilProduction() {
    return 5 - (tickCount % 5);  // returns 5 when production just fired (countdown resets)
}
```

### `demolish(Portfolio p, FacilityType type)`

Removes one copy of the given facility type from the portfolio and refunds 50% of the build cost.

```java
public void demolish(Portfolio p, FacilityType type) {
    if (!p.getFacilities().contains(type))
        throw new FacilityException("FACILITY_NOT_FOUND");
    p.removeFacility(type);                              // removes one copy
    p.setGold(p.getGold() + type.getBuildCost() * 0.5);
}
```

`Portfolio.removeFacility(FacilityType type)` removes the first occurrence from the facilities list (already a `List<FacilityType>`).

---

## 3. FacilityController — Demolish Endpoint

```
POST /api/facility/demolish
Header: X-Session-Id
Body: { "type": "MILL" }
Response 200: { "facilities": [...], "gold": 1234.5 }
Response 400: { "error": "FACILITY_NOT_FOUND" | "INVALID_FACILITY_TYPE" | "INVALID_SESSION" }
```

---

## 4. Frontend — Facility Panel UI

Replaces the current panel entirely. Structure:

```
🏭 Facilities      3/15 built · next cycle 3 ticks
─────────────────────────────────────────────────
[Mill ×2 card — green left border, Active]
  Recipe: 3 Grain + 1 Salt → 2 Bread
  → producing 4 Bread next cycle
  [Demolish] [+ Build another (150g)]
  (inline confirm when Demolish clicked)

[Forge ×1 card — red left border, Idle]
  Recipe: 2 Iron + 1 Coal → 1 Weapons
  ⚠ Missing: 1 Coal (have 0)
  [Demolish] [+ Build another (400g)]

─ Build new facility ──────────────────────────
  Winery     250g   2 Ale + 1 Honey → 1 Wine    [Build]
  Soapworks  200g   2 Wax + 1 Salt → 3 Soap     [Build]
  Apothecary 500g   2 Herbs + 1 Honey → 1 Elixir [Build - disabled]
  Mill       150g   3 Grain + 1 Salt → 2 Bread   [Build]
  Forge      400g   2 Iron + 1 Coal → 1 Weapons  [Build]
```

### Reactive state

```js
const ticksUntilProduction = ref(5);
```

### Computed: `ownedFacilityCards`

Groups `facilities` array into cards, one per unique type, with status derived from current holdings:

```js
const ownedFacilityCards = computed(() => {
    const counts = {};
    for (const f of facilities.value) counts[f] = (counts[f] || 0) + 1;
    return Object.entries(counts).map(([type, count]) => {
        const ft = facilityTypes.find(t => t.type === type);
        const canProduce = ft.inputs.every(([good, qty]) =>
            (session.holdings[good] || 0) >= qty);
        const missing = ft.inputs
            .filter(([good, qty]) => (session.holdings[good] || 0) < qty)
            .map(([good, qty]) => `${qty - (session.holdings[good]||0)} ${good}`);
        const outputQty = ft.outputQty * count;
        return { type, count, ft, canProduce, missing, outputQty };
    });
});
```

`facilityTypes` entries gain structured `inputs` arrays (not just the recipe string):

```js
{ type: 'MILL', label: 'Mill', cost: 150,
  inputs: [['Grain',3],['Salt',1]], outputGood: 'Bread', outputQty: 2,
  recipe: '3 Grain + 1 Salt → 2 Bread' }
```

### Card status logic

- **Active** (green left border, `●  Active`): `canProduce === true`
- **Idle** (red left border, `●  Idle`): `canProduce === false`; show `⚠ Missing: X Good (have Y)` for each missing input

### Demolish flow

Each card has a local `demolishPending` flag (stored in the card object or via a ref keyed on type). Clicking "Demolish" reveals the inline confirmation:

> Demolish 1 Mill for **75g** refund? `[Confirm]` `[Cancel]`

Refund displayed = `ft.cost * 0.5`. On Confirm, call `POST /api/facility/demolish`, apply response `facilities` and `gold`.

### Build section

Shows all 5 facility types (including already-owned — the owned cards already have a "Build another" shortcut, but the list stays complete). Disabled if `session.gold < ft.cost || facilities.length >= 15`.

---

## 5. Testing

- `FacilityServiceTest`: `demolish_refundsHalfCost`, `demolish_removesOneCopy`, `demolish_throwsWhenTypeAbsent`
- `FacilityControllerTest`: demolish 200 and 400 paths
- `SessionUpdate` fields populated: verified via existing `MarketEngine` tick integration (no new unit test needed — covered by end-to-end)
