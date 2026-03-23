package com.medievalmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medievalmarket.dto.BorrowRequest;
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
class MoneylenderControllerTest {

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
    void borrowReturns200WithUpdatedGoldAndLoan() throws Exception {
        mvc.perform(post("/api/moneylender/borrow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BorrowRequest(sessionId, 200.0))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loanAmount").value(200.0))
            .andExpect(jsonPath("$.gold").value(1200.0));
    }

    @Test
    void repayReturns200WithClearedLoan() throws Exception {
        mvc.perform(post("/api/moneylender/borrow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BorrowRequest(sessionId, 200.0)))).andReturn();
        mvc.perform(post("/api/moneylender/repay")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"" + sessionId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.loanAmount").value(0.0));
    }

    @Test
    void borrowBelowMinimumReturns400() throws Exception {
        mvc.perform(post("/api/moneylender/borrow")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new BorrowRequest(sessionId, 10.0))))
            .andExpect(status().isBadRequest());
    }
}
