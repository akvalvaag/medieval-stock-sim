package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class BlackMarketService {

    public static class BlackMarketException extends RuntimeException {
        public BlackMarketException(String msg) { super(msg); }
    }

    private static final List<String> MARKET_GOODS = List.of(
        "Grain","Wool","Iron","Coal","Gems","Salt","Silver","Copper",
        "Timber","Cloth","Leather","Spices","Herbs","Honey","Ale","Fish",
        "Bread","Weapons","Wine","Soap","Elixir"
    );

    private final GoodsCatalogue catalogue;

    public BlackMarketService(GoodsCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    /** Returns a confiscation flash message, or null if no confiscation. */
    public String processTick(Portfolio p) {
        // Decrement existing panel lifetime
        if (p.getBlackMarketOffers() != null) {
            p.setBlackMarketTicksRemaining(p.getBlackMarketTicksRemaining() - 1);
            if (p.getBlackMarketTicksRemaining() <= 0 || p.getBlackMarketOffers().isEmpty()) {
                p.setBlackMarketOffers(null);
            }
        }

        // Roll for appearance every 30 ticks
        p.setBlackMarketTicksSinceLastRoll(p.getBlackMarketTicksSinceLastRoll() + 1);
        if (p.getBlackMarketTicksSinceLastRoll() >= 30 && p.getBlackMarketOffers() == null) {
            p.setBlackMarketTicksSinceLastRoll(0);
            if (ThreadLocalRandom.current().nextDouble() < 0.15) {
                p.setBlackMarketOffers(generateOffers());
                p.setBlackMarketTicksRemaining(10);
            }
        }

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
        double total = offer.discountedPrice() * qty;
        if (p.getGold() < total) throw new BlackMarketException("Insufficient funds");
        if (offer.availableQty() < qty) throw new BlackMarketException("Insufficient quantity available");
        p.setGold(p.getGold() - total);
        p.addContrabandHolding(goodName, qty);
        // Replace offer with reduced qty or remove if fully purchased
        List<BlackMarketOffer> updated = new ArrayList<>(offers);
        updated.remove(offer);
        int remaining = offer.availableQty() - qty;
        if (remaining > 0) updated.add(new BlackMarketOffer(goodName, remaining, offer.discountedPrice()));
        p.setBlackMarketOffers(updated.isEmpty() ? null : updated);
    }

    private List<BlackMarketOffer> generateOffers() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int count = 1 + rng.nextInt(3); // 1-3 offers
        List<String> shuffled = new ArrayList<>(MARKET_GOODS);
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
