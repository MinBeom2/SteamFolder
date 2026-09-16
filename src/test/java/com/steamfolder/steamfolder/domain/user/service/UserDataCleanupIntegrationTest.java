package com.steamfolder.steamfolder.domain.user.service;

import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackCategory;
import com.steamfolder.steamfolder.domain.feedback.repository.FeedbackRepository;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.entity.FolderGame;
import com.steamfolder.steamfolder.domain.folder.repository.FolderGameRepository;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.entity.Game;
import com.steamfolder.steamfolder.domain.game.entity.UserGame;
import com.steamfolder.steamfolder.domain.game.repository.GameRepository;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.showcase.entity.ShowcaseLike;
import com.steamfolder.steamfolder.domain.showcase.repository.ShowcaseLikeRepository;
import com.steamfolder.steamfolder.domain.user.entity.RefreshToken;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.RefreshTokenRepository;
import com.steamfolder.steamfolder.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class UserDataCleanupIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FolderRepository folderRepository;

    @Autowired
    private FolderGameRepository folderGameRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private UserGameRepository userGameRepository;

    @Autowired
    private ShowcaseLikeRepository showcaseLikeRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("작업 공간 초기화는 계층 폴더와 폴더 게임을 삭제하고 Steam 보유 게임은 유지한다")
    void resetWorkspace_DeletesFolderTreeAndKeepsSteamLibrary() {
        User user = saveUser("reset-user", 0);
        Game game = saveGame(730L);
        Folder child = saveFolderTree(user, game);
        userGameRepository.save(UserGame.builder().user(user).game(game).playtimeForever(120).build());
        entityManager.flush();
        entityManager.clear();

        userService.resetWorkspace(user.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(folderRepository.count()).isZero();
        assertThat(folderGameRepository.count()).isZero();
        assertThat(userGameRepository.count()).isEqualTo(1);
        assertThat(gameRepository.existsById(730L)).isTrue();
        assertThat(child.getId()).isNotNull();
    }

    @Test
    @DisplayName("회원 탈퇴는 모든 사용자 연관 데이터를 삭제하고 계정을 비활성화한다")
    void deleteAccount_PurgesAllRelatedDataAndSoftDeletesUser() {
        User user = saveUser("deleted-user", 1);
        User targetUser = saveUser("target-user", 1);
        User liker = saveUser("liker-user", 0);
        Game game = saveGame(440L);
        saveFolderTree(user, game);
        userGameRepository.save(UserGame.builder().user(user).game(game).playtimeForever(300).build());
        showcaseLikeRepository.save(ShowcaseLike.builder().user(user).targetUser(targetUser).build());
        showcaseLikeRepository.save(ShowcaseLike.builder().user(liker).targetUser(user).build());
        feedbackRepository.save(Feedback.builder()
                .user(user)
                .category(FeedbackCategory.GENERAL)
                .content("탈퇴 전 피드백")
                .build());
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .token("delete-account-refresh-token")
                .expiryDate(LocalDateTime.now().plusDays(1))
                .build());
        entityManager.flush();
        entityManager.clear();

        userService.deleteAccount(user.getId());
        entityManager.flush();
        entityManager.clear();

        User deletedUser = userRepository.findById(user.getId()).orElseThrow();
        User updatedTarget = userRepository.findById(targetUser.getId()).orElseThrow();
        assertThat(deletedUser.isDeleted()).isTrue();
        assertThat(deletedUser.isPublic()).isFalse();
        assertThat(deletedUser.getLikeCount()).isZero();
        assertThat(updatedTarget.getLikeCount()).isZero();
        assertThat(folderRepository.count()).isZero();
        assertThat(folderGameRepository.count()).isZero();
        assertThat(userGameRepository.count()).isZero();
        assertThat(showcaseLikeRepository.count()).isZero();
        assertThat(feedbackRepository.count()).isZero();
        assertThat(refreshTokenRepository.count()).isZero();
        assertThat(gameRepository.existsById(440L)).isTrue();
    }

    private User saveUser(String steamId, int likeCount) {
        return userRepository.save(User.builder()
                .steamId(steamId)
                .username(steamId)
                .likeCount(likeCount)
                .isPublic(true)
                .build());
    }

    private Game saveGame(Long appId) {
        return gameRepository.save(Game.builder()
                .appId(appId)
                .name("game-" + appId)
                .build());
    }

    private Folder saveFolderTree(User user, Game game) {
        Folder root = folderRepository.save(Folder.builder()
                .user(user)
                .name("root")
                .build());
        Folder child = folderRepository.save(Folder.builder()
                .user(user)
                .parentFolder(root)
                .name("child")
                .depth(1)
                .build());
        folderGameRepository.save(FolderGame.builder()
                .folder(child)
                .game(game)
                .build());
        return child;
    }
}
