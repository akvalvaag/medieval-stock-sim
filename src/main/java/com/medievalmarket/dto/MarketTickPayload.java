package com.medievalmarket.dto;
import com.medievalmarket.model.ScoreboardEntry;
import java.util.List;
import java.util.Map;
public record MarketTickPayload(
    Map<String, Double> prices,
    Map<String, List<Double>> history,
    String event,
    String eventKey,
    List<ScoreboardEntry> scoreboard,
    String season,
    int seasonTicksRemaining,
    String tickError
) {}
