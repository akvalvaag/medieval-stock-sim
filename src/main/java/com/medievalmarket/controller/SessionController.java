package com.medievalmarket.controller;

import com.medievalmarket.dto.SessionStartRequest;
import com.medievalmarket.dto.SessionStartResponse;
import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionRegistry registry;

    public SessionController(SessionRegistry registry) { this.registry = registry; }

    @PostMapping("/start")
    public ResponseEntity<?> start(@RequestBody SessionStartRequest request) {
        PlayerClass playerClass;
        try {
            playerClass = PlayerClass.valueOf(request.playerClass().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_CLASS"));
        }
        Portfolio p = registry.createSession(playerClass);
        return ResponseEntity.ok(new SessionStartResponse(
            p.getSessionId(), p.getPlayerName(), p.getPlayerClass().name(),
            p.getGold(), p.getHoldings(), p.getAllCostBasis()));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<?> resume(@PathVariable String sessionId) {
        return registry.findById(sessionId)
            .map(p -> ResponseEntity.ok(new SessionStartResponse(
                p.getSessionId(), p.getPlayerName(), p.getPlayerClass().name(),
                p.getGold(), p.getHoldings(), p.getAllCostBasis())))
            .orElse(ResponseEntity.notFound().build());
    }
}
