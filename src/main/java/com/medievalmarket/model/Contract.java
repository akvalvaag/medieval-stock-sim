package com.medievalmarket.model;

import java.util.Map;

public class Contract {
    private final String patronName;
    private final String flavourText;
    private final Map<String, Integer> requirements;
    /** Original deadline in ticks — immutable, used as the displayed/loop limit. */
    private final int ticksRemaining;
    /** Live countdown, decremented each tick. */
    private int ticksLeft;
    private final double rewardGold;
    private final double penaltyGold;

    public Contract(String patronName, String flavourText, Map<String, Integer> requirements,
                    int ticksRemaining, double rewardGold, double penaltyGold) {
        this.patronName = patronName;
        this.flavourText = flavourText;
        this.requirements = requirements;
        this.ticksRemaining = ticksRemaining;
        this.ticksLeft = ticksRemaining;
        this.rewardGold = rewardGold;
        this.penaltyGold = penaltyGold;
    }

    public String getPatronName() { return patronName; }
    public String getFlavourText() { return flavourText; }
    public Map<String, Integer> getRequirements() { return requirements; }
    /** Returns the original deadline tick count (immutable). For live countdown use {@link #getTicksLeft()}. */
    public int getTicksRemaining() { return ticksRemaining; }
    /** Returns the live countdown — decremented each tick. */
    public int getTicksLeft() { return ticksLeft; }
    public void decrementTick() { ticksLeft--; }
    public double getRewardGold() { return rewardGold; }
    public double getPenaltyGold() { return penaltyGold; }
}
