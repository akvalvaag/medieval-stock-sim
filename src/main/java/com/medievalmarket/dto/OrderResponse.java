package com.medievalmarket.dto;
import com.medievalmarket.model.LimitOrder;
import java.util.List;
public record OrderResponse(List<LimitOrder> orders, double gold) {}
