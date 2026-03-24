# Feature Expansion 2 Design — Guilds, Production, Contracts, Rumours, Black Market

## Goal

Add five interconnected mid-to-late game systems that deepen strategy, create emergent stories, and reward planning over pure reaction.

## Architecture

All five features follow the existing Service Pipeline pattern: new `@Component` services wired into `MarketEngine.tick()`, new REST endpoints, and frontend panels added to `index.html`.

**Tick counter pattern:** Each service that needs tick-based timing maintains its own internal `int tickCount` field (same as `SeasonEngine` does with `tickInSeason`). `MarketEngine.tick()` calls each service's `processTick()` and the service increments its own counter. No global tick counter needed.

**SessionUpdate restructuring:** `SessionUpdate` is currently a `record`. It must be converted to a regular class with a full constructor (or builder) to support the additional fields required by these features. Every instantiation site in `MarketEngine` must be updated.

**EventEngine / RumourService coupling:** `MarketEngine.tick()` receives the fired event (if any) from `EventEngine.maybeFireEvent()`, then calls `RumourService.onEventFired(eventKey, allPortfolios)` passing both the event's key string and the full collection of active portfolios from `SessionRegistry`. `RumourService` iterates the portfolios and marks matching rumours as confirmed. No direct coupling between `EventEngine` and `RumourService`.

**EventDef keys:** `EventDef` gains a `key` (String) field. `FiredEvent` gains a `key` (String) field. The existing 6 events plus plague and the 2 new events use these keys:

| Key | Event |
|---|---|
| `iron_vein` | Iron vein discovered |
| `harvest` | Bad harvest |
| `spice` | Spice ship arrives |
| `war` | War declared |
| `gems` | Gem smugglers caught |
| `wool` | Wool shortage |
| `banquet` | Royal Banquet (new) |
| `embargo` | Trade Embargo (new) |
| `plague` | Plague (built separately in `buildPlagueEvent()`, key = `"plague"`) |

**Tech Stack:** Java 21, Spring Boot 3.2.3, Vue.js 3 CDN, STOMP/SockJS WebSocket. No new dependencies.

---

## New Manufactured Goods (added to GoodsCatalogue)

Five new goods added to the catalogue. They appear in the market table and are subject to normal price dynamics once in circulation.

| Good | Category | Base Price | Volatility |
|---|---|---|---|
| Bread | Agriculture | 18g | LOW |
| Weapons | Mining | 90g | MEDIUM |
| Wine | Agriculture | 65g | MEDIUM |
| Soap | Timber & Craft | 16g | LOW |
| Elixir | Agriculture | 130g | HIGH |

`EventEngine.ALL_GOODS` updated to include all 29 goods. `buildPlagueEvent()` updated: Elixir added to `PLAGUE_PRIMARY` (surges +25–35%); Bread, Wine, Soap, Weapons receive the standard random modifier from the existing non-primary range.

### Event Integration

**Existing events updated:**
- War declared: add Weapons (+30–40%), Bread (+15–25%)
- Bad harvest: add Bread (+20–30%), Wine (+15–25%)
- Plague: add Elixir to `PLAGUE_PRIMARY` (+25–35%); other new goods in non-primary range

**New events added to `EventEngine.EVENTS`:**
- "Royal Banquet announced! Food and drink prices surge." → Bread (+20–30%), Wine (+20–30%), Spices (+15–25%), Candles (+10–20%)
- "Trade Embargo declared! Craft goods flood the market." → Soap (−20–30%), Cloth (−15–25%), Dye (−15–25%), Parchment (−15–25%)

---

## Feature 1: Guilds

### Overview

Once a player's gold exceeds 1000g, they are offered membership in a randomly selected guild via the `SessionUpdate`. Players may decline; after 30 ticks a new guild (different from any previously offered) is offered. Players belong to at most one guild. Guild perks are distinct from starting class perks (which affect fees and starting capital only).

### Guild Definitions

| Enum Value | Display Name | Perk |
|---|---|---|
| `THIEVES_GUILD` | Thieves' Guild | Fence perk: once per 20 ticks, sell any good at 110% market price with no fee, no slippage, no class bonus. Immune to contraband confiscation. |
| `SCHOLARS_GUILD` | Scholars' Guild | Rumour truth rate raised to 78% (vs 60%). Tip cost 5g (vs 10g). |
| `SEA_TRADERS` | Sea Traders' Guild | Once per season, an exotic import offer appears: one purchase of a random rare good at 60% market price. |
| `ROYAL_WARRANT` | Royal Warrant | Contracts pay ×1.6 on top of the standard premium. Receive a random 50–100g stipend each season. |
| `ALCHEMISTS_SOCIETY` | Alchemists' Society | All owned facilities produce 2× output per production cycle. |

### Fence Perk (Thieves' Guild)

Fence replaces the normal sell calculation entirely: `saleValue = currentPrice * quantity * 1.10`. No fee, no slippage, no Miner bonus. Subject to a 20-tick cooldown tracked per session.

### Royal Warrant Stipend

`GuildService.processTick()` receives the current season string. It stores `lastSeenSeason`. When the season string changes, if the player holds `ROYAL_WARRANT`, add a random 50–100g to their gold directly.

### Per-Session Guild State

All guild state lives in `Portfolio` (the existing per-session store):
- `Guild guild` — nullable, the joined guild
- `int guildOfferCooldown` — ticks until next offer (decremented by `GuildService`)
- `Guild lastOfferedGuild` — prevents offering the same guild twice in a row
- `int fenceCooldown` — ticks remaining on fence cooldown
- `boolean exoticImportAvailable` — whether the exotic import can be purchased this season

### Backend

**`Guild.java`** — enum with fields: `displayName`, `description`.

**`GuildService.java`** — stateless Spring component. All state in `Portfolio`.
- `processTick(portfolio, currentSeason)`: decrements `guildOfferCooldown` and `fenceCooldown`; checks for season change to deliver stipend or refresh exotic import; if `guildOfferCooldown == 0 && guild == null`, sets a `pendingGuildOffer` field on the Portfolio with a random guild (not `lastOfferedGuild`).
- `accept(portfolio)`: reads `portfolio.pendingGuildOffer` (ignores any client-supplied guild name — the guild is always taken from the server-side pending offer to prevent exploitation), sets `portfolio.guild`, clears `pendingGuildOffer`.
- `decline(portfolio)`: clears `pendingGuildOffer`, sets `lastOfferedGuild` to declined guild, resets `guildOfferCooldown = 30`.
- `fence(portfolio, goodName, quantity)`: validates cooldown and holdings, executes sale at 110% market price, resets `fenceCooldown = 20`.
- `buyExoticImport(portfolio, goodName)`: validates `exoticImportAvailable`, executes buy at 60% price, sets `exoticImportAvailable = false`.

**`Portfolio.java`** additions: `Guild guild`, `Guild pendingGuildOffer`, `Guild lastOfferedGuild`, `int guildOfferCooldown`, `int fenceCooldown`, `boolean exoticImportAvailable`, `String lastSeenSeason`.

**REST endpoints on `GuildController`:**
- `POST /api/guild/accept` — no body; guild taken from `portfolio.pendingGuildOffer` server-side — returns `{ guild, error? }`
- `POST /api/guild/decline` — no body — returns `{}`
- `POST /api/guild/fence` — body: `{ goodName, quantity }` — returns `{ gold, error? }`
- `POST /api/guild/exotic-import/buy` — no body; good taken from `portfolio.exoticImportOffer` server-side — returns `{ gold, holding, error? }`

**`ExoticImportOffer.java`** — record: `goodName`, `discountedPrice`. Stored on `Portfolio` as `exoticImportOffer` (nullable). `GuildService` populates this when refreshing on season change for `SEA_TRADERS` members, choosing a random rare good (Gems, Spices, Elixir, Silver) at 60% of its current market price.

**`Portfolio.java`** additions (updated): `Guild guild`, `Guild pendingGuildOffer`, `Guild lastOfferedGuild`, `int guildOfferCooldown`, `int fenceCooldown`, `ExoticImportOffer exoticImportOffer`, `String lastSeenSeason`.

**`SessionUpdate`** additions: `Guild guild`, `Guild pendingGuildOffer`, `ExoticImportOffer exoticImportOffer`, `int fenceCooldown`.

### Frontend

- Guild offer modal: appears when `pendingGuildOffer` is non-null in the session update. Shows guild name, description, Accept/Decline buttons.
- Guild badge shown in the player info panel header once joined.
- Fence button: appears in the market table sell column (alongside 1/10/100/All) when `guild == THIEVES_GUILD && fenceCooldown == 0`.
- Exotic import flash panel: appears when `exoticImportOffer` is non-null for Sea Traders; shows the good name, discounted price, and a Buy button. Disappears when `exoticImportOffer` becomes null after purchase or season end.

---

## Feature 2: Production & Crafting

### Overview

Players build facilities that consume raw goods from their holdings every 5 ticks and produce manufactured goods into their holdings. Cap: 15 total facilities. Facilities add supply pressure to the output good's price (same mechanism as `TradeService.buy()`).

### Facility Definitions (FacilityType enum)

| Enum | Display | Inputs per cycle | Output | Output qty | Build Cost |
|---|---|---|---|---|---|
| `MILL` | Mill 🏚️ | 3 Grain + 1 Salt | Bread | 2 | 150g |
| `FORGE` | Forge ⚒️ | 2 Iron + 1 Coal | Weapons | 1 | 400g |
| `WINERY` | Winery 🍷 | 2 Ale + 1 Honey | Wine | 1 | 250g |
| `SOAPWORKS` | Soapworks 🧼 | 2 Wax + 1 Salt | Soap | 3 | 200g |
| `APOTHECARY` | Apothecary 🧪 | 2 Herbs + 1 Honey | Elixir | 1 | 500g |

- Alchemists' Society guild: output qty doubled.
- If player lacks inputs, facility idles silently.
- Production cycle: every 5 ticks (each `FacilityService` instance tracks its own `tickCount`).
- Supply pressure added per production run: `outputQty * 0.02` (same coefficient as `TradeService`).

### Backend

**`FacilityType.java`** — enum. Each value carries: `displayName`, `buildCost`, `inputs` (`Map<String, Integer>`), `outputGood` (String), `outputQty` (int).

**`FacilityService.java`** — depends on `GoodsCatalogue`. Stateless except for `int tickCount`.
- `build(portfolio, type)`: validates `portfolio.getGold() >= type.getBuildCost()` and `portfolio.getFacilities().size() < 15`. Deducts gold. Adds `type` to `portfolio.getFacilities()`. Returns error string or null.
- `processTick(portfolio)`: increments `tickCount`; if `tickCount % 5 == 0`, for each facility in `portfolio.getFacilities()`: checks inputs available, deducts inputs, adds output to holdings, adds supply pressure.

**`Portfolio.java`** addition: `List<FacilityType> facilities` (mutable list, initialised empty).

**`FacilityController.java`:**
- `POST /api/facility/build` — body: `{ type: "MILL" }` — returns `{ facilities, gold, error? }` where `error` is `"INSUFFICIENT_FUNDS"` or `"FACILITY_CAP_REACHED"`.

**`SessionUpdate`** addition: `List<FacilityType> facilities`.

### Frontend

- Facilities panel in the side panel. Shows owned facilities grouped by type with counts and total (e.g., "Mill ×2, Forge ×1 — 3/15"). Build section lists all 5 types with emoji, cost, input recipe, and a Build button. Build button disabled if insufficient gold or cap reached.

---

## Feature 3: Contracts

### Overview

Every ~40 ticks a contract offer appears to the player. One active contract slot. Fulfilling earns a gold premium; missing the deadline incurs a penalty deducted from gold.

Contraband goods do **not** count toward contract fulfillment. Only normal `holdings` are checked.

### Contract Patrons & Templates

| Patron | Flavour | Required goods | Ticks | Base Premium |
|---|---|---|---|---|
| 👑 The King | "His Majesty hosts a grand feast. He requires provisions." | 30 Bread + 20 Wine | 15 | 35% |
| ⚔️ Baron Aldric | "The Baron marches to war. His army needs arming." | 25 Weapons + 20 Rope | 12 | 40% |
| ⛪ The Church | "The Church blesses the harvest. They seek offerings." | 40 Grain + 10 Candles | 10 | 25% |
| 🏰 Lady Matilda | "The Lady rebuilds her keep after the great fire." | 30 Timber + 15 Stone | 14 | 30% |
| 🐟 The Harbour Master | "The fleet departs at dawn and must be provisioned." | 20 Rope + 30 Fish + 10 Salt | 12 | 32% |
| 🌿 The Apothecary Guild | "A sickness spreads through the city. Lives hang in the balance." | 15 Elixir + 20 Herbs | 10 | 45% |

- Quantities and tick deadlines randomised ±20% from template values at generation time.
- Reward: sum over required goods of `(marketPrice * quantity * premiumRate)`. Royal Warrant guild: multiply total reward by 1.6.
- Penalty: 15% of reward value deducted from gold (minimum 0g).

### State Transitions

- New offer generated: `ticksSinceLastOffer >= 40 && activeContract == null && pendingContractOffer == null`.
- Player accepts: `pendingContractOffer` moves to `activeContract`, `ticksSinceLastOffer` resets to 0.
- Player declines: `pendingContractOffer` cleared, `ticksSinceLastOffer` resets to 0 (so next offer comes after 40 ticks).
- Deadline reached: penalty applied, `activeContract` cleared.
- Player delivers: holdings checked for all required goods (normal holdings only), goods deducted, reward added to gold, `activeContract` cleared.

### Backend

**`Contract.java`** — record: `patronName`, `flavourText`, `requirements` (`Map<String, Integer>`), `ticksRemaining`, `rewardGold`, `penaltyGold`.

**`ContractService.java`** — all state in `Portfolio`. Tracks `int tickCount` internally.
- `processTick(portfolio)`: increments tick count, decrements `activeContract.ticksRemaining` if active, applies penalty and clears on expiry; increments `ticksSinceLastOffer`, generates new offer when conditions met.
- `deliver(portfolio)`: validates normal holdings meet requirements, deducts goods, adds reward, clears contract.

**`Portfolio.java`** additions: `Contract activeContract`, `Contract pendingContractOffer`, `int ticksSinceLastOffer`.

**`ContractController.java`:**
- `GET /api/contract` — returns `{ activeContract, pendingOffer }`
- `POST /api/contract/accept`
- `POST /api/contract/decline`
- `POST /api/contract/deliver` — returns `{ gold, error? }`

**`SessionUpdate`** additions: `Contract activeContract`, `Contract pendingContractOffer`.

### Frontend

- Contracts panel in the side panel. Active contract: patron name + flavour text, required goods list with current held qty shown inline (green when met, red when short), ticks remaining with urgency colour (green→yellow→red), reward/penalty amounts, Deliver button (enabled only when all requirements met from normal holdings). Pending offer: Accept/Decline buttons. No contract: "No active contract."

---

## Feature 4: Rumours

### Overview

A rumour board shows up to 3 rumours at a time. Rumours hint at upcoming events. They have an independent truth rate and a separate tip accuracy. The two are combined as follows:

- **Truth rate**: probability the hinted event fires within 30 ticks. Default 60%; Scholars' Guild 78%.
- **Tip accuracy**: probability the tip verdict is correct (i.e., matches the rumour's `isTrue` value). Fixed at 70% for all players.
- These are independent: a Scholar tipping a true rumour gets the correct verdict ("Seems reliable") 70% of the time; the rumour itself being true is 78% likely.

### Rumour Templates

Each rumour is linked to a named event key. `RumourService` is notified by `MarketEngine` when an event fires via `onEventFired(eventName)`, which marks matching active rumours as confirmed.

| Event Key | Rumour Text |
|---|---|
| `war` | "Soldiers have been seen requisitioning horses along the King's road..." |
| `harvest` | "A shepherd near Ashford claims his flock is diseased and crops are wilting..." |
| `spice` | "Foreign galleys spotted off the coast, heavy with cargo..." |
| `iron_vein` | "Miners speak of a rich new vein in the northern hills..." |
| `gems` | "Customs officers raided a warehouse near the docks last night..." |
| `wool` | "Weavers in three villages report empty looms this season..." |
| `banquet` | "The palace kitchens have been burning lights all night..." |
| `embargo` | "Merchants' caravans are turning back at the border gates..." |
| `plague` | "Physicians are unusually busy near the market district..." |

- New rumours fill empty slots every ~20 ticks.
- Rumours expire after 30 ticks; expired rumours are replaced.
- Rumours are **per-session** (each player sees independently generated rumours).

### Backend

**`Rumour.java`** — record: `id` (UUID), `text`, `eventKey`, `isTrue` (boolean, hidden from DTO), `ticksRemaining`, `tipResult` (nullable String: `"RELIABLE"` or `"DUBIOUS"`).

**`RumourService.java`** — stateless Spring component. All state in `Portfolio`. Injected with `SessionRegistry` to access all portfolios for event notification.
- `processTick(portfolio)`: decrements `ticksRemaining` on each rumour, removes expired ones, generates new rumours to fill up to 3 slots (truth rate determined at generation time: 60% true, or 78% if `Scholars' Guild`).
- `onEventFired(eventKey, Collection<Portfolio> allPortfolios)`: called by `MarketEngine` with all active session portfolios; iterates each portfolio's rumour list and marks rumours with matching `eventKey` as confirmed (sets `confirmed = true` on the rumour internally — does not expose to client).
- `tip(portfolio, rumourId)`: deducts tip cost (10g default, 5g for Scholars' Guild). 70% chance returns the correct verdict matching `isTrue`, 30% chance returns the opposite. Stores result in the `tipResult` field of the rumour.

**`Portfolio.java`** addition: `List<Rumour> rumours` (mutable, initialised empty).

**`RumourController.java`:**
- `GET /api/rumours` — returns current rumour list (without `isTrue` field)
- `POST /api/rumours/{id}/tip` — returns `{ tipResult, gold, error? }`

**`SessionUpdate`** addition: `List<RumourDTO> rumours` (DTO omits `isTrue`).

### Frontend

- Rumours panel in the side panel. Shows up to 3 rumour cards. Each: text, ticks remaining, Tip button (disabled after tipped or if insufficient gold). After tipping: inline verdict with colour (green "✓ Seems reliable" / red "✗ Seems dubious").

---

## Feature 5: Black Market

### Overview

A Black Market appearance is **per-session**: each player has an independent roll every 30 ticks with a 15% chance of an appearance. When active, 1–3 goods are offered at 40–60% of market price. Purchased goods are tagged as contraband. Each tick carries a 3% confiscation roll per player who holds contraband. Thieves' Guild members are immune to confiscation.

### Panel Lifetime

When an appearance triggers, `BlackMarketService` sets `blackMarketTicksRemaining = 10` on the portfolio. The panel stays active for 10 ticks or until all offers are purchased, whichever comes first. When `blackMarketTicksRemaining` reaches 0 or offers list empties, the appearance ends and `blackMarketOffers` is set to null. The frontend learns about expiry because `SessionUpdate.blackMarketOffers` becomes null.

### Confiscation

`BlackMarketService.processTick(portfolio)` rolls 3% confiscation chance if `contrabandHoldings` is non-empty and the player is not in `THIEVES_GUILD`. On confiscation: `contrabandHoldings` is cleared, and a confiscation event string is returned. `MarketEngine` includes this string in the individual `SessionUpdate` as a one-shot `flashMessage` field, which the frontend displays as an event-style banner.

Contraband goods do **not** count toward contract fulfillment (normal `holdings` only).

### Backend

**`BlackMarketOffer.java`** — record: `goodName`, `availableQty`, `discountedPrice`.

**`BlackMarketService.java`** — stateless Spring component. All state in `Portfolio`.
- `processTick(portfolio)`: increments internal tick counter per portfolio (tracked via `portfolio.blackMarketTicksSinceLastRoll`); rolls for appearance every 30 ticks; decrements `blackMarketTicksRemaining` if active; clears expired appearances; rolls confiscation (3%) if contraband held and not Thieves' Guild; returns optional confiscation message.
- `buy(portfolio, goodName, quantity)`: validates offer exists and player can afford it; deducts gold; adds to `contrabandHoldings` (separate from normal `holdings`); removes fulfilled offers.

**`Portfolio.java`** additions: `Map<String, Integer> contrabandHoldings`, `List<BlackMarketOffer> blackMarketOffers`, `int blackMarketTicksRemaining`, `int blackMarketTicksSinceLastRoll`.

**`BlackMarketController.java`:**
- `GET /api/blackmarket` — returns `{ offers, contrabandHoldings }` (null offers when inactive)
- `POST /api/blackmarket/buy` — body: `{ goodName, quantity }` — returns `{ gold, contrabandHoldings, error? }`

**`SessionUpdate`** additions: `List<BlackMarketOffer> blackMarketOffers`, `Map<String, Integer> contrabandHoldings`, `String flashMessage` (one-shot, null when no flash).

### Frontend

- Black market panel: hidden by default; slides in when `blackMarketOffers` is non-null in session update. Styled with amber/warning theme (⚠️). Shows goods, discounted price, quantity available, Buy button. Panel disappears when `blackMarketOffers` becomes null.
- Contraband goods: shown in a separate "Contraband ⚠️" sub-section in the holdings panel with amber colouring. Not shown in the market table "Held" column.
- Confiscation: `flashMessage` triggers the same event-banner mechanism as existing events.

---

## File Summary

### New Files

| File | Purpose |
|---|---|
| `model/Guild.java` | Enum with display name, description |
| `model/FacilityType.java` | Enum with build cost, inputs, output |
| `model/Contract.java` | Record: patron, requirements, ticks, reward, penalty |
| `model/Rumour.java` | Record: text, eventKey, isTrue, ticksRemaining, tipResult |
| `model/BlackMarketOffer.java` | Record: goodName, qty, discountedPrice |
| `model/ExoticImportOffer.java` | Record: goodName, discountedPrice |
| `service/GuildService.java` | Guild offers, fence, exotic import, stipend |
| `service/FacilityService.java` | Build facilities, production tick (depends on GoodsCatalogue) |
| `service/ContractService.java` | Contract generation, deadline tracking, delivery |
| `service/RumourService.java` | Rumour generation, tip mechanic, event notification |
| `service/BlackMarketService.java` | Appearance rolls, contraband buy, confiscation |
| `controller/GuildController.java` | accept, decline, fence, exotic-import/buy |
| `controller/FacilityController.java` | build |
| `controller/ContractController.java` | get, accept, decline, deliver |
| `controller/RumourController.java` | get, tip |
| `controller/BlackMarketController.java` | get, buy |

### Modified Files

| File | Changes |
|---|---|
| `model/Portfolio.java` | Add all per-session state fields for all 5 features |
| `dto/SessionUpdate.java` | Convert from record to class; add guild, facilities, contract, rumours, blackMarketOffers, contrabandHoldings, pendingGuildOffer, flashMessage |
| `service/GoodsCatalogue.java` | Add 5 manufactured goods |
| `service/EventEngine.java` | Add ALL_GOODS entries, add 2 new events, update war/harvest/plague modifiers, add event keys to FiredEvent |
| `service/MarketEngine.java` | Wire 5 new services into tick; pass fired event key to RumourService; collect flashMessages into SessionUpdate |
| `service/TradeService.java` | Fence perk hook (110% no-fee sell path, called from GuildController not here) |
| `resources/static/index.html` | Add 5 new panels + guild modal; update GOOD_ICONS for 5 new goods |
