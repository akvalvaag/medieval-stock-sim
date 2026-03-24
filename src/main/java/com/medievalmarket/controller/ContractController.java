package com.medievalmarket.controller;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.ContractService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/contract")
public class ContractController {

    private final ContractService contractService;
    private final SessionRegistry sessionRegistry;

    public ContractController(ContractService contractService, SessionRegistry sessionRegistry) {
        this.contractService = contractService;
        this.sessionRegistry = sessionRegistry;
    }

    @GetMapping
    public ResponseEntity<?> get(@RequestHeader("X-Session-Id") String sessionId) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        Portfolio p = opt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("activeContract", p.getActiveContract());
        result.put("pendingOffer", p.getPendingContractOffer());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/accept")
    public ResponseEntity<?> accept(@RequestHeader("X-Session-Id") String sessionId) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        try {
            contractService.accept(opt.get());
            return ResponseEntity.ok(Map.of());
        } catch (ContractService.ContractException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/decline")
    public ResponseEntity<?> decline(@RequestHeader("X-Session-Id") String sessionId) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        try {
            contractService.decline(opt.get());
            return ResponseEntity.ok(Map.of());
        } catch (ContractService.ContractException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/deliver")
    public ResponseEntity<?> deliver(@RequestHeader("X-Session-Id") String sessionId) {
        Optional<Portfolio> opt = sessionRegistry.findById(sessionId);
        if (opt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "INVALID_SESSION"));
        try {
            contractService.deliver(opt.get());
            return ResponseEntity.ok(Map.of("gold", opt.get().getGold()));
        } catch (ContractService.ContractException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
