package com.steamfolder.steamfolder.domain.folder.service;

import com.steamfolder.steamfolder.common.config.JpaAuditingConfig;
import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.folder.dto.request.CopyFolderRequest;
import com.steamfolder.steamfolder.domain.folder.dto.response.FolderResponse;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.entity.FolderGame;
import com.steamfolder.steamfolder.domain.folder.repository.FolderGameRepository;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

@DataJpaTest
@Import({JpaAuditingConfig.class, FolderService.class})
class FolderCopyIntegrationTest {

    @Autowired FolderService service;
    @Autowired FolderRepository folders;
    @Autowired FolderGameRepository placements;
    @Autowired UserRepository users;
    @Autowired GameRepository games;
    @MockitoSpyBean UserGameRepository ownedGames;
    @Autowired TestEntityManager entityManager;

    private User owner;
    private Folder source;
    private Folder child;
    private Folder grandchild;
    private Game game;

    @BeforeEach
    void setUp() {
        owner = users.save(User.builder().steamId("copy-owner").username("Owner").build());
        source = saveFolder(owner, null, "Source", 0, 0);
        source.updateFolder(null, "#123456", "gamepad");
        source.toggleOpen(true);
        child = saveFolder(owner, source, "Child", 2, 3);
        grandchild = saveFolder(owner, child, "Grandchild", 4, 5);
        game = games.save(Game.builder().appId(10L).name("Game").build());
        addGame(source, 3, 1);
        addGame(child, 1, 4);
        addGame(grandchild, 0, 2);
        ownedGames.save(UserGame.builder().user(owner).game(game).playtimeForever(125).build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void copiesAllDescendantsAndGamePlacementsWithIndependentIds() {
        FolderResponse response = service.copyFolder(owner.getId(), source.getId(),
                CopyFolderRequest.builder().build());
        entityManager.clear();

        Folder copy = folders.findById(response.getId()).orElseThrow();
        Folder copiedChild = copy.getChildFolders().getFirst();
        Folder copiedGrandchild = copiedChild.getChildFolders().getFirst();
        assertThat(copy.getId()).isNotEqualTo(source.getId());
        assertThat(copy.getName()).isEqualTo("Source - 복사본");
        assertThat(copy.getThemeColor()).isEqualTo("#123456");
        assertThat(copy.getIcon()).isEqualTo("gamepad");
        assertThat(copy.isOpen()).isTrue();
        assertThat(copy.getPositionX()).isEqualTo(1);
        assertThat(copy.getPositionY()).isZero();
        assertThat(copiedChild.getId()).isNotEqualTo(child.getId());
        assertThat(copiedChild.getName()).isEqualTo("Child");
        assertThat(copiedChild.getPositionX()).isEqualTo(2);
        assertThat(copiedChild.getPositionY()).isEqualTo(3);
        assertThat(copiedGrandchild.getId()).isNotEqualTo(grandchild.getId());
        assertThat(copiedGrandchild.getDepth()).isEqualTo(2);
        assertThat(copiedGrandchild.getPositionX()).isEqualTo(4);
        assertThat(copiedGrandchild.getPositionY()).isEqualTo(5);
        assertThat(copy.getFolderGames()).hasSize(1);
        assertThat(copiedChild.getFolderGames()).hasSize(1);
        assertThat(copiedGrandchild.getFolderGames()).hasSize(1);
        assertThat(copiedGrandchild.getFolderGames().getFirst().getPositionY()).isEqualTo(2);
        assertThat(copy.getFolderGames().getFirst().getId())
                .isNotEqualTo(folders.findById(source.getId()).orElseThrow().getFolderGames().getFirst().getId());
        assertThat(response.getGames().getFirst().getPlaytimeForever()).isEqualTo(125);
        assertThat(folders.countByUser(owner)).isEqualTo(6);
        assertThat(placements.count()).isEqualTo(6);
        assertThat(games.count()).isEqualTo(1);
        assertThat(ownedGames.countByUser(owner)).isEqualTo(1);

        folders.delete(copy);
        folders.flush();
        entityManager.clear();
        assertThat(folders.findById(source.getId())).isPresent();
        assertThat(folders.findById(child.getId())).isPresent();
        assertThat(folders.findById(grandchild.getId())).isPresent();
        assertThat(placements.count()).isEqualTo(3);
    }

    @Test
    void repeatedCopiesGetUniqueNamesWithinTheNameLimit() {
        FolderResponse first = service.copyFolder(owner.getId(), source.getId(),
                CopyFolderRequest.builder().build());
        FolderResponse second = service.copyFolder(owner.getId(), source.getId(),
                CopyFolderRequest.builder().build());
        assertThat(first.getName()).isEqualTo("Source - 복사본");
        assertThat(second.getName()).isEqualTo("Source - 복사본 (2)");

        Folder longName = saveFolder(owner, null, "x".repeat(100), 5, 0);
        FolderResponse longCopy = service.copyFolder(owner.getId(), longName.getId(),
                CopyFolderRequest.builder().build());
        assertThat(longCopy.getName()).hasSize(100).endsWith(" - 복사본");
    }

    @Test
    void copiesIntoAnotherFolderAndAvoidsGamesAtTheRequestedPosition() {
        Folder target = saveFolder(owner, null, "Target", 1, 0);
        addGame(target, 0, 0);
        entityManager.flush();
        entityManager.clear();
        FolderResponse copy = service.copyFolder(owner.getId(), source.getId(),
                CopyFolderRequest.builder().targetParentFolderId(target.getId())
                        .targetPositionX(0).targetPositionY(0).build());
        assertThat(copy.getName()).isEqualTo("Source");
        assertThat(copy.getParentFolderId()).isEqualTo(target.getId());
        assertThat(copy.getDepth()).isEqualTo(1);
        assertThat(copy.getPositionX()).isEqualTo(1);
        assertThat(copy.getPositionY()).isZero();
        entityManager.clear();
        Folder copiedChild = folders.findById(copy.getId()).orElseThrow().getChildFolders().getFirst();
        assertThat(copiedChild.getDepth()).isEqualTo(2);
        assertThat(copiedChild.getChildFolders().getFirst().getDepth()).isEqualTo(3);
    }

    @Test
    void copyingIntoDescendantCreatesAFiniteSubtreeAtMaximumAllowedDepth() {
        FolderResponse copy = service.copyFolder(owner.getId(), source.getId(),
                CopyFolderRequest.builder().targetParentFolderId(child.getId()).build());
        entityManager.clear();
        assertThat(copy.getParentFolderId()).isEqualTo(child.getId());
        assertThat(folders.countByUser(owner)).isEqualTo(6);
        assertThat(folders.findById(copy.getId()).orElseThrow()
                .getChildFolders().getFirst().getName()).isEqualTo("Child");
    }

    @Test
    void rejectsFullSubtreeWhenDepthWouldExceedFiveLevels() {
        assertFailure(source.getId(), CopyFolderRequest.builder()
                .targetParentFolderId(grandchild.getId()).build(), ErrorCode.MAX_FOLDER_DEPTH_EXCEEDED);
        assertThat(folders.countByUser(owner)).isEqualTo(3);
        assertThat(placements.count()).isEqualTo(3);
    }

    @Test
    void rejectsFullSubtreeWhenOnlyTwoFolderSlotsRemain() {
        for (int i = 0; i < 95; i++) {
            saveFolder(owner, null, "Other " + i, i, 10);
        }
        assertFailure(source.getId(), CopyFolderRequest.builder().build(),
                ErrorCode.MAX_FOLDER_COUNT_EXCEEDED);
        assertThat(folders.countByUser(owner)).isEqualTo(98);
    }

    @Test
    void rejectsForeignSourceAndDestinationAndMissingSource() {
        User other = users.save(User.builder().steamId("other-owner").username("Other").build());
        Folder foreign = saveFolder(other, null, "Foreign", 0, 0);
        assertFailure(foreign.getId(), CopyFolderRequest.builder().build(), ErrorCode.FOLDER_ACCESS_DENIED);
        assertFailure(source.getId(), CopyFolderRequest.builder()
                .targetParentFolderId(foreign.getId()).build(), ErrorCode.FOLDER_ACCESS_DENIED);
        assertFailure(Long.MAX_VALUE, CopyFolderRequest.builder().build(), ErrorCode.FOLDER_NOT_FOUND);
        assertThat(folders.countByUser(owner)).isEqualTo(3);
    }

    @Test
    void rejectsExplicitDuplicateOrBlankNames() {
        assertFailure(source.getId(), CopyFolderRequest.builder().name("Source").build(),
                ErrorCode.DUPLICATE_FOLDER_NAME);
        assertFailure(source.getId(), CopyFolderRequest.builder().name(" ").build(),
                ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void rollsBackAllCopiesIfFailureOccursAfterInsertsHaveBeenFlushed() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        try {
            doThrow(new IllegalStateException("response failure")).when(ownedGames).findByUser(any());
            assertThatThrownBy(() -> service.copyFolder(owner.getId(), source.getId(),
                    CopyFolderRequest.builder().build())).isInstanceOf(IllegalStateException.class);
            assertThat(folders.countByUser(owner)).isEqualTo(3);
            assertThat(placements.count()).isEqualTo(3);
        } finally {
            reset(ownedGames);
            TestTransaction.start();
            folders.deleteById(source.getId());
            folders.flush();
            ownedGames.deleteByUser(owner);
            games.deleteById(game.getAppId());
            users.deleteById(owner.getId());
            TestTransaction.flagForCommit();
            TestTransaction.end();
            TestTransaction.start();
        }
    }

    @Test
    void copiesAnEmptyFolderAtExplicitCoordinates() {
        Folder empty = saveFolder(owner, null, "Empty", 3, 0);
        FolderResponse copy = service.copyFolder(owner.getId(), empty.getId(),
                CopyFolderRequest.builder().name("Empty copy")
                        .targetPositionX(4).targetPositionY(2).build());
        assertThat(copy.getName()).isEqualTo("Empty copy");
        assertThat(copy.getPositionX()).isEqualTo(4);
        assertThat(copy.getPositionY()).isEqualTo(2);
        assertThat(copy.getGames()).isEmpty();
        assertThat(copy.getChildFolders()).isEmpty();
    }

    @Test
    void rejectsAnOversizedGameCollectionBeforeSavingAnyCopies() {
        for (int i = 0; i < FolderGameService.MAX_GAMES_PER_FOLDER; i++) {
            addGame(grandchild, i, 0);
        }
        entityManager.flush();
        entityManager.clear();
        assertFailure(source.getId(), CopyFolderRequest.builder().build(),
                ErrorCode.MAX_FOLDER_GAME_COUNT_EXCEEDED);
        assertThat(folders.countByUser(owner)).isEqualTo(3);
    }

    private void assertFailure(Long sourceId, CopyFolderRequest request, ErrorCode code) {
        assertThatThrownBy(() -> service.copyFolder(owner.getId(), sourceId, request))
                .isInstanceOf(CustomException.class).hasFieldOrPropertyWithValue("errorCode", code);
    }

    private Folder saveFolder(User user, Folder parent, String name, int x, int y) {
        return folders.save(Folder.builder().user(user).parentFolder(parent).name(name)
                .depth(parent == null ? 0 : parent.getDepth() + 1).positionX(x).positionY(y).build());
    }

    private void addGame(Folder folder, int x, int y) {
        placements.save(FolderGame.builder().folder(folder).game(game).positionX(x).positionY(y).build());
    }
}
