package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GuildServiceTest {

    private GuildService service;
    private GoodsCatalogue catalogue;

    @BeforeEach
    void setUp() {
        catalogue = new GoodsCatalogue();
        service = new GuildService(catalogue);
    }

    private Portfolio merchant() {
        Portfolio p = new Portfolio("s1", "Test", PlayerClass.MERCHANT);
        p.setGold(1500.0);
        return p;
    }

    @Test
    void processTick_decrementsGuildOfferCooldown() {
        Portfolio p = merchant();
        p.setGuildOfferCooldown(5);
        service.processTick(p, "SPRING");
        assertThat(p.getGuildOfferCooldown()).isEqualTo(4);
    }

    @Test
    void processTick_setsGuildOfferWhenGoldOver1000AndNoCooldown() {
        Portfolio p = merchant();
        service.processTick(p, "SPRING");
        assertThat(p.getPendingGuildOffer()).isNotNull();
    }

    @Test
    void processTick_doesNotOfferWhenGoldUnder1000() {
        Portfolio p = new Portfolio("s1", "Test", PlayerClass.MERCHANT);
        p.setGold(500.0);
        service.processTick(p, "SPRING");
        assertThat(p.getPendingGuildOffer()).isNull();
    }

    @Test
    void processTick_doesNotOfferWhenAlreadyInGuild() {
        Portfolio p = merchant();
        p.setGuild(Guild.SCHOLARS_GUILD);
        service.processTick(p, "SPRING");
        assertThat(p.getPendingGuildOffer()).isNull();
    }

    @Test
    void accept_setsGuildFromPendingOffer() {
        Portfolio p = merchant();
        p.setPendingGuildOffer(Guild.SEA_TRADERS);
        service.accept(p);
        assertThat(p.getGuild()).isEqualTo(Guild.SEA_TRADERS);
        assertThat(p.getPendingGuildOffer()).isNull();
    }

    @Test
    void accept_throwsWhenNoPendingOffer() {
        Portfolio p = merchant();
        assertThatThrownBy(() -> service.accept(p))
            .isInstanceOf(GuildService.GuildException.class)
            .hasMessageContaining("No pending");
    }

    @Test
    void decline_setsLastOfferedAndResetsCooldown() {
        Portfolio p = merchant();
        p.setPendingGuildOffer(Guild.THIEVES_GUILD);
        service.decline(p);
        assertThat(p.getLastOfferedGuild()).isEqualTo(Guild.THIEVES_GUILD);
        assertThat(p.getPendingGuildOffer()).isNull();
        assertThat(p.getGuildOfferCooldown()).isEqualTo(30);
    }

    @Test
    void fence_throwsWhenNotThievesGuild() {
        Portfolio p = merchant();
        p.setGuild(Guild.SCHOLARS_GUILD);
        assertThatThrownBy(() -> service.fence(p, "Iron", 1))
            .isInstanceOf(GuildService.GuildException.class);
    }

    @Test
    void fence_throwsWhenOnCooldown() {
        Portfolio p = merchant();
        p.setGuild(Guild.THIEVES_GUILD);
        p.setFenceCooldown(5);
        assertThatThrownBy(() -> service.fence(p, "Iron", 1))
            .isInstanceOf(GuildService.GuildException.class)
            .hasMessageContaining("cooldown");
    }

    @Test
    void fence_throwsWhenInsufficientHoldings() {
        Portfolio p = merchant();
        p.setGuild(Guild.THIEVES_GUILD);
        assertThatThrownBy(() -> service.fence(p, "Iron", 1))
            .isInstanceOf(GuildService.GuildException.class)
            .hasMessageContaining("holdings");
    }

    @Test
    void fence_sells_at_110_percent_with_no_fee() {
        Portfolio p = merchant();
        p.setGuild(Guild.THIEVES_GUILD);
        p.setHolding("Iron", 10);
        double ironPrice = catalogue.findByName("Iron").getCurrentPrice();
        double goldBefore = p.getGold();
        service.fence(p, "Iron", 5);
        double expected = ironPrice * 5 * 1.10;
        assertThat(p.getGold()).isCloseTo(goldBefore + expected, within(0.01));
        assertThat(p.getHolding("Iron")).isEqualTo(5);
        assertThat(p.getFenceCooldown()).isEqualTo(20);
    }

    @Test
    void royalWarrant_addsStipendOnSeasonChange() {
        Portfolio p = merchant();
        p.setGuild(Guild.ROYAL_WARRANT);
        p.setLastSeenSeason("SPRING");
        double goldBefore = p.getGold();
        service.processTick(p, "SUMMER");
        assertThat(p.getGold()).isGreaterThanOrEqualTo(goldBefore + 50.0);
        assertThat(p.getGold()).isLessThanOrEqualTo(goldBefore + 100.0);
    }

    @Test
    void seaTraders_setsExoticImportOnSeasonChange() {
        Portfolio p = merchant();
        p.setGuild(Guild.SEA_TRADERS);
        p.setLastSeenSeason("SPRING");
        service.processTick(p, "SUMMER");
        assertThat(p.getExoticImportOffer()).isNotNull();
    }

    @Test
    void buyExoticImport_throwsWhenNoOffer() {
        Portfolio p = merchant();
        p.setGuild(Guild.SEA_TRADERS);
        assertThatThrownBy(() -> service.buyExoticImport(p))
            .isInstanceOf(GuildService.GuildException.class);
    }

    @Test
    void buyExoticImport_purchasesAtDiscountedPrice() {
        Portfolio p = merchant();
        p.setGuild(Guild.SEA_TRADERS);
        double price = catalogue.findByName("Gems").getCurrentPrice() * 0.60;
        p.setExoticImportOffer(new ExoticImportOffer("Gems", price));
        double goldBefore = p.getGold();
        service.buyExoticImport(p);
        assertThat(p.getGold()).isCloseTo(goldBefore - price, within(0.01));
        assertThat(p.getHolding("Gems")).isEqualTo(1);
        assertThat(p.getExoticImportOffer()).isNull();
    }
}
