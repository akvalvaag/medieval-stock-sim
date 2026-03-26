package com.medievalmarket.controller;

import com.medievalmarket.model.FacilityType;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.FacilityService;
import com.medievalmarket.service.ServiceException;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

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
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        facilityService.build(p, parseType(body.get("type")));
        return ResponseEntity.ok(Map.of("facilities", p.getFacilities(), "gold", p.getGold()));
    }

    @PostMapping("/demolish")
    public ResponseEntity<?> demolish(@RequestHeader("X-Session-Id") String sessionId,
                                      @RequestBody Map<String, String> body) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        facilityService.demolish(p, parseType(body.get("type")));
        return ResponseEntity.ok(Map.of("facilities", p.getFacilities(), "gold", p.getGold(),
                                        "haltedFacilities", p.getHaltedFacilities()));
    }

    @PostMapping("/toggle-halt")
    public ResponseEntity<?> toggleHalt(@RequestHeader("X-Session-Id") String sessionId,
                                        @RequestBody Map<String, String> body) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        FacilityType type = parseType(body.get("type"));
        if (!p.getFacilities().contains(type)) throw new FacilityService.FacilityException("FACILITY_NOT_FOUND");
        p.toggleHalt(type);
        return ResponseEntity.ok(Map.of("haltedFacilities", p.getHaltedFacilities()));
    }

    private static FacilityType parseType(String s) {
        try {
            return FacilityType.valueOf(s);
        } catch (IllegalArgumentException e) {
            throw new ServiceException("INVALID_FACILITY_TYPE");
        }
    }
}
