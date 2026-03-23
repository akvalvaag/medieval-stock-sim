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
