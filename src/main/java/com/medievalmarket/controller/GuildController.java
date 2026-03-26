package com.medievalmarket.controller;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.GuildService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/guild")
public class GuildController {

    private final GuildService guildService;
    private final SessionRegistry sessionRegistry;

    public GuildController(GuildService guildService, SessionRegistry sessionRegistry) {
        this.guildService = guildService;
        this.sessionRegistry = sessionRegistry;
    }

    @PostMapping("/accept")
    public ResponseEntity<?> accept(@RequestHeader("X-Session-Id") String sessionId) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        guildService.accept(p);
        return ResponseEntity.ok(Map.of("guild", p.getGuild()));
    }

    @PostMapping("/decline")
    public ResponseEntity<?> decline(@RequestHeader("X-Session-Id") String sessionId) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        guildService.decline(p);
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/fence")
    public ResponseEntity<?> fence(@RequestHeader("X-Session-Id") String sessionId,
                                   @RequestBody Map<String, Object> body) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        String goodName = (String) body.get("goodName");
        int qty = ((Number) body.get("quantity")).intValue();
        guildService.fence(p, goodName, qty);
        return ResponseEntity.ok(Map.of("gold", p.getGold()));
    }

    @PostMapping("/exotic-import/buy")
    public ResponseEntity<?> buyExoticImport(@RequestHeader("X-Session-Id") String sessionId) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        guildService.buyExoticImport(p);
        return ResponseEntity.ok(Map.of("gold", p.getGold(), "holdings", p.getHoldings()));
    }
}
