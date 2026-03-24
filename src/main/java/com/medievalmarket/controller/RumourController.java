package com.medievalmarket.controller;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.model.Rumour;
import com.medievalmarket.service.RumourService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/rumours")
public class RumourController {

    private final RumourService rumourService;
    private final SessionRegistry sessionRegistry;

    public RumourController(RumourService rumourService, SessionRegistry sessionRegistry) {
        this.rumourService = rumourService;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping
    public ResponseEntity<?> getRumours(@RequestHeader("X-Session-Id") String sessionId) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        List<Map<String, Object>> rumours = opt.get().getRumours().stream()
            .map(r -> Map.<String, Object>of(
                "id", r.getId(),
                "text", r.getText(),
                "eventKey", r.getEventKey(),
                "ticksRemaining", r.getTicksRemaining(),
                "tipResult", r.getTipResult() == null ? "" : r.getTipResult()
            )).toList();
        return ResponseEntity.ok(rumours);
    }

    @PostMapping("/{id}/tip")
    public ResponseEntity<?> tip(@RequestHeader("X-Session-Id") String sessionId,
                                 @PathVariable String id) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        try {
            String result = rumourService.tip(opt.get(), id);
            return ResponseEntity.ok(Map.of("tipResult", result, "gold", opt.get().getGold()));
        } catch (RumourService.RumourException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
