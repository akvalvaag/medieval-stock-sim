package com.medievalmarket.model;

public record LimitOrder(
    String id,
    String sessionId,
    String goodName,
    String direction,    // "BUY" or "SELL"
    int quantity,
    double targetPrice
) {}
