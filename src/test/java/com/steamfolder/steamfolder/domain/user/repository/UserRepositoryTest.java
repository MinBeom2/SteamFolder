package com.steamfolder.steamfolder.domain.user.repository;

import com.steamfolder.steamfolder.common.config.JpaAuditingConfig;
import com.steamfolder.steamfolder.domain.user.entity.RefreshToken;
import com.steamfolder.steamfolder.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    @DisplayName("인기 쇼케이스 랭킹 No-Offset 커서 쿼리 및 정렬(likeCount DESC, id ASC) 테스트")
    void showcaseRankingsNoOffset() {
        // given: 4명의 공개 유저 + 1명 비공개 + 1명 탈퇴 유저
        User u1 = userRepository.save(User.builder().steamId("s1").username("U1").likeCount(100).isPublic(true).build());
        User u2 = userRepository.save(User.builder().steamId("s2").username("U2").likeCount(50).isPublic(true).build());
        User u3 = userRepository.save(User.builder().steamId("s3").username("U3").likeCount(50).isPublic(true).build());
        User u4 = userRepository.save(User.builder().steamId("s4").username("U4").likeCount(10).isPublic(true).build());
        userRepository.save(User.builder().steamId("s5").username("U5").likeCount(200).isPublic(false).build());
        userRepository.save(User.builder().steamId("s6").username("U6").likeCount(300).isPublic(true).deletedAt(LocalDateTime.now()).build());
        userRepository.flush();

        // when 1: 첫 페이지 2개 조회
        List<User> firstPage = userRepository.findFirstPageShowcaseRankings(PageRequest.of(0, 2));

        // then 1: u1(100), u2(50, id가 u3보다 작음) 순서로 조회되고 비공개/탈퇴는 제외됨
        assertThat(firstPage).hasSize(2);
        assertThat(firstPage.get(0).getId()).isEqualTo(u1.getId());
        assertThat(firstPage.get(1).getId()).isEqualTo(u2.getId());

        // when 2: u2 커서(50, u2.id) 기준 다음 페이지 2개 조회
        List<User> secondPage = userRepository.findNextPageShowcaseRankings(u2.getLikeCount(), u2.getId(), PageRequest.of(0, 2));

        // then 2: u3(50, id > u2.id), u4(10) 순서로 조회됨
        assertThat(secondPage).hasSize(2);
        assertThat(secondPage.get(0).getId()).isEqualTo(u3.getId());
        assertThat(secondPage.get(1).getId()).isEqualTo(u4.getId());

        // then 3: u2 커서 앞선 순위 유저 수
        long aheadCount = userRepository.countShowcasesAheadOfCursor(u2.getLikeCount(), u2.getId());
        assertThat(aheadCount).isEqualTo(2L); // u1, u2

        // then 4: 본인 순위 계산용 strictly ahead 카운트 검증
        long strictlyAheadOfU2 = userRepository.countShowcasesStrictlyAhead(u2.getLikeCount(), u2.getId());
        assertThat(strictlyAheadOfU2).isEqualTo(1L); // u1만 앞섬 -> u2 순위는 1 + 1 = 2위

        long strictlyAheadOfU3 = userRepository.countShowcasesStrictlyAhead(u3.getLikeCount(), u3.getId());
        assertThat(strictlyAheadOfU3).isEqualTo(2L); // u1, u2 앞섬 -> u3 순위는 2 + 1 = 3위
    }
}
