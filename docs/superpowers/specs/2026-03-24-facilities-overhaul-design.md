# Facilities Overhaul Design

## Goal

Fix production not updating the client's holdings/goods table, add a demolish action, and redesign the facilities panel with live status cards.

## Scope

- `Portfolio.java` — add `removeFacility(FacilityType)`
- `FacilityService.java` — add `demolish()`, expose `getTicksUntilProduction()`
- `FacilityController.java` — add `POST /api/facility/demolish` endpoint
- `SessionUpdate.java` — add `holdings`, `costBasis`, `ticksUntilProduction` fields
- `MarketEngine.java` — populate new `SessionUpdate` fields (after `facilityService.processAll()`)
- `index.html` — new facility panel UI + apply holdings/costBasis/ticksUntilProduction from `SessionUpdate`

---

## 1. Bug Fix — Holdings Not Updating

`SessionUpdate` (the per-session WebSocket push sent every tick) does not include `holdings` or `costBasis`. When `FacilityService.processAll()` runs production every 5 ticks it mutates `Portfolio.holdings`, but the frontend never hears about it.

**Fix:** Add three fields to `SessionUpdate`, with getters and builder methods matching the existing pattern:

```java
// fields
private Map<String, Integer> holdings;
private Map<String, Double>  costBasis;
private int ticksUntilProduction;

// getters
public Map<String, Integer> getHoldings()       { return holdings; }
public Map<String, Double>  getCostBasis()       { return costBasis; }
public int getTicksUntilProduction()             { return ticksUntilProduction; }

// builder methods (inside the Builder inner class)
public Builder holdings(Map<String, Integer> v)  { su.holdings = v; return this; }
public Builder costBasis(Map<String, Double> v)  { su.costBasis = v; return this; }
public Builder ticksUntilProduction(int v)       { su.ticksUntilProduction = v; return this; }
```

**MarketEngine** populates them inside the per-session loop. **The call to `getTicksUntilProduction()` must come after `facilityService.processAll(humans)` has already run** (so the tick counter has been incremented):

```java
SessionUpdate update = SessionUpdate.builder()
    // ... existing fields ...
    .holdings(p.getHoldings())
    .costBasis(p.getAllCostBasis())
    .ticksUntilProduction(facilityService.getTicksUntilProduction())
    .build();
```

**Frontend** applies them in the `/user/queue/updates` handler. `ticksUntilProduction` lives on the `session` reactive object (consistent with existing conventions — all per-session values are on `session`):

```js
if (update.holdings)              session.holdings = update.holdings;
if (update.costBasis)             session.costBasis = update.costBasis;
if (update.ticksUntilProduction != null)
    session.ticksUntilProduction = update.ticksUntilProduction;
```

Initialise `ticksUntilProduction` on the `session` reactive object:

```js
const session = reactive({
    // ... existing fields ...
    ticksUntilProduction: 5,
});
```

---

## 2. Portfolio — New Method

Add `removeFacility(FacilityType type)` — removes the first occurrence from the internal `facilities` list:

```java
public synchronized void removeFacility(FacilityType type) {
    facilities.remove(type);   // List.remove(Object) removes first occurrence
}
```

---

## 3. FacilityService — New Methods

### `getTicksUntilProduction()`

Returns ticks remaining until the next production cycle fires. Reads the same `tickCount` field used by `processAll()` — no separate counter. Called after `processAll()` increments `tickCount`:

```java
public int getTicksUntilProduction() {
    return 5 - (tickCount % 5);
    // When tickCount % 5 == 0 (production just fired), returns 5 (countdown resets).
    // Correctly counts down: tick 1→4, tick 2→3, tick 3→2, tick 4→1, tick 5→5(fired).
}
```

### `demolish(Portfolio p, FacilityType type)`

Removes one copy of the given facility type from the portfolio and refunds 50% of the build cost:

```java
public void demolish(Portfolio p, FacilityType type) {
    if (!p.getFacilities().contains(type))
        throw new FacilityException("FACILITY_NOT_FOUND");
    p.removeFacility(type);
    p.setGold(p.getGold() + type.getBuildCost() * 0.5);
    // Note: getGold/setGold are individually synchronized on Portfolio; the compound
    // read-modify-write is consistent with how all other services (TradeService, etc.)
    // mutate gold throughout this codebase.
}
```

---

## 4. FacilityController — Demolish Endpoint

```
POST /api/facility/demolish
Header: X-Session-Id
Body: { "type": "MILL" }
Response 200: { "facilities": [...], "gold": 1234.5 }
Response 400: { "error": "FACILITY_NOT_FOUND" | "INVALID_FACILITY_TYPE" | "INVALID_SESSION" }
```

---

## 5. Frontend — Facility Panel UI

Replaces the current panel entirely.

### Updated `facilityTypes` constant

Add structured `inputs` (array of `[good, qty]` pairs) and `outputQty` to all five entries:

```js
const facilityTypes = [
  { type: 'MILL',       label: 'Mill',       cost: 150,
    inputs: [['Grain',3],['Salt',1]],         outputGood: 'Bread',   outputQty: 2,
    recipe: '3 Grain + 1 Salt → 2 Bread' },
  { type: 'FORGE',      label: 'Forge',      cost: 400,
    inputs: [['Iron',2],['Coal',1]],          outputGood: 'Weapons', outputQty: 1,
    recipe: '2 Iron + 1 Coal → 1 Weapons' },
  { type: 'WINERY',     label: 'Winery',     cost: 250,
    inputs: [['Ale',2],['Honey',1]],          outputGood: 'Wine',    outputQty: 1,
    recipe: '2 Ale + 1 Honey → 1 Wine' },
  { type: 'SOAPWORKS',  label: 'Soapworks',  cost: 200,
    inputs: [['Wax',2],['Salt',1]],           outputGood: 'Soap',    outputQty: 3,
    recipe: '2 Wax + 1 Salt → 3 Soap' },
  { type: 'APOTHECARY', label: 'Apothecary', cost: 500,
    inputs: [['Herbs',2],['Honey',1]],        outputGood: 'Elixir',  outputQty: 1,
    recipe: '2 Herbs + 1 Honey → 1 Elixir' },
];
```

### Computed: `ownedFacilityCards`

Groups `facilities` array into one card per unique type, deriving live status from current holdings:

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
            .map(([good, qty]) => {
                const have = session.holdings[good] || 0;
                return `${qty - have} ${good} (have ${have})`;
            });
        const outputQty = ft.outputQty * count;
        return { type, count, ft, canProduce, missing, outputQty };
    });
});
```

### Panel template structure

```html
<!-- Facilities Panel -->
<div class="panel">
  <div style="display:flex;justify-content:space-between;align-items:baseline">
    <h3>🏭 Facilities</h3>
    <span class="muted" style="font-size:.75rem">
      {{ facilities.length }}/15 built · next cycle {{ session.ticksUntilProduction }} ticks
    </span>
  </div>

  <p v-if="ownedFacilityCards.length === 0" class="muted">No facilities built.</p>

  <!-- Owned facility cards -->
  <div v-for="card in ownedFacilityCards" :key="card.type"
       class="facility-card"
       :class="card.canProduce ? 'facility-active' : 'facility-idle'">
    <div class="facility-card-top">
      <span><strong>{{ card.ft.label }}</strong> ×{{ card.count }}</span>
      <span :class="card.canProduce ? 'status-active' : 'status-idle'">
        ● {{ card.canProduce ? 'Active' : 'Idle' }}
      </span>
    </div>
    <div class="facility-recipe">{{ card.ft.recipe }}</div>
    <div v-if="card.canProduce" class="facility-output">
      → producing {{ card.outputQty }} {{ card.ft.outputGood }} next cycle
    </div>
    <div v-else v-for="m in card.missing" :key="m" class="facility-missing">
      ⚠ Missing: {{ m }}
    </div>
    <div class="facility-actions">
      <button class="btn btn-sm btn-danger-outline"
              @click="toggleDemolishConfirm(card.type)">Demolish</button>
      <button class="btn btn-sm"
              :disabled="session.gold < card.ft.cost || facilities.length >= 15"
              @click="buildFacility(card.type)">
        + Build another ({{ card.ft.cost }}g)
      </button>
    </div>
    <div v-if="demolishConfirm === card.type" class="demolish-confirm">
      <span>Demolish 1 {{ card.ft.label }} for
            <strong>{{ (card.ft.cost * 0.5).toFixed(0) }}g</strong> refund?</span>
      <span>
        <button class="btn btn-sm btn-danger" @click="demolishFacility(card.type)">Confirm</button>
        <button class="btn btn-sm" @click="demolishConfirm = null">Cancel</button>
      </span>
    </div>
  </div>

  <!-- Build section -->
  <div class="section-label" style="margin-top:.8rem">Build new facility</div>
  <div v-for="ft in facilityTypes" :key="ft.type" class="facility-row">
    <span>{{ ft.label }} — {{ ft.cost }}g</span>
    <span class="recipe">{{ ft.recipe }}</span>
    <button class="btn btn-sm"
            :disabled="session.gold < ft.cost || facilities.length >= 15"
            @click="buildFacility(ft.type)">Build</button>
  </div>
</div>
```

### New reactive state

```js
const demolishConfirm = ref(null);  // holds the facility type key while confirm is showing
```

Add `session.ticksUntilProduction = 5` to the initial `session` reactive object.

### `toggleDemolishConfirm(type)`

```js
const toggleDemolishConfirm = (type) => {
    demolishConfirm.value = demolishConfirm.value === type ? null : type;
};
```

### `demolishFacility(type)`

```js
const demolishFacility = async (type) => {
    demolishConfirm.value = null;
    const res = await fetch('/api/facility/demolish', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Session-Id': session.id },
        body: JSON.stringify({ type })
    });
    const data = await res.json();
    if (data.error) { alert(data.error); return; }
    facilities.value = data.facilities || [];
    session.gold = data.gold;
};
```

### New CSS classes

```css
.facility-card { background: #16213e; border-radius: 5px; padding: 8px 10px; margin: 5px 0;
                 border-left: 3px solid transparent; }
.facility-active { border-left-color: #4ade80; }
.facility-idle   { border-left-color: #f87171; }
.facility-card-top { display: flex; justify-content: space-between; align-items: center; }
.facility-recipe { color: #888; font-size: .73rem; margin-top: 2px; }
.facility-output { color: #4ade80; font-size: .73rem; margin-top: 2px; }
.facility-missing { color: #f87171; font-size: .73rem; margin-top: 2px; }
.facility-actions { display: flex; gap: 6px; margin-top: 6px; }
.demolish-confirm { display: flex; justify-content: space-between; align-items: center;
                    background: #2a1a1a; border: 1px solid #f8717155; border-radius: 4px;
                    padding: 5px 8px; margin-top: 6px; font-size: .78rem; color: #f87171; }
.status-active { color: #4ade80; font-size: .75rem; }
.status-idle   { color: #f87171; font-size: .75rem; }
.section-label { color: #555; font-size: .7rem; text-transform: uppercase;
                 letter-spacing: .06em; margin: 10px 0 4px; }
```

---

## 6. Testing

### `FacilityServiceTest`

- `demolish_refundsHalfBuildCost` — build cost 400g (Forge), demolish, assert gold increased by 200g
- `demolish_removesOneCopyWhenMultipleOwned` — add Forge twice, demolish once, assert `getFacilities().size() == 1`
- `demolish_throwsWhenTypeNotOwned` — portfolio has no Forge, assert `FacilityException("FACILITY_NOT_FOUND")`
- `getTicksUntilProduction_countsDownFrom5` — call `processTick(p)` once, assert returns 4; call 3 more times, assert returns 1; call once more (fires), assert returns 5

### `FacilityControllerTest`

- `demolish_returns200WithUpdatedFacilitiesAndGold` — seed a portfolio with one Forge built, POST `/api/facility/demolish` with `{"type":"FORGE"}`, assert 200, `facilities` is empty, `gold` increased by 200g
- `demolish_returns400WhenFacilityNotOwned` — POST `/api/facility/demolish` with `{"type":"FORGE"}` on a portfolio with no Forge, assert 400 with `{"error":"FACILITY_NOT_FOUND"}`
- `demolish_returns400ForInvalidType` — POST with `{"type":"BLAH"}`, assert 400 with `{"error":"INVALID_FACILITY_TYPE"}`

### `SessionUpdateFieldsTest` (new minimal test)

Verify the three new `SessionUpdate` fields round-trip through the builder:
- Build a `SessionUpdate` with `holdings(Map.of("Grain", 5))`, `costBasis(Map.of("Grain", 12.0))`, `ticksUntilProduction(3)` and assert all three getters return the expected values.
