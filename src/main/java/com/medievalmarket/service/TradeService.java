// src/main/java/com/medievalmarket/service/TradeService.java
package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;

@Component
public class TradeService {

    private final GoodsCatalogue catalogue;

    public TradeService(GoodsCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    public void buy(Portfolio portfolio, String goodName, int quantity) {
        validateQuantity(quantity);
        Good good = catalogue.findByName(goodName);
        double feeRate = portfolio.getPlayerClass().getFeeRate();
        double slippage = quantity >= 10 ? Math.min(quantity * 0.001, 0.05) : 0.0;
        double cost = good.getCurrentPrice() * quantity * (1 + feeRate + slippage);

        synchronized (portfolio) {
            if (portfolio.getGold() < cost) throw new TradeException("INSUFFICIENT_FUNDS");
            portfolio.updateCostBasis(goodName, quantity, good.getCurrentPrice());
            portfolio.setGold(portfolio.getGold() - cost);
            portfolio.setHolding(goodName, portfolio.getHolding(goodName) + quantity);
            portfolio.touchLastTradeTime();
        }

        synchronized (good) {
            good.addSupplyPressure(quantity * 0.02);
        }
    }

    public void sell(Portfolio portfolio, String goodName, int quantity) {
        validateQuantity(quantity);
        Good good = catalogue.findByName(goodName);

        synchronized (portfolio) {
            int held = portfolio.getHolding(goodName);
            if (held < quantity) throw new TradeException("INSUFFICIENT_HOLDINGS");

            double saleValue = good.getCurrentPrice() * quantity;
            double feeRate = portfolio.getPlayerClass().getFeeRate();
            double slippage = quantity >= 10 ? Math.min(quantity * 0.001, 0.05) : 0.0;
            saleValue *= (1 - feeRate - slippage);

            if (portfolio.getPlayerClass() == PlayerClass.MINER
                    && "Mining".equals(good.getCategory())) {
                saleValue *= 1.02;
            }

            portfolio.setGold(portfolio.getGold() + saleValue);
            int remaining = held - quantity;
            portfolio.setHolding(goodName, remaining);
            if (remaining == 0) portfolio.clearCostBasis(goodName);
            portfolio.touchLastTradeTime();
        }

        synchronized (good) {
            good.addSupplyPressure(-quantity * 0.02);
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity < 1) throw new TradeException("INVALID_QUANTITY");
    }

    public static class TradeException extends RuntimeException {
        public TradeException(String message) { super(message); }
    }
}
