package com.medievalmarket.controller;

import com.medievalmarket.dto.MarketSnapshotResponse;
import com.medievalmarket.model.Good;
import com.medievalmarket.service.GoodsCatalogue;
import com.medievalmarket.service.PriceHistory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final GoodsCatalogue catalogue;
    private final PriceHistory priceHistory;

    public MarketController(GoodsCatalogue catalogue, PriceHistory priceHistory) {
        this.catalogue = catalogue;
        this.priceHistory = priceHistory;
    }

    @GetMapping("/snapshot")
    public MarketSnapshotResponse snapshot() {
        Map<String, Double> prices = catalogue.getGoods().stream()
            .collect(Collectors.toMap(Good::getName,
                g -> Math.round(g.getCurrentPrice() * 10.0) / 10.0));
        return new MarketSnapshotResponse(prices, priceHistory.getAllHistory());
    }
}
