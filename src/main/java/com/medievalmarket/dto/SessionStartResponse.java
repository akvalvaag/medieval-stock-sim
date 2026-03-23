package com.medievalmarket.dto;
import java.util.Map;
public record SessionStartResponse(
    String sessionId,
    String playerName,
    double gold,
    Map<String, Integer> holdings
) {}
