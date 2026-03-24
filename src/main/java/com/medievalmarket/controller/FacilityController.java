package com.medievalmarket.controller;

import com.medievalmarket.model.FacilityType;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.FacilityService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/facility")
public class FacilityController {

    private final FacilityService facilityService;
    private final SessionRegistry sessionRegistry;

    public FacilityController(FacilityService facilityService, SessionRegistry sessionRegistry) {
        this.facilityService = facilityService;
        this.sessionRegistry = sessionRegistry;
    }

    @PostMapping("/build")
    public ResponseEntity<?> build(@RequestHeader("X-Session-Id") String sessionId,
                                   @RequestBody Map<String, String> body) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        FacilityType type;
        try {
            type = FacilityType.valueOf(body.get("type"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_FACILITY_TYPE"));
        }
        try {
            facilityService.build(opt.get(), type);
            Portfolio p = opt.get();
            return ResponseEntity.ok(Map.of("facilities", p.getFacilities(), "gold", p.getGold()));
        } catch (FacilityService.FacilityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
