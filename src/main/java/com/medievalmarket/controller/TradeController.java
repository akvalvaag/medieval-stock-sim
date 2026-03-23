package com.medievalmarket.controller;

import com.medievalmarket.dto.TradeRequest;
import com.medievalmarket.dto.TradeResponse;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.SeasonEngine;
import com.medievalmarket.service.SessionRegistry;
import com.medievalmarket.service.TradeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/trade")
public class TradeController {

    private final SessionRegistry registry;
    private final TradeService tradeService;
    private final SeasonEngine seasonEngine;

    public TradeController(SessionRegistry registry, TradeService tradeService, SeasonEngine seasonEngine) {
        this.registry = registry;
        this.tradeService = tradeService;
        this.seasonEngine = seasonEngine;
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buy(@RequestBody TradeRequest request) {
        return execute(request, true);
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sell(@RequestBody TradeRequest request) {
        return execute(request, false);
    }

    private static final java.util.regex.Pattern UUID_PATTERN =
        java.util.regex.Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private ResponseEntity<?> execute(TradeRequest request, boolean isBuy) {
        String sid = request.sessionId();
        if (sid == null || sid.isBlank() || !UUID_PATTERN.matcher(sid).matches()) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION_ID"));
        }
        Portfolio portfolio = registry.findById(sid).orElse(null);
        if (portfolio == null) {
            return ResponseEntity.status(404).body(Map.of("error", "SESSION_NOT_FOUND"));
        }
        double realizedPnl = 0.0;
        try {
            if (isBuy) {
                tradeService.buy(portfolio, request.good(), request.quantity());
            } else {
                double avgCost = portfolio.getAvgCostBasis(request.good());
                double goldBefore = portfolio.getGold();
                tradeService.sell(portfolio, request.good(), request.quantity());
                double proceeds = portfolio.getGold() - goldBefore;
                realizedPnl = proceeds - (avgCost * request.quantity());
            }
        } catch (TradeService.TradeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "UNKNOWN_GOOD"));
        }
        return ResponseEntity.ok(new TradeResponse(
            portfolio.getGold(),
            portfolio.getHoldings(),
            portfolio.getAllCostBasis(),
            realizedPnl,
            portfolio.getLoanAmount(),
            seasonEngine.getCurrentSeason()
        ));
    }
}
