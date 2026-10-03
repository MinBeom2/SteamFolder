package com.steamfolder.steamfolder.domain.user.service;

import com.steamfolder.steamfolder.domain.feedback.repository.FeedbackRepository;
import com.steamfolder.steamfolder.domain.feedback.sync.FeedbackNotionSyncService;
import com.steamfolder.steamfolder.domain.folder.repository.FolderRepository;
import com.steamfolder.steamfolder.domain.game.repository.UserGameRepository;
import com.steamfolder.steamfolder.domain.showcase.entity.ShowcaseLike;
import com.steamfolder.steamfolder.domain.showcase.repository.ShowcaseLikeRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import com.steamfolder.steamfolder.domain.user.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserDataCleanupService {

    private final FolderRepository folderRepository;
    private final UserGameRepository userGameRepository;
    private final ShowcaseLikeRepository showcaseLikeRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackNotionSyncService notionSyncService;
    private final RefreshTokenRepository refreshTokenRepository;

    public void resetWorkspace(User user) {
        folderRepository.deleteAll(folderRepository.findByUserAndParentFolderIsNull(user));
    }

    public void purgeAccountData(User user) {
        resetWorkspace(user);
        userGameRepository.deleteByUser(user);
        deleteShowcaseLikes(user);
        notionSyncService.requestArchiveForUser(user);
        feedbackRepository.deleteByUser(user);
        refreshTokenRepository.deleteByUser(user);
    }

    private void deleteShowcaseLikes(User user) {
        List<ShowcaseLike> likes = showcaseLikeRepository.findByUserOrTargetUser(user, user);

        likes.stream()
                .filter(like -> like.getUser().getId().equals(user.getId()))
                .map(ShowcaseLike::getTargetUser)
                .filter(targetUser -> !targetUser.getId().equals(user.getId()))
                .forEach(User::decrementLikeCount);

        showcaseLikeRepository.deleteAll(likes);
    }
}
