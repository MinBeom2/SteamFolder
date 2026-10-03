package com.steamfolder.steamfolder.domain.feedback.sync;

import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.domain.feedback.entity.Feedback;
import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisStatus;
import com.steamfolder.steamfolder.domain.feedback.repository.FeedbackRepository;
import com.steamfolder.steamfolder.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackNotionSyncService {

    private final FeedbackNotionSyncRepository syncRepository;
    private final FeedbackRepository feedbackRepository;

    @Transactional
    public void requestUpsert(Long feedbackId) {
        FeedbackNotionSync sync = syncRepository.findByIdForUpdate(feedbackId).orElse(null);
        if (sync == null) {
            syncRepository.save(FeedbackNotionSync.upsert(feedbackId));
        } else {
            sync.request(FeedbackNotionSyncAction.UPSERT);
        }
    }

    @Transactional
    public void requestArchiveForUser(User user) {
        feedbackRepository.findByUserForUpdate(user).stream().map(Feedback::getId).forEach(feedbackId -> {
            FeedbackNotionSync sync = syncRepository.findByIdForUpdate(feedbackId).orElse(null);
            if (sync == null) {
                syncRepository.save(FeedbackNotionSync.archive(feedbackId));
            } else {
                sync.request(FeedbackNotionSyncAction.ARCHIVE);
            }
        });
    }

    @Transactional(readOnly = true)
    public FeedbackNotionSyncBatch findPending(long afterId, int limit) {
        if (afterId < 0 || limit < 1 || limit > 100) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        List<FeedbackNotionSync> fetched = syncRepository.findPendingAfter(afterId, PageRequest.of(0, limit + 1));
        boolean hasMore = fetched.size() > limit;
        List<FeedbackNotionSync> page = hasMore ? fetched.subList(0, limit) : fetched;
        Map<Long, Feedback> feedbackById = feedbackRepository.findAllById(page.stream()
                        .filter(sync -> sync.getAction() == FeedbackNotionSyncAction.UPSERT)
                        .map(FeedbackNotionSync::getFeedbackId)
                        .toList()).stream()
                .collect(Collectors.toMap(Feedback::getId, Function.identity()));

        List<FeedbackNotionSyncJob> items = page.stream()
                .filter(sync -> sync.getAction() == FeedbackNotionSyncAction.ARCHIVE
                        || feedbackById.containsKey(sync.getFeedbackId()))
                .map(sync -> FeedbackNotionSyncJob.from(sync, feedbackById.get(sync.getFeedbackId())))
                .toList();
        Long nextCursor = hasMore ? page.getLast().getFeedbackId() : null;
        return new FeedbackNotionSyncBatch(items, nextCursor);
    }

    @Transactional
    public void acknowledge(Long feedbackId, FeedbackNotionSyncAckRequest request) {
        if (request.action() == FeedbackNotionSyncAction.UPSERT
                && (!StringUtils.hasText(request.notionPageId()) || request.notionPageId().length() > 100)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE);
        }

        FeedbackNotionSync sync = syncRepository.findByIdForUpdate(feedbackId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENTITY_NOT_FOUND));
        if (!sync.acknowledge(request.version(), request.action(), request.notionPageId())) {
            throw new CustomException(ErrorCode.FEEDBACK_NOTION_SYNC_CONFLICT);
        }
    }

    @Transactional
    public void seedExisting(int maxAttempts) {
        while (true) {
            List<Long> ids = feedbackRepository.findTerminalIdsWithoutNotionSync(
                    FeedbackAnalysisStatus.COMPLETED,
                    FeedbackAnalysisStatus.FAILED,
                    maxAttempts,
                    PageRequest.of(0, 200)
            );
            if (ids.isEmpty()) {
                return;
            }
            syncRepository.saveAllAndFlush(ids.stream().map(FeedbackNotionSync::upsert).toList());
        }
    }
}
