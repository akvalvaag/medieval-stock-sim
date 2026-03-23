# Medieval Market Simulator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a real-time multiplayer medieval goods trading simulator with a Spring Boot WebSocket backend and Vue.js frontend.

**Architecture:** A Spring Boot 3 application serves both the REST API and the static Vue.js frontend. A `@Scheduled` `MarketEngine` ticks every 5 seconds, recomputes all 15 good prices using layered drift/supply-demand/event logic, then broadcasts the full market state (prices, history, scoreboard) to all connected clients via STOMP WebSocket. Players buy and sell via REST, with results immediately reflected in the next WebSocket tick.

**Tech Stack:** Java 21, Spring Boot 3, Spring WebSocket (STOMP), Spring MVC, Vue.js 3 (CDN), Chart.js (CDN), STOMP.js (CDN), browser localStorage for session persistence.

---

## File Map

```
pom.xml
src/main/java/com/medievalmarket/
  MedievalMarketApplication.java
  config/
    WebSocketConfig.java              — STOMP broker + endpoint registration
  model/
    Good.java                         — mutable good state: price, supplyPressure
    PlayerClass.java                  — enum MERCHANT/MINER/NOBLE with startGold, feeRate
    Portfolio.java                    — player state: sessionId, name, class, gold, holdings, netWorthHistory
    ScoreboardEntry.java              — DTO: name, class, netWorth, trend, trending
  service/
    GoodsCatalogue.java               — Spring @Component, initialises the 15 shared Good instances
    NameGenerator.java                — generates random medieval alias at session creation
    PriceHistory.java                 — circular buffer (capacity 50) per good
    PriceModel.java                   — stateless: computes new price given good + event modifier
    EventEngine.java                  — stateful: 10% chance/tick to fire an event, returns modifier map
    SessionRegistry.java              — ConcurrentHashMap<sessionId, Portfolio>, expiry logic
    TradeService.java                 — buy/sell validation + execution + supply pressure update
    ScoreboardService.java            — net worth calculation + trend + top-3 annotation
    MarketEngine.java                 — @Scheduled tick: orchestrates all services, broadcasts result
  controller/
    SessionController.java            — POST /api/session/start, GET /api/session/{id}
    TradeController.java              — POST /api/trade/buy, POST /api/trade/sell
    MarketController.java             — GET /api/market/snapshot
  dto/
    SessionStartRequest.java
    SessionStartResponse.java
    TradeRequest.java
    TradeResponse.java
    MarketTickPayload.java            — WebSocket broadcast shape
    MarketSnapshotResponse.java
src/main/resources/
  application.properties
  static/
    index.html                        — entire Vue.js frontend (single file)
src/test/java/com/medievalmarket/
  service/
    NameGeneratorTest.java
    PriceModelTest.java
    EventEngineTest.java
    TradeServiceTest.java
    ScoreboardServiceTest.java
  controller/
    SessionControllerTest.java
    TradeControllerTest.java
    MarketControllerTest.java
```

---

## Task 1: Project Scaffold

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/medievalmarket/MedievalMarketApplication.java`
- Create: `src/main/resources/application.properties`

- [ ] **Step 1: Create `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.3</version>
    <relativePath/>
  </parent>

  <groupId>com.medievalmarket</groupId>
  <artifactId>medieval-market</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <name>medieval-market</name>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Create main application class**

```java
// src/main/java/com/medievalmarket/MedievalMarketApplication.java
package com.medievalmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MedievalMarketApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedievalMarketApplication.class, args);
    }
}
```

- [ ] **Step 3: Create `application.properties`**

```properties
# src/main/resources/application.properties
server.port=8080
spring.application.name=medieval-market
```

- [ ] **Step 4: Verify the project compiles and starts**

```bash
./mvnw spring-boot:run
```

Expected: Server starts on port 8080 with no errors.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/medievalmarket/MedievalMarketApplication.java src/main/resources/application.properties
git commit -m "feat: project scaffold"
```

---

## Task 2: Domain Models

**Files:**
- Create: `src/main/java/com/medievalmarket/model/Good.java`
- Create: `src/main/java/com/medievalmarket/model/PlayerClass.java`
- Create: `src/main/java/com/medievalmarket/model/Portfolio.java`
- Create: `src/main/java/com/medievalmarket/model/ScoreboardEntry.java`

No tests needed for plain data holders — they are tested indirectly via service tests.

- [ ] **Step 1: Create `Good.java`**

```java
// src/main/java/com/medievalmarket/model/Good.java
package com.medievalmarket.model;

public class Good {
    public enum Volatility { LOW, MEDIUM, HIGH }

    private final String name;
    private final String category;
    private final double basePrice;
    private final Volatility volatility;

    // Mutable state — access must be synchronized on this object
    private double currentPrice;
    private double supplyPressure = 0.0;

    public Good(String name, String category, double basePrice, Volatility volatility) {
        this.name = name;
        this.category = category;
        this.basePrice = basePrice;
        this.volatility = volatility;
        this.currentPrice = basePrice;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getBasePrice() { return basePrice; }
    public Volatility getVolatility() { return volatility; }

    public synchronized double getCurrentPrice() { return currentPrice; }
    public synchronized void setCurrentPrice(double p) { this.currentPrice = p; }

    public synchronized double getSupplyPressure() { return supplyPressure; }
    public synchronized void addSupplyPressure(double delta) { this.supplyPressure += delta; }
    public synchronized void decaySupplyPressure() { this.supplyPressure *= 0.70; }
}
```

- [ ] **Step 2: Create `PlayerClass.java`**

```java
// src/main/java/com/medievalmarket/model/PlayerClass.java
package com.medievalmarket.model;

public enum PlayerClass {
    MERCHANT(500.0, 0.0),
    MINER(350.0, 0.03),
    NOBLE(1000.0, 0.03);

    private final double startGold;
    private final double feeRate;  // 0.0 = no fee, 0.03 = 3%

    PlayerClass(double startGold, double feeRate) {
        this.startGold = startGold;
        this.feeRate = feeRate;
    }

    public double getStartGold() { return startGold; }
    public double getFeeRate() { return feeRate; }
}
```

- [ ] **Step 3: Create `Portfolio.java`**

```java
// src/main/java/com/medievalmarket/model/Portfolio.java
package com.medievalmarket.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class Portfolio {
    private final String sessionId;
    private final String playerName;
    private final PlayerClass playerClass;
    private double gold;
    private final Map<String, Integer> holdings = new HashMap<>();
    // Circular buffer of net worth snapshots (last 10 values)
    private final Deque<Double> netWorthHistory = new ArrayDeque<>();
    private Instant lastTradeTime = Instant.now();

    public Portfolio(String sessionId, String playerName, PlayerClass playerClass) {
        this.sessionId = sessionId;
        this.playerName = playerName;
        this.playerClass = playerClass;
        this.gold = playerClass.getStartGold();
    }

    public String getSessionId() { return sessionId; }
    public String getPlayerName() { return playerName; }
    public PlayerClass getPlayerClass() { return playerClass; }

    public synchronized double getGold() { return gold; }
    public synchronized void setGold(double gold) { this.gold = gold; }

    public synchronized Map<String, Integer> getHoldings() { return new HashMap<>(holdings); }
    public synchronized int getHolding(String good) { return holdings.getOrDefault(good, 0); }
    public synchronized void setHolding(String good, int qty) {
        if (qty == 0) holdings.remove(good);
        else holdings.put(good, qty);
    }

    public synchronized void recordNetWorth(double netWorth) {
        if (netWorthHistory.size() >= 10) netWorthHistory.pollFirst();
        netWorthHistory.addLast(netWorth);
    }

    public synchronized double getTrend() {
        if (netWorthHistory.size() < 2) return 0.0;
        double current = netWorthHistory.peekLast();
        // Compare against value from 5 ticks ago, or oldest available
        Object[] arr = netWorthHistory.toArray();
        int compareIdx = Math.max(0, arr.length - 6);
        return current - (double) arr[compareIdx];
    }

    public Instant getLastTradeTime() { return lastTradeTime; }
    public void touchLastTradeTime() { this.lastTradeTime = Instant.now(); }
}
```

- [ ] **Step 4: Create `ScoreboardEntry.java`**

```java
// src/main/java/com/medievalmarket/model/ScoreboardEntry.java
package com.medievalmarket.model;

public class ScoreboardEntry {
    private final String name;
    private final String playerClass;
    private final double netWorth;
    private final double trend;
    private final String trending; // "UP", "DOWN", "NEUTRAL"

    public ScoreboardEntry(String name, String playerClass, double netWorth,
                           double trend, String trending) {
        this.name = name;
        this.playerClass = playerClass;
        this.netWorth = netWorth;
        this.trend = trend;
        this.trending = trending;
    }

    public String getName() { return name; }
    public String getPlayerClass() { return playerClass; }
    public double getNetWorth() { return netWorth; }
    public double getTrend() { return trend; }
    public String getTrending() { return trending; }
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/model/
git commit -m "feat: domain models"
```

---

## Task 3: Goods Catalogue

**Files:**
- Create: `src/main/java/com/medievalmarket/service/GoodsCatalogue.java`
- Create: `src/test/java/com/medievalmarket/service/GoodsCatalogueTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/medievalmarket/service/GoodsCatalogueTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class GoodsCatalogueTest {

    @Test
    void catalogueHas15Goods() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        assertThat(catalogue.getGoods()).hasSize(15);
    }

    @Test
    void allGoodsHavePositiveBasePrice() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        for (Good good : catalogue.getGoods()) {
            assertThat(good.getBasePrice()).isGreaterThan(0);
        }
    }

    @Test
    void miningCategoryHasFiveGoods() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        long count = catalogue.getGoods().stream()
            .filter(g -> "Mining".equals(g.getCategory()))
            .count();
        assertThat(count).isEqualTo(5);
    }

    @Test
    void goodNamesAreUnique() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        long uniqueNames = catalogue.getGoods().stream()
            .map(Good::getName).distinct().count();
        assertThat(uniqueNames).isEqualTo(15);
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./mvnw test -pl . -Dtest=GoodsCatalogueTest
```

Expected: FAIL — `GoodsCatalogue` does not exist yet.

- [ ] **Step 3: Implement `GoodsCatalogue`**

```java
// src/main/java/com/medievalmarket/service/GoodsCatalogue.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.Good.Volatility;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class GoodsCatalogue {

    private final List<Good> goods;

    public GoodsCatalogue() {
        goods = List.of(
            // Agriculture
            new Good("Grain",     "Agriculture",    10.0, Volatility.LOW),
            new Good("Wool",      "Agriculture",    20.0, Volatility.LOW),
            new Good("Livestock", "Agriculture",    35.0, Volatility.LOW),
            new Good("Ale",       "Agriculture",    15.0, Volatility.LOW),
            new Good("Spices",    "Agriculture",    80.0, Volatility.HIGH),
            // Mining
            new Good("Iron",      "Mining",         40.0, Volatility.MEDIUM),
            new Good("Coal",      "Mining",         25.0, Volatility.MEDIUM),
            new Good("Stone",     "Mining",         18.0, Volatility.LOW),
            new Good("Gems",      "Mining",        120.0, Volatility.HIGH),
            new Good("Salt",      "Mining",         22.0, Volatility.LOW),
            // Timber & Craft
            new Good("Timber",    "Timber & Craft", 30.0, Volatility.LOW),
            new Good("Rope",      "Timber & Craft", 12.0, Volatility.LOW),
            new Good("Cloth",     "Timber & Craft", 28.0, Volatility.MEDIUM),
            new Good("Leather",   "Timber & Craft", 45.0, Volatility.MEDIUM),
            new Good("Candles",   "Timber & Craft",  8.0, Volatility.LOW)
        );
    }

    public List<Good> getGoods() { return goods; }

    public Good findByName(String name) {
        return goods.stream()
            .filter(g -> g.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown good: " + name));
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=GoodsCatalogueTest
```

Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/GoodsCatalogue.java \
        src/test/java/com/medievalmarket/service/GoodsCatalogueTest.java
git commit -m "feat: goods catalogue with 15 medieval goods"
```

---

## Task 4: Name Generator

**Files:**
- Create: `src/main/java/com/medievalmarket/service/NameGenerator.java`
- Create: `src/test/java/com/medievalmarket/service/NameGeneratorTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/medievalmarket/service/NameGeneratorTest.java
package com.medievalmarket.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class NameGeneratorTest {

    @Test
    void generatedNameIsNotEmpty() {
        NameGenerator gen = new NameGenerator();
        assertThat(gen.generate()).isNotBlank();
    }

    @Test
    void generatedNameHasAtLeastTwoWords() {
        NameGenerator gen = new NameGenerator();
        // e.g. "Aldric the Bold" or "Mildred of Stonekeep"
        assertThat(gen.generate().split("\\s+").length).isGreaterThanOrEqualTo(2);
    }

    @Test
    void generatesDistinctNamesOverTime() {
        NameGenerator gen = new NameGenerator();
        // With a large enough pool there should be variety
        long distinct = java.util.stream.IntStream.range(0, 50)
            .mapToObj(i -> gen.generate())
            .distinct()
            .count();
        assertThat(distinct).isGreaterThan(5);
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./mvnw test -pl . -Dtest=NameGeneratorTest
```

Expected: FAIL.

- [ ] **Step 3: Implement `NameGenerator`**

```java
// src/main/java/com/medievalmarket/service/NameGenerator.java
package com.medievalmarket.service;

import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Random;

@Component
public class NameGenerator {

    private static final List<String> FIRST_NAMES = List.of(
        "Aldric", "Mildred", "Godwin", "Edith", "Leofric",
        "Wulfric", "Hilda", "Oswin", "Aelswith", "Thorbert",
        "Sigrid", "Aethelred", "Brunhilde", "Cynric", "Matilda"
    );

    private static final List<String> EPITHETS = List.of(
        "the Bold", "the Cunning", "the Stout", "the Shrewd",
        "the Fearless", "the Wise", "the Greedy", "the Lucky"
    );

    private static final List<String> PLACE_SUFFIXES = List.of(
        "of Stonekeep", "of Ironhaven", "of the Moors",
        "of Ashford", "of Greywall", "of the Valley"
    );

    private final Random random = new Random();

    public String generate() {
        String firstName = FIRST_NAMES.get(random.nextInt(FIRST_NAMES.size()));
        String suffix = random.nextBoolean()
            ? EPITHETS.get(random.nextInt(EPITHETS.size()))
            : PLACE_SUFFIXES.get(random.nextInt(PLACE_SUFFIXES.size()));
        return firstName + " " + suffix;
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=NameGeneratorTest
```

Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/NameGenerator.java \
        src/test/java/com/medievalmarket/service/NameGeneratorTest.java
git commit -m "feat: medieval alias name generator"
```

---

## Task 5: Price History

**Files:**
- Create: `src/main/java/com/medievalmarket/service/PriceHistory.java`
- Create: `src/test/java/com/medievalmarket/service/PriceHistoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
// src/test/java/com/medievalmarket/service/PriceHistoryTest.java
package com.medievalmarket.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PriceHistoryTest {

    @Test
    void preSeededWith50BasePriceValues() {
        PriceHistory history = new PriceHistory(40.0);
        assertThat(history.getHistory("Iron")).hasSize(50);
        assertThat(history.getHistory("Iron")).allMatch(v -> v == 40.0);
    }

    @Test
    void appendAddsToEnd() {
        PriceHistory history = new PriceHistory(10.0);
        history.append("Grain", 11.0);
        List<Double> h = history.getHistory("Grain");
        assertThat(h).hasSize(50);
        assertThat(h.get(49)).isEqualTo(11.0);
    }

    @Test
    void circularBufferDropsOldestWhenFull() {
        PriceHistory history = new PriceHistory(10.0);
        history.append("Grain", 99.0); // oldest becomes index 0's neighbour
        for (int i = 0; i < 50; i++) history.append("Grain", (double) i);
        List<Double> h = history.getHistory("Grain");
        assertThat(h).hasSize(50);
        assertThat(h.get(0)).isEqualTo(0.0);
        assertThat(h.get(49)).isEqualTo(49.0);
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./mvnw test -pl . -Dtest=PriceHistoryTest
```

Expected: FAIL.

- [ ] **Step 3: Implement `PriceHistory`**

Note: `PriceHistory` is a single shared instance that holds history for ALL goods. It is initialised with the goods catalogue.

```java
// src/main/java/com/medievalmarket/service/PriceHistory.java
package com.medievalmarket.service;

import org.springframework.stereotype.Component;
import com.medievalmarket.model.Good;
import java.util.*;

@Component
public class PriceHistory {

    private static final int CAPACITY = 50;
    private final Map<String, Deque<Double>> buffers = new HashMap<>();

    // Used by tests — single good with given base price
    PriceHistory(double singleBasePrice) {
        // Test-only constructor: seed a single dummy good named after the first call
    }

    // Spring-managed constructor
    public PriceHistory(GoodsCatalogue catalogue) {
        for (Good good : catalogue.getGoods()) {
            Deque<Double> buf = new ArrayDeque<>(CAPACITY);
            for (int i = 0; i < CAPACITY; i++) buf.addLast(good.getBasePrice());
            buffers.put(good.getName(), buf);
        }
    }

    public synchronized void append(String goodName, double price) {
        Deque<Double> buf = buffers.computeIfAbsent(goodName, k -> new ArrayDeque<>(CAPACITY));
        if (buf.size() >= CAPACITY) buf.pollFirst();
        buf.addLast(price);
    }

    public synchronized List<Double> getHistory(String goodName) {
        Deque<Double> buf = buffers.getOrDefault(goodName, new ArrayDeque<>());
        return new ArrayList<>(buf);
    }

    public synchronized Map<String, List<Double>> getAllHistory() {
        Map<String, List<Double>> result = new HashMap<>();
        buffers.forEach((k, v) -> result.put(k, new ArrayList<>(v)));
        return result;
    }
}
```

The test-only constructor needs special handling. Update the class to make the test constructor work:

```java
    // Test-only constructor: seed all calls to getHistory() with the given base price
    PriceHistory(double singleBasePrice) {
        this.singleTestBasePrice = singleBasePrice;
        this.testMode = true;
    }
```

This approach complicates the class. Instead, simplify: the tests will just directly create a `PriceHistory` using the Spring constructor with a real `GoodsCatalogue`. Update the test:

```java
// src/test/java/com/medievalmarket/service/PriceHistoryTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PriceHistoryTest {

    private PriceHistory history() {
        return new PriceHistory(new GoodsCatalogue());
    }

    @Test
    void preSeededWith50BasePriceValues() {
        PriceHistory history = history();
        List<Double> grain = history.getHistory("Grain");
        assertThat(grain).hasSize(50);
        assertThat(grain).allMatch(v -> v == 10.0); // Grain base price = 10g
    }

    @Test
    void appendAddsToEnd() {
        PriceHistory history = history();
        history.append("Grain", 11.0);
        List<Double> h = history.getHistory("Grain");
        assertThat(h).hasSize(50);
        assertThat(h.get(49)).isEqualTo(11.0);
    }

    @Test
    void circularBufferDropsOldestWhenFull() {
        PriceHistory history = history();
        // Fill with 50 new values to push all originals out
        for (int i = 0; i < 50; i++) history.append("Grain", (double) i);
        List<Double> h = history.getHistory("Grain");
        assertThat(h).hasSize(50);
        assertThat(h.get(0)).isEqualTo(0.0);
        assertThat(h.get(49)).isEqualTo(49.0);
    }
}
```

Final implementation (remove the test constructor entirely):

```java
// src/main/java/com/medievalmarket/service/PriceHistory.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class PriceHistory {

    private static final int CAPACITY = 50;
    private final Map<String, Deque<Double>> buffers = new HashMap<>();

    public PriceHistory(GoodsCatalogue catalogue) {
        for (Good good : catalogue.getGoods()) {
            Deque<Double> buf = new ArrayDeque<>(CAPACITY);
            for (int i = 0; i < CAPACITY; i++) buf.addLast(good.getBasePrice());
            buffers.put(good.getName(), buf);
        }
    }

    public synchronized void append(String goodName, double price) {
        Deque<Double> buf = buffers.computeIfAbsent(goodName, k -> new ArrayDeque<>(CAPACITY));
        if (buf.size() >= CAPACITY) buf.pollFirst();
        buf.addLast(price);
    }

    public synchronized List<Double> getHistory(String goodName) {
        return new ArrayList<>(buffers.getOrDefault(goodName, new ArrayDeque<>()));
    }

    public synchronized Map<String, List<Double>> getAllHistory() {
        Map<String, List<Double>> result = new HashMap<>();
        buffers.forEach((k, v) -> result.put(k, new ArrayList<>(v)));
        return result;
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=PriceHistoryTest
```

Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/PriceHistory.java \
        src/test/java/com/medievalmarket/service/PriceHistoryTest.java
git commit -m "feat: price history circular buffer"
```

---

## Task 6: Price Model

**Files:**
- Create: `src/main/java/com/medievalmarket/service/PriceModel.java`
- Create: `src/test/java/com/medievalmarket/service/PriceModelTest.java`

`PriceModel` is a stateless service. It takes a `Good` and an optional event modifier and returns a new price.

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/medievalmarket/service/PriceModelTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.Good.Volatility;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PriceModelTest {

    private Good iron() { return new Good("Iron", "Mining", 40.0, Volatility.MEDIUM); }

    @Test
    void priceStaysWithinFloorAndCeiling() {
        PriceModel model = new PriceModel();
        Good good = iron();
        for (int i = 0; i < 1000; i++) {
            double newPrice = model.computeNewPrice(good, 0.0);
            assertThat(newPrice).isGreaterThanOrEqualTo(good.getBasePrice() * 0.10);
            assertThat(newPrice).isLessThanOrEqualTo(good.getBasePrice() * 5.00);
        }
    }

    @Test
    void positiveEventModifierIncreasesPrice() {
        PriceModel model = new PriceModel();
        Good good = iron();
        good.setCurrentPrice(40.0);
        good.addSupplyPressure(0); // no pressure
        // Run many times — with a +0.35 event the average should be above base
        double sum = 0;
        for (int i = 0; i < 100; i++) {
            good.setCurrentPrice(40.0);
            sum += model.computeNewPrice(good, 0.35);
        }
        assertThat(sum / 100).isGreaterThan(40.0);
    }

    @Test
    void negativeEventModifierDecreasesPrice() {
        PriceModel model = new PriceModel();
        Good good = iron();
        double sum = 0;
        for (int i = 0; i < 100; i++) {
            good.setCurrentPrice(40.0);
            sum += model.computeNewPrice(good, -0.30);
        }
        assertThat(sum / 100).isLessThan(40.0);
    }

    @Test
    void supplyPressureNudgesPriceUp() {
        PriceModel model = new PriceModel();
        Good good = iron();
        good.setCurrentPrice(40.0);
        good.addSupplyPressure(10.0); // strong buy pressure
        double newPrice = model.computeNewPrice(good, 0.0);
        // supply pressure alone (10 * 0.01 = 10% nudge) should reliably push above 40
        assertThat(newPrice).isGreaterThan(40.0);
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./mvnw test -pl . -Dtest=PriceModelTest
```

Expected: FAIL.

- [ ] **Step 3: Implement `PriceModel`**

```java
// src/main/java/com/medievalmarket/service/PriceModel.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.Good.Volatility;
import org.springframework.stereotype.Component;
import java.util.Random;

@Component
public class PriceModel {

    private final Random random = new Random();

    /**
     * Computes a new price for the given good.
     * @param good          the good to reprice (reads currentPrice and supplyPressure)
     * @param eventModifier signed float in [-1, 1]; 0.0 = no event this tick
     * @return new price, clamped to [basePrice*0.10, basePrice*5.00]
     */
    public double computeNewPrice(Good good, double eventModifier) {
        double price = good.getCurrentPrice();

        // 1. Random drift
        double maxDrift = 0.05 * volatilityMultiplier(good.getVolatility());
        double drift = random.nextDouble() * maxDrift * (random.nextBoolean() ? 1 : -1);
        price *= (1 + drift);

        // 2. Supply/demand pressure
        price *= (1 + good.getSupplyPressure() * 0.01);

        // 3. Event modifier
        if (eventModifier != 0.0) {
            price *= (1 + eventModifier);
        }

        // 4. Clamp
        double floor = good.getBasePrice() * 0.10;
        double ceiling = good.getBasePrice() * 5.00;
        return Math.max(floor, Math.min(ceiling, price));
    }

    private double volatilityMultiplier(Volatility v) {
        return switch (v) {
            case LOW -> 0.5;
            case MEDIUM -> 1.0;
            case HIGH -> 1.5;
        };
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=PriceModelTest
```

Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/PriceModel.java \
        src/test/java/com/medievalmarket/service/PriceModelTest.java
git commit -m "feat: price model with drift, supply/demand, event modifier"
```

---

## Task 7: Event Engine

**Files:**
- Create: `src/main/java/com/medievalmarket/service/EventEngine.java`
- Create: `src/test/java/com/medievalmarket/service/EventEngineTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/medievalmarket/service/EventEngineTest.java
package com.medievalmarket.service;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventEngineTest {

    @Test
    void maybeFireEventReturnsNullMostOfTheTime() {
        EventEngine engine = new EventEngine();
        long nullCount = java.util.stream.IntStream.range(0, 1000)
            .mapToObj(i -> engine.maybeFireEvent())
            .filter(e -> e == null)
            .count();
        // ~90% chance of null per tick; over 1000 ticks expect at least 800 nulls
        assertThat(nullCount).isGreaterThan(800);
    }

    @Test
    void firedEventHasNonNullMessageAndModifiers() {
        EventEngine engine = new EventEngine();
        // Force fire by running many iterations
        EventEngine.FiredEvent event = null;
        for (int i = 0; i < 200 && event == null; i++) {
            event = engine.maybeFireEvent();
        }
        assertThat(event).isNotNull();
        assertThat(event.message()).isNotBlank();
        assertThat(event.modifiers()).isNotEmpty();
    }

    @Test
    void firedEventModifiersAreWithinExpectedRange() {
        EventEngine engine = new EventEngine();
        for (int attempt = 0; attempt < 500; attempt++) {
            EventEngine.FiredEvent event = engine.maybeFireEvent();
            if (event != null) {
                event.modifiers().values().forEach(modifier ->
                    assertThat(Math.abs(modifier)).isLessThanOrEqualTo(0.45)
                );
                return; // found one, test passes
            }
        }
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./mvnw test -pl . -Dtest=EventEngineTest
```

Expected: FAIL.

- [ ] **Step 3: Implement `EventEngine`**

```java
// src/main/java/com/medievalmarket/service/EventEngine.java
package com.medievalmarket.service;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class EventEngine {

    public record FiredEvent(String message, Map<String, Double> modifiers) {}

    private record EventDef(String message, Map<String, double[]> categoryRanges) {}

    private static final List<EventDef> EVENTS = List.of(
        new EventDef("Iron vein discovered! Mining prices collapse.",
            Map.of("Iron", new double[]{-0.35, -0.20},
                   "Coal", new double[]{-0.35, -0.20},
                   "Stone", new double[]{-0.35, -0.20})),
        new EventDef("Bad harvest! Agricultural prices surge.",
            Map.of("Grain", new double[]{0.25, 0.40},
                   "Livestock", new double[]{0.25, 0.40})),
        new EventDef("Spice ship arrives! Spice prices drop.",
            Map.of("Spices", new double[]{-0.25, -0.15})),
        new EventDef("War declared! Weapons and lumber in demand.",
            Map.of("Iron", new double[]{0.30, 0.40},
                   "Coal", new double[]{0.30, 0.40},
                   "Timber", new double[]{0.30, 0.40},
                   "Rope", new double[]{0.30, 0.40})),
        new EventDef("Gem smugglers caught! Gem prices rise.",
            Map.of("Gems", new double[]{0.15, 0.25})),
        new EventDef("Wool shortage grips the kingdom!",
            Map.of("Wool", new double[]{0.20, 0.30},
                   "Cloth", new double[]{0.20, 0.30}))
    );

    // The Plague event affects all goods — handle separately
    private static final List<String> ALL_GOODS = List.of(
        "Grain","Wool","Livestock","Ale","Spices",
        "Iron","Coal","Stone","Gems","Salt",
        "Timber","Rope","Cloth","Leather","Candles"
    );
    private static final Set<String> PLAGUE_PRIMARY = Set.of("Livestock", "Grain");

    private final Random random = new Random();

    /**
     * ~10% chance per call to fire a random market event.
     * Returns null if no event fires this tick.
     */
    public FiredEvent maybeFireEvent() {
        if (random.nextDouble() > 0.10) return null;

        // 1-in-7 chance for Plague (roughly equal weight with the other 6 events)
        if (random.nextInt(7) == 0) {
            return buildPlagueEvent();
        }

        EventDef def = EVENTS.get(random.nextInt(EVENTS.size()));
        Map<String, Double> modifiers = new HashMap<>();
        def.categoryRanges().forEach((good, range) -> {
            double min = range[0], max = range[1];
            modifiers.put(good, min + random.nextDouble() * (max - min));
        });
        return new FiredEvent(def.message(), modifiers);
    }

    private FiredEvent buildPlagueEvent() {
        Map<String, Double> modifiers = new HashMap<>();
        for (String good : ALL_GOODS) {
            if (PLAGUE_PRIMARY.contains(good)) {
                modifiers.put(good, 0.15 + random.nextDouble() * 0.10); // +15 to +25%
            } else {
                modifiers.put(good, -0.10 + random.nextDouble() * 0.20); // -10% to +10%
            }
        }
        return new FiredEvent("Plague outbreak! Fear grips the marketplace.", modifiers);
    }
}

```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=EventEngineTest
```

Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/EventEngine.java \
        src/test/java/com/medievalmarket/service/EventEngineTest.java
git commit -m "feat: event engine with 7 market events including Plague"
```

---

## Task 8: Session Registry

**Files:**
- Create: `src/main/java/com/medievalmarket/service/SessionRegistry.java`
- Create: `src/test/java/com/medievalmarket/service/SessionRegistryTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/medievalmarket/service/SessionRegistryTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class SessionRegistryTest {

    private SessionRegistry registry() {
        return new SessionRegistry(new NameGenerator());
    }

    @Test
    void createSessionReturnsPortfolioWithCorrectStartGold() {
        SessionRegistry registry = registry();
        Portfolio p = registry.createSession(PlayerClass.NOBLE);
        assertThat(p.getGold()).isEqualTo(1000.0);
    }

    @Test
    void createdSessionHasNonBlankName() {
        SessionRegistry registry = registry();
        Portfolio p = registry.createSession(PlayerClass.MERCHANT);
        assertThat(p.getPlayerName()).isNotBlank();
    }

    @Test
    void findByIdReturnsCreatedSession() {
        SessionRegistry registry = registry();
        Portfolio created = registry.createSession(PlayerClass.MINER);
        Optional<Portfolio> found = registry.findById(created.getSessionId());
        assertThat(found).isPresent();
        assertThat(found.get().getSessionId()).isEqualTo(created.getSessionId());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        SessionRegistry registry = registry();
        assertThat(registry.findById("nonexistent")).isEmpty();
    }

    @Test
    void getAllActiveSessionsReturnsAllCreated() {
        SessionRegistry registry = registry();
        registry.createSession(PlayerClass.MERCHANT);
        registry.createSession(PlayerClass.NOBLE);
        assertThat(registry.getAllActiveSessions()).hasSize(2);
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./mvnw test -pl . -Dtest=SessionRegistryTest
```

Expected: FAIL.

- [ ] **Step 3: Implement `SessionRegistry`**

```java
// src/main/java/com/medievalmarket/service/SessionRegistry.java
package com.medievalmarket.service;

import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    private static final Duration EXPIRY = Duration.ofHours(2);
    private final ConcurrentHashMap<String, Portfolio> sessions = new ConcurrentHashMap<>();
    private final NameGenerator nameGenerator;

    public SessionRegistry(NameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    public Portfolio createSession(PlayerClass playerClass) {
        String sessionId = UUID.randomUUID().toString();
        String name = nameGenerator.generate();
        Portfolio portfolio = new Portfolio(sessionId, name, playerClass);
        sessions.put(sessionId, portfolio);
        return portfolio;
    }

    public Optional<Portfolio> findById(String sessionId) {
        Portfolio p = sessions.get(sessionId);
        if (p == null) return Optional.empty();
        if (isExpired(p)) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public Collection<Portfolio> getAllActiveSessions() {
        sessions.entrySet().removeIf(e -> isExpired(e.getValue()));
        return sessions.values();
    }

    private boolean isExpired(Portfolio p) {
        return Duration.between(p.getLastTradeTime(), Instant.now()).compareTo(EXPIRY) > 0;
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=SessionRegistryTest
```

Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/SessionRegistry.java \
        src/test/java/com/medievalmarket/service/SessionRegistryTest.java
git commit -m "feat: session registry with 2-hour expiry"
```

---

## Task 9: Trade Service

**Files:**
- Create: `src/main/java/com/medievalmarket/service/TradeService.java`
- Create: `src/test/java/com/medievalmarket/service/TradeServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/medievalmarket/service/TradeServiceTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.Good.Volatility;
import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TradeServiceTest {

    private TradeService service;
    private Good iron;

    @BeforeEach
    void setUp() {
        GoodsCatalogue catalogue = new GoodsCatalogue();
        service = new TradeService(catalogue);
        iron = catalogue.findByName("Iron");
        iron.setCurrentPrice(40.0);
    }

    private Portfolio merchant() {
        return new Portfolio("s1", "Test", PlayerClass.MERCHANT);
    }

    private Portfolio miner() {
        return new Portfolio("s2", "Test", PlayerClass.MINER);
    }

    private Portfolio noble() {
        return new Portfolio("s3", "Test", PlayerClass.NOBLE);
    }

    @Test
    void buyDeductsGoldWithFee() {
        Portfolio p = noble(); // 1000g start
        service.buy(p, "Iron", 1);
        // Iron at 40g + 3% fee = 41.2g
        assertThat(p.getGold()).isCloseTo(1000.0 - 41.2, within(0.01));
    }

    @Test
    void merchantBuyHasNoFee() {
        Portfolio p = merchant(); // 500g start
        service.buy(p, "Iron", 1);
        // No fee: just 40g
        assertThat(p.getGold()).isCloseTo(500.0 - 40.0, within(0.01));
    }

    @Test
    void buyAddsToHoldings() {
        Portfolio p = noble();
        service.buy(p, "Iron", 3);
        assertThat(p.getHolding("Iron")).isEqualTo(3);
    }

    @Test
    void sellDeductsHoldingsAndAddGoldWithFee() {
        Portfolio p = noble();
        service.buy(p, "Iron", 2);
        double goldAfterBuy = p.getGold();
        service.sell(p, "Iron", 1);
        // Sell 1 Iron at 40g with 3% fee = 38.8g
        assertThat(p.getGold()).isCloseTo(goldAfterBuy + 38.8, within(0.01));
        assertThat(p.getHolding("Iron")).isEqualTo(1);
    }

    @Test
    void minerSellBonusAppliedAfterFee() {
        Portfolio p = miner(); // 350g start
        p.setGold(1000.0); // give them enough to buy
        service.buy(p, "Iron", 1);
        double goldAfterBuy = p.getGold();
        service.sell(p, "Iron", 1);
        // fee first: 40 * 0.97 = 38.8, then bonus: 38.8 * 1.02 = 39.576
        assertThat(p.getGold()).isCloseTo(goldAfterBuy + 39.576, within(0.01));
    }

    @Test
    void buyThrowsWhenInsufficientFunds() {
        Portfolio p = new Portfolio("s", "Test", PlayerClass.MINER); // 350g
        assertThatThrownBy(() -> service.buy(p, "Gems", 10)) // Gems at 120g * 10 * 1.03 = way over
            .hasMessageContaining("INSUFFICIENT_FUNDS");
    }

    @Test
    void sellThrowsWhenInsufficientHoldings() {
        Portfolio p = noble();
        assertThatThrownBy(() -> service.sell(p, "Iron", 1))
            .hasMessageContaining("INSUFFICIENT_HOLDINGS");
    }

    @Test
    void quantityZeroThrows() {
        Portfolio p = noble();
        assertThatThrownBy(() -> service.buy(p, "Iron", 0))
            .hasMessageContaining("INVALID_QUANTITY");
    }

    @Test
    void quantityElevenThrows() {
        Portfolio p = noble();
        assertThatThrownBy(() -> service.buy(p, "Iron", 11))
            .hasMessageContaining("INVALID_QUANTITY");
    }

    @Test
    void buyAddsBuyPressure() {
        Portfolio p = noble();
        service.buy(p, "Iron", 4);
        // pressure = +4 * 0.5 = +2.0
        assertThat(iron.getSupplyPressure()).isCloseTo(2.0, within(0.001));
    }

    @Test
    void sellAddsSellPressure() {
        Portfolio p = noble();
        service.buy(p, "Iron", 4);
        iron.setCurrentPrice(40.0);
        iron.addSupplyPressure(-iron.getSupplyPressure()); // reset
        service.sell(p, "Iron", 2);
        assertThat(iron.getSupplyPressure()).isCloseTo(-1.0, within(0.001));
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./mvnw test -pl . -Dtest=TradeServiceTest
```

Expected: FAIL.

- [ ] **Step 3: Implement `TradeService`**

```java
// src/main/java/com/medievalmarket/service/TradeService.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;

@Component
public class TradeService {

    private final GoodsCatalogue catalogue;

    public TradeService(GoodsCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    public void buy(Portfolio portfolio, String goodName, int quantity) {
        validateQuantity(quantity);
        Good good = catalogue.findByName(goodName);
        double feeRate = portfolio.getPlayerClass().getFeeRate();
        double cost = good.getCurrentPrice() * quantity * (1 + feeRate);

        synchronized (portfolio) {
            if (portfolio.getGold() < cost) throw new TradeException("INSUFFICIENT_FUNDS");
            portfolio.setGold(portfolio.getGold() - cost);
            portfolio.setHolding(goodName, portfolio.getHolding(goodName) + quantity);
            portfolio.touchLastTradeTime();
        }

        synchronized (good) {
            good.addSupplyPressure(quantity * 0.5);
        }
    }

    public void sell(Portfolio portfolio, String goodName, int quantity) {
        validateQuantity(quantity);
        Good good = catalogue.findByName(goodName);

        synchronized (portfolio) {
            int held = portfolio.getHolding(goodName);
            if (held < quantity) throw new TradeException("INSUFFICIENT_HOLDINGS");

            double saleValue = good.getCurrentPrice() * quantity;
            double feeRate = portfolio.getPlayerClass().getFeeRate();
            saleValue *= (1 - feeRate);

            // Miner bonus on Mining goods
            if (portfolio.getPlayerClass() == PlayerClass.MINER
                    && "Mining".equals(good.getCategory())) {
                saleValue *= 1.02;
            }

            portfolio.setGold(portfolio.getGold() + saleValue);
            portfolio.setHolding(goodName, held - quantity);
            portfolio.touchLastTradeTime();
        }

        synchronized (good) {
            good.addSupplyPressure(-quantity * 0.5);
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1 || quantity > 10) throw new TradeException("INVALID_QUANTITY");
    }

    public static class TradeException extends RuntimeException {
        public TradeException(String message) { super(message); }
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=TradeServiceTest
```

Expected: All 11 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/TradeService.java \
        src/test/java/com/medievalmarket/service/TradeServiceTest.java
git commit -m "feat: trade service with fee, miner perk, supply pressure"
```

---

## Task 10: Scoreboard Service

**Files:**
- Create: `src/main/java/com/medievalmarket/service/ScoreboardService.java`
- Create: `src/test/java/com/medievalmarket/service/ScoreboardServiceTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/medievalmarket/service/ScoreboardServiceTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.model.ScoreboardEntry;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ScoreboardServiceTest {

    private ScoreboardService service = new ScoreboardService();

    @Test
    void rankedByNetWorthDescending() {
        Portfolio rich = new Portfolio("1", "Rich", PlayerClass.NOBLE);    // 1000g
        Portfolio poor = new Portfolio("2", "Poor",  PlayerClass.MINER);   // 350g

        List<ScoreboardEntry> board = service.compute(List.of(rich, poor), Map.of());
        assertThat(board.get(0).getName()).isEqualTo("Rich");
        assertThat(board.get(1).getName()).isEqualTo("Poor");
    }

    @Test
    void netWorthIncludesHoldingValue() {
        Portfolio p = new Portfolio("1", "Trader", PlayerClass.NOBLE); // 1000g
        p.setHolding("Iron", 2); // 2 * 40g = 80g (assuming currentPrice=40)
        Map<String, Double> prices = Map.of("Iron", 40.0);

        List<ScoreboardEntry> board = service.compute(List.of(p), prices);
        assertThat(board.get(0).getNetWorth()).isEqualTo(1080.0);
    }

    @Test
    void top3GainersMarkedAsUp() {
        List<Portfolio> portfolios = List.of(
            makePortfolioWithTrend("A", 100.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("B", 50.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("C", 30.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("D", -20.0, PlayerClass.NOBLE)
        );

        List<ScoreboardEntry> board = service.compute(portfolios, Map.of());
        long upCount = board.stream().filter(e -> "UP".equals(e.getTrending())).count();
        assertThat(upCount).isEqualTo(3);
    }

    @Test
    void top3LosersMarkedAsDown() {
        List<Portfolio> portfolios = List.of(
            makePortfolioWithTrend("A", 10.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("B", -10.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("C", -50.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("D", -100.0, PlayerClass.NOBLE)
        );

        List<ScoreboardEntry> board = service.compute(portfolios, Map.of());
        long downCount = board.stream().filter(e -> "DOWN".equals(e.getTrending())).count();
        assertThat(downCount).isEqualTo(3);
    }

    private Portfolio makePortfolioWithTrend(String name, double trendAmount, PlayerClass pc) {
        Portfolio p = new Portfolio("id-" + name, name, pc);
        double base = p.getGold();
        // seed 6 net worth values so getTrend() returns trendAmount
        for (int i = 0; i < 5; i++) p.recordNetWorth(base);
        p.recordNetWorth(base + trendAmount);
        return p;
    }
}
```

- [ ] **Step 2: Run test to confirm failure**

```bash
./mvnw test -pl . -Dtest=ScoreboardServiceTest
```

Expected: FAIL.

- [ ] **Step 3: Implement `ScoreboardService`**

```java
// src/main/java/com/medievalmarket/service/ScoreboardService.java
package com.medievalmarket.service;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.model.ScoreboardEntry;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ScoreboardService {

    /**
     * Computes the scoreboard for one tick.
     * @param portfolios  all active sessions
     * @param prices      map of good name -> current price (freshly computed this tick)
     */
    public List<ScoreboardEntry> compute(Collection<Portfolio> portfolios,
                                         Map<String, Double> prices) {
        // 1. Compute net worth and record it in the portfolio history
        record Snapshot(Portfolio portfolio, double netWorth) {}

        List<Snapshot> snapshots = portfolios.stream().map(p -> {
            double holdingsValue = p.getHoldings().entrySet().stream()
                .mapToDouble(e -> e.getValue() * prices.getOrDefault(e.getKey(), 0.0))
                .sum();
            double netWorth = p.getGold() + holdingsValue;
            p.recordNetWorth(netWorth);
            return new Snapshot(p, netWorth);
        }).collect(Collectors.toList());

        // 2. Sort by net worth descending
        snapshots.sort(Comparator.comparingDouble(Snapshot::netWorth).reversed());

        // 3. Determine top 3 gainers and top 3 losers by trend
        List<Snapshot> byTrendDesc = new ArrayList<>(snapshots);
        byTrendDesc.sort(Comparator.comparingDouble(s -> s.portfolio().getTrend()));
        // losers = first 3 (most negative trend), gainers = last 3 (most positive)
        int size = byTrendDesc.size();
        Set<String> losers = byTrendDesc.subList(0, Math.min(3, size)).stream()
            .map(s -> s.portfolio().getSessionId()).collect(Collectors.toSet());
        Set<String> gainers = byTrendDesc.subList(Math.max(0, size - 3), size).stream()
            .map(s -> s.portfolio().getSessionId()).collect(Collectors.toSet());

        // 4. Build entries
        return snapshots.stream().map(s -> {
            String trending;
            if (gainers.contains(s.portfolio().getSessionId()) && s.portfolio().getTrend() > 0) {
                trending = "UP";
            } else if (losers.contains(s.portfolio().getSessionId()) && s.portfolio().getTrend() < 0) {
                trending = "DOWN";
            } else {
                trending = "NEUTRAL";
            }
            return new ScoreboardEntry(
                s.portfolio().getPlayerName(),
                s.portfolio().getPlayerClass().name(),
                Math.round(s.netWorth() * 10.0) / 10.0,
                Math.round(s.portfolio().getTrend() * 10.0) / 10.0,
                trending
            );
        }).collect(Collectors.toList());
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest=ScoreboardServiceTest
```

Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/medievalmarket/service/ScoreboardService.java \
        src/test/java/com/medievalmarket/service/ScoreboardServiceTest.java
git commit -m "feat: scoreboard service with net worth and trend tracking"
```

---

## Task 11: DTOs

**Files:**
- Create: `src/main/java/com/medievalmarket/dto/SessionStartRequest.java`
- Create: `src/main/java/com/medievalmarket/dto/SessionStartResponse.java`
- Create: `src/main/java/com/medievalmarket/dto/TradeRequest.java`
- Create: `src/main/java/com/medievalmarket/dto/TradeResponse.java`
- Create: `src/main/java/com/medievalmarket/dto/MarketTickPayload.java`
- Create: `src/main/java/com/medievalmarket/dto/MarketSnapshotResponse.java`

No separate tests — DTOs are tested through controller tests.

- [ ] **Step 1: Create all DTOs**

```java
// src/main/java/com/medievalmarket/dto/SessionStartRequest.java
package com.medievalmarket.dto;
public record SessionStartRequest(String playerClass) {}
```

```java
// src/main/java/com/medievalmarket/dto/SessionStartResponse.java
package com.medievalmarket.dto;
import java.util.Map;
public record SessionStartResponse(
    String sessionId,
    String playerName,
    double gold,
    Map<String, Integer> holdings
) {}
```

```java
// src/main/java/com/medievalmarket/dto/TradeRequest.java
package com.medievalmarket.dto;
public record TradeRequest(String sessionId, String good, int quantity) {}
```

```java
// src/main/java/com/medievalmarket/dto/TradeResponse.java
package com.medievalmarket.dto;
import java.util.Map;
public record TradeResponse(double gold, Map<String, Integer> holdings) {}
```

```java
// src/main/java/com/medievalmarket/dto/MarketTickPayload.java
package com.medievalmarket.dto;
import com.medievalmarket.model.ScoreboardEntry;
import java.util.List;
import java.util.Map;
public record MarketTickPayload(
    Map<String, Double> prices,
    Map<String, List<Double>> history,
    String event,
    List<ScoreboardEntry> scoreboard
) {}
```

```java
// src/main/java/com/medievalmarket/dto/MarketSnapshotResponse.java
package com.medievalmarket.dto;
import java.util.List;
import java.util.Map;
public record MarketSnapshotResponse(
    Map<String, Double> prices,
    Map<String, List<Double>> history
) {}
```

- [ ] **Step 2: Compile to verify no errors**

```bash
./mvnw compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/medievalmarket/dto/
git commit -m "feat: request/response DTOs"
```

---

## Task 12: WebSocket Config and Market Engine

**Files:**
- Create: `src/main/java/com/medievalmarket/config/WebSocketConfig.java`
- Create: `src/main/java/com/medievalmarket/service/MarketEngine.java`

- [ ] **Step 1: Create `WebSocketConfig`**

```java
// src/main/java/com/medievalmarket/config/WebSocketConfig.java
package com.medievalmarket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }
}
```

- [ ] **Step 2: Create `MarketEngine`**

```java
// src/main/java/com/medievalmarket/service/MarketEngine.java
package com.medievalmarket.service;

import com.medievalmarket.dto.MarketTickPayload;
import com.medievalmarket.model.Good;
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

    public MarketEngine(GoodsCatalogue catalogue, PriceModel priceModel,
                        EventEngine eventEngine, PriceHistory priceHistory,
                        SessionRegistry sessionRegistry, ScoreboardService scoreboardService,
                        SimpMessagingTemplate messagingTemplate) {
        this.catalogue = catalogue;
        this.priceModel = priceModel;
        this.eventEngine = eventEngine;
        this.priceHistory = priceHistory;
        this.sessionRegistry = sessionRegistry;
        this.scoreboardService = scoreboardService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 5000)
    public void tick() {
        // 1. Maybe fire an event
        EventEngine.FiredEvent event = eventEngine.maybeFireEvent();
        Map<String, Double> eventModifiers = event != null ? event.modifiers() : Map.of();

        // 2. Reprice all goods
        Map<String, Double> prices = new LinkedHashMap<>();
        for (Good good : catalogue.getGoods()) {
            double modifier = eventModifiers.getOrDefault(good.getName(), 0.0);
            double newPrice = priceModel.computeNewPrice(good, modifier);
            good.setCurrentPrice(newPrice);
            priceHistory.append(good.getName(), newPrice);
            prices.put(good.getName(), Math.round(newPrice * 10.0) / 10.0);
        }

        // 3. Decay supply pressure
        catalogue.getGoods().forEach(Good::decaySupplyPressure);

        // 4. Compute scoreboard
        List<ScoreboardEntry> scoreboard = scoreboardService.compute(
            sessionRegistry.getAllActiveSessions(), prices);

        // 5. Broadcast
        MarketTickPayload payload = new MarketTickPayload(
            prices,
            priceHistory.getAllHistory(),
            event != null ? event.message() : null,
            scoreboard
        );
        messagingTemplate.convertAndSend("/topic/market", payload);
    }
}
```

- [ ] **Step 3: Start the application and verify it ticks**

```bash
./mvnw spring-boot:run
```

Expected: Server starts with no errors. To confirm ticking is active, open `http://localhost:8080/api/market/snapshot` in a browser — refresh it twice ~5 seconds apart and verify that prices have changed between requests.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/medievalmarket/config/WebSocketConfig.java \
        src/main/java/com/medievalmarket/service/MarketEngine.java
git commit -m "feat: websocket config and market engine tick"
```

---

## Task 13: REST Controllers

**Files:**
- Create: `src/main/java/com/medievalmarket/controller/SessionController.java`
- Create: `src/main/java/com/medievalmarket/controller/TradeController.java`
- Create: `src/main/java/com/medievalmarket/controller/MarketController.java`
- Create: `src/test/java/com/medievalmarket/controller/SessionControllerTest.java`
- Create: `src/test/java/com/medievalmarket/controller/TradeControllerTest.java`

- [ ] **Step 1: Write failing controller tests**

```java
// src/test/java/com/medievalmarket/controller/SessionControllerTest.java
package com.medievalmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medievalmarket.dto.SessionStartRequest;
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
class SessionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void startSessionReturns200WithSessionId() throws Exception {
        mvc.perform(post("/api/session/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SessionStartRequest("NOBLE"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sessionId").isNotEmpty())
           .andExpect(jsonPath("$.gold").value(1000.0));
    }

    @Test
    void startSessionWithInvalidClassReturns400() throws Exception {
        mvc.perform(post("/api/session/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerClass\":\"WIZARD\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void resumeExistingSessionReturns200() throws Exception {
        MvcResult result = mvc.perform(post("/api/session/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SessionStartRequest("MERCHANT"))))
            .andReturn();
        String body = result.getResponse().getContentAsString();
        String sessionId = mapper.readTree(body).get("sessionId").asText();

        mvc.perform(get("/api/session/" + sessionId))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.gold").value(500.0));
    }

    @Test
    void resumeUnknownSessionReturns404() throws Exception {
        mvc.perform(get("/api/session/nonexistent-id"))
           .andExpect(status().isNotFound());
    }
}
```

```java
// src/test/java/com/medievalmarket/controller/TradeControllerTest.java
package com.medievalmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medievalmarket.dto.SessionStartRequest;
import com.medievalmarket.dto.TradeRequest;
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
class TradeControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String sessionId;

    @BeforeEach
    void createSession() throws Exception {
        MvcResult result = mvc.perform(post("/api/session/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SessionStartRequest("NOBLE"))))
            .andReturn();
        sessionId = mapper.readTree(result.getResponse().getContentAsString())
            .get("sessionId").asText();
    }

    @Test
    void buyReturns200AndUpdatesGold() throws Exception {
        mvc.perform(post("/api/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new TradeRequest(sessionId, "Grain", 1))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.holdings.Grain").value(1));
    }

    @Test
    void buyWithInsufficientFundsReturns400() throws Exception {
        mvc.perform(post("/api/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new TradeRequest(sessionId, "Gems", 10))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void sellWithNoHoldingsReturns400() throws Exception {
        mvc.perform(post("/api/trade/sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new TradeRequest(sessionId, "Iron", 1))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("INSUFFICIENT_HOLDINGS"));
    }

    @Test
    void invalidSessionIdFormatReturns400() throws Exception {
        // Non-UUID string → INVALID_SESSION_ID (not SESSION_NOT_FOUND)
        mvc.perform(post("/api/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new TradeRequest("not-a-uuid", "Grain", 1))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("INVALID_SESSION_ID"));
    }

    @Test
    void unknownValidUuidReturns404() throws Exception {
        String validUuid = java.util.UUID.randomUUID().toString(); // valid UUID, unknown session
        mvc.perform(post("/api/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new TradeRequest(validUuid, "Grain", 1))))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
    }
}
```

```java
// src/test/java/com/medievalmarket/controller/MarketControllerTest.java
package com.medievalmarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MarketControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void snapshotReturns200WithPricesAndHistory() throws Exception {
        mvc.perform(get("/api/market/snapshot"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.prices").isMap())
           .andExpect(jsonPath("$.history").isMap())
           .andExpect(jsonPath("$.prices.Grain").isNumber())
           .andExpect(jsonPath("$.history.Grain").isArray());
    }

    @Test
    void snapshotContainsAll15Goods() throws Exception {
        mvc.perform(get("/api/market/snapshot"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.prices.Iron").isNumber())
           .andExpect(jsonPath("$.prices.Spices").isNumber())
           .andExpect(jsonPath("$.prices.Candles").isNumber());
    }
}
```

- [ ] **Step 2: Run tests to confirm failure**

```bash
./mvnw test -pl . -Dtest="SessionControllerTest,TradeControllerTest,MarketControllerTest"
```

Expected: FAIL — controllers do not exist yet.

- [ ] **Step 3: Implement controllers**

```java
// src/main/java/com/medievalmarket/controller/SessionController.java
package com.medievalmarket.controller;

import com.medievalmarket.dto.SessionStartRequest;
import com.medievalmarket.dto.SessionStartResponse;
import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionRegistry registry;

    public SessionController(SessionRegistry registry) { this.registry = registry; }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody SessionStartRequest request) {
        PlayerClass playerClass;
        try {
            playerClass = PlayerClass.valueOf(request.playerClass().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_CLASS"));
        }
        Portfolio p = registry.createSession(playerClass);
        return ResponseEntity.ok(new SessionStartResponse(
            p.getSessionId(), p.getPlayerName(), p.getGold(), p.getHoldings()));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<?> resume(@PathVariable String sessionId) {
        return registry.findById(sessionId)
            .map(p -> ResponseEntity.ok(new SessionStartResponse(
                p.getSessionId(), p.getPlayerName(), p.getGold(), p.getHoldings())))
            .orElse(ResponseEntity.notFound().build());
    }
}
```

```java
// src/main/java/com/medievalmarket/controller/TradeController.java
package com.medievalmarket.controller;

import com.medievalmarket.dto.TradeRequest;
import com.medievalmarket.dto.TradeResponse;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.SessionRegistry;
import com.medievalmarket.service.TradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/trade")
public class TradeController {

    private final SessionRegistry registry;
    private final TradeService tradeService;

    public TradeController(SessionRegistry registry, TradeService tradeService) {
        this.registry = registry;
        this.tradeService = tradeService;
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buy(@RequestBody TradeRequest request) {
        return execute(request, true);
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sell(@RequestBody TradeRequest request) {
        return execute(request, false);
    }

    private static final java.util.regex.Pattern UUID_PATTERN =
        java.util.regex.Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private ResponseEntity<?> execute(TradeRequest request, boolean isBuy) {
        String sid = request.sessionId();
        if (sid == null || sid.isBlank() || !UUID_PATTERN.matcher(sid).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION_ID"));
        }
        Portfolio portfolio = registry.findById(request.sessionId()).orElse(null);
        if (portfolio == null) {
            return ResponseEntity.status(404).body(Map.of("error", "SESSION_NOT_FOUND"));
        }
        try {
            if (isBuy) tradeService.buy(portfolio, request.good(), request.quantity());
            else tradeService.sell(portfolio, request.good(), request.quantity());
        } catch (TradeService.TradeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "UNKNOWN_GOOD"));
        }
        return ResponseEntity.ok(new TradeResponse(portfolio.getGold(), portfolio.getHoldings()));
    }
}
```

```java
// src/main/java/com/medievalmarket/controller/MarketController.java
package com.medievalmarket.controller;

import com.medievalmarket.dto.MarketSnapshotResponse;
import com.medievalmarket.model.Good;
import com.medievalmarket.service.GoodsCatalogue;
import com.medievalmarket.service.PriceHistory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final GoodsCatalogue catalogue;
    private final PriceHistory priceHistory;

    public MarketController(GoodsCatalogue catalogue, PriceHistory priceHistory) {
        this.catalogue = catalogue;
        this.priceHistory = priceHistory;
    }

    @GetMapping("/snapshot")
    public MarketSnapshotResponse snapshot() {
        Map<String, Double> prices = catalogue.getGoods().stream()
            .collect(Collectors.toMap(Good::getName,
                g -> Math.round(g.getCurrentPrice() * 10.0) / 10.0));
        return new MarketSnapshotResponse(prices, priceHistory.getAllHistory());
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -pl . -Dtest="SessionControllerTest,TradeControllerTest,MarketControllerTest"
```

Expected: All tests PASS.

- [ ] **Step 5: Run all tests**

```bash
./mvnw test
```

Expected: All tests PASS. Fix any issues before committing.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/medievalmarket/controller/ \
        src/test/java/com/medievalmarket/controller/
git commit -m "feat: REST controllers for session, trade, and market snapshot"
```

---

## Task 14: Vue.js Frontend

**Files:**
- Create: `src/main/resources/static/index.html`

This is a single-file Vue 3 app served by Spring Boot's static file handler. No build step required. All JS/CSS is CDN-loaded.

- [ ] **Step 1: Create `index.html`**

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>The Grand Bazaar</title>
  <script src="https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js"></script>
  <script src="https://unpkg.com/vue@3/dist/vue.global.prod.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/@stomp/stompjs@7/bundles/stomp.umd.min.js"></script>
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
  <style>
    /* ── Reset & base ──────────────────────────────────── */
    *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: 'Segoe UI', system-ui, sans-serif; background: #0f0f1a; color: #e2e2f0; min-height: 100vh; }
    button { cursor: pointer; border: none; border-radius: 6px; padding: 4px 10px; font-size: .8rem; }
    input[type=number] { background: #1e1e2e; border: 1px solid #444; color: #e2e2f0; border-radius: 6px; padding: 3px 6px; width: 52px; text-align: center; }

    /* ── Layout ────────────────────────────────────────── */
    #app { display: flex; flex-direction: column; min-height: 100vh; }

    /* Event ticker */
    .ticker { background: #1a1a2e; border-bottom: 1px solid #2a2a4e; padding: .5rem 1rem; font-size: .8rem; color: #a78bfa; white-space: nowrap; overflow: hidden; }
    .ticker span { margin-right: 3rem; }

    /* Reconnecting banner */
    .reconnect-banner { background: #7f1d1d; color: #fca5a5; padding: .4rem 1rem; text-align: center; font-size: .85rem; }

    /* Main split */
    .main { display: flex; flex: 1; gap: 0; }
    .market-panel { flex: 7; padding: 1rem; overflow-y: auto; }
    .side-panel { flex: 3; border-left: 1px solid #2a2a4e; display: flex; flex-direction: column; }

    /* Market table */
    h2.section-title { color: #a78bfa; font-size: .75rem; letter-spacing: .1em; text-transform: uppercase; margin-bottom: .6rem; }
    table { width: 100%; border-collapse: collapse; font-size: .85rem; }
    th { color: #888; font-weight: 500; text-align: left; padding: .4rem .6rem; border-bottom: 1px solid #2a2a4e; font-size: .75rem; text-transform: uppercase; }
    td { padding: .5rem .6rem; border-bottom: 1px solid #1e1e2e; vertical-align: middle; }
    tr:hover td { background: #1a1a2e; }
    .price-up { color: #4ade80; }
    .price-down { color: #f87171; }
    .price-neutral { color: #94a3b8; }
    .cat-badge { font-size: .65rem; background: #2a2a4e; padding: 2px 6px; border-radius: 10px; color: #a78bfa; }
    .chart-cell { width: 120px; }
    canvas.sparkline { width: 120px !important; height: 36px !important; }

    /* Portfolio */
    .portfolio { padding: 1rem; border-bottom: 1px solid #2a2a4e; }
    .portfolio h2 { margin-bottom: .6rem; }
    .gold-display { font-size: 1.4rem; font-weight: bold; color: #fbbf24; margin-bottom: .5rem; }
    .holding-row { display: flex; justify-content: space-between; font-size: .82rem; padding: .2rem 0; color: #cbd5e1; }
    .net-worth { margin-top: .6rem; padding-top: .6rem; border-top: 1px solid #2a2a4e; font-size: .9rem; }
    .net-worth strong { color: #4ade80; }

    /* Scoreboard */
    .scoreboard { padding: 1rem; flex: 1; overflow-y: auto; }
    .scoreboard h2 { margin-bottom: .6rem; }
    .sb-row { display: flex; align-items: center; gap: .5rem; font-size: .78rem; padding: .3rem .2rem; border-bottom: 1px solid #1e1e2e; }
    .sb-row.me { font-weight: bold; background: #1e1e2e; border-radius: 4px; }
    .sb-rank { color: #888; width: 18px; text-align: right; flex-shrink: 0; }
    .sb-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .sb-worth { flex-shrink: 0; color: #e2e2f0; }
    .sb-trend { flex-shrink: 0; width: 28px; text-align: right; }
    .trending-up { color: #4ade80; }
    .trending-down { color: #f87171; }

    /* Class select */
    .class-select { display: flex; flex-direction: column; align-items: center; justify-content: center; min-height: 100vh; gap: 2rem; padding: 2rem; }
    .class-select h1 { color: #a78bfa; font-size: 1.6rem; }
    .class-cards { display: flex; gap: 1.5rem; flex-wrap: wrap; justify-content: center; }
    .class-card { background: #1e1e2e; border: 2px solid #333; border-radius: 12px; padding: 1.5rem; width: 220px; text-align: center; cursor: pointer; transition: border-color .2s; }
    .class-card:hover { border-color: #a78bfa; }
    .class-card .icon { font-size: 2.5rem; margin-bottom: .5rem; }
    .class-card h3 { margin-bottom: .3rem; }
    .class-card .difficulty { font-size: .75rem; margin-bottom: .8rem; }
    .class-card .perk { background: #2a2a3e; border-radius: 6px; padding: .5rem; font-size: .78rem; color: #4ade80; }
    .class-card .gold { font-size: .85rem; color: #fbbf24; margin-bottom: .5rem; }
    .diff-easy { color: #4ade80; }
    .diff-medium { color: #fbbf24; }
    .diff-hard { color: #f87171; }
  </style>
</head>
<body>
<div id="app">
  <!-- ── Class Selection ─────────────────────────────── -->
  <div v-if="!session.id" class="class-select">
    <h1>⚔️ The Grand Bazaar</h1>
    <p style="color:#888;font-size:.9rem">The year is 1348. The marketplace stirs. Choose your path.</p>
    <div class="class-cards">
      <div class="class-card" @click="startSession('MERCHANT')">
        <div class="icon">🧑‍💼</div>
        <h3>The Merchant</h3>
        <div class="difficulty diff-medium">★★☆ Intermediate</div>
        <div class="gold">💰 500 starting gold</div>
        <div class="perk">No trade fee on all transactions</div>
      </div>
      <div class="class-card" @click="startSession('MINER')">
        <div class="icon">⛏️</div>
        <h3>The Miner</h3>
        <div class="difficulty diff-hard">★★★ Hard</div>
        <div class="gold">💰 350 starting gold</div>
        <div class="perk">+2% sell price on mining goods</div>
      </div>
      <div class="class-card" @click="startSession('NOBLE')">
        <div class="icon">👑</div>
        <h3>The Noble</h3>
        <div class="difficulty diff-easy">★☆☆ Easy</div>
        <div class="gold">💰 1,000 starting gold</div>
        <div class="perk">Wealth is the only perk needed</div>
      </div>
    </div>
  </div>

  <!-- ── Main Trading View ───────────────────────────── -->
  <template v-else>
    <div v-if="reconnecting" class="reconnect-banner">⚠️ Connection lost — reconnecting…</div>
    <div class="ticker">
      <span v-for="(evt, i) in events.slice(0, 5)" :key="i">📯 {{ evt }}</span>
      <span v-if="events.length === 0" style="color:#555">Awaiting market news…</span>
    </div>
    <div class="main">
      <!-- Market table -->
      <div class="market-panel">
        <h2 class="section-title">Market — {{ goods.length }} goods</h2>
        <table>
          <thead>
            <tr>
              <th>Good</th>
              <th>Category</th>
              <th>Price</th>
              <th class="chart-cell">Trend</th>
              <th>Qty</th>
              <th>Trade</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="good in goods" :key="good.name">
              <td>{{ good.name }}</td>
              <td><span class="cat-badge">{{ good.category }}</span></td>
              <td :class="priceClass(good.name)">{{ fmt(prices[good.name]) }}g {{ priceArrow(good.name) }}</td>
              <td class="chart-cell"><canvas :ref="el => registerCanvas(good.name, el)" class="sparkline"></canvas></td>
              <td><input type="number" v-model.number="quantities[good.name]" min="1" max="10" /></td>
              <td style="display:flex;gap:6px">
                <button style="background:#a78bfa;color:#0f0f1a" @click="trade('buy', good.name)">Buy</button>
                <button style="background:#374151;color:#e2e2f0" @click="trade('sell', good.name)">Sell</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Side panel -->
      <div class="side-panel">
        <!-- Portfolio -->
        <div class="portfolio">
          <h2 class="section-title">{{ session.name }}</h2>
          <div class="gold-display">{{ fmt(session.gold) }} gold</div>
          <div v-for="(qty, name) in session.holdings" :key="name" class="holding-row">
            <span>{{ name }} × {{ qty }}</span>
            <span>{{ fmt((prices[name] || 0) * qty) }}g</span>
          </div>
          <div v-if="Object.keys(session.holdings).length === 0" style="color:#555;font-size:.8rem">No holdings</div>
          <div class="net-worth">Net worth: <strong>{{ fmt(netWorth) }}g</strong></div>
        </div>

        <!-- Scoreboard -->
        <div class="scoreboard">
          <h2 class="section-title">Scoreboard</h2>
          <div v-for="(entry, i) in scoreboard" :key="entry.name"
               :class="['sb-row', entry.name === session.name ? 'me' : '']">
            <span class="sb-rank">#{{ i + 1 }}</span>
            <span class="sb-name">{{ entry.name }}</span>
            <span class="sb-worth">{{ fmt(entry.netWorth) }}g</span>
            <span class="sb-trend" :class="trendClass(entry.trending)">
              {{ trendIcon(entry.trending) }} {{ entry.trending !== 'NEUTRAL' ? fmt(Math.abs(entry.trend)) : '' }}
            </span>
          </div>
          <div v-if="scoreboard.length === 0" style="color:#555;font-size:.8rem">Waiting for players…</div>
        </div>
      </div>
    </div>
  </template>
</div>

<script>
const { createApp, ref, reactive, computed, onMounted, nextTick } = Vue;

createApp({
  setup() {
    // ── State ──────────────────────────────────────────────────
    const session = reactive({ id: null, name: '', gold: 0, holdings: {} });
    const prices = reactive({});
    const prevPrices = reactive({});
    const goods = ref([]);
    const events = ref([]);
    const scoreboard = ref([]);
    const reconnecting = ref(false);
    const quantities = reactive({});

    // goods meta (name + category) — loaded from snapshot
    const goodsMeta = ref([]);

    // Chart.js instances keyed by good name
    const charts = {};
    const canvasRefs = {};

    // ── Helpers ────────────────────────────────────────────────
    const fmt = v => v == null ? '0.00' : Number(v).toFixed(2);

    const netWorth = computed(() => {
      let total = session.gold;
      for (const [name, qty] of Object.entries(session.holdings)) {
        total += (prices[name] || 0) * qty;
      }
      return total;
    });

    const priceClass = name => {
      const curr = prices[name], prev = prevPrices[name];
      if (prev == null) return 'price-neutral';
      return curr > prev ? 'price-up' : curr < prev ? 'price-down' : 'price-neutral';
    };

    const priceArrow = name => {
      const curr = prices[name], prev = prevPrices[name];
      if (prev == null) return '→';
      return curr > prev ? '↑' : curr < prev ? '↓' : '→';
    };

    const trendClass = t => t === 'UP' ? 'trending-up' : t === 'DOWN' ? 'trending-down' : '';
    const trendIcon = t => t === 'UP' ? '▲' : t === 'DOWN' ? '▼' : '';

    // ── Canvas / Chart management ──────────────────────────────
    const registerCanvas = (name, el) => { if (el) canvasRefs[name] = el; };

    const updateChart = (name, history) => {
      if (!canvasRefs[name]) return;
      const data = history.slice(-30);
      if (charts[name]) {
        charts[name].data.labels = data.map(() => '');
        charts[name].data.datasets[0].data = data;
        charts[name].update('none');
        return;
      }
      const color = data[data.length - 1] > data[0] ? '#4ade80' : '#f87171';
      charts[name] = new Chart(canvasRefs[name], {
        type: 'line',
        data: { labels: data.map(() => ''), datasets: [{ data, borderColor: color, borderWidth: 1.5, pointRadius: 0, fill: false }] },
        options: { animation: false, plugins: { legend: { display: false }, tooltip: { enabled: false } },
          scales: { x: { display: false }, y: { display: false } }, responsive: false }
      });
    };

    // ── Market tick handler ────────────────────────────────────
    const applyTick = (payload) => {
      // Snapshot prev prices
      for (const [k, v] of Object.entries(prices)) prevPrices[k] = v;
      // Update prices
      for (const [k, v] of Object.entries(payload.prices)) prices[k] = v;
      // Update charts
      if (payload.history) {
        nextTick(() => {
          for (const [name, hist] of Object.entries(payload.history)) updateChart(name, hist);
        });
      }
      // Events
      if (payload.event) {
        events.value.unshift(payload.event);
        if (events.value.length > 20) events.value.pop();
      }
      // Scoreboard
      if (payload.scoreboard) scoreboard.value = payload.scoreboard;
    };

    // ── WebSocket connection ───────────────────────────────────
    let stompClient = null;
    let reconnectAttempts = 0;

    const connect = () => {
      stompClient = new StompJs.Client({
        brokerURL: `ws://${location.host}/ws/websocket`,
        webSocketFactory: () => new SockJS('/ws'),
        onConnect: () => {
          reconnecting.value = false;
          reconnectAttempts = 0;
          stompClient.subscribe('/topic/market', msg => {
            applyTick(JSON.parse(msg.body));
          });
          // Load initial snapshot
          fetch('/api/market/snapshot').then(r => r.json()).then(snap => {
            for (const [k, v] of Object.entries(snap.prices)) prices[k] = v;
            nextTick(() => {
              if (snap.history) {
                for (const [name, hist] of Object.entries(snap.history)) updateChart(name, hist);
              }
            });
          });
        },
        onDisconnect: () => {
          if (reconnectAttempts < 5) {
            reconnecting.value = true;
            reconnectAttempts++;
            setTimeout(connect, 3000);
          }
        }
      });
      stompClient.activate();
    };

    // ── Session management ─────────────────────────────────────
    const applySession = (data) => {
      session.id = data.sessionId;
      session.name = data.playerName;
      session.gold = data.gold;
      session.holdings = data.holdings || {};
      localStorage.setItem('sessionId', data.sessionId);
    };

    const startSession = async (playerClass) => {
      const res = await fetch('/api/session/start', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerClass })
      });
      const data = await res.json();
      applySession(data);
      await loadGoodsMeta();
      connect();
    };

    const loadGoodsMeta = async () => {
      const snap = await fetch('/api/market/snapshot').then(r => r.json());
      goods.value = Object.keys(snap.prices).map(name => ({
        name,
        category: getCategoryGuess(name)
      }));
      // Initialise quantities
      goods.value.forEach(g => { if (!quantities[g.name]) quantities[g.name] = 1; });
      for (const [k, v] of Object.entries(snap.prices)) prices[k] = v;
    };

    // Derive category from known list (matches GoodsCatalogue)
    const CATEGORIES = {
      Agriculture: ['Grain','Wool','Livestock','Ale','Spices'],
      Mining: ['Iron','Coal','Stone','Gems','Salt'],
      'Timber & Craft': ['Timber','Rope','Cloth','Leather','Candles']
    };
    const getCategoryGuess = name => {
      for (const [cat, list] of Object.entries(CATEGORIES)) {
        if (list.includes(name)) return cat;
      }
      return 'Other';
    };

    // ── Trading ────────────────────────────────────────────────
    const trade = async (action, goodName) => {
      const qty = quantities[goodName] || 1;
      const res = await fetch(`/api/trade/${action}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId: session.id, good: goodName, quantity: qty })
      });
      const data = await res.json();
      if (res.ok) {
        session.gold = data.gold;
        session.holdings = data.holdings;
        localStorage.setItem('portfolio', JSON.stringify({ gold: data.gold, holdings: data.holdings }));
      } else {
        alert(data.error || 'Trade failed');
      }
    };

    // ── On mount: try resume ───────────────────────────────────
    onMounted(async () => {
      const savedId = localStorage.getItem('sessionId');
      if (savedId) {
        const res = await fetch(`/api/session/${savedId}`);
        if (res.ok) {
          const data = await res.json();
          applySession(data);
          await loadGoodsMeta();
          connect();
        }
      }
    });

    return {
      session, prices, goods, events, scoreboard, reconnecting, quantities, netWorth,
      fmt, priceClass, priceArrow, trendClass, trendIcon,
      startSession, trade, registerCanvas
    };
  }
}).mount('#app');
</script>
</body>
</html>
```

- [ ] **Step 2: Start the application and open `http://localhost:8080`**

```bash
./mvnw spring-boot:run
```

Expected: Class selection screen appears. Selecting a class leads to the trading view. Prices update every 5 seconds. Buy/sell buttons work.

- [ ] **Step 3: Verify scoreboard appears and updates**

Open two browser tabs, start sessions in each. Both should appear on each other's scoreboard and net worth should update live.

- [ ] **Step 4: Verify localStorage resume**

Start a session, note some holdings, refresh the page. The trading view should reappear (not the class select screen) with the same session state.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/static/index.html
git commit -m "feat: Vue.js frontend with trading view, scoreboard, and charts"
```

---

## Task 15: Final Integration Verification

- [ ] **Step 1: Run the full test suite**

```bash
./mvnw test
```

Expected: All tests PASS. Fix any failures before proceeding.

- [ ] **Step 2: Start the application and perform a full smoke test**

```bash
./mvnw spring-boot:run
```

Manual checks:
- [ ] Class selection screen loads at `http://localhost:8080`
- [ ] All 3 classes selectable; each assigns a different starting gold amount
- [ ] Market table shows 15 goods with prices updating every ~5 seconds
- [ ] Sparkline charts render for each good
- [ ] Buy 1 Grain → gold decreases by roughly `price × 1.03` (Noble) or `price` (Merchant)
- [ ] Sell Grain → gold increases, holding decreases
- [ ] Event ticker shows market events when they fire (~10% of ticks)
- [ ] Scoreboard shows the current player; net worth = gold + holding value at current prices
- [ ] Open a second tab, start a second session — both appear on each other's scoreboard
- [ ] Trend indicators (▲/▼) appear after ~30 seconds of play
- [ ] Refresh the page — session resumes from localStorage

- [ ] **Step 3: Tag the initial release**

```bash
git tag v0.1.0
```

---

## Appendix: Running Tests by Layer

```bash
# All tests
./mvnw test

# Service layer only (fast, no Spring context)
./mvnw test -Dtest="GoodsCatalogueTest,NameGeneratorTest,PriceHistoryTest,PriceModelTest,EventEngineTest,SessionRegistryTest,TradeServiceTest,ScoreboardServiceTest"

# Controller tests (boots full Spring context)
./mvnw test -Dtest="SessionControllerTest,TradeControllerTest,MarketControllerTest"
```
