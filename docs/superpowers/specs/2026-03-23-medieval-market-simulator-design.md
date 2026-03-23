# Medieval Market Simulator — Design Spec

**Date:** 2026-03-23
**Status:** Approved

---

## Overview

A real-time multiplayer medieval goods trading simulator with a web frontend. Players choose a starting class and trade 15 medieval commodities in a shared marketplace where their actions affect prices for all connected players. There is no win condition — it is an open-ended sandbox focused on accumulating wealth.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3 |
| Real-time comms | Spring WebSocket (STOMP) |
| REST API | Spring MVC |
| Frontend | Vue.js 3 (CDN, no build step) |
| Charts | Chart.js (CDN) |
| Persistence | Browser localStorage (session ID + portfolio cache) |
| Server state | In-memory `ConcurrentHashMap` (no database) |

---

## Architecture

### Backend Components

**`MarketEngine`**
- Scheduled task running every 5 seconds
- Orchestrates the price tick: calls `PriceModel`, then broadcasts via WebSocket
- Maintains the global supply pressure map (net buy/sell per good since last tick)

**`PriceModel`**
- Computes new price for each good per tick using three layers:
  1. **Random drift** — see Volatility Multipliers below
  2. **Supply/demand** — net buy/sell pressure nudges price; decays 30% per tick after all goods are priced (see Price Tick Sequence step 3 — decay is never applied inside the per-good loop)
  3. **Event modifier** — if `EventEngine` fires an event this tick, applies a category-level multiplier as a signed float in [−1, 1], e.g., +35% = `0.35`, −30% = `−0.30`
- Enforces a price floor (10% of base price) and ceiling (500% of base price)

**`EventEngine`**
- ~10% chance per tick to fire a random market event
- Each event targets one or more good categories with a modifier drawn from the event's defined range
- Emits an event message string (e.g., *"War declared! Iron prices surge."*) broadcast alongside prices

**`PriceHistory`**
- Stores the last 50 price points per good (circular buffer)
- Pre-seeded with 50 copies of each good's `basePrice` at server startup, so sparklines render from the first tick
- Reads by `GET /api/market/snapshot` are permitted to return a slightly stale copy; relaxed consistency is acceptable here — no synchronization required between snapshot reads and tick writes
- Included in WebSocket tick payload for chart rendering

**`SessionRegistry`**
- `ConcurrentHashMap<String sessionId, Portfolio>` — server-side portfolio state
- Inactivity is defined as: no trade REST call received for the session within 2 hours
- Sessions expire after 2 hours of inactivity; passive price-watching does not reset the timer

**`NameGenerator`**
- Assigns a random medieval alias to each player at session creation, e.g., *"Aldric the Bold"*, *"Mildred of Ironhaven"*
- Name is composed of a medieval first name + one of: an epithet ("the Bold", "the Cunning") or a place suffix ("of Stonekeep", "of Ironhaven")
- Name is stored in `Portfolio` and included in all scoreboard payloads; it cannot be changed after session creation

**`ScoreboardService`**
- Called by `MarketEngine` each tick after prices are updated
- For each active session, computes `netWorth = gold + Σ(holdingQty × currentPrice)` using the freshly computed prices
- Appends net worth to a per-session circular buffer (last 10 values, ~50 seconds of history)
- Computes `trend = currentNetWorth − netWorthFrom5TicksAgo` (or the oldest available value if fewer than 5 ticks have elapsed)
- Produces a ranked `ScoreboardEntry` list sorted by `netWorth` descending
- Annotates the top 3 entries by `trend` ascending (biggest gainers) and top 3 by `trend` descending (biggest losers)
- Result is included in the WebSocket tick broadcast

**`TradeService`**
- Handles buy/sell REST requests
- Determines the effective fee rate from the player's class before any computation: Merchant = 0%, all others = 3% — no post-hoc refund
- For Miner sells of `category == "Mining"` goods: applies `saleValue *= 1.02` after fee deduction
- Updates supply pressure using `synchronized` on the good's state object to prevent race conditions with concurrent `MarketEngine` ticks
- Returns updated portfolio state

**WebSocket Broker**
- Topic: `/topic/market` — broadcasts full price snapshot + event message every tick
- All connected clients receive the same broadcast simultaneously

**REST Endpoints**

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/session/start` | Create session, choose class; returns sessionId + initial portfolio |
| `GET` | `/api/session/{sessionId}` | Resume existing session; returns portfolio or 404 if expired/not found |
| `POST` | `/api/trade/buy` | Buy N units of a good |
| `POST` | `/api/trade/sell` | Sell N units of a good |
| `GET` | `/api/market/snapshot` | Current prices + price history (initial load) |

**`POST /api/session/start` — Request / Response**

```json
// Request
{ "class": "Merchant" }   // valid values: "Merchant", "Miner", "Noble"

// Response 200
{ "sessionId": "uuid-string", "playerName": "Aldric the Bold", "gold": 500.0, "holdings": {} }
```

**`POST /api/trade/buy` and `POST /api/trade/sell` — Request body**

```json
{ "sessionId": "uuid-string", "good": "Iron", "quantity": 3 }
```

`sessionId` is always passed in the request body (not a header or path parameter).

**Trade Endpoint Success Response (200)**

```json
{ "gold": 458.8, "holdings": { "Iron": 3, "Wool": 1 } }
```

**Trade Endpoint Error Responses**

| Condition | HTTP Status | Body |
|---|---|---|
| Insufficient gold to buy | `400 Bad Request` | `{"error": "INSUFFICIENT_FUNDS"}` |
| Insufficient holdings to sell | `400 Bad Request` | `{"error": "INSUFFICIENT_HOLDINGS"}` |
| Quantity out of range (< 1 or > 10) | `400 Bad Request` | `{"error": "INVALID_QUANTITY"}` |
| Good name not recognised | `400 Bad Request` | `{"error": "UNKNOWN_GOOD"}` |
| `sessionId` absent or not a valid UUID | `400 Bad Request` | `{"error": "INVALID_SESSION_ID"}` |
| Session not found or expired | `404 Not Found` | `{"error": "SESSION_NOT_FOUND"}` |

### Frontend Components

**`ClassSelectView`**
- Shown on first load or when no valid session exists in localStorage
- Displays three class cards (Merchant, Miner, Noble) with descriptions and difficulty ratings
- On select: calls `POST /api/session/start`, stores `sessionId` in localStorage

**`MarketView`** (main screen)
- Left panel: scrollable goods table
  - Columns: good name, category icon, current price, price delta indicator (↑↓→), sparkline chart, quantity input, buy/sell buttons
- Right panel: stacked vertically
  - **Portfolio sidebar** — gold balance, holdings list (good → quantity → current value), net worth total
  - **Scoreboard panel** — ranked list of all active players by net worth; top 3 gainers highlighted green with ▲ trend indicator; top 3 losers highlighted red with ▼ trend indicator; current player's row is bolded
- Top bar: scrolling event ticker showing recent market events

**`useMarketStore`** (Vue composable)
- Manages WebSocket connection via STOMP.js
- Holds reactive price map, price history arrays, and event log (retains last 20 events in memory; the ticker renders only the most recent 5)
- **Reconnection policy:** on disconnect, retries every 3 seconds up to 5 attempts; displays a "Reconnecting…" banner in the top bar during this window; any tick received while reconnecting is discarded. On successful reconnect, fetches a fresh snapshot via `GET /api/market/snapshot` to resync state.

**`usePortfolioStore`** (Vue composable)
- Holds gold, holdings, session ID, and player name
- Syncs to localStorage on every mutation
- On app load: attempts to resume from localStorage session ID via `GET /api/session/{sessionId}`; redirects to class selection on 404

---

## Player Classes

| Class | Starting Gold | Perk | Difficulty |
|---|---|---|---|
| **Merchant** | 500g | No trade fee on any transaction | Intermediate |
| **Miner** | 350g | +2% sell price on goods where `category == "Mining"` | Hard |
| **Noble** | 1,000g | No perk — raw capital advantage | Easy |

---

## Goods Catalogue (15 goods)

| Good | Category | Base Price | Volatility |
|---|---|---|---|
| Grain | Agriculture | 10g | Low |
| Wool | Agriculture | 20g | Low |
| Livestock | Agriculture | 35g | Low |
| Ale | Agriculture | 15g | Low |
| Spices | Agriculture | 80g | High |
| Iron | Mining | 40g | Medium |
| Coal | Mining | 25g | Medium |
| Stone | Mining | 18g | Low |
| Gems | Mining | 120g | High |
| Salt | Mining | 22g | Low |
| Timber | Timber & Craft | 30g | Low |
| Rope | Timber & Craft | 12g | Low |
| Cloth | Timber & Craft | 28g | Medium |
| Leather | Timber & Craft | 45g | Medium |
| Candles | Timber & Craft | 8g | Low |

Each good has:
- `basePrice` — anchor value in gold (used for floor/ceiling and history pre-seeding)
- `volatility` — LOW / MEDIUM / HIGH (see Volatility Multipliers)
- `category` — used for event targeting and Miner perk
- `supplyPressure` — float, decays 30% per tick toward 0 (applied after all goods are priced — see Price Tick Sequence step 3)

**Volatility Multipliers**

| Volatility | Drift Multiplier | Effective Drift Range |
|---|---|---|
| Low | 0.5× | ±0–2.5% |
| Medium | 1.0× | ±0–5% |
| High | 1.5× | ±0–7.5% |

---

## Trade Mechanics

**Trade Fee**
- Fee rate is determined by class before computation: Merchant = 0%, all others = 3%
- Gold balances are stored as `double` server-side and displayed rounded to 2 decimal places client-side; no rounding is applied between chained multiplications within a single trade
- **Buy example (non-Merchant):** Buy 1 Iron at 40g → player pays `40 × 1.03 = 41.2g`; receives 1 unit
- **Sell example (non-Miner, using Grain):** Sell 1 Grain at 10g → player receives `10 × 0.97 = 9.7g`
- **Sell example (Miner, Mining good):** Sell 1 Iron at 40g → fee deducted first: `40 × 0.97 = 38.8g`, then Miner bonus: `38.8 × 1.02 = 39.58g`

**Miner Perk**
- Triggered when player class is Miner and `good.category == "Mining"`
- Applied at sell time, after fee deduction: `saleValue *= 1.02`

**Quantity**
- Players choose 1–10 units per trade via number input
- Partial fills not supported (all-or-nothing)
- No per-good or total holdings cap — players may accumulate unlimited inventory

**Supply/Demand**
- Each buy adds `+quantity × 0.5` pressure to the good
- Each sell adds `−quantity × 0.5` pressure
- Price effect formula: `price *= (1 + supplyPressure × 0.01)` applied each tick before decay
- After application, `supplyPressure *= 0.70` (30% decay)

---

## Price Tick Sequence (every 5 seconds)

```
1. EventEngine.maybeFireEvent()   → optional event modifier
2. For each good:
   a. Apply random drift:
        drift = random(0, maxDrift) × randomSign()
        maxDrift = 0.05 × volatilityMultiplier
        randomSign() returns +1 or −1 with equal probability (uniform 50/50 coin flip)
        price *= (1 + drift)
   b. Apply supply/demand pressure:
        price *= (1 + supplyPressure × 0.01)
   c. Apply event modifier (if applicable):
        price *= (1 + eventModifier)
   d. Clamp to [basePrice × 0.10, basePrice × 5.00]
   e. Append to PriceHistory (circular buffer, max 50)
3. Decay each good's supplyPressure: supplyPressure *= 0.70
4. ScoreboardService.computeScoreboard(currentPrices) → scoreboard array
5. Broadcast via WebSocket /topic/market:
   { prices: {...}, history: {...}, event: "..." | null, scoreboard: [...] }
```

---

## Market Events

| Event | Primary Affected Goods | Primary Change | Secondary Goods | Secondary Change |
|---|---|---|---|---|
| Iron vein discovered | Iron, Coal, Stone | −20 to −35% | — | — |
| Bad harvest | Grain, Livestock | +25 to +40% | — | — |
| Spice ship arrives | Spices | −15 to −25% | — | — |
| War declared | Iron, Coal, Timber, Rope | +30 to +40% | — | — |
| Plague outbreak | Livestock, Grain | +15 to +25% | All other goods | −10% to +10% randomly per good |
| Gem smugglers caught | Gems | +15 to +25% | — | — |
| Wool shortage | Wool, Cloth | +20 to +30% | — | — |

---

## WebSocket Tick Payload

The `/topic/market` message is a JSON object broadcast every tick. Example (abbreviated to 3 goods):

```json
{
  "prices": {
    "Grain": 11.4,
    "Iron": 38.7,
    "Spices": 95.2
  },
  "history": {
    "Grain": [10.0, 10.2, 10.8, 11.1, 11.4],
    "Iron": [40.0, 41.2, 39.8, 38.7],
    "Spices": [80.0, 82.1, 88.4, 95.2]
  },
  "event": "Bad harvest! Grain and livestock prices surge.",
  "scoreboard": [
    { "name": "Aldric the Bold",    "class": "Merchant", "netWorth": 1240.5, "trend": 87.3,  "trending": "UP" },
    { "name": "Mildred of Stonekeep","class": "Noble",   "netWorth": 1105.0, "trend": -42.1, "trending": "DOWN" },
    { "name": "Godwin the Cunning", "class": "Miner",    "netWorth": 890.2,  "trend": 12.0,  "trending": "NEUTRAL" }
  ]
}
```

- `prices`: map of good name (string) → current price (float, rounded to 1 decimal place)
- `history`: map of good name → array of the last up-to-50 price points (floats), oldest first
- `event`: event message string if an event fired this tick, or `null`
- `scoreboard`: array of all active sessions, sorted by `netWorth` descending
  - `name`: player's assigned medieval alias
  - `class`: player's chosen class
  - `netWorth`: `gold + Σ(holdingQty × currentPrice)`, rounded to 1 decimal place
  - `trend`: net worth change over last 5 ticks (positive = gaining, negative = losing)
  - `trending`: `"UP"` if in top 3 gainers, `"DOWN"` if in top 3 losers, `"NEUTRAL"` otherwise

**`GET /api/market/snapshot` Response**

The snapshot response has the same shape as the WebSocket tick payload, minus the `event` field:

```json
{
  "prices": { "Grain": 11.4, "Iron": 38.7, ... },
  "history": { "Grain": [10.0, 10.2, ...], "Iron": [40.0, 41.2, ...], ... }
}
```

Used on initial page load and after WebSocket reconnect to restore full chart state.

---

## Capacity Note

The server holds all sessions in-memory with no database. The application is designed for up to ~100 concurrent sessions. The operator is responsible for sizing the JVM heap accordingly (512 MB is sufficient for this scale).

---

## UI Layout

**Main screen:** two-panel layout
- **Left (70%):** market table with sparkline charts per row
- **Right (30%):** portfolio sidebar, always visible
- **Top:** event news ticker (last 5 events, auto-scrolls)

**Class selection screen:** three-card grid, full-page, shown before session creation

---

## Session & Persistence

- On session start: server assigns UUID `sessionId`, stores portfolio in `SessionRegistry`
- Client stores `sessionId` in localStorage
- On app reload: client calls `GET /api/session/{sessionId}`; server returns portfolio or 404 if expired/not found
- If session expired or not found: redirect to class selection
- Sessions expire after 2 hours with no trade actions (passive watching does not reset the timer)

---

## Out of Scope

- User accounts / authentication
- Multiple markets / travel
- Mobile-specific layout
- Sound effects
- Persistent database storage
