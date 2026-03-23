# Feature Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 8 features to the medieval market simulator: Seasons, NPC Bots, Limit Orders, Moneylender, Warehousing Costs, Price Alerts, Achievements, and Net Worth Chart.

**Architecture:** Five new `@Component` services (SeasonEngine, BotService, LimitOrderService, MoneylenderService, WarehousingService) are each called in sequence by `MarketEngine.tick()`. Per-session state (limit orders, loan amount) is pushed to each human client via a dedicated `/user/queue/updates` WebSocket channel after each tick. Frontend-only features (Price Alerts, Achievements, Net Worth Chart) require no backend changes beyond new data fields already added.

**Tech Stack:** Java 21, Spring Boot 3.2.3, Spring WebSocket STOMP/SockJS, Vue.js 3 CDN, Chart.js CDN. Tests: JUnit 5 + AssertJ (`mvn test -Dtest=ClassName`).

---

## File Map

**New files:**
- `src/main/java/com/medievalmarket/model/LimitOrder.java` — immutable record
- `src/main/java/com/medievalmarket/service/SeasonEngine.java`
- `src/main/java/com/medievalmarket/service/BotService.java`
- `src/main/java/com/medievalmarket/service/LimitOrderService.java`
- `src/main/java/com/medievalmarket/service/MoneylenderService.java`
- `src/main/java/com/medievalmarket/service/WarehousingService.java`
- `src/main/java/com/medievalmarket/config/SessionHandshakeInterceptor.java`
- `src/main/java/com/medievalmarket/config/SessionPrincipalHandshakeHandler.java`
- `src/main/java/com/medievalmarket/dto/SessionUpdate.java`
- `src/main/java/com/medievalmarket/dto/LimitOrderFill.java`
- `src/main/java/com/medievalmarket/dto/OrderResponse.java`
- `src/main/java/com/medievalmarket/dto/LoanResponse.java`
- `src/main/java/com/medievalmarket/dto/AddOrderRequest.java`
- `src/main/java/com/medievalmarket/dto/BorrowRequest.java`
- `src/main/java/com/medievalmarket/controller/OrderController.java`
- `src/main/java/com/medievalmarket/controller/MoneylenderController.java`
- `src/test/java/com/medievalmarket/service/SeasonEngineTest.java`
- `src/test/java/com/medievalmarket/service/LimitOrderServiceTest.java`
- `src/test/java/com/medievalmarket/service/MoneylenderServiceTest.java`
- `src/test/java/com/medievalmarket/service/WarehousingServiceTest.java`
- `src/test/java/com/medievalmarket/controller/OrderControllerTest.java`
- `src/test/java/com/medievalmarket/controller/MoneylenderControllerTest.java`

**Modified files:**
- `src/main/java/com/medievalmarket/model/PlayerClass.java` — add `warehousingRate` field
- `src/main/java/com/medievalmarket/model/Portfolio.java` — add `bot`, `loanAmount`, `limitOrders`
- `src/main/java/com/medievalmarket/dto/MarketTickPayload.java` — add `season`, `seasonTicksRemaining`
- `src/main/java/com/medievalmarket/dto/TradeResponse.java` — add `realizedPnl`, `loanAmount`, `currentSeason`
- `src/main/java/com/medievalmarket/dto/SessionStartResponse.java` — add `season`, `seasonTicksRemaining`, `loanAmount`, `limitOrders`
- `src/main/java/com/medievalmarket/config/WebSocketConfig.java` — add `/queue` broker, user destination prefix, interceptor + handler
- `src/main/java/com/medievalmarket/service/SessionRegistry.java` — add `registerBot()`, `getHumanPortfolios()`, bot-exempt expiry
- `src/main/java/com/medievalmarket/service/MarketEngine.java` — wire all new services, send per-session `SessionUpdate`
- `src/main/java/com/medievalmarket/controller/TradeController.java` — compute and return `realizedPnl`
- `src/main/java/com/medievalmarket/controller/SessionController.java` — return season + loan + orders in responses
- `src/main/resources/static/index.html` — all frontend changes
- `src/test/java/com/medievalmarket/service/SessionRegistryTest.java` — update for new methods
- `src/test/java/com/medievalmarket/controller/TradeControllerTest.java` — update for new response fields
- `src/test/java/com/medievalmarket/controller/SessionControllerTest.java` — update for new response fields

---

## Task 1: Foundation — Models, DTOs, WebSocket Config

**Files:**
- Modify: `src/main/java/com/medievalmarket/model/PlayerClass.java`
- Modify: `src/main/java/com/medievalmarket/model/Portfolio.java`
- Create: `src/main/java/com/medievalmarket/model/LimitOrder.java`
- Modify: `src/main/java/com/medievalmarket/dto/MarketTickPayload.java`
- Modify: `src/main/java/com/medievalmarket/dto/TradeResponse.java`
- Modify: `src/main/java/com/medievalmarket/dto/SessionStartResponse.java`
- Create: `src/main/java/com/medievalmarket/dto/SessionUpdate.java`
- Create: `src/main/java/com/medievalmarket/dto/LimitOrderFill.java`
- Create: `src/main/java/com/medievalmarket/dto/OrderResponse.java`
- Create: `src/main/java/com/medievalmarket/dto/LoanResponse.java`
- Create: `src/main/java/com/medievalmarket/dto/AddOrderRequest.java`
- Create: `src/main/java/com/medievalmarket/dto/BorrowRequest.java`
- Create: `src/main/java/com/medievalmarket/config/SessionHandshakeInterceptor.java`
- Create: `src/main/java/com/medievalmarket/config/SessionPrincipalHandshakeHandler.java`
- Modify: `src/main/java/com/medievalmarket/config/WebSocketConfig.java`

- [ ] **Step 1: Add `warehousingRate` to `PlayerClass`**

Replace the entire `PlayerClass.java`:
```java
package com.medievalmarket.model;

public enum PlayerClass {
    MERCHANT(500.0, 0.0,  0.0025),
    MINER   (350.0, 0.03, 0.005),
    NOBLE   (1000.0,0.03, 0.005);

    private final double startGold;
    private final double feeRate;
    private final double warehousingRate;

    PlayerClass(double startGold, double feeRate, double warehousingRate) {
        this.startGold = startGold;
        this.feeRate = feeRate;
        this.warehousingRate = warehousingRate;
    }

    public double getStartGold() { return startGold; }
    public double getFeeRate() { return feeRate; }
    public double getWarehousingRate() { return warehousingRate; }
}
```

- [ ] **Step 2: Create `LimitOrder` record**

```java
// src/main/java/com/medievalmarket/model/LimitOrder.java
package com.medievalmarket.model;

public record LimitOrder(
    String id,
    String sessionId,
    String goodName,
    String direction,    // "BUY" or "SELL"
    int quantity,
    double targetPrice
) {}
```

- [ ] **Step 3: Update `Portfolio` — add `bot`, `loanAmount`, `limitOrders`**

Add these fields and methods to `Portfolio.java`:

```java
// New fields (add after existing fields):
private final boolean bot;
private double loanAmount = 0.0;
private final List<LimitOrder> limitOrders = new ArrayList<>();
```

Add import: `import com.medievalmarket.model.LimitOrder;` and `import java.util.ArrayList;` and `import java.util.List;`

Update the existing single-arg constructor to delegate, and add a new constructor:
```java
// existing constructor now delegates:
public Portfolio(String sessionId, String playerName, PlayerClass playerClass) {
    this(sessionId, playerName, playerClass, false);
}

// new constructor with bot flag:
public Portfolio(String sessionId, String playerName, PlayerClass playerClass, boolean bot) {
    this.sessionId = sessionId;
    this.playerName = playerName;
    this.playerClass = playerClass;
    this.gold = playerClass.getStartGold();
    this.bot = bot;
}
```

Add accessors:
```java
public boolean isBot() { return bot; }

public synchronized double getLoanAmount() { return loanAmount; }
public synchronized void setLoanAmount(double loanAmount) { this.loanAmount = loanAmount; }

public synchronized List<LimitOrder> getLimitOrders() { return new ArrayList<>(limitOrders); }
public synchronized void addLimitOrder(LimitOrder order) { limitOrders.add(order); }
public synchronized void removeLimitOrder(String id) {
    limitOrders.removeIf(o -> o.id().equals(id));
}
public synchronized int limitOrderCount() { return limitOrders.size(); }
```

- [ ] **Step 4: Update DTOs**

Replace `MarketTickPayload.java`:
```java
package com.medievalmarket.dto;
import com.medievalmarket.model.ScoreboardEntry;
import java.util.List;
import java.util.Map;
public record MarketTickPayload(
    Map<String, Double> prices,
    Map<String, List<Double>> history,
    String event,
    List<ScoreboardEntry> scoreboard,
    String season,
    int seasonTicksRemaining
) {}
```

Replace `TradeResponse.java`:
```java
package com.medievalmarket.dto;
import java.util.Map;
public record TradeResponse(
    double gold,
    Map<String, Integer> holdings,
    Map<String, Double> costBasis,
    double realizedPnl,
    double loanAmount,
    String currentSeason
) {}
```

Replace `SessionStartResponse.java`:
```java
package com.medievalmarket.dto;
import com.medievalmarket.model.LimitOrder;
import java.util.List;
import java.util.Map;
public record SessionStartResponse(
    String sessionId,
    String playerName,
    String playerClass,
    double gold,
    Map<String, Integer> holdings,
    Map<String, Double> costBasis,
    String season,
    int seasonTicksRemaining,
    double loanAmount,
    List<LimitOrder> limitOrders
) {}
```

- [ ] **Step 5: Create new DTOs**

`SessionUpdate.java`:
```java
package com.medievalmarket.dto;
import com.medievalmarket.model.LimitOrder;
import java.util.List;
public record SessionUpdate(
    double gold,
    List<LimitOrder> limitOrders,
    double loanAmount,
    List<LimitOrderFill> limitOrderFills
) {}
```

`LimitOrderFill.java`:
```java
package com.medievalmarket.dto;
public record LimitOrderFill(
    String goodName,
    String direction,
    int quantity,
    double executedPrice,
    double realizedPnl
) {}
```

`OrderResponse.java`:
```java
package com.medievalmarket.dto;
import com.medievalmarket.model.LimitOrder;
import java.util.List;
public record OrderResponse(List<LimitOrder> orders, double gold) {}
```

`LoanResponse.java`:
```java
package com.medievalmarket.dto;
public record LoanResponse(double loanAmount, double gold) {}
```

`AddOrderRequest.java`:
```java
package com.medievalmarket.dto;
public record AddOrderRequest(
    String sessionId, String goodName, String direction,
    int quantity, double targetPrice
) {}
```

`BorrowRequest.java`:
```java
package com.medievalmarket.dto;
public record BorrowRequest(String sessionId, double amount) {}
```

- [ ] **Step 6: Create WebSocket Principal classes**

`SessionHandshakeInterceptor.java`:
```java
package com.medievalmarket.config;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.Map;

public class SessionHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest r) {
            String sid = r.getServletRequest().getParameter("sessionId");
            if (sid != null && !sid.isBlank()) attributes.put("sessionId", sid);
        }
        return true;
    }
    @Override
    public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res,
            WebSocketHandler handler, Exception ex) {}
}
```

`SessionPrincipalHandshakeHandler.java`:
```java
package com.medievalmarket.config;

import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.http.server.ServerHttpRequest;
import java.security.Principal;
import java.util.Map;

public class SessionPrincipalHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String sid = (String) attributes.get("sessionId");
        if (sid != null) return () -> sid;
        return super.determineUser(request, wsHandler, attributes);
    }
}
```

- [ ] **Step 7: Update `WebSocketConfig`**

Replace `WebSocketConfig.java`:
```java
package com.medievalmarket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
            .addInterceptors(new SessionHandshakeInterceptor())
            .setHandshakeHandler(new SessionPrincipalHandshakeHandler())
            .withSockJS();
    }
}
```

- [ ] **Step 8: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS with no errors.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/medievalmarket/model/PlayerClass.java \
        src/main/java/com/medievalmarket/model/LimitOrder.java \
        src/main/java/com/medievalmarket/model/Portfolio.java \
        src/main/java/com/medievalmarket/dto/ \
        src/main/java/com/medievalmarket/config/
git commit -m "feat: foundation — LimitOrder model, Portfolio fields, new DTOs, WebSocket user routing"
```

---

## Task 2: SeasonEngine

**Files:**
- Create: `src/main/java/com/medievalmarket/service/SeasonEngine.java`
- Create: `src/test/java/com/medievalmarket/service/SeasonEngineTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/medievalmarket/service/SeasonEngineTest.java
package com.medievalmarket.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SeasonEngineTest {

    @Test
    void startsInSpring() {
        SeasonEngine engine = new SeasonEngine();
        assertThat(engine.getCurrentSeason()).isEqualTo("SPRING");
    }

    @Test
    void advancesSeasonAfter60Ticks() {
        SeasonEngine engine = new SeasonEngine();
        for (int i = 0; i < 60; i++) engine.advanceTick();
        assertThat(engine.getCurrentSeason()).isEqualTo("SUMMER");
    }

    @Test
    void wrapsAroundAfterWinter() {
        SeasonEngine engine = new SeasonEngine();
        for (int i = 0; i < 240; i++) engine.advanceTick();
        assertThat(engine.getCurrentSeason()).isEqualTo("SPRING");
    }

    @Test
    void ticksRemainingDecreasesEachTick() {
        SeasonEngine engine = new SeasonEngine();
        int initial = engine.getTicksRemaining();
        engine.advanceTick();
        assertThat(engine.getTicksRemaining()).isEqualTo(initial - 1);
    }

    @Test
    void springBoostsGrainAndSuppressesCoal() {
        SeasonEngine engine = new SeasonEngine(); // starts in SPRING
        assertThat(engine.getModifiers().get("Grain")).isPositive();
        assertThat(engine.getModifiers().get("Coal")).isNegative();
    }

    @Test
    void winterBoostsCoalHeavily() {
        SeasonEngine engine = new SeasonEngine();
        for (int i = 0; i < 180; i++) engine.advanceTick(); // advance to WINTER
        assertThat(engine.getModifiers().get("Coal"))
            .isGreaterThan(engine.getModifiers().get("Grain"));
    }

    @Test
    void modifiersNullForUnaffectedGoods() {
        SeasonEngine engine = new SeasonEngine(); // SPRING
        // Gems not mentioned in Spring modifiers
        assertThat(engine.getModifiers()).doesNotContainKey("Gems");
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=SeasonEngineTest -q
```
Expected: FAIL — `SeasonEngine` not found.

- [ ] **Step 3: Implement `SeasonEngine`**

```java
// src/main/java/com/medievalmarket/service/SeasonEngine.java
package com.medievalmarket.service;

import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class SeasonEngine {

    private static final int TICKS_PER_SEASON = 60;
    private static final String[] SEASONS = {"SPRING", "SUMMER", "AUTUMN", "WINTER"};

    // Raw seasonal strengths (fraction, e.g. 0.08 = 8%).
    // MarketEngine divides by 100 when applying per-tick to scale the nudge.
    private static final Map<String, Map<String, Double>> SEASON_MODIFIERS = Map.of(
        "SPRING", Map.of(
            "Grain", 0.08, "Wool", 0.08, "Livestock", 0.08, "Fish", 0.05,
            "Coal", -0.10, "Candles", -0.10),
        "SUMMER", Map.of(
            "Spices", 0.10, "Herbs", 0.12, "Ale", 0.05, "Cloth", 0.08,
            "Grain", -0.10),
        "AUTUMN", Map.of(
            "Grain", 0.08, "Timber", 0.10, "Ale", 0.10, "Coal", 0.08, "Leather", 0.05),
        "WINTER", Map.of(
            "Coal", 0.20, "Candles", 0.15, "Salt", 0.10, "Spices", 0.08, "Grain", 0.12,
            "Fish", -0.10, "Livestock", -0.08)
    );

    private int seasonIndex = 0;
    private int tickInSeason = 0;

    public void advanceTick() {
        tickInSeason++;
        if (tickInSeason >= TICKS_PER_SEASON) {
            tickInSeason = 0;
            seasonIndex = (seasonIndex + 1) % SEASONS.length;
        }
    }

    public String getCurrentSeason() {
        return SEASONS[seasonIndex];
    }

    public int getTicksRemaining() {
        return TICKS_PER_SEASON - tickInSeason;
    }

    /** Per-tick modifier strengths (divide by 100 when applying as a nudge). */
    public Map<String, Double> getModifiers() {
        return SEASON_MODIFIERS.get(getCurrentSeason());
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
mvn test -Dtest=SeasonEngineTest -q
```
Expected: BUILD SUCCESS, 7 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/SeasonEngine.java \
        src/test/java/com/medievalmarket/service/SeasonEngineTest.java
git commit -m "feat: SeasonEngine — 4-season cycle with per-tick drift modifiers"
```

---

## Task 3: SessionRegistry + BotService

**Files:**
- Modify: `src/main/java/com/medievalmarket/service/SessionRegistry.java`
- Create: `src/main/java/com/medievalmarket/service/BotService.java`
- Modify: `src/test/java/com/medievalmarket/service/SessionRegistryTest.java`

- [ ] **Step 1: Write new SessionRegistry tests**

Add these tests to `SessionRegistryTest.java` (read the file first to see existing structure, then append):

```java
@Test
void registerBotCreatesPortfolioWithBotFlag() {
    SessionRegistry registry = new SessionRegistry(new NameGenerator());
    Portfolio bot = registry.registerBot("[bot] Test");
    assertThat(bot.isBot()).isTrue();
}

@Test
void getHumanPortfoliosExcludesBots() {
    SessionRegistry registry = new SessionRegistry(new NameGenerator());
    registry.registerBot("[bot] Test");
    registry.createSession(PlayerClass.NOBLE);
    assertThat(registry.getHumanPortfolios()).hasSize(1);
    assertThat(registry.getHumanPortfolios()).allMatch(p -> !p.isBot());
}

@Test
void getAllActiveSessionsIncludesBots() {
    SessionRegistry registry = new SessionRegistry(new NameGenerator());
    registry.registerBot("[bot] Test");
    registry.createSession(PlayerClass.NOBLE);
    assertThat(registry.getAllActiveSessions()).hasSize(2);
}
```

- [ ] **Step 2: Run new tests to confirm they fail**

```bash
mvn test -Dtest=SessionRegistryTest -q
```
Expected: FAIL on the 3 new tests.

- [ ] **Step 3: Update `SessionRegistry`**

Add `registerBot()` and `getHumanPortfolios()` methods; update `isExpired()` to skip bots:

```java
public Portfolio registerBot(String name) {
    String sessionId = UUID.randomUUID().toString();
    Portfolio p = new Portfolio(sessionId, name, PlayerClass.MERCHANT, true);
    p.setGold(300.0); // bots start with 300g regardless of MERCHANT's default 500g
    sessions.put(sessionId, p);
    return p;
}

public Collection<Portfolio> getHumanPortfolios() {
    sessions.entrySet().removeIf(e -> !e.getValue().isBot() && isExpired(e.getValue()));
    return sessions.values().stream()
        .filter(p -> !p.isBot())
        .collect(java.util.stream.Collectors.toList());
}

// Update existing getAllActiveSessions():
public Collection<Portfolio> getAllActiveSessions() {
    sessions.entrySet().removeIf(e -> !e.getValue().isBot() && isExpired(e.getValue()));
    return sessions.values(); // includes bots for scoreboard
}

// Update isExpired to skip bots:
private boolean isExpired(Portfolio p) {
    if (p.isBot()) return false;
    return Duration.between(p.getLastTradeTime(), Instant.now()).compareTo(EXPIRY) > 0;
}
```

- [ ] **Step 4: Run SessionRegistry tests**

```bash
mvn test -Dtest=SessionRegistryTest -q
```
Expected: BUILD SUCCESS, all tests passing.

- [ ] **Step 5: Implement `BotService`**

```java
// src/main/java/com/medievalmarket/service/BotService.java
package com.medievalmarket.service;

import com.medievalmarket.model.Portfolio;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class BotService {

    private static final int BOT_COUNT = 5;
    private static final int TRADE_EVERY_N_TICKS = 3;

    private final SessionRegistry sessionRegistry;
    private final GoodsCatalogue catalogue;
    private final TradeService tradeService;
    private final NameGenerator nameGenerator;
    private final List<Portfolio> bots = new ArrayList<>();
    private int tickCounter = 0;

    public BotService(SessionRegistry sessionRegistry, GoodsCatalogue catalogue,
                      TradeService tradeService, NameGenerator nameGenerator) {
        this.sessionRegistry = sessionRegistry;
        this.catalogue = catalogue;
        this.tradeService = tradeService;
        this.nameGenerator = nameGenerator;
    }

    @PostConstruct
    void init() {
        for (int i = 0; i < BOT_COUNT; i++) {
            String name = "[bot] " + nameGenerator.generate();
            bots.add(sessionRegistry.registerBot(name));
        }
    }

    public void processTick() {
        tickCounter++;
        if (tickCounter % TRADE_EVERY_N_TICKS != 0) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        var goods = catalogue.getGoods();

        for (Portfolio bot : bots) {
            var good = goods.get(rng.nextInt(goods.size()));
            double price = good.getCurrentPrice();
            double base = good.getBasePrice();

            try {
                if (price < base * 0.80 && bot.getGold() >= price) {
                    int qty = rng.nextInt(1, 6);
                    tradeService.buy(bot, good.getName(), qty);
                } else if (price > base * 1.20 && bot.getHolding(good.getName()) > 0) {
                    int held = bot.getHolding(good.getName());
                    int qty = Math.min(held, rng.nextInt(1, 6));
                    tradeService.sell(bot, good.getName(), qty);
                } else {
                    if (rng.nextBoolean() && bot.getGold() >= price) {
                        tradeService.buy(bot, good.getName(), rng.nextInt(1, 4));
                    } else if (bot.getHolding(good.getName()) > 0) {
                        tradeService.sell(bot, good.getName(), 1);
                    }
                }
            } catch (TradeService.TradeException | IllegalArgumentException ignored) {
                // Bot simply skips this tick if it can't trade
            }
        }
    }
}
```

- [ ] **Step 6: Verify compilation**

```bash
mvn compile -q
```
Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/medievalmarket/service/SessionRegistry.java \
        src/main/java/com/medievalmarket/service/BotService.java \
        src/test/java/com/medievalmarket/service/SessionRegistryTest.java
git commit -m "feat: BotService — 5 NPC traders; SessionRegistry bot support"
```

---

## Task 4: LimitOrderService

**Files:**
- Create: `src/main/java/com/medievalmarket/service/LimitOrderService.java`
- Create: `src/test/java/com/medievalmarket/service/LimitOrderServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/medievalmarket/service/LimitOrderServiceTest.java
package com.medievalmarket.service;

import com.medievalmarket.dto.LimitOrderFill;
import com.medievalmarket.model.LimitOrder;
import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class LimitOrderServiceTest {

    private LimitOrderService service;
    private TradeService tradeService;
    private GoodsCatalogue catalogue;

    @BeforeEach
    void setUp() {
        catalogue = new GoodsCatalogue();
        tradeService = new TradeService(catalogue);
        service = new LimitOrderService(tradeService, catalogue);
        catalogue.findByName("Iron").setCurrentPrice(40.0);
    }

    private Portfolio noble() {
        return new Portfolio("s1", "Test", PlayerClass.NOBLE);
    }

    @Test
    void buyOrderTriggersWhenPriceAtOrBelowTarget() {
        Portfolio p = noble();
        p.addLimitOrder(new LimitOrder("id1", "s1", "Iron", "BUY", 2, 45.0));
        // price 40 <= target 45 → should trigger
        Map<String, List<LimitOrderFill>> fills = service.processAll(List.of(p));
        assertThat(p.getHolding("Iron")).isEqualTo(2);
        assertThat(fills.get("s1")).hasSize(1);
        assertThat(fills.get("s1").get(0).direction()).isEqualTo("BUY");
    }

    @Test
    void buyOrderDoesNotTriggerWhenPriceAboveTarget() {
        Portfolio p = noble();
        p.addLimitOrder(new LimitOrder("id1", "s1", "Iron", "BUY", 2, 30.0));
        // price 40 > target 30 → should NOT trigger
        service.processAll(List.of(p));
        assertThat(p.getHolding("Iron")).isEqualTo(0);
        assertThat(p.getLimitOrders()).hasSize(1);
    }

    @Test
    void sellOrderTriggersWhenPriceAtOrAboveTarget() {
        Portfolio p = noble();
        tradeService.buy(p, "Iron", 3);
        catalogue.findByName("Iron").setCurrentPrice(40.0);
        p.addLimitOrder(new LimitOrder("id2", "s1", "Iron", "SELL", 2, 38.0));
        // price 40 >= target 38 → should trigger
        Map<String, List<LimitOrderFill>> fills = service.processAll(List.of(p));
        assertThat(p.getHolding("Iron")).isEqualTo(1);
        assertThat(fills.get("s1")).hasSize(1);
        assertThat(fills.get("s1").get(0).realizedPnl()).isNotNaN();
    }

    @Test
    void orderRemovedAfterTrigger() {
        Portfolio p = noble();
        p.addLimitOrder(new LimitOrder("id1", "s1", "Iron", "BUY", 1, 50.0));
        service.processAll(List.of(p));
        assertThat(p.getLimitOrders()).isEmpty();
    }

    @Test
    void failedOrderIsCancelledSilently() {
        Portfolio p = noble();
        p.setGold(0.0); // can't afford
        p.addLimitOrder(new LimitOrder("id1", "s1", "Iron", "BUY", 100, 50.0));
        assertThatCode(() -> service.processAll(List.of(p))).doesNotThrowAnyException();
        assertThat(p.getLimitOrders()).isEmpty(); // cancelled
    }

    @Test
    void returnsEmptyFillsWhenNoOrdersTriggered() {
        Portfolio p = noble();
        p.addLimitOrder(new LimitOrder("id1", "s1", "Iron", "BUY", 1, 10.0)); // below price
        Map<String, List<LimitOrderFill>> fills = service.processAll(List.of(p));
        assertThat(fills.getOrDefault("s1", List.of())).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=LimitOrderServiceTest -q
```
Expected: FAIL — `LimitOrderService` not found.

- [ ] **Step 3: Implement `LimitOrderService`**

```java
// src/main/java/com/medievalmarket/service/LimitOrderService.java
package com.medievalmarket.service;

import com.medievalmarket.dto.LimitOrderFill;
import com.medievalmarket.model.LimitOrder;
import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class LimitOrderService {

    private final TradeService tradeService;
    private final GoodsCatalogue catalogue;

    public LimitOrderService(TradeService tradeService, GoodsCatalogue catalogue) {
        this.tradeService = tradeService;
        this.catalogue = catalogue;
    }

    /** Processes all limit orders for every human portfolio. Returns fills keyed by sessionId. */
    public Map<String, List<LimitOrderFill>> processAll(Collection<Portfolio> humanPortfolios) {
        Map<String, List<LimitOrderFill>> fillsBySession = new HashMap<>();

        for (Portfolio portfolio : humanPortfolios) {
            List<LimitOrder> toProcess = portfolio.getLimitOrders(); // snapshot copy
            for (LimitOrder order : toProcess) {
                double currentPrice = catalogue.findByName(order.goodName()).getCurrentPrice();
                boolean triggered = "BUY".equals(order.direction())
                    ? currentPrice <= order.targetPrice()
                    : currentPrice >= order.targetPrice();

                if (!triggered) continue;

                LimitOrderFill fill = execute(portfolio, order, currentPrice);
                portfolio.removeLimitOrder(order.id());
                if (fill != null) {
                    fillsBySession.computeIfAbsent(portfolio.getSessionId(), k -> new ArrayList<>())
                        .add(fill);
                }
            }
        }
        return fillsBySession;
    }

    private LimitOrderFill execute(Portfolio portfolio, LimitOrder order, double currentPrice) {
        try {
            if ("BUY".equals(order.direction())) {
                tradeService.buy(portfolio, order.goodName(), order.quantity());
                return new LimitOrderFill(order.goodName(), "BUY",
                    order.quantity(), currentPrice, 0.0);
            } else {
                double avgCost = portfolio.getAvgCostBasis(order.goodName());
                double goldBefore = portfolio.getGold();
                tradeService.sell(portfolio, order.goodName(), order.quantity());
                double proceeds = portfolio.getGold() - goldBefore;
                double realizedPnl = proceeds - (avgCost * order.quantity());
                return new LimitOrderFill(order.goodName(), "SELL",
                    order.quantity(), currentPrice, realizedPnl);
            }
        } catch (TradeService.TradeException | IllegalArgumentException e) {
            return null; // order cancelled, fill not recorded
        }
    }

    /** Adds an order if the portfolio has fewer than 3 pending orders. */
    public LimitOrder addOrder(Portfolio portfolio, String goodName, String direction,
                               int quantity, double targetPrice) {
        if (portfolio.limitOrderCount() >= 3) {
            throw new LimitOrderException("MAX_ORDERS_REACHED");
        }
        if (!"BUY".equals(direction) && !"SELL".equals(direction)) {
            throw new LimitOrderException("INVALID_DIRECTION");
        }
        catalogue.findByName(goodName); // throws if unknown
        LimitOrder order = new LimitOrder(
            UUID.randomUUID().toString(), portfolio.getSessionId(),
            goodName, direction, quantity, targetPrice);
        portfolio.addLimitOrder(order);
        return order;
    }

    public static class LimitOrderException extends RuntimeException {
        public LimitOrderException(String message) { super(message); }
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
mvn test -Dtest=LimitOrderServiceTest -q
```
Expected: BUILD SUCCESS, 6 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/LimitOrderService.java \
        src/test/java/com/medievalmarket/service/LimitOrderServiceTest.java
git commit -m "feat: LimitOrderService — auto-execute buy/sell orders on price condition"
```

---

## Task 5: MoneylenderService

**Files:**
- Create: `src/main/java/com/medievalmarket/service/MoneylenderService.java`
- Create: `src/test/java/com/medievalmarket/service/MoneylenderServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/medievalmarket/service/MoneylenderServiceTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MoneylenderServiceTest {

    private MoneylenderService service;
    private TradeService tradeService;
    private GoodsCatalogue catalogue;

    @BeforeEach
    void setUp() {
        catalogue = new GoodsCatalogue();
        tradeService = new TradeService(catalogue);
        service = new MoneylenderService(tradeService, catalogue);
    }

    private Portfolio noble() {
        return new Portfolio("s1", "Test", PlayerClass.NOBLE); // 1000g start
    }

    @Test
    void borrowAddsGoldAndSetsLoanAmount() {
        Portfolio p = noble();
        service.borrow(p, 200.0);
        assertThat(p.getGold()).isCloseTo(1200.0, within(0.01));
        assertThat(p.getLoanAmount()).isCloseTo(200.0, within(0.01));
    }

    @Test
    void borrowRejectsAmountBelowMinimum() {
        Portfolio p = noble();
        assertThatThrownBy(() -> service.borrow(p, 40.0))
            .hasMessageContaining("BELOW_MINIMUM");
    }

    @Test
    void borrowRejectsAmountAboveLimit() {
        Portfolio p = noble(); // 1000g → max = min(2000, 5000) = 2000
        assertThatThrownBy(() -> service.borrow(p, 2001.0))
            .hasMessageContaining("ABOVE_LIMIT");
    }

    @Test
    void repayReducesLoanAndDeductsGold() {
        Portfolio p = noble();
        service.borrow(p, 200.0);
        service.repay(p);
        assertThat(p.getLoanAmount()).isCloseTo(0.0, within(0.01));
        assertThat(p.getGold()).isCloseTo(1000.0, within(0.01));
    }

    @Test
    void repayIsLimitedByAvailableGold() {
        Portfolio p = noble();
        service.borrow(p, 200.0);
        p.setGold(100.0); // only 100g left
        service.repay(p);
        assertThat(p.getGold()).isCloseTo(0.0, within(0.01));
        assertThat(p.getLoanAmount()).isCloseTo(100.0, within(0.01)); // 100 remaining
    }

    @Test
    void interestCompoundsEachTick() {
        Portfolio p = noble();
        service.borrow(p, 100.0);
        service.processTick(p);
        assertThat(p.getLoanAmount()).isCloseTo(102.0, within(0.01));
    }

    @Test
    void seizureOccursWhenInsolvent() {
        Portfolio p = new Portfolio("s", "T", PlayerClass.MERCHANT); // 500g
        service.borrow(p, 200.0); // loan=200, gold=700
        // buy some Iron so there are holdings to seize
        catalogue.findByName("Iron").setCurrentPrice(10.0);
        tradeService.buy(p, "Iron", 10);
        p.setGold(0.0); // make them insolvent
        p.setLoanAmount(p.getGold() + 200.0); // loan > gold+holdings triggers seizure
        service.processTick(p); // should seize Iron
        assertThat(p.getLoanAmount()).isCloseTo(0.0, within(1.0)); // mostly cleared
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=MoneylenderServiceTest -q
```
Expected: FAIL — `MoneylenderService` not found.

- [ ] **Step 3: Implement `MoneylenderService`**

```java
// src/main/java/com/medievalmarket/service/MoneylenderService.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MoneylenderService {

    private static final double INTEREST_RATE = 1.02;
    private static final double MIN_BORROW = 50.0;
    private static final double MAX_BORROW_CAP = 5000.0;

    private final TradeService tradeService;
    private final GoodsCatalogue catalogue;

    public MoneylenderService(TradeService tradeService, GoodsCatalogue catalogue) {
        this.tradeService = tradeService;
        this.catalogue = catalogue;
    }

    public void borrow(Portfolio portfolio, double amount) {
        if (amount < MIN_BORROW) throw new LoanException("BELOW_MINIMUM");
        double maxBorrow = Math.min(portfolio.getGold() * 2, MAX_BORROW_CAP);
        if (amount > maxBorrow) throw new LoanException("ABOVE_LIMIT");
        portfolio.setLoanAmount(portfolio.getLoanAmount() + amount);
        portfolio.setGold(portfolio.getGold() + amount);
    }

    public void repay(Portfolio portfolio) {
        double loan = portfolio.getLoanAmount();
        if (loan <= 0.0) return;
        double payment = Math.min(portfolio.getGold(), loan);
        portfolio.setGold(portfolio.getGold() - payment);
        portfolio.setLoanAmount(loan - payment);
    }

    public void processTick(Portfolio portfolio) {
        if (portfolio.getLoanAmount() <= 0.0) return;
        portfolio.setLoanAmount(portfolio.getLoanAmount() * INTEREST_RATE);
        checkSolvencyAndSeize(portfolio);
    }

    public void processAll(Collection<Portfolio> humanPortfolios) {
        humanPortfolios.forEach(this::processTick);
    }

    private void checkSolvencyAndSeize(Portfolio portfolio) {
        double holdingsValue = holdingsValue(portfolio);
        if (portfolio.getLoanAmount() <= portfolio.getGold() + holdingsValue) return;

        // Seize cheapest holdings first
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(
            portfolio.getHoldings().entrySet());
        sorted.sort(Comparator.comparingDouble(e ->
            catalogue.findByName(e.getKey()).getCurrentPrice() * e.getValue()));

        for (Map.Entry<String, Integer> entry : sorted) {
            if (portfolio.getLoanAmount() <= 0.0) break;
            try {
                double goldBefore = portfolio.getGold();
                tradeService.sell(portfolio, entry.getKey(), entry.getValue());
                double proceeds = portfolio.getGold() - goldBefore;
                portfolio.setLoanAmount(portfolio.getLoanAmount() - proceeds);
            } catch (TradeService.TradeException ignored) {}
        }
        if (portfolio.getLoanAmount() < 0.0) portfolio.setLoanAmount(0.0);
    }

    private double holdingsValue(Portfolio portfolio) {
        return portfolio.getHoldings().entrySet().stream()
            .mapToDouble(e -> catalogue.findByName(e.getKey()).getCurrentPrice() * e.getValue())
            .sum();
    }

    public static class LoanException extends RuntimeException {
        public LoanException(String message) { super(message); }
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
mvn test -Dtest=MoneylenderServiceTest -q
```
Expected: BUILD SUCCESS, 7 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/MoneylenderService.java \
        src/test/java/com/medievalmarket/service/MoneylenderServiceTest.java
git commit -m "feat: MoneylenderService — borrow/repay with compounding interest and seizure on default"
```

---

## Task 6: WarehousingService

**Files:**
- Create: `src/main/java/com/medievalmarket/service/WarehousingService.java`
- Create: `src/test/java/com/medievalmarket/service/WarehousingServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/medievalmarket/service/WarehousingServiceTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WarehousingServiceTest {

    private WarehousingService service;
    private GoodsCatalogue catalogue;
    private TradeService tradeService;

    @BeforeEach
    void setUp() {
        catalogue = new GoodsCatalogue();
        tradeService = new TradeService(catalogue);
        service = new WarehousingService(catalogue);
        catalogue.findByName("Iron").setCurrentPrice(40.0);
    }

    @Test
    void deductsFeeFromGoldEachTick() {
        Portfolio p = new Portfolio("s", "T", PlayerClass.NOBLE); // 1000g, 0.5%
        tradeService.buy(p, "Iron", 5); // buy 5 Iron at 40g = 200g value
        double goldAfterBuy = p.getGold();
        service.processTick(p);
        // fee = 200 * 0.005 = 1.0g
        assertThat(p.getGold()).isCloseTo(goldAfterBuy - 1.0, within(0.1));
    }

    @Test
    void merchantPaysHalfRate() {
        Portfolio noble = new Portfolio("s1", "T", PlayerClass.NOBLE);
        Portfolio merchant = new Portfolio("s2", "T", PlayerClass.MERCHANT);
        tradeService.buy(noble, "Iron", 5);
        double nobleGold = noble.getGold();
        // Manually set merchant gold and holdings equivalent
        merchant.setGold(nobleGold);
        merchant.setHolding("Iron", 5);
        service.processTick(noble);
        service.processTick(merchant);
        assertThat(noble.getGold()).isLessThan(merchant.getGold());
    }

    @Test
    void skipsDeductionIfGoldWouldGoNegative() {
        Portfolio p = new Portfolio("s", "T", PlayerClass.NOBLE);
        tradeService.buy(p, "Iron", 20); // heavy buy
        p.setGold(0.01); // almost broke
        assertThatCode(() -> service.processTick(p)).doesNotThrowAnyException();
        assertThat(p.getGold()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void noFeeWhenNoHoldings() {
        Portfolio p = new Portfolio("s", "T", PlayerClass.NOBLE);
        double gold = p.getGold();
        service.processTick(p);
        assertThat(p.getGold()).isEqualTo(gold);
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
mvn test -Dtest=WarehousingServiceTest -q
```
Expected: FAIL — `WarehousingService` not found.

- [ ] **Step 3: Implement `WarehousingService`**

```java
// src/main/java/com/medievalmarket/service/WarehousingService.java
package com.medievalmarket.service;

import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;
import java.util.Collection;

@Component
public class WarehousingService {

    private final GoodsCatalogue catalogue;

    public WarehousingService(GoodsCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    public void processTick(Portfolio portfolio) {
        double holdingsValue = portfolio.getHoldings().entrySet().stream()
            .mapToDouble(e -> catalogue.findByName(e.getKey()).getCurrentPrice() * e.getValue())
            .sum();
        if (holdingsValue == 0.0) return;

        double fee = holdingsValue * portfolio.getPlayerClass().getWarehousingRate();
        if (portfolio.getGold() - fee < 0.0) return; // skip — would go negative
        portfolio.setGold(portfolio.getGold() - fee);
    }

    public void processAll(Collection<Portfolio> humanPortfolios) {
        humanPortfolios.forEach(this::processTick);
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
mvn test -Dtest=WarehousingServiceTest -q
```
Expected: BUILD SUCCESS, 4 tests passing.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/WarehousingService.java \
        src/test/java/com/medievalmarket/service/WarehousingServiceTest.java
git commit -m "feat: WarehousingService — per-tick inventory holding fee"
```

---

## Task 7: MarketEngine Wiring + SessionUpdate Push

**Files:**
- Modify: `src/main/java/com/medievalmarket/service/MarketEngine.java`
- Modify: `src/main/java/com/medievalmarket/controller/TradeController.java`
- Modify: `src/main/java/com/medievalmarket/controller/SessionController.java`
- Modify: `src/test/java/com/medievalmarket/controller/TradeControllerTest.java`
- Modify: `src/test/java/com/medievalmarket/controller/SessionControllerTest.java`

- [ ] **Step 1: Update `MarketEngine`**

Replace `MarketEngine.java`:
```java
package com.medievalmarket.service;

import com.medievalmarket.dto.LimitOrderFill;
import com.medievalmarket.dto.MarketTickPayload;
import com.medievalmarket.dto.SessionUpdate;
import com.medievalmarket.model.Good;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.model.ScoreboardEntry;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MarketEngine {

    private final GoodsCatalogue catalogue;
    private final PriceModel priceModel;
    private final EventEngine eventEngine;
    private final PriceHistory priceHistory;
    private final SessionRegistry sessionRegistry;
    private final ScoreboardService scoreboardService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SeasonEngine seasonEngine;
    private final BotService botService;
    private final LimitOrderService limitOrderService;
    private final MoneylenderService moneylenderService;
    private final WarehousingService warehousingService;

    public MarketEngine(GoodsCatalogue catalogue, PriceModel priceModel,
                        EventEngine eventEngine, PriceHistory priceHistory,
                        SessionRegistry sessionRegistry, ScoreboardService scoreboardService,
                        SimpMessagingTemplate messagingTemplate, SeasonEngine seasonEngine,
                        BotService botService, LimitOrderService limitOrderService,
                        MoneylenderService moneylenderService, WarehousingService warehousingService) {
        this.catalogue = catalogue;
        this.priceModel = priceModel;
        this.eventEngine = eventEngine;
        this.priceHistory = priceHistory;
        this.sessionRegistry = sessionRegistry;
        this.scoreboardService = scoreboardService;
        this.messagingTemplate = messagingTemplate;
        this.seasonEngine = seasonEngine;
        this.botService = botService;
        this.limitOrderService = limitOrderService;
        this.moneylenderService = moneylenderService;
        this.warehousingService = warehousingService;
    }

    @Scheduled(fixedRate = 5000)
    public void tick() {
        // 1. Advance season
        seasonEngine.advanceTick();
        Map<String, Double> seasonMods = seasonEngine.getModifiers();

        // 2. Maybe fire an event
        EventEngine.FiredEvent event = eventEngine.maybeFireEvent();
        Map<String, Double> eventModifiers = event != null ? event.modifiers() : Map.of();

        // 3. Reprice all goods (event modifier + season nudge)
        Map<String, Double> prices = new LinkedHashMap<>();
        for (Good good : catalogue.getGoods()) {
            double eventMod = eventModifiers.getOrDefault(good.getName(), 0.0);
            double seasonNudge = seasonMods.getOrDefault(good.getName(), 0.0) / 100.0;
            double newPrice = priceModel.computeNewPrice(good, eventMod + seasonNudge);
            good.setCurrentPrice(newPrice);
            priceHistory.append(good.getName(), newPrice);
            prices.put(good.getName(), Math.round(newPrice * 10.0) / 10.0);
        }

        // 4. Decay supply pressure
        catalogue.getGoods().forEach(Good::decaySupplyPressure);

        // 5. Bots trade
        botService.processTick();

        // 6–8. Per-human-session services
        Collection<Portfolio> humans = sessionRegistry.getHumanPortfolios();
        Map<String, List<LimitOrderFill>> fills = limitOrderService.processAll(humans);
        moneylenderService.processAll(humans);
        warehousingService.processAll(humans);

        // 9. Compute scoreboard (includes bots)
        List<ScoreboardEntry> scoreboard = scoreboardService.compute(
            sessionRegistry.getAllActiveSessions(), prices);

        // 10. Broadcast global market state
        MarketTickPayload payload = new MarketTickPayload(
            prices,
            priceHistory.getAllHistory(),
            event != null ? event.message() : null,
            scoreboard,
            seasonEngine.getCurrentSeason(),
            seasonEngine.getTicksRemaining()
        );
        messagingTemplate.convertAndSend("/topic/market", payload);

        // 11. Push per-session updates
        for (Portfolio p : humans) {
            SessionUpdate update = new SessionUpdate(
                p.getGold(),
                p.getLimitOrders(),
                p.getLoanAmount(),
                fills.getOrDefault(p.getSessionId(), List.of())
            );
            messagingTemplate.convertAndSendToUser(p.getSessionId(), "/queue/updates", update);
        }
    }
}
```

- [ ] **Step 2: Update `TradeController` to compute `realizedPnl`**

Replace the `execute` method in `TradeController.java`. The sell path now reads cost basis and gold before calling sell, then computes realized PnL:

```java
private ResponseEntity<?> execute(TradeRequest request, boolean isBuy) {
    String sid = request.sessionId();
    if (sid == null || sid.isBlank() || !UUID_PATTERN.matcher(sid).matches()) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION_ID"));
    }
    Portfolio portfolio = registry.findById(sid).orElse(null);
    if (portfolio == null) {
        return ResponseEntity.status(404).body(Map.of("error", "SESSION_NOT_FOUND"));
    }
    double realizedPnl = 0.0;
    try {
        if (isBuy) {
            tradeService.buy(portfolio, request.good(), request.quantity());
        } else {
            double avgCost = portfolio.getAvgCostBasis(request.good());
            double goldBefore = portfolio.getGold();
            tradeService.sell(portfolio, request.good(), request.quantity());
            double proceeds = portfolio.getGold() - goldBefore;
            realizedPnl = proceeds - (avgCost * request.quantity());
        }
    } catch (TradeService.TradeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "UNKNOWN_GOOD"));
    }
    return ResponseEntity.ok(new TradeResponse(
        portfolio.getGold(),
        portfolio.getHoldings(),
        portfolio.getAllCostBasis(),
        realizedPnl,
        portfolio.getLoanAmount(),
        seasonEngine.getCurrentSeason()
    ));
}
```

Also inject `SeasonEngine` into `TradeController`:
```java
private final SeasonEngine seasonEngine;

public TradeController(SessionRegistry registry, TradeService tradeService, SeasonEngine seasonEngine) {
    this.registry = registry;
    this.tradeService = tradeService;
    this.seasonEngine = seasonEngine;
}
```

- [ ] **Step 3: Update `SessionController`**

Update both `start` and `resume` methods to return the expanded `SessionStartResponse`:
```java
private SessionStartResponse toResponse(Portfolio p) {
    return new SessionStartResponse(
        p.getSessionId(), p.getPlayerName(), p.getPlayerClass().name(),
        p.getGold(), p.getHoldings(), p.getAllCostBasis(),
        seasonEngine.getCurrentSeason(), seasonEngine.getTicksRemaining(),
        p.getLoanAmount(), p.getLimitOrders()
    );
}
```

Inject `SeasonEngine`:
```java
private final SeasonEngine seasonEngine;

public SessionController(SessionRegistry registry, SeasonEngine seasonEngine) {
    this.registry = registry;
    this.seasonEngine = seasonEngine;
}
```

Replace both `ResponseEntity.ok(new SessionStartResponse(...))` calls with `ResponseEntity.ok(toResponse(p))`.

- [ ] **Step 4: Update controller tests for new response fields**

In `TradeControllerTest.java`, update assertions that check response fields to also accept (not validate, just not break on) `realizedPnl`, `loanAmount`, `currentSeason`.

In `SessionControllerTest.java`, update similar assertions.

The key change: read `sessionId` from the response and check `gold` still exists. Add checks:
```java
.andExpect(jsonPath("$.realizedPnl").exists())
.andExpect(jsonPath("$.currentSeason").exists())
```

- [ ] **Step 5: Run all tests**

```bash
mvn test -q
```
Expected: BUILD SUCCESS, all tests passing.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/medievalmarket/service/MarketEngine.java \
        src/main/java/com/medievalmarket/controller/TradeController.java \
        src/main/java/com/medievalmarket/controller/SessionController.java \
        src/test/java/com/medievalmarket/controller/
git commit -m "feat: wire all services into MarketEngine tick; per-session SessionUpdate push; realizedPnl in TradeResponse"
```

---

## Task 8: OrderController + MoneylenderController

**Files:**
- Create: `src/main/java/com/medievalmarket/controller/OrderController.java`
- Create: `src/main/java/com/medievalmarket/controller/MoneylenderController.java`
- Create: `src/test/java/com/medievalmarket/controller/OrderControllerTest.java`
- Create: `src/test/java/com/medievalmarket/controller/MoneylenderControllerTest.java`

- [ ] **Step 1: Write failing tests for OrderController**

```java
// src/test/java/com/medievalmarket/controller/OrderControllerTest.java
package com.medievalmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medievalmarket.dto.AddOrderRequest;
import com.medievalmarket.dto.SessionStartRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

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

    @Test
    void addOrderReturns200AndListsOrder() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new AddOrderRequest(sessionId, "Iron", "BUY", 2, 30.0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orders[0].goodName").value("Iron"))
            .andExpect(jsonPath("$.orders[0].direction").value("BUY"));
    }

    @Test
    void cancelOrderReturns200AndRemovesOrder() throws Exception {
        MvcResult addResult = mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new AddOrderRequest(sessionId, "Iron", "BUY", 2, 30.0))))
            .andReturn();
        String orderId = mapper.readTree(addResult.getResponse().getContentAsString())
            .get("orders").get(0).get("id").asText();

        mvc.perform(delete("/api/orders/" + orderId)
                .param("sessionId", sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orders").isEmpty());
    }

    @Test
    void exceeding3OrdersReturns400() throws Exception {
        AddOrderRequest req = new AddOrderRequest(sessionId, "Iron", "BUY", 1, 30.0);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req))).andExpect(status().isOk());
        }
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSessionReturns404() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new AddOrderRequest("00000000-0000-0000-0000-000000000000", "Iron", "BUY", 1, 30.0))))
            .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Write failing tests for MoneylenderController**

```java
// src/test/java/com/medievalmarket/controller/MoneylenderControllerTest.java
package com.medievalmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medievalmarket.dto.BorrowRequest;
import com.medievalmarket.dto.SessionStartRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MoneylenderControllerTest {

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

    @Test
    void borrowReturns200WithUpdatedGoldAndLoan() throws Exception {
        mvc.perform(post("/api/moneylender/borrow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BorrowRequest(sessionId, 200.0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loanAmount").value(200.0))
            .andExpect(jsonPath("$.gold").value(1200.0));
    }

    @Test
    void repayReturns200WithClearedLoan() throws Exception {
        mvc.perform(post("/api/moneylender/borrow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BorrowRequest(sessionId, 200.0)))).andReturn();
        mvc.perform(post("/api/moneylender/repay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + sessionId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loanAmount").value(0.0));
    }

    @Test
    void borrowBelowMinimumReturns400() throws Exception {
        mvc.perform(post("/api/moneylender/borrow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BorrowRequest(sessionId, 10.0))))
            .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 3: Run tests to confirm they fail**

```bash
mvn test -Dtest=OrderControllerTest,MoneylenderControllerTest -q
```
Expected: FAIL — controllers not found.

- [ ] **Step 4: Implement `OrderController`**

```java
// src/main/java/com/medievalmarket/controller/OrderController.java
package com.medievalmarket.controller;

import com.medievalmarket.dto.AddOrderRequest;
import com.medievalmarket.dto.OrderResponse;
import com.medievalmarket.model.LimitOrder;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.LimitOrderService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final SessionRegistry registry;
    private final LimitOrderService limitOrderService;

    public OrderController(SessionRegistry registry, LimitOrderService limitOrderService) {
        this.registry = registry;
        this.limitOrderService = limitOrderService;
    }

    @PostMapping
    public ResponseEntity<?> addOrder(@RequestBody AddOrderRequest request) {
        Portfolio p = registry.findById(request.sessionId()).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        try {
            limitOrderService.addOrder(p, request.goodName(), request.direction(),
                request.quantity(), request.targetPrice());
        } catch (LimitOrderService.LimitOrderException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "UNKNOWN_GOOD"));
        }
        return ResponseEntity.ok(new OrderResponse(p.getLimitOrders(), p.getGold()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelOrder(@PathVariable String id,
                                         @RequestParam String sessionId) {
        Portfolio p = registry.findById(sessionId).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        p.removeLimitOrder(id);
        return ResponseEntity.ok(new OrderResponse(p.getLimitOrders(), p.getGold()));
    }
}
```

- [ ] **Step 5: Implement `MoneylenderController`**

```java
// src/main/java/com/medievalmarket/controller/MoneylenderController.java
package com.medievalmarket.controller;

import com.medievalmarket.dto.BorrowRequest;
import com.medievalmarket.dto.LoanResponse;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.MoneylenderService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/moneylender")
public class MoneylenderController {

    private final SessionRegistry registry;
    private final MoneylenderService moneylenderService;

    public MoneylenderController(SessionRegistry registry, MoneylenderService moneylenderService) {
        this.registry = registry;
        this.moneylenderService = moneylenderService;
    }

    @PostMapping("/borrow")
    public ResponseEntity<?> borrow(@RequestBody BorrowRequest request) {
        Portfolio p = registry.findById(request.sessionId()).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        try {
            moneylenderService.borrow(p, request.amount());
        } catch (MoneylenderService.LoanException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(new LoanResponse(p.getLoanAmount(), p.getGold()));
    }

    @PostMapping("/repay")
    public ResponseEntity<?> repay(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        if (sessionId == null) return ResponseEntity.badRequest().body(Map.of("error", "MISSING_SESSION"));
        Portfolio p = registry.findById(sessionId).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        moneylenderService.repay(p);
        return ResponseEntity.ok(new LoanResponse(p.getLoanAmount(), p.getGold()));
    }
}
```

- [ ] **Step 6: Run all tests**

```bash
mvn test -q
```
Expected: BUILD SUCCESS, all tests passing.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/medievalmarket/controller/OrderController.java \
        src/main/java/com/medievalmarket/controller/MoneylenderController.java \
        src/test/java/com/medievalmarket/controller/OrderControllerTest.java \
        src/test/java/com/medievalmarket/controller/MoneylenderControllerTest.java
git commit -m "feat: OrderController and MoneylenderController REST endpoints"
```

---

## Task 9: Frontend — WebSocket, Season, Net Worth Chart, Limit Orders, Moneylender

This task updates `src/main/resources/static/index.html`. The file is large; make targeted edits. All Vue data, computed, methods changes live inside the existing `createApp({...})` block.

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Update SockJS connection URL to include sessionId**

Find the line:
```js
const socket = new SockJS('/ws');
```
Replace with:
```js
const socket = new SockJS('/ws?sessionId=' + session.id);
```

- [ ] **Step 2: Subscribe to per-session `/user/queue/updates`**

After the existing `.subscribe('/topic/market', ...)` call, add:
```js
stompClient.subscribe('/user/queue/updates', function(msg) {
    const update = JSON.parse(msg.body);
    session.gold = update.gold;
    session.loanAmount = update.loanAmount;
    session.limitOrders = update.limitOrders;
    // Process limit order fills for achievements
    for (const fill of (update.limitOrderFills || [])) {
        if (fill.direction === 'SELL') {
            checkAchievementsOnSell(fill.realizedPnl, fill.goodName);
        }
    }
});
```

- [ ] **Step 3: Add season, loanAmount, limitOrders to Vue reactive `session` object**

Find where `session` is declared/initialized (after `POST /api/session/start` response). Add fields:
```js
session.season = data.season || 'SPRING';
session.seasonTicksRemaining = data.seasonTicksRemaining || 60;
session.loanAmount = data.loanAmount || 0.0;
session.limitOrders = data.limitOrders || [];
```

Also add to the `session` reactive object declaration (in `data()` or `reactive()`):
```js
season: 'SPRING',
seasonTicksRemaining: 60,
loanAmount: 0.0,
limitOrders: [],
showAddOrder: false,
newOrder: { goodName: '', direction: 'BUY', quantity: 1, targetPrice: 0 },
showBorrow: false,
borrowAmount: 100,
```

- [ ] **Step 4: Update market tick handler to include season data**

In the `/topic/market` subscription handler, add:
```js
session.season = data.season;
session.seasonTicksRemaining = data.seasonTicksRemaining;
```

- [ ] **Step 5: Add net worth history tracking**

Add to Vue data:
```js
netWorthHistory: [],
```

In the tick handler, after computing `netWorth`:
```js
const nw = session.gold + Object.entries(session.holdings)
    .reduce((sum, [g, q]) => sum + (prices[g] || 0) * q, 0);
if (netWorthHistory.length >= 20) netWorthHistory.shift();
netWorthHistory.push(nw);
```

- [ ] **Step 6: Add season indicator to the HTML header**

Find the header bar HTML (the div containing "MEDIEVAL MARKET" title). Add a season display after the title:
```html
<div class="season-badge">
  <span>{{ seasonIcon }}</span>
  <strong>{{ session.season }}</strong>
  <div class="season-progress">
    <div class="season-fill" :style="{ width: seasonProgress + '%' }"></div>
  </div>
  <span class="dim">{{ session.seasonTicksRemaining }}t</span>
</div>
```

Add computed properties:
```js
seasonIcon() {
    return { SPRING: '🌱', SUMMER: '☀️', AUTUMN: '🍂', WINTER: '❄️' }[this.session.season] || '🌱';
},
seasonProgress() {
    return Math.round((1 - this.session.seasonTicksRemaining / 60) * 100);
},
```

Add CSS:
```css
.season-badge { display:flex; align-items:center; gap:.4rem; background:#1e1e30; border:1px solid #a78bfa44; border-radius:6px; padding:.3rem .7rem; font-size:.75rem; color:#a78bfa; }
.season-progress { width:60px; height:3px; background:#2a2a3a; border-radius:2px; }
.season-fill { height:3px; background:#a78bfa; border-radius:2px; transition:width .5s; }
```

- [ ] **Step 7: Add net worth sparkline to portfolio panel**

In the portfolio HTML, below the net worth display line, add:
```html
<div class="nw-chart-wrap" v-if="netWorthHistory.length > 1">
  <canvas :id="'nw-chart'" width="160" height="32"></canvas>
</div>
```

Add a method `updateNetWorthChart()` that draws/updates a Chart.js line chart:
```js
updateNetWorthChart() {
    const canvas = document.getElementById('nw-chart');
    if (!canvas || this.netWorthHistory.length < 2) return;
    const color = this.netWorthHistory[this.netWorthHistory.length - 1] >=
        this.netWorthHistory[0] ? '#4ade80' : '#f87171';
    if (this.nwChart) {
        this.nwChart.data.datasets[0].data = [...this.netWorthHistory];
        this.nwChart.data.datasets[0].borderColor = color;
        this.nwChart.update('none');
    } else {
        this.nwChart = new Chart(canvas, {
            type: 'line',
            data: { labels: this.netWorthHistory.map(() => ''),
                    datasets: [{ data: [...this.netWorthHistory], borderColor: color,
                                 borderWidth: 1.5, pointRadius: 0, tension: 0.3,
                                 fill: false }] },
            options: { animation: false, plugins: { legend: { display: false } },
                       scales: { x: { display: false }, y: { display: false } } }
        });
    }
},
```

Call `this.updateNetWorthChart()` at the end of the tick handler.

Add `nwChart: null` to Vue data.

- [ ] **Step 8: Add Limit Orders panel to portfolio HTML**

Add after the holdings section in the portfolio panel:
```html
<div class="panel-section">
  <div class="panel-header">
    <span>📋 Orders</span>
    <span class="dim">{{ session.limitOrders.length }}/3</span>
  </div>
  <div v-for="order in session.limitOrders" :key="order.id" class="order-row">
    <span>{{ order.direction }} {{ order.goodName }}
      {{ order.direction === 'BUY' ? '≤' : '≥' }}{{ order.targetPrice }}g × {{ order.quantity }}</span>
    <button class="btn-cancel" @click="cancelOrder(order.id)">✕</button>
  </div>
  <button class="btn-add-order" @click="session.showAddOrder = !session.showAddOrder"
    :disabled="session.limitOrders.length >= 3">+ Add Order</button>
  <div v-if="session.showAddOrder" class="order-form">
    <select v-model="session.newOrder.goodName">
      <option v-for="g in allGoods" :key="g" :value="g">{{ g }}</option>
    </select>
    <select v-model="session.newOrder.direction">
      <option value="BUY">BUY</option><option value="SELL">SELL</option>
    </select>
    <input type="number" v-model.number="session.newOrder.quantity" min="1" placeholder="Qty" style="width:50px">
    <input type="number" v-model.number="session.newOrder.targetPrice" min="0" placeholder="Price" style="width:60px">
    <button class="btn-buy" @click="addOrder">Set</button>
  </div>
</div>
```

Add `allGoods` computed:
```js
allGoods() {
    return Object.keys(this.prices);
},
```

Add methods:
```js
async addOrder() {
    const r = await fetch('/api/orders', { method: 'POST',
        headers: {'Content-Type':'application/json'},
        body: JSON.stringify({ sessionId: this.session.id, ...this.session.newOrder }) });
    if (r.ok) {
        const d = await r.json();
        this.session.limitOrders = d.orders;
        this.session.gold = d.gold;
        this.session.showAddOrder = false;
    }
},
async cancelOrder(id) {
    const r = await fetch(`/api/orders/${id}?sessionId=${this.session.id}`, { method: 'DELETE' });
    if (r.ok) {
        const d = await r.json();
        this.session.limitOrders = d.orders;
    }
},
```

- [ ] **Step 9: Add Moneylender panel to portfolio HTML**

Add after limit orders panel:
```html
<div class="panel-section moneylender">
  <div class="panel-header">
    <span>🏦 Moneylender</span>
    <span v-if="session.loanAmount > 0" class="debt-label">
      Debt: {{ session.loanAmount.toFixed(1) }}g (+2%/tick)
    </span>
  </div>
  <div style="display:flex;gap:.4rem;margin-top:.4rem">
    <button class="btn-repay" @click="repay"
      :disabled="session.loanAmount <= 0 || session.gold <= 0">Repay</button>
    <button class="btn-borrow" @click="session.showBorrow = !session.showBorrow">Borrow</button>
  </div>
  <div v-if="session.showBorrow" style="display:flex;gap:.4rem;margin-top:.4rem;align-items:center">
    <input type="number" v-model.number="session.borrowAmount" min="50"
      :max="Math.min(session.gold * 2, 5000)" style="width:80px">
    <span class="dim">max: {{ Math.min(session.gold * 2, 5000).toFixed(0) }}g</span>
    <button class="btn-buy" @click="borrow" :disabled="session.borrowAmount < 50">OK</button>
  </div>
</div>
```

Add CSS:
```css
.debt-label { color:#f87171; font-size:.72rem; }
.btn-repay { background:#7f1d1d; color:#fca5a5; border:none; border-radius:4px; padding:5px 10px; font-size:.78rem; cursor:pointer; }
.btn-borrow { background:#1e1e30; color:#a78bfa; border:1px solid #a78bfa44; border-radius:4px; padding:5px 10px; font-size:.78rem; cursor:pointer; }
```

Add methods:
```js
async borrow() {
    const r = await fetch('/api/moneylender/borrow', { method: 'POST',
        headers: {'Content-Type':'application/json'},
        body: JSON.stringify({ sessionId: this.session.id, amount: this.session.borrowAmount }) });
    if (r.ok) {
        const d = await r.json();
        this.session.gold = d.gold;
        this.session.loanAmount = d.loanAmount;
        this.session.showBorrow = false;
    } else {
        const e = await r.json(); alert(e.error);
    }
},
async repay() {
    const r = await fetch('/api/moneylender/repay', { method: 'POST',
        headers: {'Content-Type':'application/json'},
        body: JSON.stringify({ sessionId: this.session.id }) });
    if (r.ok) {
        const d = await r.json();
        this.session.gold = d.gold;
        this.session.loanAmount = d.loanAmount;
    }
},
```

- [ ] **Step 10: Verify app starts and new UI elements appear**

```bash
mvn spring-boot:run
```
Open `http://localhost:8080`. Verify:
- Season badge appears in header
- Portfolio panel shows Limit Orders and Moneylender sections
- Borrow/Repay buttons work (check browser console for errors)

Stop the server with Ctrl+C.

- [ ] **Step 11: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: frontend — season indicator, net worth chart, limit orders panel, moneylender panel"
```

---

## Task 10: Frontend — Price Alerts, Achievements

**Files:**
- Modify: `src/main/resources/static/index.html`

- [ ] **Step 1: Add price alerts — Vue data and bell column**

Add to Vue data:
```js
priceAlerts: {},      // { goodName: { above: number|null, below: number|null } }
alertPopover: null,   // goodName currently showing popover
alertAbove: '',
alertBelow: '',
prevPrices: {},       // prices from previous tick for threshold comparison
```

Add a bell icon column to the market table header (after the Buy column header):
```html
<th class="col-bell">🔔</th>
```

Add to each market row:
```html
<td class="col-bell">
  <span :class="['bell-icon', hasAlert(good.name) ? 'bell-active' : 'bell-dim']"
        @click.stop="toggleAlertPopover(good.name)">🔔</span>
  <div v-if="alertPopover === good.name" class="alert-popover" @click.stop>
    <div>Above: <input type="number" v-model.number="alertAbove" placeholder="—" style="width:55px">g</div>
    <div>Below: <input type="number" v-model.number="alertBelow" placeholder="—" style="width:55px">g</div>
    <div style="display:flex;gap:.4rem;margin-top:.3rem">
      <button class="btn-buy" style="padding:3px 8px;font-size:.72rem" @click="setAlert(good.name)">Set</button>
      <button class="btn-sell" style="padding:3px 8px;font-size:.72rem" @click="clearAlert(good.name)">Clear</button>
    </div>
  </div>
</td>
```

Add CSS:
```css
.col-bell { width: 28px; text-align: center; position: relative; }
.bell-icon { cursor: pointer; font-size: .85rem; }
.bell-active { filter: sepia(1) saturate(5) hue-rotate(5deg); }
.bell-dim { opacity: .3; }
.alert-popover { position:absolute; right:0; top:1.6rem; z-index:200; background:#1e1e30; border:1px solid #a78bfa44; border-radius:6px; padding:.5rem .6rem; font-size:.75rem; min-width:150px; }
```

- [ ] **Step 2: Add price alert methods**

```js
hasAlert(name) {
    const a = this.priceAlerts[name];
    return a && (a.above != null || a.below != null);
},
toggleAlertPopover(name) {
    if (this.alertPopover === name) { this.alertPopover = null; return; }
    this.alertPopover = name;
    const a = this.priceAlerts[name] || {};
    this.alertAbove = a.above ?? '';
    this.alertBelow = a.below ?? '';
},
setAlert(name) {
    this.priceAlerts[name] = {
        above: this.alertAbove !== '' ? Number(this.alertAbove) : null,
        below: this.alertBelow !== '' ? Number(this.alertBelow) : null
    };
    this.alertPopover = null;
},
clearAlert(name) {
    delete this.priceAlerts[name];
    this.alertPopover = null;
},
```

- [ ] **Step 3: Check alerts on each tick**

In the tick handler, after updating prices, add:
```js
// Check price alerts
for (const [name, alert] of Object.entries(this.priceAlerts)) {
    const prev = this.prevPrices[name];
    const curr = this.prices[name];
    if (prev == null || curr == null) continue;
    if (alert.above != null && prev < alert.above && curr >= alert.above) {
        this.flashAlertBanner(name + ' reached ' + curr.toFixed(1) + 'g (above target)');
    }
    if (alert.below != null && prev > alert.below && curr <= alert.below) {
        this.flashAlertBanner(name + ' dropped to ' + curr.toFixed(1) + 'g (below target)');
    }
}
this.prevPrices = { ...this.prices };
```

Add the `flashAlertBanner` method (similar to the existing event flash, but amber):
```js
flashAlertBanner(message) {
    this.alertMessage = message;
    setTimeout(() => { this.alertMessage = null; }, 3000);
},
```

Add `alertMessage: null` to Vue data.

Add alert banner HTML (alongside the event banner):
```html
<div v-if="alertMessage" class="event-banner alert-banner">🔔 {{ alertMessage }}</div>
```

Add CSS:
```css
.alert-banner { background: #78350f; border-color: #d97706; color: #fde68a; }
```

- [ ] **Step 4: Add achievements — data and definitions**

Add to Vue data:
```js
achievements: JSON.parse(localStorage.getItem('mms_achievements') || '[]'),
achievementToast: null,
consecutiveProfits: 0,
seasonsWithProfit: new Set(),
warTicksRemaining: 0,
```

Add achievement definitions as a constant (outside the Vue app, at the top of the `<script>` block):
```js
const ACHIEVEMENTS = [
    { id: 'first_trade',    icon: '✅', name: 'First Steps',        check: (ctx) => ctx.anyTrade },
    { id: 'fortune_1k',     icon: '💰', name: "Fortune's Child",    check: (ctx) => ctx.netWorth >= 1000 },
    { id: 'fortune_5k',     icon: '💎', name: 'Merchant Prince',    check: (ctx) => ctx.netWorth >= 5000 },
    { id: 'fortune_10k',    icon: '👑', name: 'Marketplace Legend', check: (ctx) => ctx.netWorth >= 10000 },
    { id: 'war_profiteer',  icon: '⚔️', name: 'War Profiteer',      check: (ctx) => ctx.warSell },
    { id: 'hoarder',        icon: '📦', name: 'Hoarder',            check: (ctx) => ctx.distinctGoods >= 6 },
    { id: 'gem_baron',      icon: '💎', name: 'Gem Baron',          check: (ctx) => ctx.gems >= 10 },
    { id: 'debt_free',      icon: '🏦', name: 'Debt Free',          check: (ctx) => ctx.debtCleared },
    { id: 'limit_master',   icon: '📋', name: 'Limit Master',       check: (ctx) => ctx.orders >= 3 },
    { id: 'seasonal_trader',icon: '🌍', name: 'Seasonal Trader',    check: (ctx) => ctx.allSeasons },
    { id: 'plague_survivor',icon: '🦠', name: 'Plague Survivor',    check: (ctx) => ctx.plagueOk },
    { id: 'lucky_streak',   icon: '🍀', name: 'Lucky Streak',       check: (ctx) => ctx.streak >= 3 },
];
```

- [ ] **Step 5: Add achievement unlock logic**

Add method `unlockAchievement(id)`:
```js
unlockAchievement(id) {
    if (this.achievements.includes(id)) return;
    this.achievements.push(id);
    localStorage.setItem('mms_achievements', JSON.stringify(this.achievements));
    const def = ACHIEVEMENTS.find(a => a.id === id);
    if (def) {
        this.achievementToast = def.icon + ' ' + def.name + ' unlocked!';
        setTimeout(() => { this.achievementToast = null; }, 3000);
    }
},
```

Add method `checkAchievements(ctx)`:
```js
checkAchievements(ctx) {
    for (const a of ACHIEVEMENTS) {
        if (!this.achievements.includes(a.id) && a.check(ctx)) {
            this.unlockAchievement(a.id);
        }
    }
},
```

Add method `checkAchievementsOnTick(data)` — called from the market tick handler:
```js
checkAchievementsOnTick(data) {
    if (this.warTicksRemaining > 0) this.warTicksRemaining--;
    const netWorth = this.session.gold +
        Object.entries(this.session.holdings)
            .reduce((s,[g,q]) => s + (this.prices[g]||0)*q, 0);
    const gems = this.session.holdings['Gems'] || 0;
    const distinctGoods = Object.keys(this.session.holdings).length;

    // Plague survivor: snapshot before (stored as prevNetWorth), check after
    let plagueOk = false;
    if (data.event && data.event.includes('Plague') && this.prevNetWorth != null) {
        plagueOk = netWorth >= this.prevNetWorth;
    }
    this.prevNetWorth = netWorth;

    // War event tracking
    if (data.event && data.event.includes('War')) this.warTicksRemaining = 2;

    this.checkAchievements({
        netWorth, gems, distinctGoods,
        orders: this.session.limitOrders.length,
        allSeasons: ['SPRING','SUMMER','AUTUMN','WINTER'].every(s => this.seasonsWithProfit.has(s)),
        plagueOk,
        anyTrade: false, warSell: false, debtCleared: false, streak: this.consecutiveProfits,
    });
},
```

Add method `checkAchievementsOnSell(realizedPnl, goodName)`:
```js
checkAchievementsOnSell(realizedPnl, goodName) {
    if (realizedPnl > 0) {
        this.consecutiveProfits++;
        this.seasonsWithProfit.add(this.session.season);
    } else {
        this.consecutiveProfits = 0;
    }
    const warSell = this.warTicksRemaining > 0 &&
        ['Iron','Coal','Timber','Rope'].includes(goodName);

    this.checkAchievements({
        netWorth: 0, gems: 0, distinctGoods: 0, orders: 0,
        allSeasons: ['SPRING','SUMMER','AUTUMN','WINTER'].every(s => this.seasonsWithProfit.has(s)),
        plagueOk: false, anyTrade: true, warSell,
        debtCleared: false, streak: this.consecutiveProfits,
    });
},
```

Call `checkAchievementsOnTick(data)` at the end of the market tick handler.

In the buy/sell API response handlers, add:
```js
// after successful buy:
this.checkAchievements({ anyTrade: true, /* rest 0/false */ netWorth:0, gems:0, distinctGoods:0, orders:0, allSeasons:false, plagueOk:false, warSell:false, debtCleared:false, streak:0 });

// after successful sell (data is TradeResponse):
// 'goodName' here is the same variable used in the sell fetch call, e.g. the good
// passed to tradeService.sell(). Store it before the async call if needed:
//   const goodName = request.good;  ← capture from the existing trade request object
this.checkAchievementsOnSell(data.realizedPnl, goodName);
```

Also add debt cleared check in the repay handler (after `loanAmount` updates to 0):
```js
if (this.session.loanAmount === 0 && prevLoan > 0) {
    this.checkAchievements({ debtCleared: true, /* rest 0/false */ ... });
}
```

Add `prevNetWorth: null` to Vue data.

- [ ] **Step 6: Add achievements HTML to portfolio panel**

Add after the moneylender panel:
```html
<div class="panel-section">
  <div class="panel-header">🏅 Achievements ({{ achievements.length }}/12)</div>
  <div class="badge-shelf">
    <div v-for="a in allAchievements" :key="a.id"
         :class="['badge', achievements.includes(a.id) ? 'badge-unlocked' : 'badge-locked']"
         :title="a.name">
      <span v-if="achievements.includes(a.id)">{{ a.icon }} {{ a.name }}</span>
      <span v-else>🔒 ???</span>
    </div>
  </div>
</div>
```

Add achievement toast banner HTML (alongside event and alert banners):
```html
<div v-if="achievementToast" class="event-banner achievement-banner">{{ achievementToast }}</div>
```

Add computed:
```js
allAchievements() { return ACHIEVEMENTS; },
```

Add CSS:
```css
.badge-shelf { display:flex; flex-wrap:wrap; gap:.3rem; }
.badge { border-radius:5px; padding:3px 7px; font-size:.7rem; }
.badge-unlocked { background:#1e1e30; border:1px solid #fbbf2466; color:#fbbf24; }
.badge-locked { background:#111; border:1px solid #2a2a3a; color:#444; }
.achievement-banner { background:#064e3b; border-color:#34d399; color:#d1fae5; }
```

- [ ] **Step 7: Verify the full frontend**

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`. Verify:
- Create a session, trade a good — "First Steps" achievement unlocks with a toast
- Bell icon is present in the market table; clicking opens a popover
- Setting an alert and waiting for a price cross triggers the amber banner
- Achievement shelf shows locked/unlocked badges

Stop server with Ctrl+C.

- [ ] **Step 8: Run full test suite**

```bash
mvn test -q
```
Expected: BUILD SUCCESS, all tests passing.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: frontend — price alerts with bell icon, 12 achievements with localStorage and toasts"
```

---

## Task 11: Final Integration Verification

- [ ] **Step 1: Run all tests**

```bash
mvn test
```
Expected: BUILD SUCCESS, all tests GREEN.

- [ ] **Step 2: Start the application and verify all features**

```bash
mvn spring-boot:run
```

Open `http://localhost:8080` in two browser tabs. Check:

| Feature | How to verify |
|---------|---------------|
| Seasons | Season badge in header changes every 5 min; try reloading |
| Bots | Scoreboard shows 5 `[bot]` entries |
| Limit Orders | Add a BUY order below current price, wait for price to drop; order auto-fills |
| Moneylender | Borrow 100g — gold increases, debt shows; repay — debt clears; `Debt Free` achievement unlocks |
| Warehousing | Buy goods, watch gold slowly decrease each tick |
| Price Alerts | Set alert, watch for amber banner |
| Achievements | Trade, hoard, reach fortune thresholds — badges unlock |
| Net Worth Chart | Sparkline grows in portfolio panel over time |
| Multiplayer | Actions in tab 1 affect prices seen in tab 2 |

- [ ] **Step 3: Commit final state**

```bash
git add -A
git commit -m "feat: complete feature expansion — seasons, bots, limit orders, moneylender, warehousing, alerts, achievements, net worth chart"
```
