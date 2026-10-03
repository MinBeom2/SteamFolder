package com.steamfolder.steamfolder.domain.feedback.sync;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FeedbackNotionSyncAckRequest(
        @Min(1) long version,
        @NotNull FeedbackNotionSyncAction action,
        String notionPageId
) {
}
