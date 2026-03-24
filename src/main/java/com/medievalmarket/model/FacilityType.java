package com.medievalmarket.model;

import java.util.Map;

public enum FacilityType {
    MILL("Mill", 150, Map.of("Grain", 3, "Salt", 1), "Bread", 2),
    FORGE("Forge", 400, Map.of("Iron", 2, "Coal", 1), "Weapons", 1),
    WINERY("Winery", 250, Map.of("Ale", 2, "Honey", 1), "Wine", 1),
    SOAPWORKS("Soapworks", 200, Map.of("Wax", 2, "Salt", 1), "Soap", 3),
    APOTHECARY("Apothecary", 500, Map.of("Herbs", 2, "Honey", 1), "Elixir", 1);

    private final String displayName;
    private final int buildCost;
    private final Map<String, Integer> inputs;
    private final String outputGood;
    private final int outputQty;

    FacilityType(String displayName, int buildCost, Map<String, Integer> inputs,
                 String outputGood, int outputQty) {
        this.displayName = displayName;
        this.buildCost = buildCost;
        this.inputs = inputs;
        this.outputGood = outputGood;
        this.outputQty = outputQty;
    }

    public String getDisplayName() { return displayName; }
    public int getBuildCost() { return buildCost; }
    public Map<String, Integer> getInputs() { return inputs; }
    public String getOutputGood() { return outputGood; }
    public int getOutputQty() { return outputQty; }
}
