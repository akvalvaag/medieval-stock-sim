package com.medievalmarket.dto;
import java.util.List;
import java.util.Map;
public record MarketSnapshotResponse(
    Map<String, Double> prices,
    Map<String, List<Double>> history
) {}
