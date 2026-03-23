package com.medievalmarket.service;

import com.medievalmarket.dto.LimitOrderFill;
import com.medievalmarket.dto.MarketTickPayload;
import com.medievalmarket.dto.SessionUpdate;
import com.medievalmarket.model.Good;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.model.ScoreboardEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MarketEngine {

    private static final Logger log = LoggerFactory.getLogger(MarketEngine.class);
    private final GoodsCatalogue catalogue;
    private final PriceModel priceModel;
    private final EventEngine eventEngine;
    private final PriceHistory priceHistory;
    private final SessionRegistry sessionRegistry;
    private final ScoreboardService scoreboardService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SeasonEngine seasonEngine;
    private final BotService botService;
    private final LimitOrderService limitOrderService;
    private final MoneylenderService moneylenderService;
    private final WarehousingService warehousingService;

    public MarketEngine(GoodsCatalogue catalogue, PriceModel priceModel,
                        EventEngine eventEngine, PriceHistory priceHistory,
                        SessionRegistry sessionRegistry, ScoreboardService scoreboardService,
                        SimpMessagingTemplate messagingTemplate, SeasonEngine seasonEngine,
                        BotService botService, LimitOrderService limitOrderService,
                        MoneylenderService moneylenderService, WarehousingService warehousingService) {
        this.catalogue = catalogue;
        this.priceModel = priceModel;
        this.eventEngine = eventEngine;
        this.priceHistory = priceHistory;
        this.sessionRegistry = sessionRegistry;
        this.scoreboardService = scoreboardService;
        this.messagingTemplate = messagingTemplate;
        this.seasonEngine = seasonEngine;
        this.botService = botService;
        this.limitOrderService = limitOrderService;
        this.moneylenderService = moneylenderService;
        this.warehousingService = warehousingService;
    }

    @Scheduled(fixedRate = 5000)
    public void tick() {
        try {
            // 1. Advance season
            seasonEngine.advanceTick();
            Map<String, Double> seasonMods = seasonEngine.getModifiers();

            // 2. Maybe fire an event
            EventEngine.FiredEvent event = eventEngine.maybeFireEvent();
            Map<String, Double> eventModifiers = event != null ? event.modifiers() : Map.of();

            // 3. Reprice all goods (event modifier + season nudge)
            Map<String, Double> prices = new LinkedHashMap<>();
            for (Good good : catalogue.getGoods()) {
                double eventMod = eventModifiers.getOrDefault(good.getName(), 0.0);
                double seasonNudge = seasonMods.getOrDefault(good.getName(), 0.0) / 100.0;
                double newPrice = priceModel.computeNewPrice(good, eventMod + seasonNudge);
                good.setCurrentPrice(newPrice);
                priceHistory.append(good.getName(), newPrice);
                prices.put(good.getName(), Math.round(newPrice * 10.0) / 10.0);
            }

            // 4. Decay supply pressure
            catalogue.getGoods().forEach(Good::decaySupplyPressure);

            // 5. Bots trade
            botService.processTick();

            // 6–8. Per-human-session services
            Collection<Portfolio> humans = sessionRegistry.getHumanPortfolios();
            Map<String, List<LimitOrderFill>> fills = limitOrderService.processAll(humans);
            moneylenderService.processAll(humans);
            warehousingService.processAll(humans);

            // 9. Compute scoreboard (includes bots)
            List<ScoreboardEntry> scoreboard = scoreboardService.compute(
                sessionRegistry.getAllActiveSessions(), prices);

            // 10. Broadcast global market state
            MarketTickPayload payload = new MarketTickPayload(
                prices,
                priceHistory.getAllHistory(),
                event != null ? event.message() : null,
                scoreboard,
                seasonEngine.getCurrentSeason(),
                seasonEngine.getTicksRemaining()
            );
            messagingTemplate.convertAndSend("/topic/market", payload);

            // 11. Push per-session updates
            for (Portfolio p : humans) {
                SessionUpdate update = new SessionUpdate(
                    p.getGold(),
                    p.getLimitOrders(),
                    p.getLoanAmount(),
                    fills.getOrDefault(p.getSessionId(), List.of())
                );
                messagingTemplate.convertAndSendToUser(p.getSessionId(), "/queue/updates", update);
            }
        } catch (Exception e) {
            log.error("Market tick failed", e);
        }
    }
}
