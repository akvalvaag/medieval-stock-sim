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
