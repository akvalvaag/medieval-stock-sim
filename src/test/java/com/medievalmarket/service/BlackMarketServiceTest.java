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

    @Test
    void buy_failsWhenInsufficientQuantity() {
        Portfolio p = merchant();
        p.setBlackMarketOffers(new java.util.ArrayList<>(List.of(new BlackMarketOffer("Iron", 2, 20.0))));
        assertThatThrownBy(() -> service.buy(p, "Iron", 5))
            .isInstanceOf(BlackMarketService.BlackMarketException.class)
            .hasMessageContaining("quantity");
    }

    @Test
    void buy_failsWhenNoOffersActive() {
        Portfolio p = merchant();
        // No offers set (null)
        assertThatThrownBy(() -> service.buy(p, "Iron", 1))
            .isInstanceOf(BlackMarketService.BlackMarketException.class)
            .hasMessageContaining("No black market");
    }

    @Test
    void buy_exhaustsOfferWhenFullyPurchased() {
        Portfolio p = merchant();
        BlackMarketOffer offer = new BlackMarketOffer("Iron", 3, 20.0);
        p.setBlackMarketOffers(new java.util.ArrayList<>(List.of(offer)));
        service.buy(p, "Iron", 3);
        // Offer fully consumed — offers list should be null or empty
        assertThat(p.getBlackMarketOffers()).isNull();
    }

    @Test
    void buy_reducesOfferQtyOnPartialPurchase() {
        Portfolio p = merchant();
        BlackMarketOffer offer = new BlackMarketOffer("Iron", 5, 20.0);
        p.setBlackMarketOffers(new java.util.ArrayList<>(List.of(offer)));
        service.buy(p, "Iron", 2);
        assertThat(p.getBlackMarketOffers()).hasSize(1);
        assertThat(p.getBlackMarketOffers().get(0).availableQty()).isEqualTo(3);
    }

    @Test
    void sell_addsGoldAndReducesContraband() {
        Portfolio p = merchant();
        catalogue.findByName("Iron").setCurrentPrice(40.0);
        p.addContrabandHolding("Iron", 5);
        p.setContrabandAge("Iron", BlackMarketService.HOLDING_PERIOD);
        service.sell(p, "Iron", 3);
        assertThat(p.getContrabandHolding("Iron")).isEqualTo(2);
        assertThat(p.getGold()).isCloseTo(1000.0 + 120.0, within(0.01));
    }

    @Test
    void sell_failsWhenNotAged() {
        Portfolio p = merchant();
        p.addContrabandHolding("Iron", 5);
        p.setContrabandAge("Iron", 0);
        assertThatThrownBy(() -> service.sell(p, "Iron", 1))
            .isInstanceOf(BlackMarketService.BlackMarketException.class)
            .hasMessage("CONTRABAND_NOT_AGED");
    }

    @Test
    void sell_failsWhenNoneHeld() {
        Portfolio p = merchant();
        assertThatThrownBy(() -> service.sell(p, "Iron", 1))
            .isInstanceOf(BlackMarketService.BlackMarketException.class)
            .hasMessageContaining("No contraband");
    }

    @Test
    void sell_failsWhenInsufficientContraband() {
        Portfolio p = merchant();
        p.addContrabandHolding("Iron", 2);
        p.setContrabandAge("Iron", BlackMarketService.HOLDING_PERIOD);
        assertThatThrownBy(() -> service.sell(p, "Iron", 5))
            .isInstanceOf(BlackMarketService.BlackMarketException.class)
            .hasMessageContaining("Insufficient");
    }
}
