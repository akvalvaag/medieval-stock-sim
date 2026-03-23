// src/main/java/com/medievalmarket/service/SessionRegistry.java
package com.medievalmarket.service;

import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    private static final Duration EXPIRY = Duration.ofHours(2);
    private final ConcurrentHashMap<String, Portfolio> sessions = new ConcurrentHashMap<>();
    private final NameGenerator nameGenerator;

    public SessionRegistry(NameGenerator nameGenerator) {
        this.nameGenerator = nameGenerator;
    }

    public Portfolio createSession(PlayerClass playerClass) {
        String sessionId = UUID.randomUUID().toString();
        String name = nameGenerator.generate();
        Portfolio portfolio = new Portfolio(sessionId, name, playerClass);
        sessions.put(sessionId, portfolio);
        return portfolio;
    }

    public Optional<Portfolio> findById(String sessionId) {
        Portfolio p = sessions.get(sessionId);
        if (p == null) return Optional.empty();
        if (isExpired(p)) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(p);
    }

    public Portfolio registerBot(String name) {
        String sessionId = UUID.randomUUID().toString();
        Portfolio p = new Portfolio(sessionId, name, PlayerClass.MERCHANT, true);
        p.setGold(300.0); // bots start with 300g regardless of MERCHANT's default 500g
        sessions.put(sessionId, p);
        return p;
    }

    public Collection<Portfolio> getHumanPortfolios() {
        sessions.entrySet().removeIf(e -> !e.getValue().isBot() && isExpired(e.getValue()));
        return sessions.values().stream()
            .filter(p -> !p.isBot())
            .collect(java.util.stream.Collectors.toList());
    }

    public Collection<Portfolio> getAllActiveSessions() {
        sessions.entrySet().removeIf(e -> !e.getValue().isBot() && isExpired(e.getValue()));
        return new java.util.ArrayList<>(sessions.values());
    }

    private boolean isExpired(Portfolio p) {
        if (p.isBot()) return false;
        return Duration.between(p.getLastTradeTime(), Instant.now()).compareTo(EXPIRY) > 0;
    }
}
