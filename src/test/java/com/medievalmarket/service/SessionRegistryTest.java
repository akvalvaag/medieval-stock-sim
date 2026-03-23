// src/test/java/com/medievalmarket/service/SessionRegistryTest.java
package com.medievalmarket.service;

import com.medievalmarket.model.PlayerClass;
import com.medievalmarket.model.Portfolio;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class SessionRegistryTest {

    private SessionRegistry registry() {
        return new SessionRegistry(new NameGenerator());
    }

    @Test
    void createSessionReturnsPortfolioWithCorrectStartGold() {
        SessionRegistry registry = registry();
        Portfolio p = registry.createSession(PlayerClass.NOBLE);
        assertThat(p.getGold()).isEqualTo(1000.0);
    }

    @Test
    void createdSessionHasNonBlankName() {
        SessionRegistry registry = registry();
        Portfolio p = registry.createSession(PlayerClass.MERCHANT);
        assertThat(p.getPlayerName()).isNotBlank();
    }

    @Test
    void findByIdReturnsCreatedSession() {
        SessionRegistry registry = registry();
        Portfolio created = registry.createSession(PlayerClass.MINER);
        Optional<Portfolio> found = registry.findById(created.getSessionId());
        assertThat(found).isPresent();
        assertThat(found.get().getSessionId()).isEqualTo(created.getSessionId());
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        SessionRegistry registry = registry();
        assertThat(registry.findById("nonexistent")).isEmpty();
    }

    @Test
    void getAllActiveSessionsReturnsAllCreated() {
        SessionRegistry registry = registry();
        registry.createSession(PlayerClass.MERCHANT);
        registry.createSession(PlayerClass.NOBLE);
        assertThat(registry.getAllActiveSessions()).hasSize(2);
    }
}
