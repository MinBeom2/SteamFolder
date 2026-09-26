package com.steamfolder.steamfolder.domain.feedback.dto.analysis;

import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackPriority;

public record FeedbackAnalysisResponse(
        FeedbackPriority priority,
        String summary
) {
}
