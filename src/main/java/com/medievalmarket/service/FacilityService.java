package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.springframework.stereotype.Component;
import java.util.Collection;

@Component
public class FacilityService {

    private final GoodsCatalogue catalogue;
    private volatile int tickCount = 0;

    public FacilityService(GoodsCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    public static class FacilityException extends RuntimeException {
        public FacilityException(String msg) { super(msg); }
    }

    public void build(Portfolio p, FacilityType type) {
        if (p.getGold() < type.getBuildCost())
            throw new FacilityException("INSUFFICIENT_FUNDS");
        if (p.getFacilityCount() >= 15)
            throw new FacilityException("FACILITY_CAP_REACHED");
        p.setGold(p.getGold() - type.getBuildCost());
        p.addFacility(type);
    }

    public void demolish(Portfolio p, FacilityType type) {
        synchronized (p) {
            if (!p.getFacilities().contains(type))
                throw new FacilityException("FACILITY_NOT_FOUND");
            p.removeFacility(type);
            p.setGold(p.getGold() + type.getBuildCost() * 0.5);
        }
    }

    public int getTicksUntilProduction() {
        return 5 - (tickCount % 5);
        // Returns 5 when production just fired (tickCount % 5 == 0); counts down 4,3,2,1 otherwise.
    }

    /** For tests — increments counter once per call. */
    public void processTick(Portfolio p) {
        tickCount++;
        if (tickCount % 5 != 0) return;
        runProduction(p);
    }

    /** For MarketEngine — increments counter ONCE per market tick, runs all portfolios. */
    public void processAll(Collection<Portfolio> portfolios) {
        tickCount++;
        if (tickCount % 5 != 0) return;
        portfolios.forEach(this::runProduction);
    }

    private void runProduction(Portfolio p) {
        boolean doubled = p.getGuild() == Guild.ALCHEMISTS_SOCIETY;
        for (FacilityType facility : p.getFacilities()) {
            if (!hasInputs(p, facility)) continue;
            consumeInputs(p, facility);
            int outputQty = facility.getOutputQty() * (doubled ? 2 : 1);
            p.setHolding(facility.getOutputGood(), p.getHolding(facility.getOutputGood()) + outputQty);
            Good outputGood = catalogue.findByName(facility.getOutputGood());
            outputGood.addSupplyPressure(outputQty * 0.02);
        }
    }

    private boolean hasInputs(Portfolio p, FacilityType type) {
        return type.getInputs().entrySet().stream()
            .allMatch(e -> p.getHolding(e.getKey()) >= e.getValue());
    }

    private void consumeInputs(Portfolio p, FacilityType type) {
        type.getInputs().forEach((good, qty) ->
            p.setHolding(good, p.getHolding(good) - qty));
    }
}
