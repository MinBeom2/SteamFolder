package com.steamfolder.steamfolder.domain.feedback.sync;

import java.util.List;

public record FeedbackNotionSyncBatch(
        List<FeedbackNotionSyncJob> items,
        Long nextCursor
) {
}
