package com.medievalmarket.controller;

import com.medievalmarket.dto.AddOrderRequest;
import com.medievalmarket.dto.OrderResponse;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.service.LimitOrderService;
import com.medievalmarket.service.SessionRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final SessionRegistry registry;
    private final LimitOrderService limitOrderService;

    public OrderController(SessionRegistry registry, LimitOrderService limitOrderService) {
        this.registry = registry;
        this.limitOrderService = limitOrderService;
    }

    @PostMapping
    public ResponseEntity<?> addOrder(@RequestBody AddOrderRequest request) {
        Portfolio p = registry.findById(request.sessionId()).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        try {
            limitOrderService.addOrder(p, request.goodName(), request.direction(),
                request.quantity(), request.targetPrice());
        } catch (LimitOrderService.LimitOrderException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "UNKNOWN_GOOD"));
        }
        return ResponseEntity.ok(new OrderResponse(p.getLimitOrders(), p.getGold()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> cancelOrder(@PathVariable String id,
                                         @RequestParam String sessionId) {
        Portfolio p = registry.findById(sessionId).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        p.removeLimitOrder(id);
        return ResponseEntity.ok(new OrderResponse(p.getLimitOrders(), p.getGold()));
    }
}
