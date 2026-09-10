package com.steamfolder.steamfolder.domain.system.controller;

import com.steamfolder.steamfolder.common.ratelimit.RateLimitDecision;
import com.steamfolder.steamfolder.common.ratelimit.RateLimitFilter;
import com.steamfolder.steamfolder.common.ratelimit.RateLimitIdentityType;
import com.steamfolder.steamfolder.common.ratelimit.RateLimitPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemController.class)
@AutoConfigureMockMvc(addFilters = false)
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("현재 요청에 적용된 레이트 리밋 상태를 노출한다")
    void getsCurrentRateLimitStatus() throws Exception {
        RateLimitDecision decision = new RateLimitDecision(
                true, RateLimitPolicy.READ, RateLimitIdentityType.USER, 120, 119, 0
        );

        mockMvc.perform(get("/api/v1/system/rate-limit")
                        .requestAttr(RateLimitFilter.DECISION_ATTRIBUTE, decision))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.policy").value("READ"))
                .andExpect(jsonPath("$.data.identityType").value("USER"))
                .andExpect(jsonPath("$.data.limit").value(120))
                .andExpect(jsonPath("$.data.remainingRequests").value(119));
    }
}
