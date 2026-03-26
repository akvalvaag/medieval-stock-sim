package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class BlackMarketService {

    public static class BlackMarketException extends ServiceException {
        public BlackMarketException(String msg) { super(msg); }
    }

    private final GoodsCatalogue catalogue;
    private final List<String> marketGoods;

    public BlackMarketService(GoodsCatalogue catalogue) {
        this.catalogue = catalogue;
        this.marketGoods = catalogue.getGoods().stream().map(Good::getName).toList();
    }

    /** Returns a confiscation flash message, or null if no confiscation. */
    public String processTick(Portfolio p) {
        // Decrement existing panel lifetime
        if (p.getBlackMarketOffers() != null) {
            p.setBlackMarketTicksRemaining(p.getBlackMarketTicksRemaining() - 1);
            if (p.getBlackMarketTicksRemaining() <= 0) {
                p.setBlackMarketOffers(null);
                p.setBlackMarketTicksSinceLastRoll(0); // reset counter when panel closes
            }
        }

        // Only count ticks and roll when no offers active
        if (p.getBlackMarketOffers() == null) {
            p.setBlackMarketTicksSinceLastRoll(p.getBlackMarketTicksSinceLastRoll() + 1);
            if (p.getBlackMarketTicksSinceLastRoll() >= 30) {
                p.setBlackMarketTicksSinceLastRoll(0);
                if (ThreadLocalRandom.current().nextDouble() < 0.15) {
                    p.setBlackMarketOffers(generateOffers());
                    p.setBlackMarketTicksRemaining(10);
                }
            }
        }

        // Age contraband each tick
        p.incrementContrabandAges();

        // Confiscation roll (3%) if contraband held and not Thieves' Guild
        if (p.hasContraband() && p.getGuild() != Guild.THIEVES_GUILD) {
            if (ThreadLocalRandom.current().nextDouble() < 0.03) {
                p.clearContrabandHoldings();
                return "Guards have seized your contraband goods!";
            }
        }
        return null;
    }

    public void buy(Portfolio p, String goodName, int qty) {
        List<BlackMarketOffer> offers = p.getBlackMarketOffers();
        if (offers == null) throw new BlackMarketException("No black market active");
        BlackMarketOffer offer = offers.stream()
            .filter(o -> o.goodName().equals(goodName))
            .findFirst()
            .orElseThrow(() -> new BlackMarketException("No offer for: " + goodName));
        if (offer.availableQty() < qty) throw new BlackMarketException("Insufficient quantity available");
        double total = offer.discountedPrice() * qty;
        if (p.getGold() < total) throw new BlackMarketException("Insufficient funds");
        p.setGold(p.getGold() - total);
        boolean alreadyHeld = p.getContrabandHolding(goodName) > 0;
        p.addContrabandHolding(goodName, qty);
        if (!alreadyHeld) p.setContrabandAge(goodName, 0);
        // Replace offer with reduced qty or remove if fully purchased
        List<BlackMarketOffer> updated = new ArrayList<>(offers);
        updated.remove(offer);
        int remaining = offer.availableQty() - qty;
        if (remaining > 0) updated.add(new BlackMarketOffer(goodName, remaining, offer.discountedPrice()));
        p.setBlackMarketOffers(updated.isEmpty() ? null : updated);
    }

    public static final int HOLDING_PERIOD = 5;

    public void sell(Portfolio p, String goodName, int qty) {
        synchronized (p) {
            int held = p.getContrabandHolding(goodName);
            if (held == 0) throw new BlackMarketException("No contraband held: " + goodName);
            if (held < qty) throw new BlackMarketException("Insufficient contraband quantity");
            if (p.getContrabandAge(goodName) < HOLDING_PERIOD)
                throw new BlackMarketException("CONTRABAND_NOT_AGED");
            double price = catalogue.findByName(goodName).getCurrentPrice();
            p.removeContrabandHolding(goodName, qty);
            p.setGold(p.getGold() + price * qty);
        }
    }

    private List<BlackMarketOffer> generateOffers() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = 1 + rng.nextInt(3); // 1-3 offers
        List<String> shuffled = new ArrayList<>(marketGoods);
        Collections.shuffle(shuffled);
        List<BlackMarketOffer> offers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String good = shuffled.get(i);
            double marketPrice = catalogue.findByName(good).getCurrentPrice();
            double discount = 0.40 + rng.nextDouble() * 0.20; // 40-60% of market
            double price = marketPrice * discount;
            int qty = 5 + rng.nextInt(16); // 5-20 units
            offers.add(new BlackMarketOffer(good, qty, price));
        }
        return offers;
    }
}
