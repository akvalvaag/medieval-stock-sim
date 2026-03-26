package com.medievalmarket.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Portfolio {

    // ── Identity ──────────────────────────────────────────────────────────────
    private final String sessionId;
    private final String playerName;
    private final PlayerClass playerClass;
    private final boolean bot;

    // ── Core financial state ──────────────────────────────────────────────────
    private double gold;
    private final Map<String, Integer> holdings    = new HashMap<>();
    private final Map<String, Double>  avgCostBasis = new HashMap<>();
    private final Deque<Double> netWorthHistory    = new ArrayDeque<>();
    private Instant lastTradeTime = Instant.now();
    private double loanAmount = 0.0;
    private final List<LimitOrder> limitOrders = new ArrayList<>();

    // ── Rumour tips ───────────────────────────────────────────────────────────
    private final Map<String, String> tipResults = new HashMap<>();

    // ── Flash message ─────────────────────────────────────────────────────────
    private String lastFlashMessage = null;

    // ── Game-system state (grouped into inner holders) ────────────────────────
    private final GuildState       guildState    = new GuildState();
    private final FacilityState    facilityState = new FacilityState();
    private final ContractState    contractState = new ContractState();
    private final BlackMarketState bmState       = new BlackMarketState();

    // ── Inner state holders ───────────────────────────────────────────────────

    static class GuildState {
        Guild guild             = null;
        Guild pendingOffer      = null;
        Guild lastOffered       = null;
        int   offerCooldown     = 0;
        int   fenceCooldown     = 0;
        ExoticImportOffer exoticImportOffer = null;
        String lastSeenSeason   = null;
    }

    static class FacilityState {
        final List<FacilityType> list   = new ArrayList<>();
        final Set<FacilityType>  halted = new HashSet<>();
    }

    static class ContractState {
        Contract active             = null;
        Contract pendingOffer       = null;
        int      ticksSinceLastOffer = 0;
    }

    static class BlackMarketState {
        final Map<String, Integer> holdings    = new HashMap<>();
        final Map<String, Integer> contrabandAge = new HashMap<>();
        List<BlackMarketOffer> offers          = null;
        int ticksRemaining                     = 0;
        int ticksSinceLastRoll                 = 0;
    }

    // ── Constructors ──────────────────────────────────────────────────────────

    public Portfolio(String sessionId, String playerName, PlayerClass playerClass) {
        this(sessionId, playerName, playerClass, false);
    }

    public Portfolio(String sessionId, String playerName, PlayerClass playerClass, boolean bot) {
        this.sessionId   = sessionId;
        this.playerName  = playerName;
        this.playerClass = playerClass;
        this.gold        = playerClass.getStartGold();
        this.bot         = bot;
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    public String      getSessionId()   { return sessionId; }
    public String      getPlayerName()  { return playerName; }
    public PlayerClass getPlayerClass() { return playerClass; }
    public boolean     isBot()          { return bot; }

    // ── Gold ──────────────────────────────────────────────────────────────────

    public synchronized double getGold()            { return gold; }
    public synchronized void   setGold(double gold) { this.gold = gold; }

    // ── Holdings ──────────────────────────────────────────────────────────────

    public synchronized Map<String, Integer> getHoldings()               { return new HashMap<>(holdings); }
    public synchronized int                  getHolding(String good)     { return holdings.getOrDefault(good, 0); }
    public synchronized void setHolding(String good, int qty) {
        if (qty == 0) holdings.remove(good);
        else          holdings.put(good, qty);
    }

    // ── Cost basis ────────────────────────────────────────────────────────────

    public synchronized double getAvgCostBasis(String good)    { return avgCostBasis.getOrDefault(good, 0.0); }
    public synchronized Map<String, Double> getAllCostBasis()   { return new HashMap<>(avgCostBasis); }

    /** Updates running weighted-average cost per unit (before fees). */
    public synchronized void updateCostBasis(String good, int addedQty, double pricePerUnit) {
        int    existingQty = holdings.getOrDefault(good, 0);
        double existingAvg = avgCostBasis.getOrDefault(good, 0.0);
        double newAvg = (existingAvg * existingQty + pricePerUnit * addedQty) / (existingQty + addedQty);
        avgCostBasis.put(good, newAvg);
    }

    public synchronized void clearCostBasis(String good) { avgCostBasis.remove(good); }

    // ── Net worth history ─────────────────────────────────────────────────────

    public synchronized void recordNetWorth(double netWorth) {
        if (netWorthHistory.size() >= 10) netWorthHistory.pollFirst();
        netWorthHistory.addLast(netWorth);
    }

    public synchronized double getTrend() {
        if (netWorthHistory.size() < 2) return 0.0;
        double   current    = netWorthHistory.peekLast();
        Object[] arr        = netWorthHistory.toArray();
        int      compareIdx = Math.max(0, arr.length - 6);
        return current - (double) arr[compareIdx];
    }

    // ── Session timing ────────────────────────────────────────────────────────

    public synchronized Instant getLastTradeTime()    { return lastTradeTime; }
    public synchronized void    touchLastTradeTime()  { this.lastTradeTime = Instant.now(); }

    // ── Loan ──────────────────────────────────────────────────────────────────

    public synchronized double getLoanAmount()            { return loanAmount; }
    public synchronized void   setLoanAmount(double v)   { this.loanAmount = v; }

    // ── Limit orders ──────────────────────────────────────────────────────────

    public synchronized List<LimitOrder> getLimitOrders()          { return new ArrayList<>(limitOrders); }
    public synchronized void             addLimitOrder(LimitOrder o) { limitOrders.add(o); }
    public synchronized void             removeLimitOrder(String id) { limitOrders.removeIf(o -> o.id().equals(id)); }
    public synchronized int              limitOrderCount()           { return limitOrders.size(); }

    // ── Guild ─────────────────────────────────────────────────────────────────

    public synchronized Guild getGuild()                    { return guildState.guild; }
    public synchronized void  setGuild(Guild g)             { guildState.guild = g; }
    public synchronized Guild getPendingGuildOffer()        { return guildState.pendingOffer; }
    public synchronized void  setPendingGuildOffer(Guild g) { guildState.pendingOffer = g; }
    public synchronized Guild getLastOfferedGuild()         { return guildState.lastOffered; }
    public synchronized void  setLastOfferedGuild(Guild g)  { guildState.lastOffered = g; }
    public synchronized int   getGuildOfferCooldown()       { return guildState.offerCooldown; }
    public synchronized void  setGuildOfferCooldown(int v)  { guildState.offerCooldown = v; }
    public synchronized int   getFenceCooldown()            { return guildState.fenceCooldown; }
    public synchronized void  setFenceCooldown(int v)       { guildState.fenceCooldown = v; }
    public synchronized ExoticImportOffer getExoticImportOffer()        { return guildState.exoticImportOffer; }
    public synchronized void              setExoticImportOffer(ExoticImportOffer o) { guildState.exoticImportOffer = o; }
    public synchronized String getLastSeenSeason()          { return guildState.lastSeenSeason; }
    public synchronized void   setLastSeenSeason(String s)  { guildState.lastSeenSeason = s; }

    // ── Facilities ────────────────────────────────────────────────────────────

    public synchronized List<FacilityType> getFacilities()     { return new ArrayList<>(facilityState.list); }
    public synchronized void               addFacility(FacilityType f) { facilityState.list.add(f); }
    public synchronized int                getFacilityCount()  { return facilityState.list.size(); }
    public synchronized void removeFacility(FacilityType type) {
        facilityState.list.remove(type);
        if (!facilityState.list.contains(type)) facilityState.halted.remove(type);
    }

    public synchronized boolean          isHalted(FacilityType type)  { return facilityState.halted.contains(type); }
    public synchronized Set<FacilityType> getHaltedFacilities()        { return new HashSet<>(facilityState.halted); }
    public synchronized void toggleHalt(FacilityType type) {
        if (facilityState.halted.contains(type)) facilityState.halted.remove(type);
        else                                      facilityState.halted.add(type);
    }

    // ── Contracts ─────────────────────────────────────────────────────────────

    public synchronized Contract getActiveContract()             { return contractState.active; }
    public synchronized void     setActiveContract(Contract c)   { contractState.active = c; }
    public synchronized Contract getPendingContractOffer()       { return contractState.pendingOffer; }
    public synchronized void     setPendingContractOffer(Contract c) { contractState.pendingOffer = c; }
    public synchronized int      getTicksSinceLastOffer()        { return contractState.ticksSinceLastOffer; }
    public synchronized void     setTicksSinceLastOffer(int v)   { contractState.ticksSinceLastOffer = v; }

    // ── Rumour tips ───────────────────────────────────────────────────────────

    public synchronized String              getTipResult(String rumourId)              { return tipResults.get(rumourId); }
    public synchronized void                setTipResult(String rumourId, String result) { tipResults.put(rumourId, result); }
    public synchronized Map<String, String> getTipResults()                            { return new HashMap<>(tipResults); }
    public synchronized void removeTipResultsNotIn(Set<String> activeIds) {
        tipResults.keySet().retainAll(activeIds);
    }

    // ── Black market ──────────────────────────────────────────────────────────

    public synchronized Map<String, Integer> getContrabandHoldings()         { return new HashMap<>(bmState.holdings); }
    public synchronized int                  getContrabandHolding(String g)  { return bmState.holdings.getOrDefault(g, 0); }
    public synchronized void addContrabandHolding(String good, int qty)      { bmState.holdings.merge(good, qty, Integer::sum); }
    public synchronized void removeContrabandHolding(String good, int qty) {
        int current = bmState.holdings.getOrDefault(good, 0);
        if (current <= qty) { bmState.holdings.remove(good); bmState.contrabandAge.remove(good); }
        else                  bmState.holdings.put(good, current - qty);
    }
    public synchronized void    clearContrabandHoldings() { bmState.holdings.clear(); bmState.contrabandAge.clear(); }
    public synchronized boolean hasContraband()           { return !bmState.holdings.isEmpty(); }

    public synchronized int                  getContrabandAge(String good)    { return bmState.contrabandAge.getOrDefault(good, 0); }
    public synchronized Map<String, Integer> getContrabandAges()              { return new HashMap<>(bmState.contrabandAge); }
    public synchronized void                 setContrabandAge(String good, int age) { bmState.contrabandAge.put(good, age); }
    public synchronized void                 clearContrabandAge(String good)  { bmState.contrabandAge.remove(good); }
    public synchronized void                 incrementContrabandAges() {
        bmState.holdings.keySet().forEach(g -> bmState.contrabandAge.merge(g, 1, Integer::sum));
    }

    public synchronized List<BlackMarketOffer> getBlackMarketOffers()                 { return bmState.offers == null ? null : new ArrayList<>(bmState.offers); }
    public synchronized void                   setBlackMarketOffers(List<BlackMarketOffer> o) { bmState.offers = o; }
    public synchronized int  getBlackMarketTicksRemaining()     { return bmState.ticksRemaining; }
    public synchronized void setBlackMarketTicksRemaining(int v){ bmState.ticksRemaining = v; }
    public synchronized int  getBlackMarketTicksSinceLastRoll()     { return bmState.ticksSinceLastRoll; }
    public synchronized void setBlackMarketTicksSinceLastRoll(int v){ bmState.ticksSinceLastRoll = v; }

    // ── Flash message ─────────────────────────────────────────────────────────

    public synchronized String getLastFlashMessage()          { return lastFlashMessage; }
    public synchronized void   setLastFlashMessage(String msg){ this.lastFlashMessage = msg; }
}
