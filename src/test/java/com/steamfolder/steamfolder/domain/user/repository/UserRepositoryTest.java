package com.steamfolder.steamfolder.domain.user.repository;

import com.steamfolder.steamfolder.common.config.JpaAuditingConfig;
import com.steamfolder.steamfolder.domain.user.entity.RefreshToken;
import com.steamfolder.steamfolder.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("유저 저장 및 SteamID 기반 조회 테스트 (새 스키마 필드 검증)")
    void saveAndFindBySteamId() {
        // given
        User user = User.builder()
                .steamId("76561198000000001")
                .username("GamerGabe")
                .avatarUrl("https://avatars.steamstatic.com/sample_large_avatar_url_500.jpg")
                .themeColor("#1A1B26")
                .isPublic(true)
                .build();

        // when
        User savedUser = userRepository.save(user);
        Optional<User> found = userRepository.findBySteamId("76561198000000001");

        // then
        assertThat(savedUser.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedUser.getId());
        assertThat(found.get().getUsername()).isEqualTo("GamerGabe");
        assertThat(found.get().getThemeColor()).isEqualTo("#1A1B26");
        assertThat(found.get().isPublic()).isTrue();
        assertThat(found.get().getDeletedAt()).isNull();
        assertThat(found.get().getCreatedAt()).isNotNull();
        assertThat(found.get().getUpdatedAt()).isNotNull();
        assertThat(userRepository.existsBySteamId("76561198000000001")).isTrue();
        assertThat(userRepository.existsBySteamId("99999999999999999")).isFalse();
    }

    @Test
    @DisplayName("RefreshToken 저장 및 유저 연관관계 조회 테스트")
    void saveAndFindRefreshToken() {
        // given
        User user = userRepository.save(User.builder()
                .steamId("76561198000000002")
                .username("SteamUser2")
                .build());

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .token("sample-refresh-token-uuid-12345")
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();

        // when
        refreshTokenRepository.save(token);
        Optional<RefreshToken> foundToken = refreshTokenRepository.findByToken("sample-refresh-token-uuid-12345");

        // then
        assertThat(foundToken).isPresent();
        assertThat(foundToken.get().getUser().getId()).isEqualTo(user.getId());
        assertThat(foundToken.get().getUser().getSteamId()).isEqualTo("76561198000000002");
        assertThat(foundToken.get().isExpired()).isFalse();
    }

    @Test
    @DisplayName("유저 프로필 수정, 테마 색상 변경 및 소프트 딜리트 테스트")
    void updateUserProfileAndSoftDelete() {
        // given
        User user = userRepository.save(User.builder()
                .steamId("76561198000000003")
                .username("OldName")
                .avatarUrl("https://avatars.steamstatic.com/old.jpg")
                .themeColor("#1A1B26")
                .isPublic(true)
                .build());

        // when
        user.syncSteamProfile("NewName", "https://avatars.steamstatic.com/new.jpg");
        user.updateThemeColor("#FF007F");
        user.updateVisibility(false);
        user.softDelete();
        userRepository.flush();

        Optional<User> updatedUser = userRepository.findBySteamId("76561198000000003");

        // then
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getUsername()).isEqualTo("NewName");
        assertThat(updatedUser.get().getAvatarUrl()).isEqualTo("https://avatars.steamstatic.com/new.jpg");
        assertThat(updatedUser.get().getThemeColor()).isEqualTo("#FF007F");
        assertThat(updatedUser.get().isPublic()).isFalse();
        assertThat(updatedUser.get().isDeleted()).isTrue();
        assertThat(updatedUser.get().getDeletedAt()).isNotNull();
    }
}
