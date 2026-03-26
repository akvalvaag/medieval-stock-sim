package com.medievalmarket.controller;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.BlackMarketService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

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
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        return ResponseEntity.ok(Map.of(
            "offers", p.getBlackMarketOffers() == null ? List.of() : p.getBlackMarketOffers(),
            "contrabandHoldings", p.getContrabandHoldings()
        ));
    }

    @PostMapping("/buy")
    public ResponseEntity<?> buy(@RequestHeader("X-Session-Id") String sessionId,
                                 @RequestBody Map<String, Object> body) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        String goodName = (String) body.get("goodName");
        int qty = ((Number) body.get("quantity")).intValue();
        blackMarketService.buy(p, goodName, qty);
        List<Object> offers = p.getBlackMarketOffers() == null ? List.of() : List.copyOf(p.getBlackMarketOffers());
        return ResponseEntity.ok(Map.of("gold", p.getGold(), "contrabandHoldings", p.getContrabandHoldings(), "blackMarketOffers", offers));
    }

    @PostMapping("/sell")
    public ResponseEntity<?> sell(@RequestHeader("X-Session-Id") String sessionId,
                                  @RequestBody Map<String, Object> body) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        String goodName = (String) body.get("goodName");
        int qty = ((Number) body.get("quantity")).intValue();
        blackMarketService.sell(p, goodName, qty);
        return ResponseEntity.ok(Map.of("gold", p.getGold(), "contrabandHoldings", p.getContrabandHoldings()));
    }
}
