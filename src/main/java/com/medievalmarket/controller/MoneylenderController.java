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
        Portfolio p = registry.findOrThrow(request.sessionId());
        moneylenderService.borrow(p, request.amount());
        return ResponseEntity.ok(new LoanResponse(p.getLoanAmount(), p.getGold()));
    }

    @PostMapping("/repay")
    public ResponseEntity<?> repay(@RequestBody Map<String, String> body) {
        Portfolio p = registry.findOrThrow(body.get("sessionId"));
        moneylenderService.repay(p);
        return ResponseEntity.ok(new LoanResponse(p.getLoanAmount(), p.getGold()));
    }
}
