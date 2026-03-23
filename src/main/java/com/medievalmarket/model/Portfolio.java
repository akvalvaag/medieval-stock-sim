package com.medievalmarket.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class Portfolio {
    private final String sessionId;
    private final String playerName;
    private final PlayerClass playerClass;
    private double gold;
    private final Map<String, Integer> holdings = new HashMap<>();
    private final Map<String, Double> avgCostBasis = new HashMap<>();
    private final Deque<Double> netWorthHistory = new ArrayDeque<>();
    private Instant lastTradeTime = Instant.now();

    public Portfolio(String sessionId, String playerName, PlayerClass playerClass) {
        this.sessionId = sessionId;
        this.playerName = playerName;
        this.playerClass = playerClass;
        this.gold = playerClass.getStartGold();
    }

    public String getSessionId() { return sessionId; }
    public String getPlayerName() { return playerName; }
    public PlayerClass getPlayerClass() { return playerClass; }

    public synchronized double getGold() { return gold; }
    public synchronized void setGold(double gold) { this.gold = gold; }

    public synchronized Map<String, Integer> getHoldings() { return new HashMap<>(holdings); }
    public synchronized int getHolding(String good) { return holdings.getOrDefault(good, 0); }
    public synchronized void setHolding(String good, int qty) {
        if (qty == 0) holdings.remove(good);
        else holdings.put(good, qty);
    }

    public synchronized void recordNetWorth(double netWorth) {
        if (netWorthHistory.size() >= 10) netWorthHistory.pollFirst();
        netWorthHistory.addLast(netWorth);
    }

    public synchronized double getTrend() {
        if (netWorthHistory.size() < 2) return 0.0;
        double current = netWorthHistory.peekLast();
        Object[] arr = netWorthHistory.toArray();
        int compareIdx = Math.max(0, arr.length - 6);
        return current - (double) arr[compareIdx];
    }

    public synchronized double getAvgCostBasis(String good) {
        return avgCostBasis.getOrDefault(good, 0.0);
    }

    public synchronized Map<String, Double> getAllCostBasis() {
        return new HashMap<>(avgCostBasis);
    }

    /** Called on buy: updates running weighted average cost per unit (before fees). */
    public synchronized void updateCostBasis(String good, int addedQty, double pricePerUnit) {
        int existingQty = holdings.getOrDefault(good, 0);
        double existingAvg = avgCostBasis.getOrDefault(good, 0.0);
        double newAvg = (existingAvg * existingQty + pricePerUnit * addedQty) / (existingQty + addedQty);
        avgCostBasis.put(good, newAvg);
    }

    /** Called on full sell: clears cost basis for the good. */
    public synchronized void clearCostBasis(String good) {
        avgCostBasis.remove(good);
    }

    public synchronized Instant getLastTradeTime() { return lastTradeTime; }
    public synchronized void touchLastTradeTime() { this.lastTradeTime = Instant.now(); }
}
