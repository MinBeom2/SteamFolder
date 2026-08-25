package com.steamfolder.steamfolder.domain.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.steamfolder.steamfolder.common.exception.GlobalExceptionHandler;
import com.steamfolder.steamfolder.common.security.UserPrincipal;
import com.steamfolder.steamfolder.common.security.config.SecurityConfig;
import com.steamfolder.steamfolder.common.security.jwt.JwtAuthenticationEntryPoint;
import com.steamfolder.steamfolder.common.security.jwt.JwtTokenProvider;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateProfileRequest;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateVisibilityRequest;
import com.steamfolder.steamfolder.domain.user.dto.response.UserResponse;
import com.steamfolder.steamfolder.domain.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    private UserPrincipal testPrincipal;
    private UsernamePasswordAuthenticationToken testAuth;

    @BeforeEach
    void setUp() {
        testPrincipal = UserPrincipal.builder()
                .userId(1L)
                .steamId("76561198000000001")
                .username("TestUser")
                .build();
        testAuth = new UsernamePasswordAuthenticationToken(testPrincipal, null, testPrincipal.getAuthorities());
    }

    @Test
    @DisplayName("GET /api/v1/users/me - 내 프로필 조회 200 OK")
    void getMyProfile_Returns200() throws Exception {
        // given
        UserResponse response = UserResponse.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("TestUser")
                .avatarUrl("https://avatars.steamstatic.com/test.jpg")
                .themeColor("#1A1B26")
                .isPublic(true)
                .build();

        given(userService.getMyProfile(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.username").value("TestUser"))
                .andExpect(jsonPath("$.data.isPublic").value(true));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/profile - 쇼케이스 테마 색상 수정 200 OK")
    void updateProfile_Returns200() throws Exception {
        // given
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .themeColor("#2A2D3E")
                .build();

        UserResponse response = UserResponse.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("TestUser")
                .avatarUrl("https://avatars.steamstatic.com/test.jpg")
                .themeColor("#2A2D3E")
                .isPublic(true)
                .build();

        given(userService.updateProfile(eq(1L), any(UpdateProfileRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/users/me/profile")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.themeColor").value("#2A2D3E"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me/visibility - 쇼케이스 공개 범위 수정 200 OK")
    void updateVisibility_Returns200() throws Exception {
        // given
        UpdateVisibilityRequest request = UpdateVisibilityRequest.builder()
                .isPublic(false)
                .build();

        UserResponse response = UserResponse.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("TestUser")
                .isPublic(false)
                .build();

        given(userService.updateVisibility(eq(1L), any(UpdateVisibilityRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/users/me/visibility")
                        .with(authentication(testAuth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.isPublic").value(false));
    }

    @Test
    @DisplayName("DELETE /api/v1/users/me - 회원 탈퇴 200 OK")
    void deleteAccount_Returns200() throws Exception {
        // given
        doNothing().when(userService).deleteAccount(1L);

        // when & then
        mockMvc.perform(delete("/api/v1/users/me")
                        .with(authentication(testAuth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("회원 탈퇴 및 연동 해제가 성공적으로 완료되었습니다."));
    }
}
