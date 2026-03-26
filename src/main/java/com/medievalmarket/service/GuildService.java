package com.medievalmarket.service;

import com.medievalmarket.model.*;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class GuildService {

    private static final List<String> RARE_GOODS = List.of("Gems", "Spices", "Elixir", "Silver");
    private final GoodsCatalogue catalogue;

    public GuildService(GoodsCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    public static class GuildException extends ServiceException {
        public GuildException(String msg) { super(msg); }
    }

    public void processTick(Portfolio p, String currentSeason) {
        if (p.getGuildOfferCooldown() > 0) {
            p.setGuildOfferCooldown(p.getGuildOfferCooldown() - 1);
        }
        if (p.getFenceCooldown() > 0) {
            p.setFenceCooldown(p.getFenceCooldown() - 1);
        }

        String lastSeason = p.getLastSeenSeason();
        if (lastSeason != null && !lastSeason.equals(currentSeason)) {
            if (p.getGuild() == Guild.ROYAL_WARRANT) {
                double stipend = 50.0 + ThreadLocalRandom.current().nextDouble() * 50.0;
                p.setGold(p.getGold() + stipend);
            }
            if (p.getGuild() == Guild.SEA_TRADERS) {
                ThreadLocalRandom rng = ThreadLocalRandom.current();
                String rareName = RARE_GOODS.get(rng.nextInt(RARE_GOODS.size()));
                int qty = 1 + rng.nextInt(10); // 1–10 units
                double price = catalogue.findByName(rareName).getCurrentPrice() * 0.60;
                p.setExoticImportOffer(new ExoticImportOffer(rareName, qty, price));
            }
        }
        p.setLastSeenSeason(currentSeason);

        if (p.getGuild() == null && p.getPendingGuildOffer() == null
                && p.getGuildOfferCooldown() == 0 && p.getGold() >= 1000.0) {
            Guild offer = randomGuildExcluding(p.getLastOfferedGuild());
            p.setPendingGuildOffer(offer);
        }
    }

    public void accept(Portfolio p) {
        if (p.getPendingGuildOffer() == null) throw new GuildException("No pending guild offer");
        p.setGuild(p.getPendingGuildOffer());
        p.setPendingGuildOffer(null);
    }

    public void decline(Portfolio p) {
        if (p.getPendingGuildOffer() == null) throw new GuildException("No pending guild offer");
        p.setLastOfferedGuild(p.getPendingGuildOffer());
        p.setPendingGuildOffer(null);
        p.setGuildOfferCooldown(30);
    }

    public void fence(Portfolio p, String goodName, int qty) {
        if (p.getGuild() != Guild.THIEVES_GUILD)
            throw new GuildException("Only Thieves' Guild members can fence goods");
        if (p.getFenceCooldown() > 0)
            throw new GuildException("Fence on cooldown: " + p.getFenceCooldown() + " ticks remaining");
        if (p.getHolding(goodName) < qty)
            throw new GuildException("Insufficient holdings");
        double price = catalogue.findByName(goodName).getCurrentPrice();
        double saleValue = price * qty * 1.10;
        p.setGold(p.getGold() + saleValue);
        p.setHolding(goodName, p.getHolding(goodName) - qty);
        p.setFenceCooldown(20);
    }

    public void buyExoticImport(Portfolio p) {
        ExoticImportOffer offer = p.getExoticImportOffer();
        if (offer == null) throw new GuildException("No exotic import available");
        double totalCost = offer.discountedPrice() * offer.quantity();
        if (p.getGold() < totalCost) throw new GuildException("Insufficient funds");
        p.setGold(p.getGold() - totalCost);
        p.updateCostBasis(offer.goodName(), offer.quantity(), offer.discountedPrice());
        p.setHolding(offer.goodName(), p.getHolding(offer.goodName()) + offer.quantity());
        p.setExoticImportOffer(null);
    }

    private Guild randomGuildExcluding(Guild exclude) {
        Guild[] values = Guild.values();
        Guild pick;
        do {
            pick = values[ThreadLocalRandom.current().nextInt(values.length)];
        } while (pick == exclude);
        return pick;
    }
}
