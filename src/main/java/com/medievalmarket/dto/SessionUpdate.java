package com.medievalmarket.dto;

import com.medievalmarket.model.LimitOrder;
import java.util.List;

public class SessionUpdate {
    private double gold;
    private List<LimitOrder> limitOrders;
    private double loanAmount;
    private List<LimitOrderFill> limitOrderFills;

    private SessionUpdate() {}

    public double getGold() { return gold; }
    public List<LimitOrder> getLimitOrders() { return limitOrders; }
    public double getLoanAmount() { return loanAmount; }
    public List<LimitOrderFill> getLimitOrderFills() { return limitOrderFills; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final SessionUpdate su = new SessionUpdate();
        public Builder gold(double v) { su.gold = v; return this; }
        public Builder limitOrders(List<LimitOrder> v) { su.limitOrders = v; return this; }
        public Builder loanAmount(double v) { su.loanAmount = v; return this; }
        public Builder limitOrderFills(List<LimitOrderFill> v) { su.limitOrderFills = v; return this; }
        public SessionUpdate build() { return su; }
    }

    /** DTO for rumours — excludes isTrue field (not exposed to client). */
    public record RumourDTO(String id, String text, String eventKey, int ticksRemaining, String tipResult) {}
}
