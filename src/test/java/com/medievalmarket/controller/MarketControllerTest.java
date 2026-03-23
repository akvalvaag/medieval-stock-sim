package com.medievalmarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MarketControllerTest {

    @Autowired MockMvc mvc;

    @Test
    void snapshotReturns200WithPricesAndHistory() throws Exception {
        mvc.perform(get("/api/market/snapshot"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.prices").isMap())
           .andExpect(jsonPath("$.history").isMap())
           .andExpect(jsonPath("$.prices.Grain").isNumber())
           .andExpect(jsonPath("$.history.Grain").isArray());
    }

    @Test
    void snapshotContainsAll15Goods() throws Exception {
        mvc.perform(get("/api/market/snapshot"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.prices.Iron").isNumber())
           .andExpect(jsonPath("$.prices.Spices").isNumber())
           .andExpect(jsonPath("$.prices.Candles").isNumber());
    }
}
