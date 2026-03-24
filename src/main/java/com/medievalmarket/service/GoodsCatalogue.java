package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.Good.Volatility;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class GoodsCatalogue {

    private final List<Good> goods;

    public GoodsCatalogue() {
        goods = List.of(
            // Agriculture (8 original)
            new Good("Grain",     "Agriculture",    10.0, Volatility.LOW),
            new Good("Wool",      "Agriculture",    20.0, Volatility.LOW),
            new Good("Livestock", "Agriculture",    35.0, Volatility.LOW),
            new Good("Ale",       "Agriculture",    15.0, Volatility.LOW),
            new Good("Spices",    "Agriculture",    80.0, Volatility.HIGH),
            new Good("Fish",      "Agriculture",    12.0, Volatility.LOW),
            new Good("Honey",     "Agriculture",    25.0, Volatility.LOW),
            new Good("Herbs",     "Agriculture",    50.0, Volatility.MEDIUM),
            // Mining (7 original)
            new Good("Iron",      "Mining",         40.0, Volatility.MEDIUM),
            new Good("Coal",      "Mining",         25.0, Volatility.MEDIUM),
            new Good("Stone",     "Mining",         18.0, Volatility.LOW),
            new Good("Gems",      "Mining",        120.0, Volatility.HIGH),
            new Good("Salt",      "Mining",         22.0, Volatility.LOW),
            new Good("Silver",    "Mining",         85.0, Volatility.HIGH),
            new Good("Copper",    "Mining",         30.0, Volatility.MEDIUM),
            // Timber & Craft (9 original)
            new Good("Timber",    "Timber & Craft", 30.0, Volatility.LOW),
            new Good("Rope",      "Timber & Craft", 12.0, Volatility.LOW),
            new Good("Cloth",     "Timber & Craft", 28.0, Volatility.MEDIUM),
            new Good("Leather",   "Timber & Craft", 45.0, Volatility.MEDIUM),
            new Good("Candles",   "Timber & Craft",  8.0, Volatility.LOW),
            new Good("Pitch",     "Timber & Craft", 20.0, Volatility.LOW),
            new Good("Wax",       "Timber & Craft", 22.0, Volatility.LOW),
            new Good("Parchment", "Timber & Craft", 35.0, Volatility.MEDIUM),
            new Good("Dye",       "Timber & Craft", 55.0, Volatility.HIGH),
            // Manufactured Goods (5 new)
            new Good("Bread",   "Agriculture",    18.0, Volatility.LOW),
            new Good("Weapons", "Mining",         90.0, Volatility.MEDIUM),
            new Good("Wine",    "Agriculture",    65.0, Volatility.MEDIUM),
            new Good("Soap",    "Timber & Craft", 16.0, Volatility.LOW),
            new Good("Elixir",  "Agriculture",   130.0, Volatility.HIGH)
        );
    }

    public List<Good> getGoods() { return goods; }

    public Good findByName(String name) {
        return goods.stream()
            .filter(g -> g.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown good: " + name));
    }
}
