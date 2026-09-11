package com.steamfolder.steamfolder.common.security;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityIntegrationTest.TestSecurityController.class)
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @RestController
    @Component
    static class TestSecurityController {

        @GetMapping("/api/v1/auth/test-public")
        public ApiResponse<String> publicEndpoint() {
            return ApiResponse.success("Public Access Allowed");
        }

        @GetMapping("/api/v1/users/test-protected")
        public ApiResponse<String> protectedEndpoint(@AuthenticationPrincipal UserPrincipal userPrincipal) {
            return ApiResponse.success("Protected User: " + userPrincipal.getUsername() + ", SteamID: " + userPrincipal.getSteamId());
        }
    }

    @Test
    @DisplayName("공개 엔드포인트(/api/v1/auth/**)는 인증 없이 200 OK 응답")
    void testPublicEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/test-public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Public Access Allowed"));
    }

    @Test
    @DisplayName("보호된 엔드포인트에 토큰 없이 접근 시 401 Unauthorized 반환")
    void testProtectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/test-protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A001"));
    }

    @Test
    @DisplayName("유효한 Bearer JWT 토큰으로 보호된 엔드포인트 접근 시 인증 성공 및 200 OK")
    void testProtectedEndpointWithValidToken() throws Exception {
        // given
        String validToken = tokenProvider.createAccessToken(10L, "76561198999999999", "AuthGamer");

        // when & then
        mockMvc.perform(get("/api/v1/users/test-protected")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Protected User: AuthGamer, SteamID: 76561198999999999"));
    }

    @Test
    @DisplayName("CORS 응답에서 레이트 리밋 헤더를 프론트에 노출한다")
    void exposesRateLimitHeadersForCorsRequests() throws Exception {
        mockMvc.perform(options("/api/v1/auth/test-public")
                        .header("Origin", "https://example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-RateLimit-Limit")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("X-RateLimit-Remaining")))
                .andExpect(header().string("Access-Control-Expose-Headers", containsString("Retry-After")));
    }
}
