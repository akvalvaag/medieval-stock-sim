package com.medievalmarket.service;

import com.medievalmarket.dto.LimitOrderFill;
import com.medievalmarket.model.LimitOrder;
import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class LimitOrderService {

    private final TradeService tradeService;
    private final GoodsCatalogue catalogue;

    public LimitOrderService(TradeService tradeService, GoodsCatalogue catalogue) {
        this.tradeService = tradeService;
        this.catalogue = catalogue;
    }

    /** Processes all limit orders for every human portfolio. Returns fills keyed by sessionId. */
    public Map<String, List<LimitOrderFill>> processAll(Collection<Portfolio> humanPortfolios) {
        Map<String, List<LimitOrderFill>> fillsBySession = new HashMap<>();

        for (Portfolio portfolio : humanPortfolios) {
            List<LimitOrder> toProcess = portfolio.getLimitOrders(); // snapshot copy
            for (LimitOrder order : toProcess) {
                double currentPrice = catalogue.findByName(order.goodName()).getCurrentPrice();
                boolean triggered = "BUY".equals(order.direction())
                    ? currentPrice <= order.targetPrice()
                    : currentPrice >= order.targetPrice();

                if (!triggered) continue;

                LimitOrderFill fill = execute(portfolio, order, currentPrice);
                portfolio.removeLimitOrder(order.id());
                if (fill != null) {
                    fillsBySession.computeIfAbsent(portfolio.getSessionId(), k -> new ArrayList<>())
                        .add(fill);
                }
            }
        }
        return fillsBySession;
    }

    private LimitOrderFill execute(Portfolio portfolio, LimitOrder order, double currentPrice) {
        try {
            if ("BUY".equals(order.direction())) {
                tradeService.buy(portfolio, order.goodName(), order.quantity());
                return new LimitOrderFill(order.goodName(), "BUY",
                    order.quantity(), currentPrice, 0.0);
            } else {
                double avgCost = portfolio.getAvgCostBasis(order.goodName());
                double goldBefore = portfolio.getGold();
                tradeService.sell(portfolio, order.goodName(), order.quantity());
                double proceeds = portfolio.getGold() - goldBefore;
                double realizedPnl = proceeds - (avgCost * order.quantity());
                return new LimitOrderFill(order.goodName(), "SELL",
                    order.quantity(), currentPrice, realizedPnl);
            }
        } catch (TradeService.TradeException | IllegalArgumentException e) {
            return null; // order cancelled, fill not recorded
        }
    }

    /**
     * Adds an order if the portfolio has fewer than 3 pending orders.
     * The check-and-add is synchronized on the portfolio to prevent TOCTOU.
     */
    public LimitOrder addOrder(Portfolio portfolio, String goodName, String direction,
                               int quantity, double targetPrice) {
        if (!"BUY".equals(direction) && !"SELL".equals(direction)) {
            throw new LimitOrderException("INVALID_DIRECTION");
        }
        if (quantity <= 0) {
            throw new LimitOrderException("INVALID_QUANTITY");
        }
        if (targetPrice <= 0) {
            throw new LimitOrderException("INVALID_PRICE");
        }
        catalogue.findByName(goodName); // throws if unknown good
        LimitOrder order = new LimitOrder(
            UUID.randomUUID().toString(), portfolio.getSessionId(),
            goodName, direction, quantity, targetPrice);
        synchronized (portfolio) {
            if (portfolio.limitOrderCount() >= 3) {
                throw new LimitOrderException("MAX_ORDERS_REACHED");
            }
            portfolio.addLimitOrder(order);
        }
        return order;
    }

    public static class LimitOrderException extends RuntimeException {
        public LimitOrderException(String message) { super(message); }
    }
}
