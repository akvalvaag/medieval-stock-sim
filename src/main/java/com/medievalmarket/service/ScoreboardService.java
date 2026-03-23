// src/main/java/com/medievalmarket/service/ScoreboardService.java
package com.medievalmarket.service;

import com.medievalmarket.model.Portfolio;
import com.medievalmarket.model.ScoreboardEntry;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ScoreboardService {

    public List<ScoreboardEntry> compute(Collection<Portfolio> portfolios,
                                         Map<String, Double> prices) {
        record Snapshot(Portfolio portfolio, double netWorth, double trend) {}

        List<Snapshot> snapshots = portfolios.stream().map(p -> {
            double holdingsValue = p.getHoldings().entrySet().stream()
                .mapToDouble(e -> e.getValue() * prices.getOrDefault(e.getKey(), 0.0))
                .sum();
            double netWorth = p.getGold() + holdingsValue;
            double trend = p.getTrend();
            p.recordNetWorth(netWorth);
            return new Snapshot(p, netWorth, trend);
        }).collect(Collectors.toList());

        snapshots.sort(Comparator.comparingDouble(Snapshot::netWorth).reversed());

        List<Snapshot> byTrendAsc = new ArrayList<>(snapshots);
        byTrendAsc.sort(Comparator.comparingDouble(Snapshot::trend));
        int size = byTrendAsc.size();
        Set<String> losers = byTrendAsc.subList(0, Math.min(3, size)).stream()
            .map(s -> s.portfolio().getSessionId()).collect(Collectors.toSet());
        Set<String> gainers = byTrendAsc.subList(Math.max(0, size - 3), size).stream()
            .map(s -> s.portfolio().getSessionId()).collect(Collectors.toSet());

        return snapshots.stream().map(s -> {
            String trending;
            if (gainers.contains(s.portfolio().getSessionId()) && s.trend() > 0) {
                trending = "UP";
            } else if (losers.contains(s.portfolio().getSessionId()) && s.trend() < 0) {
                trending = "DOWN";
            } else {
                trending = "NEUTRAL";
            }
            return new ScoreboardEntry(
                s.portfolio().getPlayerName(),
                s.portfolio().getPlayerClass().name(),
                Math.round(s.netWorth() * 10.0) / 10.0,
                Math.round(s.trend() * 10.0) / 10.0,
                trending
            );
        }).collect(Collectors.toList());
    }
}
