package com.medievalmarket.service;

import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MoneylenderServiceTest {

    private MoneylenderService service;
    private TradeService tradeService;
    private GoodsCatalogue catalogue;

    @BeforeEach
    void setUp() {
        catalogue = new GoodsCatalogue();
        tradeService = new TradeService(catalogue);
        service = new MoneylenderService(tradeService, catalogue);
    }

    private Portfolio noble() {
        return new Portfolio("s1", "Test", PlayerClass.NOBLE); // 1000g start
    }

    @Test
    void borrowAddsGoldAndSetsLoanAmount() {
        Portfolio p = noble();
        service.borrow(p, 200.0);
        assertThat(p.getGold()).isCloseTo(1200.0, within(0.01));
        assertThat(p.getLoanAmount()).isCloseTo(200.0, within(0.01));
    }

    @Test
    void borrowRejectsAmountBelowMinimum() {
        Portfolio p = noble();
        assertThatThrownBy(() -> service.borrow(p, 40.0))
            .hasMessageContaining("BELOW_MINIMUM");
    }

    @Test
    void borrowRejectsAmountAboveLimit() {
        Portfolio p = noble(); // 1000g → max = min(2000, 5000) = 2000
        assertThatThrownBy(() -> service.borrow(p, 2001.0))
            .hasMessageContaining("ABOVE_LIMIT");
    }

    @Test
    void repayReducesLoanAndDeductsGold() {
        Portfolio p = noble();
        service.borrow(p, 200.0);
        service.repay(p);
        assertThat(p.getLoanAmount()).isCloseTo(0.0, within(0.01));
        assertThat(p.getGold()).isCloseTo(1000.0, within(0.01));
    }

    @Test
    void repayIsLimitedByAvailableGold() {
        Portfolio p = noble();
        service.borrow(p, 200.0);
        p.setGold(100.0); // only 100g left
        service.repay(p);
        assertThat(p.getGold()).isCloseTo(0.0, within(0.01));
        assertThat(p.getLoanAmount()).isCloseTo(100.0, within(0.01)); // 100 remaining
    }

    @Test
    void interestCompoundsEachTick() {
        Portfolio p = noble();
        service.borrow(p, 100.0);
        service.processTick(p);
        assertThat(p.getLoanAmount()).isCloseTo(102.0, within(0.01));
    }

    @Test
    void seizureOccursWhenInsolvent() {
        Portfolio p = new Portfolio("s", "T", PlayerClass.MERCHANT); // 500g
        service.borrow(p, 200.0); // loan=200, gold=700
        // buy some Iron so there are holdings to seize
        catalogue.findByName("Iron").setCurrentPrice(10.0);
        tradeService.buy(p, "Iron", 10);
        p.setGold(0.0); // make them insolvent
        p.setLoanAmount(p.getGold() + 200.0); // loan=200, gold=0+holdings=100 → insolvent
        // processTick: loan *= 1.02 → 204; holdingsValue=100; 204 > 0+100 → seize
        // sell Iron(10) at 10g with MERCHANT fee 0.25%: proceeds ≈ 99.75; loan reduced to ≈104.25
        // Seizure confirms Iron is cleared; loan is reduced from pre-seizure 204 down toward loan-proceedings
        service.processTick(p); // should seize Iron
        // After seizure Iron is gone and loan was reduced. Cannot reach 0 with only 100g of Iron vs 204 loan.
        // Adjusted assertion: loan must be less than 110 (confirming seizure reduced it from 204).
        assertThat(p.getHolding("Iron")).isEqualTo(0); // all Iron seized
        assertThat(p.getLoanAmount()).isCloseTo(104.0, within(2.0)); // 204 - 100 proceeds
    }
}
