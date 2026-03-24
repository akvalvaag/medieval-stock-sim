package com.medievalmarket.controller;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.GuildService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

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
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        try {
            guildService.accept(opt.get());
            return ResponseEntity.ok(Map.of("guild", opt.get().getGuild()));
        } catch (GuildService.GuildException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/decline")
    public ResponseEntity<?> decline(@RequestHeader("X-Session-Id") String sessionId) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        try {
            guildService.decline(opt.get());
            return ResponseEntity.ok(Map.of());
        } catch (GuildService.GuildException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fence")
    public ResponseEntity<?> fence(@RequestHeader("X-Session-Id") String sessionId,
                                   @RequestBody Map<String, Object> body) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        String goodName = (String) body.get("goodName");
        int qty = ((Number) body.get("quantity")).intValue();
        try {
            guildService.fence(opt.get(), goodName, qty);
            return ResponseEntity.ok(Map.of("gold", opt.get().getGold()));
        } catch (GuildService.GuildException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/exotic-import/buy")
    public ResponseEntity<?> buyExoticImport(@RequestHeader("X-Session-Id") String sessionId) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        try {
            guildService.buyExoticImport(opt.get());
            Portfolio p = opt.get();
            return ResponseEntity.ok(Map.of("gold", p.getGold(), "holdings", p.getHoldings()));
        } catch (GuildService.GuildException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
