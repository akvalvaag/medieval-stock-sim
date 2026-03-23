package com.medievalmarket.service;

import com.medievalmarket.model.Good;
import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class MoneylenderService {

    private static final double INTEREST_RATE = 1.02;
    private static final double MIN_BORROW = 50.0;
    private static final double MAX_BORROW_CAP = 5000.0;

    private final TradeService tradeService;
    private final GoodsCatalogue catalogue;

    public MoneylenderService(TradeService tradeService, GoodsCatalogue catalogue) {
        this.tradeService = tradeService;
        this.catalogue = catalogue;
    }

    public void borrow(Portfolio portfolio, double amount) {
        if (amount < MIN_BORROW) throw new LoanException("BELOW_MINIMUM");
        double maxBorrow = Math.min(portfolio.getGold() * 2, MAX_BORROW_CAP);
        if (amount > maxBorrow) throw new LoanException("ABOVE_LIMIT");
        portfolio.setLoanAmount(portfolio.getLoanAmount() + amount);
        portfolio.setGold(portfolio.getGold() + amount);
    }

    public void repay(Portfolio portfolio) {
        double loan = portfolio.getLoanAmount();
        if (loan <= 0.0) return;
        double payment = Math.min(portfolio.getGold(), loan);
        portfolio.setGold(portfolio.getGold() - payment);
        portfolio.setLoanAmount(loan - payment);
    }

    public void processTick(Portfolio portfolio) {
        if (portfolio.getLoanAmount() <= 0.0) return;
        portfolio.setLoanAmount(portfolio.getLoanAmount() * INTEREST_RATE);
        checkSolvencyAndSeize(portfolio);
    }

    public void processAll(Collection<Portfolio> humanPortfolios) {
        humanPortfolios.forEach(this::processTick);
    }

    private void checkSolvencyAndSeize(Portfolio portfolio) {
        double holdingsValue = holdingsValue(portfolio);
        if (portfolio.getLoanAmount() <= portfolio.getGold() + holdingsValue) return;

        // Seize cheapest holdings first
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(
            portfolio.getHoldings().entrySet());
        sorted.sort(Comparator.comparingDouble(e ->
            catalogue.findByName(e.getKey()).getCurrentPrice() * e.getValue()));

        for (Map.Entry<String, Integer> entry : sorted) {
            if (portfolio.getLoanAmount() <= 0.0) break;
            try {
                double goldBefore = portfolio.getGold();
                tradeService.sell(portfolio, entry.getKey(), entry.getValue());
                double proceeds = portfolio.getGold() - goldBefore;
                portfolio.setLoanAmount(portfolio.getLoanAmount() - proceeds);
            } catch (TradeService.TradeException ignored) {}
        }
        if (portfolio.getLoanAmount() < 0.0) portfolio.setLoanAmount(0.0);
    }

    private double holdingsValue(Portfolio portfolio) {
        return portfolio.getHoldings().entrySet().stream()
            .mapToDouble(e -> catalogue.findByName(e.getKey()).getCurrentPrice() * e.getValue())
            .sum();
    }

    public static class LoanException extends RuntimeException {
        public LoanException(String message) { super(message); }
    }
}
