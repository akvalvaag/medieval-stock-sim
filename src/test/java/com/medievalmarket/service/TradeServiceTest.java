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
        assertThatThrownBy(() -> service.buy(p, "Gems", 10))
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
    void largeBuySucceedsWithSufficientFunds() {
        Portfolio p = noble(); // 1000g
        // Buy 20 Grain at ~10g each = ~206g with fee, well within 1000g
        service.buy(p, "Grain", 20);
        assertThat(p.getHolding("Grain")).isEqualTo(20);
    }

    @Test
    void buyAddsBuyPressure() {
        Portfolio p = noble();
        service.buy(p, "Iron", 4);
        // pressure = +4 * 0.02 = +0.08
        assertThat(iron.getSupplyPressure()).isCloseTo(0.08, within(0.001));
    }

    @Test
    void sellAddsSellPressure() {
        Portfolio p = noble();
        service.buy(p, "Iron", 4);
        iron.setCurrentPrice(40.0);
        iron.addSupplyPressure(-iron.getSupplyPressure()); // reset
        service.sell(p, "Iron", 2);
        // pressure = -2 * 0.02 = -0.04
        assertThat(iron.getSupplyPressure()).isCloseTo(-0.04, within(0.001));
    }
}
