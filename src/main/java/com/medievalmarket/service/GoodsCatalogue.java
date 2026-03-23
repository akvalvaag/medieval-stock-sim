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
            // Agriculture
            new Good("Grain",     "Agriculture",    10.0, Volatility.LOW),
            new Good("Wool",      "Agriculture",    20.0, Volatility.LOW),
            new Good("Livestock", "Agriculture",    35.0, Volatility.LOW),
            new Good("Ale",       "Agriculture",    15.0, Volatility.LOW),
            new Good("Spices",    "Agriculture",    80.0, Volatility.HIGH),
            // Mining
            new Good("Iron",      "Mining",         40.0, Volatility.MEDIUM),
            new Good("Coal",      "Mining",         25.0, Volatility.MEDIUM),
            new Good("Stone",     "Mining",         18.0, Volatility.LOW),
            new Good("Gems",      "Mining",        120.0, Volatility.HIGH),
            new Good("Salt",      "Mining",         22.0, Volatility.LOW),
            // Timber & Craft
            new Good("Timber",    "Timber & Craft", 30.0, Volatility.LOW),
            new Good("Rope",      "Timber & Craft", 12.0, Volatility.LOW),
            new Good("Cloth",     "Timber & Craft", 28.0, Volatility.MEDIUM),
            new Good("Leather",   "Timber & Craft", 45.0, Volatility.MEDIUM),
            new Good("Candles",   "Timber & Craft",  8.0, Volatility.LOW)
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
