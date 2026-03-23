package com.medievalmarket.dto;
public record AddOrderRequest(
    String sessionId, String goodName, String direction,
    int quantity, double targetPrice
) {}
