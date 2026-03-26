package com.medievalmarket.controller;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.ContractService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

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
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        return ResponseEntity.ok(Map.of(
            "activeContract", p.getActiveContract(),
            "pendingContractOffer", p.getPendingContractOffer()
        ));
    }

    @PostMapping("/accept")
    public ResponseEntity<?> accept(@RequestHeader("X-Session-Id") String sessionId) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        contractService.accept(p);
        return ResponseEntity.ok(Map.of("activeContract", p.getActiveContract()));
    }

    @PostMapping("/decline")
    public ResponseEntity<?> decline(@RequestHeader("X-Session-Id") String sessionId) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        contractService.decline(p);
        return ResponseEntity.ok(Map.of());
    }

    @PostMapping("/deliver")
    public ResponseEntity<?> deliver(@RequestHeader("X-Session-Id") String sessionId) {
        Portfolio p = sessionRegistry.findOrThrow(sessionId);
        contractService.deliver(p);
        return ResponseEntity.ok(Map.of("gold", p.getGold(), "holdings", p.getHoldings()));
    }
}
