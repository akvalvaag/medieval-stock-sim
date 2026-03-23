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

    public Collection<Portfolio> getAllActiveSessions() {
        sessions.entrySet().removeIf(e -> isExpired(e.getValue()));
        return sessions.values();
    }

    private boolean isExpired(Portfolio p) {
        return Duration.between(p.getLastTradeTime(), Instant.now()).compareTo(EXPIRY) > 0;
    }
}
