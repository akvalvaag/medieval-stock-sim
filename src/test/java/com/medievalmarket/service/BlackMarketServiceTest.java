package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class BlackMarketServiceTest {

    private BlackMarketService service;
    private GoodsCatalogue catalogue;

    @BeforeEach
    void setUp() {
        catalogue = new GoodsCatalogue();
        service = new BlackMarketService(catalogue);
    }

    private Portfolio merchant() {
        Portfolio p = new Portfolio("s1", "Test", PlayerClass.MERCHANT);
        p.setGold(1000.0);
        return p;
    }

    @Test
    void processTick_decrementsPanelLifetime() {
        Portfolio p = merchant();
        p.setBlackMarketOffers(List.of(new BlackMarketOffer("Iron", 5, 20.0)));
        p.setBlackMarketTicksRemaining(3);
        service.processTick(p);
        assertThat(p.getBlackMarketTicksRemaining()).isEqualTo(2);
    }

    @Test
    void processTick_closesMarketWhenExpired() {
        Portfolio p = merchant();
        p.setBlackMarketOffers(List.of(new BlackMarketOffer("Iron", 5, 20.0)));
        p.setBlackMarketTicksRemaining(1);
        service.processTick(p);
        assertThat(p.getBlackMarketOffers()).isNull();
    }

    @Test
    void buy_addsToContrabandHoldings() {
        Portfolio p = merchant();
        BlackMarketOffer offer = new BlackMarketOffer("Iron", 5, 20.0);
        p.setBlackMarketOffers(new java.util.ArrayList<>(List.of(offer)));
        p.setBlackMarketTicksRemaining(10);
        service.buy(p, "Iron", 3);
        assertThat(p.getContrabandHolding("Iron")).isEqualTo(3);
        assertThat(p.getGold()).isCloseTo(1000.0 - 60.0, within(0.01));
    }

    @Test
    void buy_failsWhenOfferNotFound() {
        Portfolio p = merchant();
        p.setBlackMarketOffers(new java.util.ArrayList<>(List.of(new BlackMarketOffer("Iron", 5, 20.0))));
        assertThatThrownBy(() -> service.buy(p, "Gems", 1))
            .isInstanceOf(BlackMarketService.BlackMarketException.class)
            .hasMessageContaining("No offer");
    }

    @Test
    void buy_failsWhenInsufficientFunds() {
        Portfolio p = merchant();
        p.setGold(10.0);
        p.setBlackMarketOffers(new java.util.ArrayList<>(List.of(new BlackMarketOffer("Gems", 1, 100.0))));
        assertThatThrownBy(() -> service.buy(p, "Gems", 1))
            .isInstanceOf(BlackMarketService.BlackMarketException.class)
            .hasMessageContaining("funds");
    }

    @Test
    void processTick_confiscatesContrabandAtRoughly3Percent() {
        // Run 1000 ticks and count confiscations — should be ~30
        int confiscations = 0;
        for (int i = 0; i < 1000; i++) {
            Portfolio p = merchant();
            p.addContrabandHolding("Iron", 5);
            String msg = service.processTick(p);
            if (msg != null) confiscations++;
        }
        // 3% of 1000 = 30, allow generous range 10-80
        assertThat(confiscations).isBetween(10, 80);
    }

    @Test
    void processTick_thievesGuildImmuneToConfiscation() {
        int confiscations = 0;
        for (int i = 0; i < 1000; i++) {
            Portfolio p = merchant();
            p.setGuild(Guild.THIEVES_GUILD);
            p.addContrabandHolding("Iron", 5);
            String msg = service.processTick(p);
            if (msg != null) confiscations++;
        }
        assertThat(confiscations).isEqualTo(0);
    }
}
