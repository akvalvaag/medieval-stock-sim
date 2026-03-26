package com.medievalmarket.controller;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.RumourService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        List<Map<String, Object>> rumours = rumourService.getRumours().stream()
            .map(r -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", r.getId());
                dto.put("text", r.getText());
                dto.put("eventKey", r.getEventKey());
                dto.put("ticksRemaining", r.getTicksRemaining());
                String tip = p.getTipResult(r.getId());
                dto.put("tipResult", tip == null ? "" : tip);
                return dto;
            }).toList();
        return ResponseEntity.ok(rumours);
    }

    @PostMapping("/{id}/tip")
    public ResponseEntity<?> tip(@RequestHeader("X-Session-Id") String sessionId,
                                 @PathVariable String id) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        String result = rumourService.tip(p, id);
        return ResponseEntity.ok(Map.of("tipResult", result, "gold", p.getGold()));
    }
}
