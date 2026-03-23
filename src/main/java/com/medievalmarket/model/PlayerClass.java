package com.medievalmarket.model;

public enum PlayerClass {
    MERCHANT(500.0, 0.0,  0.0025),
    MINER   (350.0, 0.03, 0.005),
    NOBLE   (1000.0,0.03, 0.005);

    private final double startGold;
    private final double feeRate;
    private final double warehousingRate;

    PlayerClass(double startGold, double feeRate, double warehousingRate) {
        this.startGold = startGold;
        this.feeRate = feeRate;
        this.warehousingRate = warehousingRate;
    }

    public double getStartGold() { return startGold; }
    public double getFeeRate() { return feeRate; }
    public double getWarehousingRate() { return warehousingRate; }
}
