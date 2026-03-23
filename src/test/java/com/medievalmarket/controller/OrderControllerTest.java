package com.medievalmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medievalmarket.dto.AddOrderRequest;
import com.medievalmarket.dto.SessionStartRequest;
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
class OrderControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    private String sessionId;

    @BeforeEach
    void createSession() throws Exception {
        MvcResult r = mvc.perform(post("/api/session/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SessionStartRequest("NOBLE"))))
            .andReturn();
        sessionId = mapper.readTree(r.getResponse().getContentAsString())
            .get("sessionId").asText();
    }

    @Test
    void addOrderReturns200AndListsOrder() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new AddOrderRequest(sessionId, "Iron", "BUY", 2, 30.0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orders[0].goodName").value("Iron"))
            .andExpect(jsonPath("$.orders[0].direction").value("BUY"));
    }

    @Test
    void cancelOrderReturns200AndRemovesOrder() throws Exception {
        MvcResult addResult = mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new AddOrderRequest(sessionId, "Iron", "BUY", 2, 30.0))))
            .andReturn();
        String orderId = mapper.readTree(addResult.getResponse().getContentAsString())
            .get("orders").get(0).get("id").asText();

        mvc.perform(delete("/api/orders/" + orderId)
                .param("sessionId", sessionId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orders").isEmpty());
    }

    @Test
    void exceeding3OrdersReturns400() throws Exception {
        AddOrderRequest req = new AddOrderRequest(sessionId, "Iron", "BUY", 1, 30.0);
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req))).andExpect(status().isOk());
        }
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void invalidSessionReturns404() throws Exception {
        mvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(
                    new AddOrderRequest("00000000-0000-0000-0000-000000000000", "Iron", "BUY", 1, 30.0))))
            .andExpect(status().isNotFound());
    }
}
