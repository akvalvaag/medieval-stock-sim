// src/test/java/com/medievalmarket/service/ScoreboardServiceTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import com.medievalmarket.model.ScoreboardEntry;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ScoreboardServiceTest {

    private ScoreboardService service = new ScoreboardService();

    @Test
    void rankedByNetWorthDescending() {
        Portfolio rich = new Portfolio("1", "Rich", PlayerClass.NOBLE);    // 1000g
        Portfolio poor = new Portfolio("2", "Poor",  PlayerClass.MINER);   // 350g

        List<ScoreboardEntry> board = service.compute(List.of(rich, poor), Map.of());
        assertThat(board.get(0).getName()).isEqualTo("Rich");
        assertThat(board.get(1).getName()).isEqualTo("Poor");
    }

    @Test
    void netWorthIncludesHoldingValue() {
        Portfolio p = new Portfolio("1", "Trader", PlayerClass.NOBLE); // 1000g
        p.setHolding("Iron", 2); // 2 * 40g = 80g
        Map<String, Double> prices = Map.of("Iron", 40.0);

        List<ScoreboardEntry> board = service.compute(List.of(p), prices);
        assertThat(board.get(0).getNetWorth()).isEqualTo(1080.0);
    }

    @Test
    void top3GainersMarkedAsUp() {
        List<Portfolio> portfolios = List.of(
            makePortfolioWithTrend("A", 100.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("B", 50.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("C", 30.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("D", -20.0, PlayerClass.NOBLE)
        );

        List<ScoreboardEntry> board = service.compute(portfolios, Map.of());
        long upCount = board.stream().filter(e -> "UP".equals(e.getTrending())).count();
        assertThat(upCount).isEqualTo(3);
    }

    @Test
    void top3LosersMarkedAsDown() {
        List<Portfolio> portfolios = List.of(
            makePortfolioWithTrend("A", 10.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("B", -10.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("C", -50.0, PlayerClass.NOBLE),
            makePortfolioWithTrend("D", -100.0, PlayerClass.NOBLE)
        );

        List<ScoreboardEntry> board = service.compute(portfolios, Map.of());
        long downCount = board.stream().filter(e -> "DOWN".equals(e.getTrending())).count();
        assertThat(downCount).isEqualTo(3);
    }

    private Portfolio makePortfolioWithTrend(String name, double trendAmount, PlayerClass pc) {
        Portfolio p = new Portfolio("id-" + name, name, pc);
        double base = p.getGold();
        for (int i = 0; i < 5; i++) p.recordNetWorth(base);
        p.recordNetWorth(base + trendAmount);
        return p;
    }
}
