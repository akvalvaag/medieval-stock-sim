// src/main/java/com/medievalmarket/service/PriceModel.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.Good.Volatility;
import org.springframework.stereotype.Component;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class PriceModel {

    public double computeNewPrice(Good good, double eventModifier) {
        double price = good.getCurrentPrice();

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        // 1. Random drift
        double maxDrift = 0.05 * volatilityMultiplier(good.getVolatility());
        double drift = rng.nextDouble() * maxDrift * (rng.nextBoolean() ? 1 : -1);
        price *= (1 + drift);

        // 2. Supply/demand pressure (capped at ±5% per tick to prevent manipulation)
        double pressureEffect = Math.max(-0.05, Math.min(0.05, good.getSupplyPressure() * 0.01));
        price *= (1 + pressureEffect);

        // 3. Event modifier
        price *= (1 + eventModifier);

        // 4. Mean reversion — gentle pull toward base price, stronger the further away
        double deviation = (price - good.getBasePrice()) / good.getBasePrice();
        price *= (1 - deviation * 0.015);

        // 5. Clamp
        double floor = good.getBasePrice() * 0.10;
        double ceiling = good.getBasePrice() * 5.00;
        return Math.max(floor, Math.min(ceiling, price));
    }

    private double volatilityMultiplier(Volatility v) {
        return switch (v) {
            case LOW -> 0.5;
            case MEDIUM -> 1.0;
            case HIGH -> 1.5;
        };
    }
}
