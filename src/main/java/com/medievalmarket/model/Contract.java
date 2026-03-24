package com.medievalmarket.model;

import java.util.Map;

public class Contract {
    private final String patronName;
    private final String flavourText;
    private final Map<String, Integer> requirements;
    private int ticksRemaining;
    private final double rewardGold;
    private final double penaltyGold;

    public Contract(String patronName, String flavourText, Map<String, Integer> requirements,
                    int ticksRemaining, double rewardGold, double penaltyGold) {
        this.patronName = patronName;
        this.flavourText = flavourText;
        this.requirements = requirements;
        this.ticksRemaining = ticksRemaining;
        this.rewardGold = rewardGold;
        this.penaltyGold = penaltyGold;
    }

    public String getPatronName() { return patronName; }
    public String getFlavourText() { return flavourText; }
    public Map<String, Integer> getRequirements() { return requirements; }
    public int getTicksRemaining() { return ticksRemaining; }
    public void decrementTick() { ticksRemaining--; }
    public double getRewardGold() { return rewardGold; }
    public double getPenaltyGold() { return penaltyGold; }
}
