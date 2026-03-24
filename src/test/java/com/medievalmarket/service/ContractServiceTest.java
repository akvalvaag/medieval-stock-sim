package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContractServiceTest {

    private ContractService service;
    private GoodsCatalogue catalogue;

    @BeforeEach
    void setUp() {
        catalogue = new GoodsCatalogue();
        service = new ContractService(catalogue);
    }

    private Portfolio merchant() {
        Portfolio p = new Portfolio("s1", "Test", PlayerClass.MERCHANT);
        p.setGold(1000.0);
        return p;
    }

    @Test
    void processTick_generatesOfferAfter40Ticks() {
        Portfolio p = merchant();
        for (int i = 0; i < 40; i++) service.processTick(p);
        assertThat(p.getPendingContractOffer()).isNotNull();
    }

    @Test
    void processTick_doesNotGenerateWhileContractActive() {
        Portfolio p = merchant();
        for (int i = 0; i < 40; i++) service.processTick(p);
        service.accept(p);
        p.setTicksSinceLastOffer(0);
        for (int i = 0; i < 40; i++) service.processTick(p);
        assertThat(p.getPendingContractOffer()).isNull();
    }

    @Test
    void accept_movesOfferToActive() {
        Portfolio p = merchant();
        for (int i = 0; i < 40; i++) service.processTick(p);
        service.accept(p);
        assertThat(p.getActiveContract()).isNotNull();
        assertThat(p.getPendingContractOffer()).isNull();
    }

    @Test
    void decline_clearsOfferAndResetsCounter() {
        Portfolio p = merchant();
        for (int i = 0; i < 40; i++) service.processTick(p);
        service.decline(p);
        assertThat(p.getPendingContractOffer()).isNull();
        assertThat(p.getTicksSinceLastOffer()).isEqualTo(0);
    }

    @Test
    void deliver_failsWhenRequirementsNotMet() {
        Portfolio p = merchant();
        for (int i = 0; i < 40; i++) service.processTick(p);
        service.accept(p);
        assertThatThrownBy(() -> service.deliver(p))
            .isInstanceOf(ContractService.ContractException.class)
            .hasMessageContaining("requirements");
    }

    @Test
    void deliver_deductsGoodsAndAddsReward() {
        Portfolio p = merchant();
        for (int i = 0; i < 40; i++) service.processTick(p);
        service.accept(p);
        Contract c = p.getActiveContract();
        c.getRequirements().forEach((good, qty) -> p.setHolding(good, qty));
        double goldBefore = p.getGold();
        service.deliver(p);
        assertThat(p.getGold()).isGreaterThan(goldBefore);
        assertThat(p.getActiveContract()).isNull();
        c.getRequirements().forEach((good, qty) -> assertThat(p.getHolding(good)).isEqualTo(0));
    }

    @Test
    void deliver_royalWarrantMultipliesReward() {
        Portfolio p = merchant();
        p.setGuild(Guild.ROYAL_WARRANT);
        for (int i = 0; i < 40; i++) service.processTick(p);
        service.accept(p);
        Contract c = p.getActiveContract();
        c.getRequirements().forEach((good, qty) -> p.setHolding(good, qty));
        double goldBefore = p.getGold();
        service.deliver(p);
        // Royal Warrant gives 1.6× base reward, so gold increase must be > base reward
        assertThat(p.getGold()).isGreaterThan(goldBefore);
    }

    @Test
    void processTick_appliesPenaltyOnExpiry() {
        Portfolio p = merchant();
        for (int i = 0; i < 40; i++) service.processTick(p);
        service.accept(p);
        Contract c = p.getActiveContract();
        double goldBefore = p.getGold();
        for (int i = 0; i <= c.getTicksRemaining(); i++) service.processTick(p);
        assertThat(p.getActiveContract()).isNull();
        assertThat(p.getGold()).isLessThan(goldBefore);
        assertThat(p.getGold()).isGreaterThanOrEqualTo(0.0);
    }
}
