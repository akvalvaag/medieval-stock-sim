package com.medievalmarket.dto;
import com.medievalmarket.model.LimitOrder;
import java.util.List;
import java.util.Map;
public record SessionStartResponse(
    String sessionId,
    String playerName,
    String playerClass,
    double gold,
    Map<String, Integer> holdings,
    Map<String, Double> costBasis,
    String season,
    int seasonTicksRemaining,
    double loanAmount,
    List<LimitOrder> limitOrders
) {}
