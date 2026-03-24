# Facilities Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix production not updating the client's holdings/goods table, add a demolish action, and redesign the facilities panel with live status cards.

**Architecture:** Four independent tasks: (1) backend service layer — `Portfolio.removeFacility` and two new `FacilityService` methods; (2) data pipeline — three new fields on `SessionUpdate` wired from `MarketEngine`; (3) controller — demolish HTTP endpoint; (4) frontend — updated WebSocket handler and new facilities panel UI. Tasks 3 and 4 depend on Tasks 1 and 2 respectively, but 1 and 2 can be done in either order.

**Tech Stack:** Java 21, Spring Boot 3.2, JUnit 5 + AssertJ, Vue 3 (CDN), Maven (`mvn test` to run all tests from `D:/OSC/Repos/stock_sim`)

---

## File Map

| File | Change |
|------|--------|
| `src/main/java/com/medievalmarket/model/Portfolio.java` | Add `removeFacility(FacilityType)` after line 151 |
| `src/main/java/com/medievalmarket/service/FacilityService.java` | Add `demolish()` and `getTicksUntilProduction()` |
| `src/main/java/com/medievalmarket/dto/SessionUpdate.java` | Add `holdings`, `costBasis`, `ticksUntilProduction` fields/getters/builder |
| `src/main/java/com/medievalmarket/service/MarketEngine.java` | Populate new `SessionUpdate` fields in per-session loop |
| `src/main/java/com/medievalmarket/controller/FacilityController.java` | Add `POST /api/facility/demolish` endpoint |
| `src/main/resources/static/index.html` | New facility panel + WebSocket handler update |
| `src/test/java/com/medievalmarket/service/FacilityServiceTest.java` | Add 4 new tests |
| `src/test/java/com/medievalmarket/dto/SessionUpdateFieldsTest.java` | **Create** — 1 new test |
| `src/test/java/com/medievalmarket/controller/FacilityControllerTest.java` | **Create** — 3 new tests |

---

## Task 1: Portfolio.removeFacility + FacilityService demolish/countdown

**Files:**
- Modify: `src/main/java/com/medievalmarket/model/Portfolio.java`
- Modify: `src/main/java/com/medievalmarket/service/FacilityService.java`
- Modify: `src/test/java/com/medievalmarket/service/FacilityServiceTest.java`

- [ ] **Step 1: Add 4 failing tests to `FacilityServiceTest`**

Add these four test methods to the existing `FacilityServiceTest` class (after the last existing test, before the closing `}`):

```java
@Test
void demolish_refundsHalfBuildCost() {
    Portfolio p = richMerchant();
    service.build(p, FacilityType.FORGE); // costs 400g
    double goldAfterBuild = p.getGold();
    service.demolish(p, FacilityType.FORGE);
    assertThat(p.getGold()).isEqualTo(goldAfterBuild + 200.0); // 50% of 400
}

@Test
void demolish_removesOneCopyWhenMultipleOwned() {
    Portfolio p = richMerchant();
    service.build(p, FacilityType.FORGE);
    service.build(p, FacilityType.FORGE);
    service.demolish(p, FacilityType.FORGE);
    assertThat(p.getFacilities()).hasSize(1).containsExactly(FacilityType.FORGE);
}

@Test
void demolish_throwsWhenTypeNotOwned() {
    Portfolio p = richMerchant();
    assertThatThrownBy(() -> service.demolish(p, FacilityType.FORGE))
        .isInstanceOf(FacilityService.FacilityException.class)
        .hasMessageContaining("FACILITY_NOT_FOUND");
}

@Test
void getTicksUntilProduction_countsDownFrom5() {
    Portfolio p = richMerchant();
    service.processTick(p); assertThat(service.getTicksUntilProduction()).isEqualTo(4);
    service.processTick(p); assertThat(service.getTicksUntilProduction()).isEqualTo(3);
    service.processTick(p); assertThat(service.getTicksUntilProduction()).isEqualTo(2);
    service.processTick(p); assertThat(service.getTicksUntilProduction()).isEqualTo(1);
    service.processTick(p); assertThat(service.getTicksUntilProduction()).isEqualTo(5); // fired, resets
}
```

- [ ] **Step 2: Run to confirm all 4 new tests fail**

```
cd D:/OSC/Repos/stock_sim && mvn test -Dtest=FacilityServiceTest 2>&1 | grep -E "FAIL|ERROR|Tests run"
```

Expected: 4 failures (methods not yet defined).

- [ ] **Step 3: Add `removeFacility` to `Portfolio.java`**

In `src/main/java/com/medievalmarket/model/Portfolio.java`, after line 151 (`public synchronized int getFacilityCount() { return facilities.size(); }`), add:

```java
public synchronized void removeFacility(FacilityType type) {
    facilities.remove(type); // removes first occurrence
}
```

- [ ] **Step 4: Add `demolish()` and `getTicksUntilProduction()` to `FacilityService.java`**

In `src/main/java/com/medievalmarket/service/FacilityService.java`, after the `build()` method (after line 28), add:

```java
public void demolish(Portfolio p, FacilityType type) {
    if (!p.getFacilities().contains(type))
        throw new FacilityException("FACILITY_NOT_FOUND");
    p.removeFacility(type);
    p.setGold(p.getGold() + type.getBuildCost() * 0.5);
}

public int getTicksUntilProduction() {
    return 5 - (tickCount % 5);
    // Returns 5 when production just fired (tickCount % 5 == 0); counts down 4,3,2,1 otherwise.
    // Reads the same tickCount field used by processAll() — no separate counter.
}
```

- [ ] **Step 5: Run tests to confirm all pass**

```
cd D:/OSC/Repos/stock_sim && mvn test -Dtest=FacilityServiceTest 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 10, Failures: 0, Errors: 0` and `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```
git add src/main/java/com/medievalmarket/model/Portfolio.java \
        src/main/java/com/medievalmarket/service/FacilityService.java \
        src/test/java/com/medievalmarket/service/FacilityServiceTest.java
git commit -m "feat: Portfolio.removeFacility, FacilityService.demolish + getTicksUntilProduction"
```

---

## Task 2: SessionUpdate new fields + MarketEngine wiring

**Files:**
- Modify: `src/main/java/com/medievalmarket/dto/SessionUpdate.java`
- Modify: `src/main/java/com/medievalmarket/service/MarketEngine.java`
- Create: `src/test/java/com/medievalmarket/dto/SessionUpdateFieldsTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/medievalmarket/dto/SessionUpdateFieldsTest.java`:

```java
package com.medievalmarket.dto;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class SessionUpdateFieldsTest {

    @Test
    void newFields_roundTripThroughBuilder() {
        SessionUpdate update = SessionUpdate.builder()
            .holdings(Map.of("Grain", 5))
            .costBasis(Map.of("Grain", 12.0))
            .ticksUntilProduction(3)
            .build();
        assertThat(update.getHoldings()).containsEntry("Grain", 5);
        assertThat(update.getCostBasis()).containsEntry("Grain", 12.0);
        assertThat(update.getTicksUntilProduction()).isEqualTo(3);
    }
}
```

- [ ] **Step 2: Run to confirm test fails**

```
cd D:/OSC/Repos/stock_sim && mvn test -Dtest=SessionUpdateFieldsTest 2>&1 | grep -E "FAIL|ERROR|Tests run"
```

Expected: compilation failure (methods not yet defined).

- [ ] **Step 3: Add new fields to `SessionUpdate.java`**

In `src/main/java/com/medievalmarket/dto/SessionUpdate.java`:

After the existing `// Facility fields` block (after `private List<FacilityType> facilities;`, around line 25), add:

```java
// Holdings and production fields
private Map<String, Integer> holdings;
private Map<String, Double>  costBasis;
private int ticksUntilProduction;
```

After the existing `getFacilities()` getter (around line 51), add:

```java
public Map<String, Integer> getHoldings()       { return holdings; }
public Map<String, Double>  getCostBasis()       { return costBasis; }
public int getTicksUntilProduction()             { return ticksUntilProduction; }
```

After the existing `.facilities(...)` builder method (around line 71), add:

```java
public Builder holdings(Map<String, Integer> v)  { su.holdings = v; return this; }
public Builder costBasis(Map<String, Double> v)  { su.costBasis = v; return this; }
public Builder ticksUntilProduction(int v)       { su.ticksUntilProduction = v; return this; }
```

`SessionUpdate.java` already imports `java.util.List` and `java.util.Map` — no new imports needed.

- [ ] **Step 4: Run test to confirm it passes**

```
cd D:/OSC/Repos/stock_sim && mvn test -Dtest=SessionUpdateFieldsTest 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 1, Failures: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Wire new fields in `MarketEngine.java`**

In `src/main/java/com/medievalmarket/service/MarketEngine.java`, inside the per-session `SessionUpdate` builder call (around line 153), add three new builder calls after `.fenceCooldown(p.getFenceCooldown())`:

```java
.holdings(p.getHoldings())
.costBasis(p.getAllCostBasis())
.ticksUntilProduction(facilityService.getTicksUntilProduction())
```

**Important:** This call must come after `facilityService.processAll(humans)` (line 116) has already incremented `tickCount`. The builder call is inside the per-session loop starting at line 146, which is after `processAll` — so no reordering needed.

- [ ] **Step 6: Run full test suite**

```
cd D:/OSC/Repos/stock_sim && mvn test 2>&1 | grep -E "Tests run:|BUILD" | tail -5
```

Expected: all tests pass, `BUILD SUCCESS`.

- [ ] **Step 7: Commit**

```
git add src/main/java/com/medievalmarket/dto/SessionUpdate.java \
        src/main/java/com/medievalmarket/service/MarketEngine.java \
        src/test/java/com/medievalmarket/dto/SessionUpdateFieldsTest.java
git commit -m "feat: SessionUpdate carries holdings, costBasis, ticksUntilProduction each tick"
```

---

## Task 3: FacilityController demolish endpoint

**Files:**
- Modify: `src/main/java/com/medievalmarket/controller/FacilityController.java`
- Create: `src/test/java/com/medievalmarket/controller/FacilityControllerTest.java`

Note: `NOBLE` player class starts with **1000g**. Building a `FORGE` (400g) leaves 600g. Demolishing refunds 200g (50%), leaving **800g**.

- [ ] **Step 1: Write the failing controller tests**

Create `src/test/java/com/medievalmarket/controller/FacilityControllerTest.java`:

```java
package com.medievalmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medievalmarket.dto.SessionStartRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FacilityControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    private String sessionId;

    @BeforeEach
    void createSession() throws Exception {
        MvcResult r = mvc.perform(post("/api/session/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SessionStartRequest("NOBLE"))))
            .andReturn();
        sessionId = mapper.readTree(r.getResponse().getContentAsString())
            .get("sessionId").asText();
    }

    private void buildFacility(String type) throws Exception {
        mvc.perform(post("/api/facility/build")
                .header("X-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("type", type))))
            .andExpect(status().isOk());
    }

    @Test
    void demolish_returns200WithUpdatedFacilitiesAndGold() throws Exception {
        buildFacility("FORGE"); // NOBLE 1000g - 400g = 600g; refund 200g → 800g
        mvc.perform(post("/api/facility/demolish")
                .header("X-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("type", "FORGE"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.facilities").isArray())
            .andExpect(jsonPath("$.facilities.length()").value(0))
            .andExpect(jsonPath("$.gold").value(800.0));
    }

    @Test
    void demolish_returns400WhenFacilityNotOwned() throws Exception {
        mvc.perform(post("/api/facility/demolish")
                .header("X-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("type", "FORGE"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("FACILITY_NOT_FOUND"));
    }

    @Test
    void demolish_returns400ForInvalidType() throws Exception {
        mvc.perform(post("/api/facility/demolish")
                .header("X-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("type", "BLAH"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_FACILITY_TYPE"));
    }
}
```

- [ ] **Step 2: Run to confirm tests fail**

```
cd D:/OSC/Repos/stock_sim && mvn test -Dtest=FacilityControllerTest 2>&1 | grep -E "FAIL|ERROR|Tests run"
```

Expected: 3 failures (endpoint not yet defined).

- [ ] **Step 3: Add demolish endpoint to `FacilityController.java`**

In `src/main/java/com/medievalmarket/controller/FacilityController.java`, add after the closing `}` of the existing `build()` method (before the class closing `}`):

```java
@PostMapping("/demolish")
public ResponseEntity<?> demolish(@RequestHeader("X-Session-Id") String sessionId,
                                   @RequestBody Map<String, String> body) {
    Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
    if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
    FacilityType type;
    try {
        type = FacilityType.valueOf(body.get("type"));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_FACILITY_TYPE"));
    }
    try {
        facilityService.demolish(opt.get(), type);
        Portfolio p = opt.get();
        return ResponseEntity.ok(Map.of("facilities", p.getFacilities(), "gold", p.getGold()));
    } catch (FacilityService.FacilityException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
```

No new imports needed — all types (`Optional`, `Map`, `ResponseEntity`, `FacilityType`) are already imported.

- [ ] **Step 4: Run tests to confirm all pass**

```
cd D:/OSC/Repos/stock_sim && mvn test -Dtest=FacilityControllerTest 2>&1 | grep -E "Tests run|BUILD"
```

Expected: `Tests run: 3, Failures: 0` and `BUILD SUCCESS`.

- [ ] **Step 5: Run full suite**

```
cd D:/OSC/Repos/stock_sim && mvn test 2>&1 | grep -E "Tests run:|BUILD" | tail -4
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Commit**

```
git add src/main/java/com/medievalmarket/controller/FacilityController.java \
        src/test/java/com/medievalmarket/controller/FacilityControllerTest.java
git commit -m "feat: POST /api/facility/demolish — removes one copy, refunds 50% build cost"
```

---

## Task 4: Frontend overhaul

**Files:**
- Modify: `src/main/resources/static/index.html`

This task makes 7 targeted edits to `index.html`. Read the file before starting. All line numbers below are approximate — search for the quoted strings to locate each edit precisely.

- [ ] **Step 1: Add `ticksUntilProduction` to the `session` reactive object**

Find the `session` reactive object initialisation (search for `const session = reactive({`). Add `ticksUntilProduction: 5,` after `loanAmount: 0.0,`:

```js
const session = reactive({
  id: null,
  name: '',
  gold: 0,
  holdings: {},
  playerClass: '',
  costBasis: {},
  season: 'SPRING',
  seasonTicksRemaining: 60,
  loanAmount: 0.0,
  ticksUntilProduction: 5,    // ← add this line
  limitOrders: [],
  showAddOrder: false,
  newOrder: { goodName: 'Grain', direction: 'BUY', quantity: 1, targetPrice: 0 },
  showBorrow: false,
  borrowAmount: 100,
});
```

- [ ] **Step 2: Replace `facilityTypes` constant**

Find `const facilityTypes = [` and replace the entire block (5 entries) with the new version that adds `inputs` and `outputQty`:

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

- [ ] **Step 3: Add `demolishConfirm` ref and `ownedFacilityCards` computed**

Find `const facilityCounts = computed(...)` block and **replace it** with:

```js
const demolishConfirm = ref(null);

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

- [ ] **Step 4: Add `toggleDemolishConfirm` and `demolishFacility` functions**

Find `const buildFacility = ` function. After its closing `};`, add:

```js
const toggleDemolishConfirm = (type) => {
  demolishConfirm.value = demolishConfirm.value === type ? null : type;
};

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

- [ ] **Step 5: Update the WebSocket `/user/queue/updates` handler**

Find `if (update.flashMessage) showEvent(update.flashMessage);` (the last line of the update handler). After it, add:

```js
if (update.holdings)   session.holdings  = update.holdings;
if (update.costBasis)  session.costBasis = update.costBasis;
if (update.ticksUntilProduction != null)
    session.ticksUntilProduction = update.ticksUntilProduction;
```

- [ ] **Step 6: Replace the facilities panel HTML**

Find the `<!-- Facilities Panel -->` comment block (everything from that comment through the closing `</div>` of that panel, ending before `<!-- Contracts Panel -->`). Replace it entirely with:

```html
<!-- Facilities Panel -->
<div class="panel">
  <div style="display:flex;justify-content:space-between;align-items:baseline">
    <h3>🏭 Facilities</h3>
    <span class="muted" style="font-size:.75rem">
      {{ facilities.length }}/15 built · next cycle {{ session.ticksUntilProduction }} tick{{ session.ticksUntilProduction === 1 ? '' : 's' }}
    </span>
  </div>

  <p v-if="ownedFacilityCards.length === 0" class="muted">No facilities built.</p>

  <div v-for="card in ownedFacilityCards" :key="card.type"
       class="facility-card"
       :class="card.canProduce ? 'facility-active' : 'facility-idle'">
    <div class="facility-card-top">
      <span><strong>{{ card.ft.label }}</strong> <span style="color:#f59e0b">×{{ card.count }}</span></span>
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
      <button class="btn btn-sm" style="color:#f87171;border-color:#f87171"
              @click="toggleDemolishConfirm(card.type)">Demolish</button>
      <button class="btn btn-sm"
              :disabled="session.gold < card.ft.cost || facilities.length >= 15"
              @click="buildFacility(card.type)">
        + Build another ({{ card.ft.cost }}g)
      </button>
    </div>
    <div v-if="demolishConfirm === card.type" class="demolish-confirm">
      <span>Demolish 1 {{ card.ft.label }} for
        <strong style="color:#4ade80">{{ (card.ft.cost * 0.5).toFixed(0) }}g</strong> refund?</span>
      <span style="display:flex;gap:5px">
        <button class="btn btn-sm" style="color:#f87171;border-color:#f87171"
                @click="demolishFacility(card.type)">Confirm</button>
        <button class="btn btn-sm" @click="demolishConfirm = null">Cancel</button>
      </span>
    </div>
  </div>

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

- [ ] **Step 7: Add new CSS classes**

Find the `<style>` block. After `.facility-build-list { margin-top: .4rem; }` (or wherever the existing facility CSS is), add:

```css
.facility-card { background: #16213e; border-radius: 5px; padding: 8px 10px; margin: 5px 0; border-left: 3px solid transparent; }
.facility-active { border-left-color: #4ade80; }
.facility-idle   { border-left-color: #f87171; }
.facility-card-top { display: flex; justify-content: space-between; align-items: center; }
.facility-recipe { color: #888; font-size: .73rem; margin-top: 2px; }
.facility-output { color: #4ade80; font-size: .73rem; margin-top: 2px; }
.facility-missing { color: #f87171; font-size: .73rem; margin-top: 2px; }
.facility-actions { display: flex; gap: 6px; margin-top: 6px; }
.demolish-confirm { display: flex; justify-content: space-between; align-items: center; background: #2a1a1a; border: 1px solid #f8717155; border-radius: 4px; padding: 5px 8px; margin-top: 6px; font-size: .78rem; color: #f87171; gap: 8px; }
.status-active { color: #4ade80; font-size: .75rem; }
.status-idle   { color: #f87171; font-size: .75rem; }
.section-label { color: #555; font-size: .7rem; text-transform: uppercase; letter-spacing: .06em; margin: 10px 0 4px; }
```

- [ ] **Step 8: Update the `return` block of `setup()`**

**Must be done after Step 6** — Step 6 removes the only template reference to `facilityCounts`. Applying Step 8 before Step 6 will cause a Vue 3 runtime warning about an undefined template variable.

Find the large `return {` block near the end of the `setup()` function. Make these two changes:

1. Replace `facilities, facilityTypes, facilityCounts,` with:
   ```
   facilities, facilityTypes, ownedFacilityCards, demolishConfirm,
   ```

2. Add `toggleDemolishConfirm, demolishFacility,` after `buildFacility,`:
   ```
   buildFacility, toggleDemolishConfirm, demolishFacility,
   ```

- [ ] **Step 9: Run full test suite**

```
cd D:/OSC/Repos/stock_sim && mvn test 2>&1 | grep -E "Tests run:|BUILD" | tail -4
```

Expected: `BUILD SUCCESS`. (Frontend changes are not covered by Java tests — verify visually by running the app.)

- [ ] **Step 10: Commit**

```
git add src/main/resources/static/index.html
git commit -m "feat: facilities panel overhaul — status cards, idle hints, demolish, live holdings"
```
