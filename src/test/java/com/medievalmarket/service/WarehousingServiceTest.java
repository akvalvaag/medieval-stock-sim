package com.medievalmarket.service;

import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WarehousingServiceTest {

    private final WarehousingService service = new WarehousingService();

    @Test
    void noFeeDeductedRegardlessOfHoldings() {
        Portfolio p = new Portfolio("s", "T", PlayerClass.NOBLE);
        p.setHolding("Iron", 100);
        double gold = p.getGold();
        service.processTick(p);
        assertThat(p.getGold()).isEqualTo(gold);
    }

    @Test
    void processAllDoesNotChangeGold() {
        Portfolio p1 = new Portfolio("s1", "T", PlayerClass.MERCHANT);
        Portfolio p2 = new Portfolio("s2", "T", PlayerClass.MINER);
        p1.setHolding("Grain", 50);
        p2.setHolding("Gems", 10);
        double g1 = p1.getGold(), g2 = p2.getGold();
        service.processAll(java.util.List.of(p1, p2));
        assertThat(p1.getGold()).isEqualTo(g1);
        assertThat(p2.getGold()).isEqualTo(g2);
    }
}
