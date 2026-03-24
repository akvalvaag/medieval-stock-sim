package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FacilityServiceTest {

    private FacilityService service;
    private GoodsCatalogue catalogue;

    @BeforeEach
    void setUp() {
        catalogue = new GoodsCatalogue();
        service = new FacilityService(catalogue);
    }

    private Portfolio richMerchant() {
        Portfolio p = new Portfolio("s1", "Test", PlayerClass.MERCHANT);
        p.setGold(2000.0);
        return p;
    }

    @Test
    void build_deductsGoldAndAddsFacility() {
        Portfolio p = richMerchant();
        service.build(p, FacilityType.MILL);
        assertThat(p.getGold()).isEqualTo(2000.0 - FacilityType.MILL.getBuildCost());
        assertThat(p.getFacilities()).containsExactly(FacilityType.MILL);
    }

    @Test
    void build_failsWhenInsufficientFunds() {
        Portfolio p = new Portfolio("s1", "Test", PlayerClass.MERCHANT);
        p.setGold(100.0);
        assertThatThrownBy(() -> service.build(p, FacilityType.MILL))
            .isInstanceOf(FacilityService.FacilityException.class)
            .hasMessageContaining("INSUFFICIENT_FUNDS");
    }

    @Test
    void build_failsAtCap() {
        Portfolio p = richMerchant();
        p.setGold(100000.0);
        for (int i = 0; i < 15; i++) service.build(p, FacilityType.MILL);
        assertThatThrownBy(() -> service.build(p, FacilityType.MILL))
            .isInstanceOf(FacilityService.FacilityException.class)
            .hasMessageContaining("FACILITY_CAP_REACHED");
    }

    @Test
    void processTick_producesOnEvery5thTick() {
        Portfolio p = richMerchant();
        p.setHolding("Grain", 30);
        p.setHolding("Salt", 10);
        service.build(p, FacilityType.MILL);
        for (int i = 0; i < 4; i++) service.processTick(p);
        assertThat(p.getHolding("Bread")).isEqualTo(0);
        service.processTick(p);
        assertThat(p.getHolding("Bread")).isEqualTo(2);
    }

    @Test
    void processTick_idlesWhenInputsMissing() {
        Portfolio p = richMerchant();
        service.build(p, FacilityType.MILL);
        for (int i = 0; i < 5; i++) service.processTick(p);
        assertThat(p.getHolding("Bread")).isEqualTo(0);
    }

    @Test
    void alchemistsSociety_doublesOutput() {
        Portfolio p = richMerchant();
        p.setGuild(Guild.ALCHEMISTS_SOCIETY);
        p.setHolding("Grain", 30);
        p.setHolding("Salt", 10);
        service.build(p, FacilityType.MILL);
        for (int i = 0; i < 5; i++) service.processTick(p);
        assertThat(p.getHolding("Bread")).isEqualTo(4); // 2 × 2 = 4
    }

    @Test
    void demolish_refundsHalfBuildCost() {
        Portfolio p = richMerchant();
        service.build(p, FacilityType.FORGE); // costs 400g
        double goldAfterBuild = p.getGold();
        service.demolish(p, FacilityType.FORGE);
        assertThat(p.getGold()).isEqualTo(goldAfterBuild + 200.0); // 50% of 400
    }

    @Test
    void demolish_removesOneCopyWhenMultipleOwned() {
        Portfolio p = richMerchant();
        service.build(p, FacilityType.FORGE);
        service.build(p, FacilityType.FORGE);
        service.demolish(p, FacilityType.FORGE);
        assertThat(p.getFacilities()).hasSize(1).containsExactly(FacilityType.FORGE);
    }

    @Test
    void demolish_throwsWhenTypeNotOwned() {
        Portfolio p = richMerchant();
        assertThatThrownBy(() -> service.demolish(p, FacilityType.FORGE))
            .isInstanceOf(FacilityService.FacilityException.class)
            .hasMessageContaining("FACILITY_NOT_FOUND");
    }

    @Test
    void getTicksUntilProduction_countsDownFrom5() {
        Portfolio p = richMerchant();
        service.processTick(p); assertThat(service.getTicksUntilProduction()).isEqualTo(4);
        service.processTick(p); assertThat(service.getTicksUntilProduction()).isEqualTo(3);
        service.processTick(p); assertThat(service.getTicksUntilProduction()).isEqualTo(2);
        service.processTick(p); assertThat(service.getTicksUntilProduction()).isEqualTo(1);
        service.processTick(p); assertThat(service.getTicksUntilProduction()).isEqualTo(5); // fired, resets
    }
}
