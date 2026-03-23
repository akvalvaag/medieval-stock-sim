package com.medievalmarket.dto;
public record LimitOrderFill(
    String goodName,
    String direction,
    int quantity,
    double executedPrice,
    double realizedPnl
) {}
