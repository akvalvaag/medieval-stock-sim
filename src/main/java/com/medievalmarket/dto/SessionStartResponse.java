package com.medievalmarket.dto;
import java.util.Map;
public record SessionStartResponse(
    String sessionId,
    String playerName,
    String playerClass,
    double gold,
    Map<String, Integer> holdings,
    Map<String, Double> costBasis
) {}
