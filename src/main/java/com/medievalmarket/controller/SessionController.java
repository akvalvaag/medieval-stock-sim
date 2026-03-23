package com.medievalmarket.controller;

import com.medievalmarket.dto.SessionStartRequest;
import com.medievalmarket.dto.SessionStartResponse;
import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.SeasonEngine;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionRegistry registry;
    private final SeasonEngine seasonEngine;

    public SessionController(SessionRegistry registry, SeasonEngine seasonEngine) {
        this.registry = registry;
        this.seasonEngine = seasonEngine;
    }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody SessionStartRequest request) {
        PlayerClass playerClass;
        try {
            playerClass = PlayerClass.valueOf(request.playerClass().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_CLASS"));
        }
        Portfolio p = registry.createSession(playerClass);
        return ResponseEntity.ok(toResponse(p));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<?> resume(@PathVariable String sessionId) {
        return registry.findById(sessionId)
            .map(p -> ResponseEntity.ok(toResponse(p)))
            .orElse(ResponseEntity.notFound().build());
    }

    private SessionStartResponse toResponse(Portfolio p) {
        return new SessionStartResponse(
            p.getSessionId(), p.getPlayerName(), p.getPlayerClass().name(),
            p.getGold(), p.getHoldings(), p.getAllCostBasis(),
            seasonEngine.getCurrentSeason(), seasonEngine.getTicksRemaining(),
            p.getLoanAmount(), p.getLimitOrders()
        );
    }
}
