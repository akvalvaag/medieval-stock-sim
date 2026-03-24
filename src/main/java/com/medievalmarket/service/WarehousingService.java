package com.medievalmarket.service;

import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;
import java.util.Collection;

@Component
public class WarehousingService {

    public void processTick(Portfolio portfolio) {
        // Warehousing cost removed
    }

    public void processAll(Collection<Portfolio> portfolios) {
        portfolios.forEach(this::processTick);
    }
}
