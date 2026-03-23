package com.medievalmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medievalmarket.dto.SessionStartRequest;
import com.medievalmarket.dto.TradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TradeControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String sessionId;

    @BeforeEach
    void createSession() throws Exception {
        MvcResult result = mvc.perform(post("/api/session/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SessionStartRequest("NOBLE"))))
            .andReturn();
        sessionId = mapper.readTree(result.getResponse().getContentAsString())
            .get("sessionId").asText();
    }

    @Test
    void buyReturns200AndUpdatesGold() throws Exception {
        mvc.perform(post("/api/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new TradeRequest(sessionId, "Grain", 1))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.holdings.Grain").value(1));
    }

    @Test
    void buyWithInsufficientFundsReturns400() throws Exception {
        mvc.perform(post("/api/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new TradeRequest(sessionId, "Gems", 10))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void sellWithNoHoldingsReturns400() throws Exception {
        mvc.perform(post("/api/trade/sell")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new TradeRequest(sessionId, "Iron", 1))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("INSUFFICIENT_HOLDINGS"));
    }

    @Test
    void invalidSessionIdFormatReturns400() throws Exception {
        mvc.perform(post("/api/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new TradeRequest("not-a-uuid", "Grain", 1))))
           .andExpect(status().isBadRequest())
           .andExpect(jsonPath("$.error").value("INVALID_SESSION_ID"));
    }

    @Test
    void unknownValidUuidReturns404() throws Exception {
        String validUuid = java.util.UUID.randomUUID().toString();
        mvc.perform(post("/api/trade/buy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new TradeRequest(validUuid, "Grain", 1))))
           .andExpect(status().isNotFound())
           .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
    }
}
