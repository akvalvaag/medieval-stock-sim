package com.medievalmarket.model;

public enum PlayerClass {
    MERCHANT(500.0, 0.0),
    MINER(350.0, 0.03),
    NOBLE(1000.0, 0.03);

    private final double startGold;
    private final double feeRate;

    PlayerClass(double startGold, double feeRate) {
        this.startGold = startGold;
        this.feeRate = feeRate;
    }

    public double getStartGold() { return startGold; }
    public double getFeeRate() { return feeRate; }
}
