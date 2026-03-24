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
                                     String rewardGood,
                                     int baseTicks) {}

    private static final List<ContractTemplate> TEMPLATES = List.of(
        new ContractTemplate("The Miller", "The mill runs day and night. Flour and preservation are needed.",
            Map.of("Grain", 7, "Salt", 4), "Bread", 60),
        new ContractTemplate("Master Blacksmith", "The forge burns hot. Iron and fuel must be supplied.",
            Map.of("Iron", 6, "Coal", 6), "Iron", 60),
        new ContractTemplate("The Vintner", "The vintner prepares his finest vintage for the season.",
            Map.of("Grain", 10, "Honey", 4), "Wine", 60),
        new ContractTemplate("Court Apothecary", "The apothecary's stores run low. Lives depend on timely delivery.",
            Map.of("Herbs", 7, "Honey", 3), "Elixir", 60),
        new ContractTemplate("The Shipwright", "The shipwright races to complete the hull before the tide turns.",
            Map.of("Timber", 8, "Rope", 6), "Timber", 60),
        new ContractTemplate("Merchant Guild", "The guild requires diverse stock to supply the autumn fair.",
            Map.of("Grain", 4, "Wool", 4, "Iron", 3), "Silver", 60)
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
                throw new ContractException("Requirements not met");
        }
        c.getRequirements().forEach((good, qty) ->
            p.setHolding(good, p.getHolding(good) - qty));
        double reward = c.getRewardGold();
        if (p.getGuild() == Guild.ROYAL_WARRANT) reward *= 1.6;
        p.setGold(p.getGold() + reward);
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
        int ticks = 40 + rng.nextInt(41); // 40–80 ticks
        double premiumRate = 1.15 + rng.nextDouble() * 0.20; // 1.15–1.35
        int totalQty = reqs.values().stream().mapToInt(Integer::intValue).sum();
        double rewardGoodPrice = catalogue.findByName(tmpl.rewardGood()).getCurrentPrice();
        double reward = rewardGoodPrice * totalQty * premiumRate;
        reward = reward * (0.80 + rng.nextDouble() * 0.40); // ±20%
        double penalty = Math.max(0.0, reward * 0.15);
        return new Contract(tmpl.patronName(), tmpl.flavourText(), reqs, ticks, reward, penalty);
    }
}
