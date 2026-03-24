package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ContractService {

    public static class ContractException extends RuntimeException {
        public ContractException(String msg) { super(msg); }
    }

    private record ContractTemplate(String patronName, String flavourText,
                                     Map<String, Integer> baseRequirements,
                                     int baseTicks, double premiumRate) {}

    private static final List<ContractTemplate> TEMPLATES = List.of(
        new ContractTemplate("The King", "His Majesty hosts a grand feast. He requires provisions.",
            Map.of("Bread", 30, "Wine", 20), 15, 0.35),
        new ContractTemplate("Baron Aldric", "The Baron marches to war. His army needs arming.",
            Map.of("Weapons", 25, "Rope", 20), 12, 0.40),
        new ContractTemplate("The Church", "The Church blesses the harvest. They seek offerings.",
            Map.of("Grain", 40, "Candles", 10), 10, 0.25),
        new ContractTemplate("Lady Matilda", "The Lady rebuilds her keep after the great fire.",
            Map.of("Timber", 30, "Stone", 15), 14, 0.30),
        new ContractTemplate("The Harbour Master", "The fleet departs at dawn and must be provisioned.",
            Map.of("Rope", 20, "Fish", 30, "Salt", 10), 12, 0.32),
        new ContractTemplate("The Apothecary Guild", "A sickness spreads through the city. Lives hang in the balance.",
            Map.of("Elixir", 15, "Herbs", 20), 10, 0.45)
    );

    private final GoodsCatalogue catalogue;

    public ContractService(GoodsCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    /** For tests — one call = one tick for the given portfolio. */
    public void processTick(Portfolio p) {
        tickPortfolio(p);
    }

    /** For MarketEngine — one call = one market tick for all portfolios. */
    public void processAll(Collection<Portfolio> portfolios) {
        portfolios.forEach(this::tickPortfolio);
    }

    private void tickPortfolio(Portfolio p) {
        if (p.getActiveContract() != null) {
            p.getActiveContract().decrementTick();
            if (p.getActiveContract().getTicksLeft() <= 0) {
                double penalty = p.getActiveContract().getPenaltyGold();
                p.setGold(Math.max(0.0, p.getGold() - penalty));
                p.setActiveContract(null);
                p.setTicksSinceLastOffer(0); // cooldown after expiry
            }
        }
        p.setTicksSinceLastOffer(p.getTicksSinceLastOffer() + 1);
        if (p.getTicksSinceLastOffer() >= 40
                && p.getActiveContract() == null
                && p.getPendingContractOffer() == null) {
            p.setPendingContractOffer(generateContract(p));
        }
    }

    public void accept(Portfolio p) {
        if (p.getPendingContractOffer() == null)
            throw new ContractException("No pending contract offer");
        p.setActiveContract(p.getPendingContractOffer());
        p.setPendingContractOffer(null);
        p.setTicksSinceLastOffer(0);
    }

    public void decline(Portfolio p) {
        if (p.getPendingContractOffer() == null)
            throw new ContractException("No pending contract offer");
        p.setPendingContractOffer(null);
        p.setTicksSinceLastOffer(0);
    }

    public void deliver(Portfolio p) {
        Contract c = p.getActiveContract();
        if (c == null) throw new ContractException("No active contract");
        for (Map.Entry<String, Integer> e : c.getRequirements().entrySet()) {
            if (p.getHolding(e.getKey()) < e.getValue())
                throw new ContractException("Cannot meet requirements: need more " + e.getKey());
        }
        c.getRequirements().forEach((good, qty) ->
            p.setHolding(good, p.getHolding(good) - qty));
        p.setGold(p.getGold() + c.getRewardGold());
        p.setActiveContract(null);
    }

    private Contract generateContract(Portfolio p) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        ContractTemplate tmpl = TEMPLATES.get(rng.nextInt(TEMPLATES.size()));
        Map<String, Integer> reqs = new LinkedHashMap<>();
        tmpl.baseRequirements().forEach((good, baseQty) -> {
            int qty = (int) Math.max(1, Math.round(baseQty * (0.80 + rng.nextDouble() * 0.40)));
            reqs.put(good, qty);
        });
        int ticks = (int) Math.max(5, Math.round(tmpl.baseTicks() * (0.80 + rng.nextDouble() * 0.40)));
        double reward = reqs.entrySet().stream()
            .mapToDouble(e -> catalogue.findByName(e.getKey()).getCurrentPrice() * e.getValue() * tmpl.premiumRate())
            .sum();
        if (p.getGuild() == Guild.ROYAL_WARRANT) reward *= 1.6;
        double penalty = Math.max(0.0, reward * 0.15);
        return new Contract(tmpl.patronName(), tmpl.flavourText(), reqs, ticks, reward, penalty);
    }
}
