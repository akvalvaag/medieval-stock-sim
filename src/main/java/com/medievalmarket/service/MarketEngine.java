package com.medievalmarket.service;

import com.medievalmarket.dto.LimitOrderFill;
import com.medievalmarket.dto.MarketTickPayload;
import com.medievalmarket.dto.SessionUpdate;
import com.medievalmarket.model.Good;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.model.Rumour;
import com.medievalmarket.model.ScoreboardEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

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
    private final GuildService guildService;
    private final FacilityService facilityService;
    private final ContractService contractService;
    private final RumourService rumourService;
    private final BlackMarketService blackMarketService;

    public MarketEngine(GoodsCatalogue catalogue, PriceModel priceModel,
                        EventEngine eventEngine, PriceHistory priceHistory,
                        SessionRegistry sessionRegistry, ScoreboardService scoreboardService,
                        SimpMessagingTemplate messagingTemplate, SeasonEngine seasonEngine,
                        BotService botService, LimitOrderService limitOrderService,
                        MoneylenderService moneylenderService,
                        GuildService guildService, FacilityService facilityService,
                        ContractService contractService, RumourService rumourService,
                        BlackMarketService blackMarketService) {
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
        this.guildService = guildService;
        this.facilityService = facilityService;
        this.contractService = contractService;
        this.rumourService = rumourService;
        this.blackMarketService = blackMarketService;
    }

    @Scheduled(fixedRate = 5000)
    public void tick() {
        try {
            // 1. Advance season
            seasonEngine.advanceTick();
            Map<String, Double> seasonMods = seasonEngine.getModifiers();

            // 2. Maybe fire an event
            Set<String> boostedKeys = rumourService.getRumours().stream()
                .filter(Rumour::isTrue)
                .map(Rumour::getEventKey)
                .collect(Collectors.toSet());
            EventEngine.FiredEvent event = eventEngine.maybeFireEvent(boostedKeys);
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

            // 9. Per-session services (guild offers, black market flash messages)
            String currentSeason = seasonEngine.getCurrentSeason();
            String firedEventKey = event != null ? event.key() : null;
            for (Portfolio p : humans) {
                guildService.processTick(p, currentSeason);
                String flashMsg = blackMarketService.processTick(p);
                if (flashMsg != null) p.setLastFlashMessage(flashMsg);
            }
            facilityService.processAll(humans);
            contractService.processAll(humans);
            rumourService.processTick();
            // Notify RumourService of any fired event before collecting active IDs,
            // so the just-fired rumour is excluded from the snapshot and its tip
            // results are cleaned up in the same tick.
            if (firedEventKey != null) {
                rumourService.onEventFired(firedEventKey);
            }
            Set<String> activeRumourIds = rumourService.getRumours().stream()
                .map(Rumour::getId)
                .collect(Collectors.toSet());
            humans.forEach(p -> p.removeTipResultsNotIn(activeRumourIds));

            // 10. Compute scoreboard (includes bots)
            List<ScoreboardEntry> scoreboard = scoreboardService.compute(
                sessionRegistry.getAllActiveSessions(), prices);

            // 11. Broadcast global market state
            MarketTickPayload payload = new MarketTickPayload(
                prices,
                priceHistory.getAllHistory(),
                event != null ? event.message() : null,
                firedEventKey,
                scoreboard,
                seasonEngine.getCurrentSeason(),
                seasonEngine.getTicksRemaining(),
                null
            );
            messagingTemplate.convertAndSend("/topic/market", payload);

            // 12. Push per-session updates
            int ticksUntilProduction = facilityService.getTicksUntilProduction();
            for (Portfolio p : humans) {
                // Build rumour DTOs (omit isTrue — client must not know)
                List<SessionUpdate.RumourDTO> rumourDTOs = rumourService.getRumours().stream()
                    .map(r -> new SessionUpdate.RumourDTO(r.getId(), r.getText(), r.getEventKey(),
                                                           r.getTicksRemaining(), p.getTipResult(r.getId())))
                    .toList();

                SessionUpdate update = SessionUpdate.builder()
                    .gold(p.getGold())
                    .limitOrders(p.getLimitOrders())
                    .loanAmount(p.getLoanAmount())
                    .limitOrderFills(fills.getOrDefault(p.getSessionId(), List.of()))
                    .guild(p.getGuild())
                    .pendingGuildOffer(p.getPendingGuildOffer())
                    .exoticImportOffer(p.getExoticImportOffer())
                    .fenceCooldown(p.getFenceCooldown())
                    .facilities(p.getFacilities())
                    .haltedFacilities(p.getHaltedFacilities())
                    .holdings(p.getHoldings())
                    .costBasis(p.getAllCostBasis())
                    .ticksUntilProduction(ticksUntilProduction)
                    .activeContract(p.getActiveContract())
                    .pendingContractOffer(p.getPendingContractOffer())
                    .rumours(rumourDTOs)
                    .blackMarketOffers(p.getBlackMarketOffers())
                    .contrabandHoldings(p.getContrabandHoldings())
                    .contrabandAge(p.getContrabandAges())
                    .flashMessage(p.getLastFlashMessage())
                    .build();
                messagingTemplate.convertAndSendToUser(p.getSessionId(), "/queue/updates", update);
                p.setLastFlashMessage(null); // consume flash message after sending
            }
        } catch (Exception e) {
            log.error("Market tick failed", e);
            messagingTemplate.convertAndSend("/topic/market",
                new MarketTickPayload(null, null, null, null, null, null, 0, e.getMessage()));
        }
    }
}
