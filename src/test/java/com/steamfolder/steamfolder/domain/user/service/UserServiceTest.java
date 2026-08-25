package com.steamfolder.steamfolder.domain.user.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateProfileRequest;
import com.steamfolder.steamfolder.domain.user.dto.request.UpdateVisibilityRequest;
import com.steamfolder.steamfolder.domain.user.dto.response.UserResponse;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.RefreshTokenRepository;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("내 프로필 조회 성공 검증")
    void getMyProfile_Success() {
        // given
        User user = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("TestUser")
                .avatarUrl("https://avatars.steamstatic.com/test.jpg")
                .themeColor("#1A1B26")
                .isPublic(true)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getMyProfile(1L);

        // then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getSteamId()).isEqualTo("76561198000000001");
        assertThat(response.getUsername()).isEqualTo("TestUser");
        assertThat(response.getThemeColor()).isEqualTo("#1A1B26");
        assertThat(response.isPublic()).isTrue();
    }

    @Test
    @DisplayName("쇼케이스 메인 테마 색상 수정 성공 검증")
    void updateProfile_ThemeColor_Success() {
        // given
        User user = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("SteamUser")
                .themeColor("#1A1B26")
                .build();

        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .themeColor("#2A2D3E")
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.updateProfile(1L, request);

        // then
        assertThat(response.getThemeColor()).isEqualTo("#2A2D3E");
    }

    @Test
    @DisplayName("쇼케이스 공개 범위(Public/Private) 변경 성공 검증")
    void updateVisibility_Success() {
        // given
        User user = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .isPublic(true)
                .build();

        UpdateVisibilityRequest request = UpdateVisibilityRequest.builder()
                .isPublic(false)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.updateVisibility(1L, request);

        // then
        assertThat(response.isPublic()).isFalse();
    }

    @Test
    @DisplayName("회원 탈퇴 시 RefreshToken 삭제 및 Soft Delete 처리 검증")
    void deleteAccount_Success() {
        // given
        User user = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when
        userService.deleteAccount(1L);

        // then
        verify(refreshTokenRepository).deleteByUser(user);
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 탈퇴한 회원 조회 시 USER_NOT_FOUND 예외 발생 검증")
    void getMyProfile_DeletedUser_ThrowsException() {
        // given
        User user = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .build();
        user.softDelete();

        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        // when & then
        assertThatThrownBy(() -> userService.getMyProfile(1L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
