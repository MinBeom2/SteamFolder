package com.steamfolder.steamfolder.domain.showcase.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.repository.FolderGameRepository;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.showcase.dto.response.ShowcaseResponse;
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
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ShowcaseServiceTest {

    @InjectMocks
    private ShowcaseService showcaseService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FolderGameRepository folderGameRepository;

    @Mock
    private UserGameRepository userGameRepository;

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
}
