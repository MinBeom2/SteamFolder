package com.steamfolder.steamfolder.domain.folder.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.folder.dto.request.CreateFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.MoveFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.UpdateFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.UpdatePositionsRequest;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderResponse;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.entity.FolderGame;
import com.steamfolder.steamfolder.domain.folder.repository.FolderGameRepository;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FolderServiceTest {

    @InjectMocks
    private FolderService folderService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FolderGameRepository folderGameRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserGameRepository userGameRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("FolderMaster")
                .build();
    }

    @Test
    @DisplayName("루트 폴더 생성 시 빈 칸 자동 탐색을 통해 (0,0)에 배치 검증")
    void createFolder_Root_AutoPlacement() {
        // given
        CreateFolderRequest request = CreateFolderRequest.builder()
                .name("FPS")
                .themeColor("#00F0FF")
                .icon("gamepad")
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(folderRepository.countByUser(testUser)).willReturn(0L);
        given(folderRepository.existsByUserAndParentFolderIsNullAndName(testUser, "FPS")).willReturn(false);
        given(folderRepository.findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(testUser))
                .willReturn(List.of());

        Folder savedFolder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("FPS")
                .themeColor("#00F0FF")
                .icon("gamepad")
                .positionX(0)
                .positionY(0)
                .depth(0)
                .build();

        given(folderRepository.save(any(Folder.class))).willReturn(savedFolder);

        // when
        FolderResponse response = folderService.createFolder(1L, request);

        // then
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("FPS");
        assertThat(response.getPositionX()).isEqualTo(0);
        assertThat(response.getPositionY()).isEqualTo(0);
        assertThat(response.getDepth()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 경로 내 동일 폴더명 존재 시 DUPLICATE_FOLDER_NAME 예외 발생")
    void createFolder_DuplicateName_ThrowsException() {
        // given
        CreateFolderRequest request = CreateFolderRequest.builder()
                .name("FPS")
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(folderRepository.countByUser(testUser)).willReturn(1L);
        given(folderRepository.existsByUserAndParentFolderIsNullAndName(testUser, "FPS")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> folderService.createFolder(1L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_FOLDER_NAME);
    }

    @Test
    @DisplayName("폴더 최대 깊이(5단계) 초과 시 MAX_FOLDER_DEPTH_EXCEEDED 예외 발생")
    void createFolder_MaxDepthExceeded_ThrowsException() {
        // given
        Folder depth4Folder = Folder.builder()
                .id(4L)
                .user(testUser)
                .name("Depth4")
                .depth(4)
                .build();

        CreateFolderRequest request = CreateFolderRequest.builder()
                .name("Depth5")
                .parentFolderId(4L)
                .build();

        given(userRepository.findById(1L)).willReturn(Optional.of(testUser));
        given(folderRepository.countByUser(testUser)).willReturn(5L);
        given(folderRepository.findById(4L)).willReturn(Optional.of(depth4Folder));

        // when & then
        assertThatThrownBy(() -> folderService.createFolder(1L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAX_FOLDER_DEPTH_EXCEEDED);
    }

    @Test
    @DisplayName("폴더 이동 시 자기 자신 또는 하위 자식으로 이동 시 CIRCULAR_FOLDER_REFERENCE 예외 발생")
    void moveFolder_CircularReference_ThrowsException() {
        // given
        Folder parentFolder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("Parent")
                .depth(0)
                .childFolders(new ArrayList<>())
                .build();

        Folder childFolder = Folder.builder()
                .id(20L)
                .user(testUser)
                .parentFolder(parentFolder)
                .name("Child")
                .depth(1)
                .childFolders(new ArrayList<>())
                .build();
        parentFolder.addChildFolder(childFolder);

        MoveFolderRequest request = MoveFolderRequest.builder()
                .targetParentFolderId(20L) // 부모를 자식의 하위로 이동 시도
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(parentFolder));
        given(folderRepository.findById(20L)).willReturn(Optional.of(childFolder));

        // when & then
        assertThatThrownBy(() -> folderService.moveFolder(1L, 10L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CIRCULAR_FOLDER_REFERENCE);
    }

    @Test
    @DisplayName("폴더 정보(이름, 테마색상, 아이콘) 정상 수정 검증")
    void updateFolder_Success() {
        // given
        Folder folder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("OldName")
                .themeColor("#1A1B26")
                .icon("folder")
                .build();

        UpdateFolderRequest request = UpdateFolderRequest.builder()
                .name("NewName")
                .themeColor("#EF4444")
                .icon("fire")
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(folder));
        given(folderRepository.existsByUserAndParentFolderIsNullAndName(testUser, "NewName")).willReturn(false);

        // when
        FolderResponse response = folderService.updateFolder(1L, 10L, request);

        // then
        assertThat(response.getName()).isEqualTo("NewName");
        assertThat(response.getThemeColor()).isEqualTo("#EF4444");
        assertThat(response.getIcon()).isEqualTo("fire");
    }

    @Test
    @DisplayName("폴더 삭제 시 folderRepository.delete 호출 검증")
    void deleteFolder_Success() {
        // given
        Folder folder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("DeleteMe")
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(folder));

        // when
        folderService.deleteFolder(1L, 10L);

        // then
        verify(folderRepository).delete(folder);
    }

    @Test
    @DisplayName("드래그 앤 드롭 2D 격자 좌표 일괄 업데이트 검증")
    void updatePositions_Success() {
        // given
        Folder folder = Folder.builder()
                .id(10L)
                .user(testUser)
                .positionX(0)
                .positionY(0)
                .build();

        UpdatePositionsRequest request = UpdatePositionsRequest.builder()
                .positions(List.of(
                        UpdatePositionsRequest.ItemPositionDto.builder()
                                .type("FOLDER")
                                .id(10L)
                                .positionX(3)
                                .positionY(2)
                                .build()
                ))
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(folder));

        // when
        folderService.updatePositions(1L, request);

        // then
        assertThat(folder.getPositionX()).isEqualTo(3);
        assertThat(folder.getPositionY()).isEqualTo(2);
    }

    @Test
    @DisplayName("폴더 상세 조회 - 본인 소유 폴더 정상 조회 검증")
    void getFolderDetail_Owner_Success() {
        // given
        Folder folder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("MyFolder")
                .childFolders(new ArrayList<>())
                .folderGames(new ArrayList<>())
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(folder));
        given(userGameRepository.findByUser(testUser)).willReturn(List.of());

        // when
        FolderResponse response = folderService.getFolderDetail(1L, 10L);

        // then
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("MyFolder");
    }

    @Test
    @DisplayName("폴더 상세 조회 - 게임의 플레이타임 및 도전과제(업적 달성률) 정보가 정상 매핑됨")
    void getFolderDetail_MapsPlaytimeAndAchievements_Success() {
        // given
        Game game = Game.builder()
                .appId(1091500L)
                .name("Cyberpunk 2077")
                .headerImage("https://cdn.example.com/header.jpg")
                .genres(List.of("RPG", "Action"))
                .build();

        Folder folder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("RPG Folder")
                .childFolders(new ArrayList<>())
                .folderGames(new ArrayList<>())
                .build();

        FolderGame folderGame = FolderGame.builder()
                .id(1L)
                .folder(folder)
                .game(game)
                .positionX(0)
                .positionY(0)
                .build();

        folder.getFolderGames().add(folderGame);

        LocalDateTime lastPlayed = LocalDateTime.of(2026, 9, 1, 12, 0);
        UserGame userGame = UserGame.builder()
                .user(testUser)
                .game(game)
                .playtimeForever(3600)
                .lastPlayedAt(lastPlayed)
                .totalAchievements(50)
                .achievedAchievements(25)
                .achievementRate(50.0)
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(folder));
        given(userGameRepository.findByUser(testUser)).willReturn(List.of(userGame));

        // when
        FolderResponse response = folderService.getFolderDetail(1L, 10L);

        // then
        assertThat(response.getGames()).hasSize(1);
        FolderResponse.FolderGameDto gameDto = response.getGames().get(0);
        assertThat(gameDto.getAppId()).isEqualTo(1091500L);
        assertThat(gameDto.getPlaytimeForever()).isEqualTo(3600);
        assertThat(gameDto.getLastPlayedAt()).isEqualTo(lastPlayed);
        assertThat(gameDto.getTotalAchievements()).isEqualTo(50);
        assertThat(gameDto.getAchievedAchievements()).isEqualTo(25);
        assertThat(gameDto.getAchievementRate()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("폴더 상세 조회 - 비로그인 게스트 또는 타인이 공개(isPublic=true) 폴더 조회 시 정상 허용")
    void getFolderDetail_Guest_PublicFolder_Success() {
        // given
        User publicUser = User.builder().id(2L).steamId("76561198000000002").username("PublicUser").isPublic(true).build();
        Folder folder = Folder.builder()
                .id(20L)
                .user(publicUser)
                .name("PublicFolder")
                .childFolders(new ArrayList<>())
                .folderGames(new ArrayList<>())
                .build();

        given(folderRepository.findById(20L)).willReturn(Optional.of(folder));
        given(userGameRepository.findByUser(publicUser)).willReturn(List.of());

        // when (비로그인 userId = null)
        FolderResponse guestResponse = folderService.getFolderDetail(null, 20L);
        // when (다른 유저 userId = 1L)
        FolderResponse otherUserResponse = folderService.getFolderDetail(1L, 20L);

        // then
        assertThat(guestResponse.getId()).isEqualTo(20L);
        assertThat(otherUserResponse.getId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("폴더 상세 조회 - 비로그인 게스트 또는 타인이 비공개(isPublic=false) 폴더라도 URL 링크 접근 시 정상 허용")
    void getFolderDetail_Guest_PrivateFolder_Success() {
        // given
        User privateUser = User.builder().id(3L).steamId("76561198000000003").username("PrivateUser").isPublic(false).build();
        Folder folder = Folder.builder()
                .id(30L)
                .user(privateUser)
                .name("PrivateFolder")
                .childFolders(new ArrayList<>())
                .folderGames(new ArrayList<>())
                .build();

        given(folderRepository.findById(30L)).willReturn(Optional.of(folder));
        given(userGameRepository.findByUser(privateUser)).willReturn(List.of());

        // when (비로그인 userId = null)
        FolderResponse guestResponse = folderService.getFolderDetail(null, 30L);
        // when (다른 유저 userId = 1L)
        FolderResponse otherUserResponse = folderService.getFolderDetail(1L, 30L);

        // then
        assertThat(guestResponse.getId()).isEqualTo(30L);
        assertThat(otherUserResponse.getId()).isEqualTo(30L);
    }

    @Test
    @DisplayName("폴더 상세 조회 - 탈퇴한 계정의 폴더 조회 시 FOLDER_ACCESS_DENIED 예외 발생")
    void getFolderDetail_DeletedUser_ThrowsException() {
        // given
        User deletedUser = User.builder()
                .id(4L)
                .steamId("76561198000000004")
                .username("DeletedUser")
                .deletedAt(java.time.LocalDateTime.now())
                .build();
        Folder folder = Folder.builder()
                .id(40L)
                .user(deletedUser)
                .name("DeletedFolder")
                .build();

        given(folderRepository.findById(40L)).willReturn(Optional.of(folder));

        // when & then
        assertThatThrownBy(() -> folderService.getFolderDetail(null, 40L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FOLDER_ACCESS_DENIED);

        assertThatThrownBy(() -> folderService.getFolderDetail(1L, 40L))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FOLDER_ACCESS_DENIED);
    }
}
