package com.medievalmarket.model;

public enum Guild {
    THIEVES_GUILD("Thieves' Guild",
        "Once per 20 ticks, sell any good at 110% market price with no fee or slippage. Immune to contraband confiscation."),
    SCHOLARS_GUILD("Scholars' Guild",
        "Rumour truth rate raised to 78%. Tip cost reduced to 5g."),
    SEA_TRADERS("Sea Traders' Guild",
        "Once per season, buy a rare good at 60% market price."),
    ROYAL_WARRANT("Royal Warrant",
        "Contracts pay ×1.6. Receive a 50–100g stipend each season."),
    ALCHEMISTS_SOCIETY("Alchemists' Society",
        "All owned facilities produce 2× output per production cycle.");

    private final String displayName;
    private final String description;

    Guild(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
