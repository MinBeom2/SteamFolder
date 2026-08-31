package com.steamfolder.steamfolder.domain.folder.repository;

import com.steamfolder.steamfolder.common.config.JpaAuditingConfig;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.entity.FolderGame;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class FolderRepositoryTest {

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private FolderGameRepository folderGameRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("2D 그리드 좌표계(positionX, positionY)를 지원하는 계층형 폴더 생성 및 조회 검증")
    void saveAndFindHierarchyFoldersWithGridCoordinates() {
        // given
        User user = userRepository.save(User.builder()
                .steamId("76561198000000001")
                .username("FolderMaster")
                .build());

        // 1. 루트 폴더 생성 (depth: 0, (0, 0) 위치)
        Folder rootFolder = folderRepository.save(Folder.builder()
                .user(user)
                .name("장르별")
                .themeColor("#00F0FF")
                .icon("folder")
                .depth(0)
                .positionX(0)
                .positionY(0)
                .build());

        // 2. 하위 자식 폴더 생성 (자유 격자 배치: FPS는 (0, 0), RPG는 빈 공간 건너뛰고 (3, 0)에 배치)
        Folder childFolder1 = folderRepository.save(Folder.builder()
                .user(user)
                .parentFolder(rootFolder)
                .name("FPS 명작")
                .themeColor("#00F0FF")
                .depth(1)
                .positionX(0)
                .positionY(0)
                .build());

        Folder childFolder2 = folderRepository.save(Folder.builder()
                .user(user)
                .parentFolder(rootFolder)
                .name("RPG 컬렉션")
                .themeColor("#EF4444")
                .depth(1)
                .positionX(3)
                .positionY(0)
                .build());

        entityManager.flush();
        entityManager.clear();

        // when (루트 폴더 조회)
        List<Folder> rootFolders = folderRepository.findByUserAndParentFolderIsNullOrderByPositionYAscPositionXAsc(user);
        // when (하위 자식 폴더 조회)
        List<Folder> childFolders = folderRepository.findByUserAndParentFolderOrderByPositionYAscPositionXAsc(user, rootFolder);

        // then
        assertThat(rootFolders).hasSize(1);
        assertThat(rootFolders.get(0).getName()).isEqualTo("장르별");
        assertThat(rootFolders.get(0).getPositionX()).isEqualTo(0);
        assertThat(rootFolders.get(0).getPositionY()).isEqualTo(0);

        assertThat(childFolders).hasSize(2);
        assertThat(childFolders.get(0).getName()).isEqualTo("FPS 명작");
        assertThat(childFolders.get(0).getPositionX()).isEqualTo(0);
        assertThat(childFolders.get(1).getName()).isEqualTo("RPG 컬렉션");
        assertThat(childFolders.get(1).getPositionX()).isEqualTo(3); // 빈 공간 건너뛴 자유 배치 검증
    }

    @Test
    @DisplayName("폴더 내 게임(FolderGame)의 2D 격자 좌표 배치 및 위치 변경 검증")
    void saveAndMoveFolderGamesGridPosition() {
        // given
        User user = userRepository.save(User.builder()
                .steamId("76561198000000002")
                .username("GameCollector")
                .build());

        Folder folder = folderRepository.save(Folder.builder()
                .user(user)
                .name("최애 게임들")
                .build());

        Game game1 = gameRepository.save(Game.builder().appId(730L).name("CS2").build());
        Game game2 = gameRepository.save(Game.builder().appId(1091500L).name("Cyberpunk 2077").build());

        // 게임1을 (0, 0), 게임2를 (2, 1)에 배치
        FolderGame folderGame1 = folderGameRepository.save(FolderGame.builder()
                .folder(folder)
                .game(game1)
                .positionX(0)
                .positionY(0)
                .build());

        FolderGame folderGame2 = folderGameRepository.save(FolderGame.builder()
                .folder(folder)
                .game(game2)
                .positionX(2)
                .positionY(1)
                .build());

        entityManager.flush();
        entityManager.clear();

        // when (게임1의 위치를 (4, 2)로 드래그 이동)
        FolderGame managedGame1 = folderGameRepository.findById(folderGame1.getId()).orElseThrow();
        managedGame1.updatePosition(4, 2);

        entityManager.flush();
        entityManager.clear();

        Folder managedFolder = folderRepository.findById(folder.getId()).orElseThrow();
        List<FolderGame> sortedGames = folderGameRepository.findByFolderOrderByPositionYAscPositionXAsc(managedFolder);

        // then (Y -> X 순서 정렬: Cyberpunk (2, 1) -> CS2 (4, 2))
        assertThat(sortedGames).hasSize(2);
        assertThat(sortedGames.get(0).getGame().getName()).isEqualTo("Cyberpunk 2077");
        assertThat(sortedGames.get(0).getPositionX()).isEqualTo(2);
        assertThat(sortedGames.get(0).getPositionY()).isEqualTo(1);

        assertThat(sortedGames.get(1).getGame().getName()).isEqualTo("CS2");
        assertThat(sortedGames.get(1).getPositionX()).isEqualTo(4);
        assertThat(sortedGames.get(1).getPositionY()).isEqualTo(2);
    }

    @Test
    @DisplayName("부모 폴더 삭제 시 Cascade로 자식 폴더 및 FolderGame 연쇄 삭제 검증")
    void deleteFolder_CascadesChildrenAndGames() {
        // given
        User user = userRepository.save(User.builder()
                .steamId("76561198000000003")
                .username("TestUser")
                .build());

        Folder parentFolder = folderRepository.save(Folder.builder()
                .user(user)
                .name("부모 폴더")
                .build());

        Folder childFolder = folderRepository.save(Folder.builder()
                .user(user)
                .parentFolder(parentFolder)
                .name("자식 폴더")
                .build());

        Game game = gameRepository.save(Game.builder().appId(570L).name("Dota 2").build());

        FolderGame folderGame = folderGameRepository.save(FolderGame.builder()
                .folder(childFolder)
                .game(game)
                .build());

        entityManager.flush();
        entityManager.clear();

        // when (부모 폴더 조회 후 삭제)
        Folder managedParent = folderRepository.findById(parentFolder.getId()).orElseThrow();
        folderRepository.delete(managedParent);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(folderRepository.findById(parentFolder.getId())).isEmpty();
        assertThat(folderRepository.findById(childFolder.getId())).isEmpty();
        assertThat(folderGameRepository.findById(folderGame.getId())).isEmpty();
        // 게임 마스터 테이블은 유지되어야 함
        assertThat(gameRepository.findById(570L)).isPresent();
    }

    @Test
    @DisplayName("동일 폴더 내 동일 게임을 서로 다른 2D 좌표에 다중 복제 배치 가능 검증")
    void saveMultipleCopiesOfSameGameInSameFolder() {
        // given
        User user = userRepository.save(User.builder()
                .steamId("76561198000000005")
                .username("MemeMaster")
                .build());

        Folder folder = folderRepository.save(Folder.builder()
                .user(user)
                .name("최애 게임 도배")
                .build());

        Game game = gameRepository.save(Game.builder().appId(730L).name("CS2").build());

        // 동일 게임을 (0, 0), (1, 0), (2, 0)에 3개 복제 배치
        FolderGame copy1 = folderGameRepository.save(FolderGame.builder()
                .folder(folder)
                .game(game)
                .positionX(0)
                .positionY(0)
                .build());

        FolderGame copy2 = folderGameRepository.save(FolderGame.builder()
                .folder(folder)
                .game(game)
                .positionX(1)
                .positionY(0)
                .build());

        FolderGame copy3 = folderGameRepository.save(FolderGame.builder()
                .folder(folder)
                .game(game)
                .positionX(2)
                .positionY(0)
                .build());

        entityManager.flush();
        entityManager.clear();

        // when
        Folder managedFolder = folderRepository.findById(folder.getId()).orElseThrow();
        List<FolderGame> games = folderGameRepository.findByFolderOrderByPositionYAscPositionXAsc(managedFolder);

        // then
        assertThat(games).hasSize(3);
        assertThat(games.get(0).getGame().getAppId()).isEqualTo(730L);
        assertThat(games.get(1).getGame().getAppId()).isEqualTo(730L);
        assertThat(games.get(2).getGame().getAppId()).isEqualTo(730L);
        assertThat(games.get(0).getPositionX()).isEqualTo(0);
        assertThat(games.get(1).getPositionX()).isEqualTo(1);
        assertThat(games.get(2).getPositionX()).isEqualTo(2);
    }
}
