package com.steamfolder.steamfolder.domain.user.service;

import com.steamfolder.steamfolder.domain.feedback.repository.FeedbackRepository;
import com.steamfolder.steamfolder.domain.feedback.sync.FeedbackNotionSyncService;
import com.steamfolder.steamfolder.domain.folder.entity.Folder;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.showcase.entity.ShowcaseLike;
import com.steamfolder.steamfolder.domain.showcase.repository.ShowcaseLikeRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.RefreshTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserDataCleanupServiceTest {

    @InjectMocks
    private UserDataCleanupService userDataCleanupService;

    @Mock
    private FolderRepository folderRepository;

    @Mock
    private UserGameRepository userGameRepository;

    @Mock
    private ShowcaseLikeRepository showcaseLikeRepository;

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackNotionSyncService notionSyncService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    @DisplayName("작업 공간 초기화는 폴더와 폴더 게임만 삭제한다")
    void resetWorkspace_DeletesOnlyFolders() {
        User user = user(1L, 0);
        Folder rootFolder = Folder.builder().id(10L).user(user).name("root").build();
        given(folderRepository.findByUserAndParentFolderIsNull(user)).willReturn(List.of(rootFolder));

        userDataCleanupService.resetWorkspace(user);

        verify(folderRepository).deleteAll(List.of(rootFolder));
        verifyNoInteractions(userGameRepository, showcaseLikeRepository, feedbackRepository,
                notionSyncService, refreshTokenRepository);
    }

    @Test
    @DisplayName("회원 탈퇴 데이터 정리는 폴더, 보유 게임, 좋아요, 피드백, 토큰을 삭제한다")
    void purgeAccountData_DeletesAllUserDataAndAdjustsTargetLikeCounts() {
        User user = user(1L, 1);
        User targetUser = user(2L, 3);
        User liker = user(3L, 0);
        ShowcaseLike outgoingLike = ShowcaseLike.builder().id(10L).user(user).targetUser(targetUser).build();
        ShowcaseLike incomingLike = ShowcaseLike.builder().id(11L).user(liker).targetUser(user).build();
        given(showcaseLikeRepository.findByUserOrTargetUser(user, user))
                .willReturn(List.of(outgoingLike, incomingLike));
        Folder rootFolder = Folder.builder().id(20L).user(user).name("root").build();
        given(folderRepository.findByUserAndParentFolderIsNull(user)).willReturn(List.of(rootFolder));

        userDataCleanupService.purgeAccountData(user);

        verify(folderRepository).deleteAll(List.of(rootFolder));
        verify(userGameRepository).deleteByUser(user);
        verify(showcaseLikeRepository).deleteAll(List.of(outgoingLike, incomingLike));
        verify(notionSyncService).requestArchiveForUser(user);
        verify(feedbackRepository).deleteByUser(user);
        verify(refreshTokenRepository).deleteByUser(user);
        assertThat(targetUser.getLikeCount()).isEqualTo(2);
        assertThat(user.getLikeCount()).isEqualTo(1);
    }

    private User user(Long id, int likeCount) {
        return User.builder()
                .id(id)
                .steamId("steam-" + id)
                .username("user-" + id)
                .likeCount(likeCount)
                .build();
    }
}
