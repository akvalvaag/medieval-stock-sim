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
  1. **Random drift** — each good moves ±0–5%, weighted by its `volatility` rating
  2. **Supply/demand** — net buy/sell pressure nudges price up/down; decays to neutral over ~3 ticks
  3. **Event modifier** — if `EventEngine` fires an event this tick, applies a category-level multiplier
- Enforces a price floor (10% of base price) and ceiling (500% of base price)

**`EventEngine`**
- ~10% chance per tick to fire a random market event
- Each event targets one or more good categories with a ±15–40% price modifier
- Emits an event message string (e.g., *"War declared! Iron prices surge."*) broadcast alongside prices

**`PriceHistory`**
- Stores the last 50 price points per good (circular buffer)
- Included in WebSocket tick payload for chart rendering

**`SessionRegistry`**
- `ConcurrentHashMap<String sessionId, Portfolio>` — server-side portfolio state
- Sessions expire after 2 hours of inactivity

**`TradeService`**
- Handles buy/sell REST requests
- Applies trade fee (3%), class perks, and supply pressure updates
- Returns updated portfolio state

**WebSocket Broker**
- Topic: `/topic/market` — broadcasts full price snapshot + event message every tick
- All connected clients receive the same broadcast simultaneously

**REST Endpoints**

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/session/start` | Create session, choose class, returns sessionId + initial portfolio |
| `POST` | `/api/session/resume` | Resume existing session by sessionId |
| `POST` | `/api/trade/buy` | Buy N units of a good |
| `POST` | `/api/trade/sell` | Sell N units of a good |
| `GET` | `/api/market/snapshot` | Current prices + price history (initial load) |

### Frontend Components

**`ClassSelectView`**
- Shown on first load or when no valid session exists in localStorage
- Displays three class cards (Merchant, Miner, Noble) with descriptions and difficulty ratings
- On select: calls `POST /api/session/start`, stores `sessionId` in localStorage

**`MarketView`** (main screen)
- Left panel: scrollable goods table
  - Columns: good name, category icon, current price, price delta indicator (↑↓→), sparkline chart, quantity input, buy/sell buttons
- Right panel: portfolio sidebar
  - Current gold balance
  - Holdings list (good → quantity → current value)
  - Net worth total
- Top bar: scrolling event ticker showing recent market events

**`useMarketStore`** (Vue composable)
- Manages WebSocket connection via STOMP.js
- Holds reactive price map, price history arrays, and event log
- On reconnect: fetches fresh snapshot via `GET /api/market/snapshot`

**`usePortfolioStore`** (Vue composable)
- Holds gold, holdings, session ID
- Syncs to localStorage on every mutation
- On app load: attempts to resume from localStorage session ID

---

## Player Classes

| Class | Starting Gold | Perk | Difficulty |
|---|---|---|---|
| **Merchant** | 500g | No trade fee on any transaction | Intermediate |
| **Miner** | 350g | +2% sell price on mining goods (Iron, Coal, Stone, Gems, Salt) | Hard |
| **Noble** | 1,000g | No perk — raw capital advantage | Easy |

---

## Goods Catalogue (15 goods)

| Category | Goods | Volatility |
|---|---|---|
| **Agriculture** | Grain, Wool, Livestock, Ale, Spices | Low / Low / Low / Low / High |
| **Mining** | Iron, Coal, Stone, Gems, Salt | Medium / Medium / Low / High / Low |
| **Timber & Craft** | Timber, Rope, Cloth, Leather, Candles | Low / Low / Medium / Medium / Low |

Each good has:
- `basePrize` — anchor value in gold
- `volatility` — LOW / MEDIUM / HIGH (scales random drift range)
- `category` — used for event targeting and Miner perk
- `supplyPressure` — float, decays toward 0 each tick

---

## Trade Mechanics

**Trade Fee**
- 3% of transaction value deducted on both buys and sells
- Merchant class: fee = 0%

**Miner Perk**
- Applied at sell time: `saleValue *= 1.02` for goods in the Mining category

**Quantity**
- Players choose 1–10 units per trade via number input
- Partial fills not supported (all-or-nothing)

**Supply/Demand**
- Each buy adds `+quantity * 0.5` pressure to the good
- Each sell adds `-quantity * 0.5` pressure
- Pressure is factored into `PriceModel` then decays by 30% per tick

---

## Price Tick Sequence (every 5 seconds)

```
1. EventEngine.maybeFireEvent()   → optional event modifier
2. For each good:
   a. Apply random drift (±0–5% × volatility weight)
   b. Apply supply/demand pressure
   c. Apply event modifier (if applicable)
   d. Clamp to [floor, ceiling]
   e. Append to PriceHistory
3. Reset supply pressure (decay step)
4. Broadcast via WebSocket /topic/market:
   { prices: {...}, history: {...}, event: "..." | null }
```

---

## Market Events

| Event | Affected Goods | Price Change |
|---|---|---|
| Iron vein discovered | Iron, Coal, Stone | −20 to −35% |
| Bad harvest | Grain, Livestock | +25 to +40% |
| Spice ship arrives | Spices | −15 to −25% |
| War declared | Iron, Coal, Timber, Rope | +30 to +40% |
| Plague outbreak | Livestock, Grain | +20%, all goods ±10% |
| Gem smugglers caught | Gems | +15 to +25% |
| Wool shortage | Wool, Cloth | +20 to +30% |

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
- On app reload: client sends `sessionId` to `POST /api/session/resume`; server restores portfolio
- If session expired or not found: redirect to class selection
- Sessions expire after 2 hours of server-side inactivity

---

## Out of Scope

- User accounts / authentication
- Leaderboards
- Multiple markets / travel
- Mobile-specific layout
- Sound effects
- Persistent database storage
