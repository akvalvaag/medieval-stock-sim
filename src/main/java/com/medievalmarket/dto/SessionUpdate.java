package com.medievalmarket.dto;
import com.medievalmarket.model.LimitOrder;
import java.util.List;
public record SessionUpdate(
    double gold,
    List<LimitOrder> limitOrders,
    double loanAmount,
    List<LimitOrderFill> limitOrderFills
) {}
