package com.medievalmarket.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medievalmarket.dto.SessionStartRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FacilityControllerTest {

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

    private void buildFacility(String type) throws Exception {
        mvc.perform(post("/api/facility/build")
                .header("X-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("type", type))))
            .andExpect(status().isOk());
    }

    @Test
    void demolish_returns200WithUpdatedFacilitiesAndGold() throws Exception {
        buildFacility("FORGE"); // NOBLE 1000g - 400g = 600g; refund 200g → 800g
        mvc.perform(post("/api/facility/demolish")
                .header("X-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("type", "FORGE"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.facilities").isArray())
            .andExpect(jsonPath("$.facilities.length()").value(0))
            .andExpect(jsonPath("$.gold").value(800.0));
    }

    @Test
    void demolish_returns400WhenFacilityNotOwned() throws Exception {
        mvc.perform(post("/api/facility/demolish")
                .header("X-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("type", "FORGE"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("FACILITY_NOT_FOUND"));
    }

    @Test
    void demolish_returns400ForInvalidType() throws Exception {
        mvc.perform(post("/api/facility/demolish")
                .header("X-Session-Id", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("type", "BLAH"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("INVALID_FACILITY_TYPE"));
    }
}
