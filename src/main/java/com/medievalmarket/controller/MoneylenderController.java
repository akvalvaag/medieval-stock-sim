package com.medievalmarket.controller;

import com.medievalmarket.dto.BorrowRequest;
import com.medievalmarket.dto.LoanResponse;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.MoneylenderService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/moneylender")
public class MoneylenderController {

    private final SessionRegistry registry;
    private final MoneylenderService moneylenderService;

    public MoneylenderController(SessionRegistry registry, MoneylenderService moneylenderService) {
        this.registry = registry;
        this.moneylenderService = moneylenderService;
    }

    @PostMapping("/borrow")
    public ResponseEntity<?> borrow(@RequestBody BorrowRequest request) {
        Portfolio p = registry.findById(request.sessionId()).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        try {
            moneylenderService.borrow(p, request.amount());
        } catch (MoneylenderService.LoanException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(new LoanResponse(p.getLoanAmount(), p.getGold()));
    }

    @PostMapping("/repay")
    public ResponseEntity<?> repay(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        if (sessionId == null) return ResponseEntity.badRequest().body(Map.of("error", "MISSING_SESSION"));
        Portfolio p = registry.findById(sessionId).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        moneylenderService.repay(p);
        return ResponseEntity.ok(new LoanResponse(p.getLoanAmount(), p.getGold()));
    }
}
