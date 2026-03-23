package com.medievalmarket.service;

import com.medievalmarket.dto.MarketTickPayload;
import com.medievalmarket.model.Good;
import com.medievalmarket.model.ScoreboardEntry;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MarketEngine {

    private final GoodsCatalogue catalogue;
    private final PriceModel priceModel;
    private final EventEngine eventEngine;
    private final PriceHistory priceHistory;
    private final SessionRegistry sessionRegistry;
    private final ScoreboardService scoreboardService;
    private final SimpMessagingTemplate messagingTemplate;

    public MarketEngine(GoodsCatalogue catalogue, PriceModel priceModel,
                        EventEngine eventEngine, PriceHistory priceHistory,
                        SessionRegistry sessionRegistry, ScoreboardService scoreboardService,
                        SimpMessagingTemplate messagingTemplate) {
        this.catalogue = catalogue;
        this.priceModel = priceModel;
        this.eventEngine = eventEngine;
        this.priceHistory = priceHistory;
        this.sessionRegistry = sessionRegistry;
        this.scoreboardService = scoreboardService;
        this.messagingTemplate = messagingTemplate;
    }

    @Scheduled(fixedRate = 5000)
    public void tick() {
        // 1. Maybe fire an event
        EventEngine.FiredEvent event = eventEngine.maybeFireEvent();
        Map<String, Double> eventModifiers = event != null ? event.modifiers() : Map.of();

        // 2. Reprice all goods
        Map<String, Double> prices = new LinkedHashMap<>();
        for (Good good : catalogue.getGoods()) {
            double modifier = eventModifiers.getOrDefault(good.getName(), 0.0);
            double newPrice = priceModel.computeNewPrice(good, modifier);
            good.setCurrentPrice(newPrice);
            priceHistory.append(good.getName(), newPrice);
            prices.put(good.getName(), Math.round(newPrice * 10.0) / 10.0);
        }

        // 3. Decay supply pressure (after all goods are repriced)
        catalogue.getGoods().forEach(Good::decaySupplyPressure);

        // 4. Compute scoreboard
        List<ScoreboardEntry> scoreboard = scoreboardService.compute(
            sessionRegistry.getAllActiveSessions(), prices);

        // 5. Broadcast
        MarketTickPayload payload = new MarketTickPayload(
            prices,
            priceHistory.getAllHistory(),
            event != null ? event.message() : null,
            scoreboard,
            "SPRING",   // placeholder — SeasonEngine wired in Task 7
            60          // placeholder
        );
        messagingTemplate.convertAndSend("/topic/market", payload);
    }
}
