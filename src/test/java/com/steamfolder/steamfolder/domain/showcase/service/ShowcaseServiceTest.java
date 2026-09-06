package com.steamfolder.steamfolder.domain.showcase.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseLikeResponse;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseResponse;
import com.steamfolder.steamfolder.domain.showcase.entity.ShowcaseLike;
import com.steamfolder.steamfolder.domain.showcase.repository.ShowcaseLikeRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShowcaseServiceTest {

    @InjectMocks
    private ShowcaseService showcaseService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private UserGameRepository userGameRepository;

    @Mock
    private ShowcaseLikeRepository showcaseLikeRepository;

    private User testUser;
    private Folder rootFolder;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("ShowcaseKing")
                .avatarUrl("https://steamcdn.com/avatar.jpg")
                .themeColor("#00F0FF")
                .isPublic(true)
                .build();

        rootFolder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("인생 명작들")
                .positionX(0)
                .positionY(0)
                .depth(0)
                .build();
    }

    @Test
    @DisplayName("내 쇼케이스 전체 뷰 조회 및 요약 통계 집계 검증")
    void getMyShowcase_Success() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(folderRepository.findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(testUser))
                .willReturn(List.of(rootFolder));
        given(folderRepository.countByUser(testUser)).willReturn(3L);
        given(userGameRepository.countByUser(testUser)).willReturn(15L);
        given(userGameRepository.sumPlaytimeByUser(testUser)).willReturn(12500);

        // when
        ShowcaseResponse response = showcaseService.getMyShowcase(1L);

        // then
        assertThat(response.getUser().getUsername()).isEqualTo("ShowcaseKing");
        assertThat(response.getUser().getThemeColor()).isEqualTo("#00F0FF");
        assertThat(response.getStats().getTotalFolders()).isEqualTo(3);
        assertThat(response.getStats().getTotalOwnedGames()).isEqualTo(15);
        assertThat(response.getStats().getTotalPlaytimeForever()).isEqualTo(12500);
        assertThat(response.getRootFolders()).hasSize(1);
        assertThat(response.getRootFolders().get(0).getName()).isEqualTo("인생 명작들");
    }

    @Test
    @DisplayName("공개 유저 쇼케이스 게스트 조회 검증 (steamId 기준)")
    void getPublicShowcase_PublicUser_Success() {
        // given
        given(userRepository.findBySteamId("76561198000000001")).willReturn(Optional.of(testUser));
        given(folderRepository.findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(testUser))
                .willReturn(List.of(rootFolder));

        // when
        ShowcaseResponse response = showcaseService.getPublicShowcase("76561198000000001", null);

        // then
        assertThat(response.getUser().getSteamId()).isEqualTo("76561198000000001");
        assertThat(response.getUser().isPublic()).isTrue();
    }

    @Test
    @DisplayName("비공개 유저 쇼케이스를 타인 또는 비로그인 게스트가 조회 시 HANDLE_ACCESS_DENIED 예외 발생")
    void getPublicShowcase_PrivateUser_ByOther_ThrowsException() {
        // given
        User privateUser = User.builder()
                .id(2L)
                .steamId("76561198000000002")
                .username("PrivateGamer")
                .isPublic(false)
                .build();

        given(userRepository.findBySteamId("76561198000000002")).willReturn(Optional.of(privateUser));

        // when & then
        assertThatThrownBy(() -> showcaseService.getPublicShowcase("76561198000000002", null))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.HANDLE_ACCESS_DENIED);
    }

    @Test
    @DisplayName("비공개 유저 쇼케이스라도 본인이 조회할 때는 정상 조회 허용 검증")
    void getPublicShowcase_PrivateUser_BySelf_Success() {
        // given
        User privateUser = User.builder()
                .id(2L)
                .steamId("76561198000000002")
                .username("PrivateGamer")
                .isPublic(false)
                .build();

        given(userRepository.findBySteamId("76561198000000002")).willReturn(Optional.of(privateUser));
        given(folderRepository.findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(privateUser))
                .willReturn(List.of());

        // when
        ShowcaseResponse response = showcaseService.getPublicShowcase("76561198000000002", 2L);

        // then
        assertThat(response.getUser().getUsername()).isEqualTo("PrivateGamer");
        assertThat(response.getUser().isPublic()).isFalse();
    }

    @Test
    @DisplayName("쇼케이스 좋아요 신규 등록 성공 (liked: true, likeCount: 1)")
    void toggleShowcaseLike_AddNewLike_Success() {
        // given
        User targetUser = User.builder()
                .id(2L)
                .steamId("76561198000000002")
                .username("TargetUser")
                .likeCount(0)
                .build();

        given(userRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(testUser));
        given(userRepository.findById(2L)).willReturn(Optional.of(targetUser));
        given(showcaseLikeRepository.findByUserAndTargetUser(testUser, targetUser)).willReturn(Optional.empty());
        given(userRepository.getLikeCount(2L)).willReturn(1);

        // when
        ShowcaseLikeResponse response = showcaseService.toggleShowcaseLike(1L, 2L);

        // then
        assertThat(response.getTargetUserId()).isEqualTo(2L);
        assertThat(response.isLiked()).isTrue();
        assertThat(response.getTotalLikeCount()).isEqualTo(1);
        verify(showcaseLikeRepository).saveAndFlush(any(ShowcaseLike.class));
        verify(userRepository).incrementLikeCount(2L);
    }

    @Test
    @DisplayName("이미 좋아요를 누른 쇼케이스 다시 요청 시 취소 (liked: false, likeCount: 0)")
    void toggleShowcaseLike_RemoveExistingLike_Success() {
        // given
        User targetUser = User.builder()
                .id(2L)
                .steamId("76561198000000002")
                .username("TargetUser")
                .likeCount(1)
                .build();

        ShowcaseLike existingLike = ShowcaseLike.builder()
                .id(100L)
                .user(testUser)
                .targetUser(targetUser)
                .build();

        given(userRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(testUser));
        given(userRepository.findById(2L)).willReturn(Optional.of(targetUser));
        given(showcaseLikeRepository.findByUserAndTargetUser(testUser, targetUser)).willReturn(Optional.of(existingLike));
        given(userRepository.getLikeCount(2L)).willReturn(0);

        // when
        ShowcaseLikeResponse response = showcaseService.toggleShowcaseLike(1L, 2L);

        // then
        assertThat(response.getTargetUserId()).isEqualTo(2L);
        assertThat(response.isLiked()).isFalse();
        assertThat(response.getTotalLikeCount()).isEqualTo(0);
        verify(showcaseLikeRepository).delete(existingLike);
        verify(showcaseLikeRepository).flush();
        verify(userRepository).decrementLikeCount(2L);
    }

    @Test
    @DisplayName("본인 쇼케이스에 좋아요 요청 시 CANNOT_LIKE_OWN_SHOWCASE 예외 발생")
    void toggleShowcaseLike_CannotLikeOwnShowcase_ThrowsException() {
        // when & then
        assertThatThrownBy(() -> showcaseService.toggleShowcaseLike(1L, 1L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CANNOT_LIKE_OWN_SHOWCASE);
    }

    @Test
    @DisplayName("존재하지 않는 유저 쇼케이스에 좋아요 요청 시 USER_NOT_FOUND 예외 발생")
    void toggleShowcaseLike_TargetUserNotFound_ThrowsException() {
        // given
        given(userRepository.findByIdWithPessimisticLock(1L)).willReturn(Optional.of(testUser));
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> showcaseService.toggleShowcaseLike(1L, 999L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
