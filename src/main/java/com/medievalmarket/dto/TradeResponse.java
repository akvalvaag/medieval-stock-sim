package com.medievalmarket.dto;
import java.util.Map;
public record TradeResponse(
    double gold,
    Map<String, Integer> holdings,
    Map<String, Double> costBasis,
    double realizedPnl,
    double loanAmount,
    String currentSeason
) {}
