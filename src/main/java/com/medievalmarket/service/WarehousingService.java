package com.medievalmarket.service;

import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;
import java.util.Collection;

@Component
public class WarehousingService {

    private final GoodsCatalogue catalogue;

    public WarehousingService(GoodsCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    public void processTick(Portfolio portfolio) {
        double holdingsValue = portfolio.getHoldings().entrySet().stream()
            .mapToDouble(e -> catalogue.findByName(e.getKey()).getCurrentPrice() * e.getValue())
            .sum();
        if (holdingsValue == 0.0) return;

        double fee = holdingsValue * portfolio.getPlayerClass().getWarehousingRate();
        synchronized (portfolio) {
            if (portfolio.getGold() - fee < 0.0) return; // skip — would go negative
            portfolio.setGold(portfolio.getGold() - fee);
        }
    }

    public void processAll(Collection<Portfolio> humanPortfolios) {
        humanPortfolios.forEach(this::processTick);
    }
}
