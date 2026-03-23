// src/test/java/com/medievalmarket/service/PriceModelTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.Good.Volatility;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PriceModelTest {

    private Good iron() { return new Good("Iron", "Mining", 40.0, Volatility.MEDIUM); }

    @Test
    void priceStaysWithinFloorAndCeiling() {
        PriceModel model = new PriceModel();
        Good good = iron();
        for (int i = 0; i < 1000; i++) {
            double newPrice = model.computeNewPrice(good, 0.0);
            assertThat(newPrice).isGreaterThanOrEqualTo(good.getBasePrice() * 0.10);
            assertThat(newPrice).isLessThanOrEqualTo(good.getBasePrice() * 5.00);
        }
    }

    @Test
    void positiveEventModifierIncreasesPrice() {
        PriceModel model = new PriceModel();
        Good good = iron();
        good.setCurrentPrice(40.0);
        good.addSupplyPressure(0); // no pressure
        double sum = 0;
        for (int i = 0; i < 100; i++) {
            good.setCurrentPrice(40.0);
            sum += model.computeNewPrice(good, 0.35);
        }
        assertThat(sum / 100).isGreaterThan(40.0);
    }

    @Test
    void negativeEventModifierDecreasesPrice() {
        PriceModel model = new PriceModel();
        Good good = iron();
        double sum = 0;
        for (int i = 0; i < 100; i++) {
            good.setCurrentPrice(40.0);
            sum += model.computeNewPrice(good, -0.30);
        }
        assertThat(sum / 100).isLessThan(40.0);
    }

    @Test
    void supplyPressureNudgesPriceUp() {
        PriceModel model = new PriceModel();
        Good good = iron();
        good.setCurrentPrice(40.0);
        good.addSupplyPressure(10.0); // strong buy pressure
        double newPrice = model.computeNewPrice(good, 0.0);
        assertThat(newPrice).isGreaterThan(40.0);
    }
}
