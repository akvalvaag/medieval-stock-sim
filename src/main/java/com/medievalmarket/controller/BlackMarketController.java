package com.medievalmarket.controller;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.BlackMarketService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/blackmarket")
public class BlackMarketController {

    private final BlackMarketService blackMarketService;
    private final SessionRegistry sessionRegistry;

    public BlackMarketController(BlackMarketService blackMarketService, SessionRegistry sessionRegistry) {
        this.blackMarketService = blackMarketService;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping
    public ResponseEntity<?> get(@RequestHeader("X-Session-Id") String sessionId) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        Portfolio p = opt.get();
        return ResponseEntity.ok(Map.of(
            "offers", p.getBlackMarketOffers() == null ? List.of() : p.getBlackMarketOffers(),
            "contrabandHoldings", p.getContrabandHoldings()
        ));
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buy(@RequestHeader("X-Session-Id") String sessionId,
                                 @RequestBody Map<String, Object> body) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        String goodName = (String) body.get("goodName");
        Object qtyObj = body.get("quantity");
        if (goodName == null || qtyObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing goodName or quantity"));
        }
        int qty = ((Number) qtyObj).intValue();
        try {
            blackMarketService.buy(opt.get(), goodName, qty);
            Portfolio p = opt.get();
            return ResponseEntity.ok(Map.of("gold", p.getGold(), "contrabandHoldings", p.getContrabandHoldings()));
        } catch (BlackMarketService.BlackMarketException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
