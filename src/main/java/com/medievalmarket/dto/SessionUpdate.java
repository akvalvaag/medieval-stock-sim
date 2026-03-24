package com.medievalmarket.dto;

import com.medievalmarket.model.BlackMarketOffer;
import com.medievalmarket.model.Contract;
import com.medievalmarket.model.ExoticImportOffer;
import com.medievalmarket.model.FacilityType;
import com.medievalmarket.model.Guild;
import com.medievalmarket.model.LimitOrder;
import java.util.List;
import java.util.Map;

public class SessionUpdate {
    private double gold;
    private List<LimitOrder> limitOrders;
    private double loanAmount;
    private List<LimitOrderFill> limitOrderFills;

    // Guild fields
    private Guild guild;
    private Guild pendingGuildOffer;
    private ExoticImportOffer exoticImportOffer;
    private int fenceCooldown;

    // Facility fields
    private List<FacilityType> facilities;

    // Contract fields
    private Contract activeContract;
    private Contract pendingContractOffer;

    // Rumour fields
    private List<RumourDTO> rumours;

    // Black market fields
    private List<BlackMarketOffer> blackMarketOffers;
    private Map<String, Integer> contrabandHoldings;

    // Flash message
    private String flashMessage;

    private SessionUpdate() {}

    public double getGold() { return gold; }
    public List<LimitOrder> getLimitOrders() { return limitOrders; }
    public double getLoanAmount() { return loanAmount; }
    public List<LimitOrderFill> getLimitOrderFills() { return limitOrderFills; }
    public Guild getGuild() { return guild; }
    public Guild getPendingGuildOffer() { return pendingGuildOffer; }
    public ExoticImportOffer getExoticImportOffer() { return exoticImportOffer; }
    public int getFenceCooldown() { return fenceCooldown; }
    public List<FacilityType> getFacilities() { return facilities; }
    public Contract getActiveContract() { return activeContract; }
    public Contract getPendingContractOffer() { return pendingContractOffer; }
    public List<RumourDTO> getRumours() { return rumours; }
    public List<BlackMarketOffer> getBlackMarketOffers() { return blackMarketOffers; }
    public Map<String, Integer> getContrabandHoldings() { return contrabandHoldings; }
    public String getFlashMessage() { return flashMessage; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final SessionUpdate su = new SessionUpdate();
        public Builder gold(double v) { su.gold = v; return this; }
        public Builder limitOrders(List<LimitOrder> v) { su.limitOrders = v; return this; }
        public Builder loanAmount(double v) { su.loanAmount = v; return this; }
        public Builder limitOrderFills(List<LimitOrderFill> v) { su.limitOrderFills = v; return this; }
        public Builder guild(Guild v) { su.guild = v; return this; }
        public Builder pendingGuildOffer(Guild v) { su.pendingGuildOffer = v; return this; }
        public Builder exoticImportOffer(ExoticImportOffer v) { su.exoticImportOffer = v; return this; }
        public Builder fenceCooldown(int v) { su.fenceCooldown = v; return this; }
        public Builder facilities(List<FacilityType> v) { su.facilities = v; return this; }
        public Builder activeContract(Contract v) { su.activeContract = v; return this; }
        public Builder pendingContractOffer(Contract v) { su.pendingContractOffer = v; return this; }
        public Builder rumours(List<RumourDTO> v) { su.rumours = v; return this; }
        public Builder blackMarketOffers(List<BlackMarketOffer> v) { su.blackMarketOffers = v; return this; }
        public Builder contrabandHoldings(Map<String, Integer> v) { su.contrabandHoldings = v; return this; }
        public Builder flashMessage(String v) { su.flashMessage = v; return this; }
        public SessionUpdate build() { return su; }
    }

    /** DTO for rumours — excludes isTrue field (not exposed to client). */
    public record RumourDTO(String id, String text, String eventKey, int ticksRemaining, String tipResult) {}
}
