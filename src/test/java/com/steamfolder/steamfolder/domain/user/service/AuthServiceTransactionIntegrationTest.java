package com.steamfolder.steamfolder.domain.user.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.game.client.SteamApiClient;
import com.steamfolder.steamfolder.domain.game.service.SteamSyncService;
import com.steamfolder.steamfolder.domain.user.dto.request.SteamLoginRequest;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class AuthServiceTransactionIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private SteamApiClient steamApiClient;

    @MockitoBean
    private SteamSyncService steamSyncService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("신규 가입 중 Steam 게임 정보가 비공개이면 사용자 저장까지 롤백한다")
    void loginWithSteam_PrivateProfile_RollsBackNewUser() {
        // given
        String steamId = "76561198000000008";
        SteamLoginRequest request = SteamLoginRequest.builder()
                .steamId(steamId)
                .username("PrivateGamer")
                .build();

        given(steamApiClient.getPlayerSummary(steamId)).willReturn(Optional.empty());
        given(steamSyncService.syncUserGames(any()))
                .willThrow(new CustomException(ErrorCode.STEAM_PROFILE_PRIVATE));

        // when & then
        assertThatThrownBy(() -> authService.loginWithSteam(request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.STEAM_PROFILE_PRIVATE);

        assertThat(userRepository.findBySteamId(steamId)).isEmpty();
    }
}
