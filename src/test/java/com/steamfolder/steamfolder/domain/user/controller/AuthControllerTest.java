package com.steamfolder.steamfolder.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.steamfolder.steamfolder.common.exception.GlobalExceptionHandler;
import com.steamfolder.steamfolder.common.security.config.SecurityConfig;
import com.steamfolder.steamfolder.common.security.jwt.JwtAuthenticationEntryPoint;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.user.dto.request.SteamLoginRequest;
import com.steamfolder.steamfolder.domain.user.dto.request.TokenRefreshRequest;
import com.steamfolder.steamfolder.domain.user.dto.response.AuthResponse;
import com.steamfolder.steamfolder.domain.user.dto.response.TokenRefreshResponse;
import com.steamfolder.steamfolder.domain.user.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Test
    @DisplayName("POST /api/v1/auth/steam/login - 신규 회원가입 시 201 CREATED 및 표준 ApiResponse 반환")
    void loginWithSteam_NewUser_Returns201() throws Exception {
        // given
        SteamLoginRequest request = SteamLoginRequest.builder()
                .openIdParams(Map.of("openid.claimed_id", "https://steamcommunity.com/openid/id/76561198000000001"))
                .username("NewUser")
                .build();

        AuthResponse mockResponse = AuthResponse.builder()
                .accessToken("mock-access-token")
                .refreshToken("mock-refresh-token")
                .userId(1L)
                .steamId("76561198000000001")
                .username("NewUser")
                .themeColor("#1A1B26")
                .isNewUser(true)
                .build();

        given(authService.loginWithSteam(any(SteamLoginRequest.class))).willReturn(mockResponse);

        // when & then
        mockMvc.perform(post("/api/v1/auth/steam/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원가입 및 로그인이 완료되었습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.data.userId").value(1L))
                .andExpect(jsonPath("$.data.steamId").value("76561198000000001"))
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - 유효한 토큰 재발급 요청 시 200 OK 및 새 토큰 반환")
    void refreshToken_Returns200() throws Exception {
        // given
        TokenRefreshRequest request = TokenRefreshRequest.builder()
                .refreshToken("existing-refresh-token")
                .build();

        TokenRefreshResponse mockResponse = TokenRefreshResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .build();

        given(authService.refreshToken(any(TokenRefreshRequest.class))).willReturn(mockResponse);

        // when & then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Access Token이 성공적으로 재발급되었습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }
}
