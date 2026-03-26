package com.medievalmarket.service;

import com.medievalmarket.model.Portfolio;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class BotService {

    private static final int BOT_COUNT = 50;
    private static final int TRADE_EVERY_N_TICKS = 3;

    private final SessionRegistry sessionRegistry;
    private final GoodsCatalogue catalogue;
    private final TradeService tradeService;
    private final NameGenerator nameGenerator;
    private final List<Portfolio> bots = new ArrayList<>();
    private final AtomicInteger tickCounter = new AtomicInteger(0);

    public BotService(SessionRegistry sessionRegistry, GoodsCatalogue catalogue,
                      TradeService tradeService, NameGenerator nameGenerator) {
        this.sessionRegistry = sessionRegistry;
        this.catalogue = catalogue;
        this.tradeService = tradeService;
        this.nameGenerator = nameGenerator;
    }

    @PostConstruct
    void init() {
        for (int i = 0; i < BOT_COUNT; i++) {
            String name = "[bot] " + nameGenerator.generate();
            bots.add(sessionRegistry.registerBot(name));
        }
    }

    public void processTick() {
        if (tickCounter.incrementAndGet() % TRADE_EVERY_N_TICKS != 0) return;

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        var goods = catalogue.getGoods();

        for (Portfolio bot : bots) {
            var good = goods.get(rng.nextInt(goods.size()));
            double price = good.getCurrentPrice();
            double base = good.getBasePrice();

            try {
                if (price < base * 0.80 && bot.getGold() >= price) {
                    int qty = rng.nextInt(1, 6);
                    tradeService.buy(bot, good.getName(), qty);
                } else if (price > base * 1.20 && bot.getHolding(good.getName()) > 0) {
                    int held = bot.getHolding(good.getName());
                    int qty = Math.min(held, rng.nextInt(1, 6));
                    tradeService.sell(bot, good.getName(), qty);
                } else {
                    if (rng.nextBoolean() && bot.getGold() >= price) {
                        tradeService.buy(bot, good.getName(), rng.nextInt(1, 4));
                    } else if (bot.getHolding(good.getName()) > 0) {
                        tradeService.sell(bot, good.getName(), 1);
                    }
                }
            } catch (ServiceException ignored) {
                // Bot simply skips this tick if it can't trade
            }
        }
    }
}
