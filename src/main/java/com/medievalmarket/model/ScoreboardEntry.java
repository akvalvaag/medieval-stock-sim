package com.medievalmarket.model;

public class ScoreboardEntry {
    private final String name;
    private final String playerClass;
    private final double netWorth;
    private final double trend;
    private final String trending;

    public ScoreboardEntry(String name, String playerClass, double netWorth,
                           double trend, String trending) {
        this.name = name;
        this.playerClass = playerClass;
        this.netWorth = netWorth;
        this.trend = trend;
        this.trending = trending;
    }

    public String getName() { return name; }
    public String getPlayerClass() { return playerClass; }
    public double getNetWorth() { return netWorth; }
    public double getTrend() { return trend; }
    public String getTrending() { return trending; }
}
