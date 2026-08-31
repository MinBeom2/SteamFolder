package com.steamfolder.steamfolder.domain.folder.service;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.folder.dto.request.AddFolderGameRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.MoveFolderGameRequest;
import com.steamfolder.steamfolder.domain.folder.dto.request.RemoveFolderGamesRequest;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderGameDetailResponse;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.entity.FolderGame;
import com.steamfolder.steamfolder.domain.folder.repository.FolderGameRepository;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.client.SteamApiClient;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FolderGameServiceTest {

    @InjectMocks
    private FolderGameService folderGameService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private FolderGameRepository folderGameRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private FolderService folderService;

    @Mock
    private SteamApiClient steamApiClient;

    private User testUser;
    private Folder testFolder;
    private Game testGame;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .steamId("76561198000000001")
                .username("FolderMaster")
                .build();

        testFolder = Folder.builder()
                .id(10L)
                .user(testUser)
                .name("FPS 명작")
                .build();

        testGame = Game.builder()
                .appId(730L)
                .name("Counter-Strike 2")
                .headerImage("https://cdn.akamai.steamstatic.com/steam/apps/730/header.jpg")
                .genres(List.of("FPS", "Action"))
                .build();
    }

    @Test
    @DisplayName("폴더 내 게임 추가 시 2D 빈 칸 자동 탐색 및 정상 추가 검증")
    void addGameToFolder_Success() {
        // given
        AddFolderGameRequest request = AddFolderGameRequest.builder()
                .appId(730L)
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(testFolder));
        given(gameRepository.findById(730L)).willReturn(Optional.of(testGame));
        given(folderService.findNextAvailablePosition(eq(testUser), eq(testFolder), any(Integer.class)))
                .willReturn(new int[]{0, 0});

        FolderGame folderGame = FolderGame.builder()
                .id(100L)
                .folder(testFolder)
                .game(testGame)
                .positionX(0)
                .positionY(0)
                .build();

        given(folderGameRepository.save(any(FolderGame.class))).willReturn(folderGame);

        // when
        FolderGameDetailResponse response = folderGameService.addGameToFolder(1L, 10L, request);

        // then
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getAppId()).isEqualTo(730L);
        assertThat(response.getName()).isEqualTo("Counter-Strike 2");
        assertThat(response.getPositionX()).isEqualTo(0);
        assertThat(response.getPositionY()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 폴더 내 동일 게임 다중 복제 추가 성공 검증")
    void addGameToFolder_MultipleCopies_Success() {
        // given
        AddFolderGameRequest request = AddFolderGameRequest.builder()
                .appId(730L)
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(testFolder));
        given(gameRepository.findById(730L)).willReturn(Optional.of(testGame));
        given(folderService.findNextAvailablePosition(eq(testUser), eq(testFolder), any(Integer.class)))
                .willReturn(new int[]{1, 0});

        FolderGame secondCopy = FolderGame.builder()
                .id(101L)
                .folder(testFolder)
                .game(testGame)
                .positionX(1)
                .positionY(0)
                .build();

        given(folderGameRepository.save(any(FolderGame.class))).willReturn(secondCopy);

        // when
        FolderGameDetailResponse response = folderGameService.addGameToFolder(1L, 10L, request);

        // then
        assertThat(response.getId()).isEqualTo(101L);
        assertThat(response.getAppId()).isEqualTo(730L);
        assertThat(response.getPositionX()).isEqualTo(1);
        assertThat(response.getPositionY()).isEqualTo(0);
    }

    @Test
    @DisplayName("게임 여러 개 일괄 추가/복사 붙여넣기(Batch Add) 성공 검증")
    void batchAddGamesToFolder_Success() {
        // given
        com.steamfolder.steamfolder.domain.folder.dto.request.BatchAddFolderGamesRequest request =
                com.steamfolder.steamfolder.domain.folder.dto.request.BatchAddFolderGamesRequest.builder()
                        .appIds(List.of(730L, 730L))
                        .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(testFolder));
        given(folderGameRepository.countByFolder(testFolder)).willReturn(0L);
        given(gameRepository.findById(730L)).willReturn(Optional.of(testGame));
        given(folderService.findNextAvailablePosition(eq(testUser), eq(testFolder), any(Integer.class)))
                .willReturn(new int[]{0, 0})
                .willReturn(new int[]{1, 0});

        FolderGame firstGame = FolderGame.builder().id(100L).folder(testFolder).game(testGame).positionX(0).positionY(0).build();
        FolderGame secondGame = FolderGame.builder().id(101L).folder(testFolder).game(testGame).positionX(1).positionY(0).build();

        given(folderGameRepository.save(any(FolderGame.class)))
                .willReturn(firstGame)
                .willReturn(secondGame);

        // when
        List<FolderGameDetailResponse> response = folderGameService.batchAddGamesToFolder(1L, 10L, request);

        // then
        assertThat(response).hasSize(2);
        assertThat(response.get(0).getId()).isEqualTo(100L);
        assertThat(response.get(1).getId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("폴더당 최대 게임 개수(102개)를 초과하여 추가 시 MAX_FOLDER_GAME_COUNT_EXCEEDED 예외 발생")
    void addGameToFolder_ExceedsMaxCount_ThrowsException() {
        // given
        AddFolderGameRequest request = AddFolderGameRequest.builder()
                .appId(730L)
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(testFolder));
        given(folderGameRepository.countByFolder(testFolder)).willReturn(102L); // 이미 102개 존재

        // when & then
        assertThatThrownBy(() -> folderGameService.addGameToFolder(1L, 10L, request))
                .isInstanceOf(CustomException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MAX_FOLDER_GAME_COUNT_EXCEEDED);
    }

    @Test
    @DisplayName("특정 배치 인스턴스(folderGameId) 단일 삭제 검증")
    void removeFolderGame_Success() {
        // given
        FolderGame folderGame = FolderGame.builder()
                .id(100L)
                .folder(testFolder)
                .game(testGame)
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(testFolder));
        given(folderGameRepository.findByIdAndFolder(100L, testFolder)).willReturn(Optional.of(folderGame));

        // when
        folderGameService.removeFolderGame(1L, 10L, 100L);

        // then
        verify(folderGameRepository).delete(folderGame);
    }

    @Test
    @DisplayName("폴더 내 선택한 게임 인스턴스들 일괄 삭제 검증")
    void removeFolderGames_Success() {
        // given
        FolderGame folderGame = FolderGame.builder()
                .id(100L)
                .folder(testFolder)
                .game(testGame)
                .build();

        RemoveFolderGamesRequest request = RemoveFolderGamesRequest.builder()
                .folderGameIds(List.of(100L))
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(testFolder));
        given(folderGameRepository.findByIdAndFolder(100L, testFolder)).willReturn(Optional.of(folderGame));

        // when
        folderGameService.removeFolderGames(1L, 10L, request);

        // then
        verify(folderGameRepository).delete(folderGame);
    }

    @Test
    @DisplayName("폴더 간 게임 이동(잘라내기 ➡️ 대상 폴더에 2D 격자 배치) 검증")
    void moveGameToAnotherFolder_Success() {
        // given
        Folder targetFolder = Folder.builder()
                .id(20L)
                .user(testUser)
                .name("인생 갓겜")
                .build();

        MoveFolderGameRequest request = MoveFolderGameRequest.builder()
                .targetFolderId(20L)
                .targetPositionX(2)
                .targetPositionY(1)
                .build();

        FolderGame sourceFolderGame = FolderGame.builder()
                .id(100L)
                .folder(testFolder)
                .game(testGame)
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(testFolder));
        given(folderRepository.findById(20L)).willReturn(Optional.of(targetFolder));
        given(folderGameRepository.findByIdAndFolder(100L, testFolder)).willReturn(Optional.of(sourceFolderGame));
        given(folderGameRepository.existsByFolderAndPositionXAndPositionY(targetFolder, 2, 1)).willReturn(false);

        FolderGame newFolderGame = FolderGame.builder()
                .id(200L)
                .folder(targetFolder)
                .game(testGame)
                .positionX(2)
                .positionY(1)
                .build();

        given(folderGameRepository.save(any(FolderGame.class))).willReturn(newFolderGame);

        // when
        FolderGameDetailResponse response = folderGameService.moveGameToAnotherFolder(1L, 10L, 100L, request);

        // then
        verify(folderGameRepository).delete(sourceFolderGame);
        assertThat(response.getFolderId()).isEqualTo(20L);
        assertThat(response.getPositionX()).isEqualTo(2);
        assertThat(response.getPositionY()).isEqualTo(1);
    }

    @Test
    @DisplayName("폴더 간 게임 여러 개 일괄 이동(잘라내기 ➡️ 대상 폴더에 순차 빈 칸 배치) 검증")
    void batchMoveFolderGames_Success() {
        // given
        Folder targetFolder = Folder.builder()
                .id(20L)
                .user(testUser)
                .name("인생 갓겜")
                .build();

        com.steamfolder.steamfolder.domain.folder.dto.request.BatchMoveFolderGamesRequest request =
                com.steamfolder.steamfolder.domain.folder.dto.request.BatchMoveFolderGamesRequest.builder()
                        .folderGameIds(List.of(100L))
                        .targetFolderId(20L)
                        .build();

        FolderGame sourceFolderGame = FolderGame.builder()
                .id(100L)
                .folder(testFolder)
                .game(testGame)
                .build();

        given(folderRepository.findById(10L)).willReturn(Optional.of(testFolder));
        given(folderRepository.findById(20L)).willReturn(Optional.of(targetFolder));
        given(folderGameRepository.countByFolder(targetFolder)).willReturn(5L);
        given(folderGameRepository.findByIdAndFolder(100L, testFolder)).willReturn(Optional.of(sourceFolderGame));
        given(folderService.findNextAvailablePosition(eq(testUser), eq(targetFolder), any(Integer.class)))
                .willReturn(new int[]{0, 1});

        FolderGame newFolderGame = FolderGame.builder()
                .id(200L)
                .folder(targetFolder)
                .game(testGame)
                .positionX(0)
                .positionY(1)
                .build();

        given(folderGameRepository.save(any(FolderGame.class))).willReturn(newFolderGame);

        // when
        List<FolderGameDetailResponse> response = folderGameService.batchMoveFolderGames(1L, 10L, request);

        // then
        verify(folderGameRepository).delete(sourceFolderGame);
        assertThat(response).hasSize(1);
        assertThat(response.get(0).getFolderId()).isEqualTo(20L);
        assertThat(response.get(0).getPositionX()).isEqualTo(0);
        assertThat(response.get(0).getPositionY()).isEqualTo(1);
    }
}
