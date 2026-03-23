package com.medievalmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medievalmarket.dto.SessionStartRequest;
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
class SessionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void startSessionReturns200WithSessionId() throws Exception {
        mvc.perform(post("/api/session/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SessionStartRequest("NOBLE"))))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.sessionId").isNotEmpty())
           .andExpect(jsonPath("$.gold").value(1000.0));
    }

    @Test
    void startSessionWithInvalidClassReturns400() throws Exception {
        mvc.perform(post("/api/session/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerClass\":\"WIZARD\"}"))
           .andExpect(status().isBadRequest());
    }

    @Test
    void resumeExistingSessionReturns200() throws Exception {
        MvcResult result = mvc.perform(post("/api/session/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new SessionStartRequest("MERCHANT"))))
            .andReturn();
        String body = result.getResponse().getContentAsString();
        String sessionId = mapper.readTree(body).get("sessionId").asText();

        mvc.perform(get("/api/session/" + sessionId))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.gold").value(500.0));
    }

    @Test
    void resumeUnknownSessionReturns404() throws Exception {
        mvc.perform(get("/api/session/nonexistent-id"))
           .andExpect(status().isNotFound());
    }
}
