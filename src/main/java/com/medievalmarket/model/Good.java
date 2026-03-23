package com.medievalmarket.model;

public class Good {
    public enum Volatility { LOW, MEDIUM, HIGH }

    private final String name;
    private final String category;
    private final double basePrice;
    private final Volatility volatility;

    // Mutable state — access must be synchronized on this object
    private double currentPrice;
    private double supplyPressure = 0.0;

    public Good(String name, String category, double basePrice, Volatility volatility) {
        this.name = name;
        this.category = category;
        this.basePrice = basePrice;
        this.volatility = volatility;
        this.currentPrice = basePrice;
    }

    public String getName() { return name; }
    public String getCategory() { return category; }
    public double getBasePrice() { return basePrice; }
    public Volatility getVolatility() { return volatility; }

    public synchronized double getCurrentPrice() { return currentPrice; }
    public synchronized void setCurrentPrice(double p) { this.currentPrice = p; }

    public synchronized double getSupplyPressure() { return supplyPressure; }
    public synchronized void addSupplyPressure(double delta) { this.supplyPressure += delta; }
    public synchronized void decaySupplyPressure() { this.supplyPressure *= 0.70; }
}
