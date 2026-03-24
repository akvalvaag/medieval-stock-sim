package com.medievalmarket.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.medievalmarket.model.Guild;
import com.medievalmarket.model.FacilityType;
import com.medievalmarket.model.Contract;
import com.medievalmarket.model.BlackMarketOffer;
import com.medievalmarket.model.ExoticImportOffer;

public class Portfolio {
    private final String sessionId;
    private final String playerName;
    private final PlayerClass playerClass;
    private double gold;
    private final Map<String, Integer> holdings = new HashMap<>();
    private final Map<String, Double> avgCostBasis = new HashMap<>();
    private final Deque<Double> netWorthHistory = new ArrayDeque<>();
    private Instant lastTradeTime = Instant.now();
    private final boolean bot;
    private double loanAmount = 0.0;
    private final List<LimitOrder> limitOrders = new ArrayList<>();

    // Guild state
    private Guild guild = null;
    private Guild pendingGuildOffer = null;
    private Guild lastOfferedGuild = null;
    private int guildOfferCooldown = 0;
    private int fenceCooldown = 0;
    private ExoticImportOffer exoticImportOffer = null;
    private String lastSeenSeason = null;

    // Facility state
    private final List<FacilityType> facilities = new ArrayList<>();

    // Contract state
    private Contract activeContract = null;
    private Contract pendingContractOffer = null;
    private int ticksSinceLastOffer = 0;

    // Tip results (rumourId → "RELIABLE" | "DUBIOUS")
    private final Map<String, String> tipResults = new HashMap<>();

    // Black market state
    private final Map<String, Integer> contrabandHoldings = new HashMap<>();
    private List<BlackMarketOffer> blackMarketOffers = null;
    private int blackMarketTicksRemaining = 0;
    private int blackMarketTicksSinceLastRoll = 0;

    // Flash message (one-shot, consumed after SessionUpdate push)
    private String lastFlashMessage = null;

    public Portfolio(String sessionId, String playerName, PlayerClass playerClass) {
        this(sessionId, playerName, playerClass, false);
    }

    public Portfolio(String sessionId, String playerName, PlayerClass playerClass, boolean bot) {
        this.sessionId = sessionId;
        this.playerName = playerName;
        this.playerClass = playerClass;
        this.gold = playerClass.getStartGold();
        this.bot = bot;
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

    public boolean isBot() { return bot; }

    public synchronized double getLoanAmount() { return loanAmount; }
    public synchronized void setLoanAmount(double loanAmount) { this.loanAmount = loanAmount; }

    public synchronized List<LimitOrder> getLimitOrders() { return new ArrayList<>(limitOrders); }
    public synchronized void addLimitOrder(LimitOrder order) { limitOrders.add(order); }
    public synchronized void removeLimitOrder(String id) {
        limitOrders.removeIf(o -> o.id().equals(id));
    }
    public synchronized int limitOrderCount() { return limitOrders.size(); }

    public synchronized Guild getGuild() { return guild; }
    public synchronized void setGuild(Guild guild) { this.guild = guild; }
    public synchronized Guild getPendingGuildOffer() { return pendingGuildOffer; }
    public synchronized void setPendingGuildOffer(Guild g) { this.pendingGuildOffer = g; }
    public synchronized Guild getLastOfferedGuild() { return lastOfferedGuild; }
    public synchronized void setLastOfferedGuild(Guild g) { this.lastOfferedGuild = g; }
    public synchronized int getGuildOfferCooldown() { return guildOfferCooldown; }
    public synchronized void setGuildOfferCooldown(int v) { this.guildOfferCooldown = v; }
    public synchronized int getFenceCooldown() { return fenceCooldown; }
    public synchronized void setFenceCooldown(int v) { this.fenceCooldown = v; }
    public synchronized ExoticImportOffer getExoticImportOffer() { return exoticImportOffer; }
    public synchronized void setExoticImportOffer(ExoticImportOffer o) { this.exoticImportOffer = o; }
    public synchronized String getLastSeenSeason() { return lastSeenSeason; }
    public synchronized void setLastSeenSeason(String s) { this.lastSeenSeason = s; }

    public synchronized List<FacilityType> getFacilities() { return new ArrayList<>(facilities); }
    public synchronized void addFacility(FacilityType f) { facilities.add(f); }
    public synchronized int getFacilityCount() { return facilities.size(); }
    public synchronized void removeFacility(FacilityType type) {
        facilities.remove(type); // removes first occurrence
    }

    public synchronized Contract getActiveContract() { return activeContract; }
    public synchronized void setActiveContract(Contract c) { this.activeContract = c; }
    public synchronized Contract getPendingContractOffer() { return pendingContractOffer; }
    public synchronized void setPendingContractOffer(Contract c) { this.pendingContractOffer = c; }
    public synchronized int getTicksSinceLastOffer() { return ticksSinceLastOffer; }
    public synchronized void setTicksSinceLastOffer(int v) { this.ticksSinceLastOffer = v; }

    public synchronized String getTipResult(String rumourId) {
        return tipResults.get(rumourId);
    }
    public synchronized void setTipResult(String rumourId, String result) {
        tipResults.put(rumourId, result);
    }
    public synchronized void removeTipResultsNotIn(Set<String> activeIds) {
        tipResults.keySet().retainAll(activeIds);
    }
    public synchronized Map<String, String> getTipResults() {
        return new HashMap<>(tipResults);
    }

    public synchronized Map<String, Integer> getContrabandHoldings() { return new HashMap<>(contrabandHoldings); }
    public synchronized int getContrabandHolding(String good) { return contrabandHoldings.getOrDefault(good, 0); }
    public synchronized void addContrabandHolding(String good, int qty) {
        contrabandHoldings.merge(good, qty, Integer::sum);
    }
    public synchronized void clearContrabandHoldings() { contrabandHoldings.clear(); }
    public synchronized boolean hasContraband() { return !contrabandHoldings.isEmpty(); }

    public synchronized List<BlackMarketOffer> getBlackMarketOffers() {
        return blackMarketOffers == null ? null : new ArrayList<>(blackMarketOffers);
    }
    public synchronized void setBlackMarketOffers(List<BlackMarketOffer> offers) { this.blackMarketOffers = offers; }
    public synchronized int getBlackMarketTicksRemaining() { return blackMarketTicksRemaining; }
    public synchronized void setBlackMarketTicksRemaining(int v) { this.blackMarketTicksRemaining = v; }
    public synchronized int getBlackMarketTicksSinceLastRoll() { return blackMarketTicksSinceLastRoll; }
    public synchronized void setBlackMarketTicksSinceLastRoll(int v) { this.blackMarketTicksSinceLastRoll = v; }

    public synchronized String getLastFlashMessage() { return lastFlashMessage; }
    public synchronized void setLastFlashMessage(String msg) { this.lastFlashMessage = msg; }
}
