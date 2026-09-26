package com.steamfolder.steamfolder.domain.feedback.client;

import com.steamfolder.steamfolder.domain.feedback.entity.FeedbackAnalysisFailureReason;
import lombok.Getter;

@Getter
public class FeedbackAnalysisException extends RuntimeException {

    private final FeedbackAnalysisFailureReason failureReason;

    public FeedbackAnalysisException(FeedbackAnalysisFailureReason failureReason, String message) {
        super(message);
        this.failureReason = failureReason;
    }

    public FeedbackAnalysisException(
            FeedbackAnalysisFailureReason failureReason,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.failureReason = failureReason;
    }
}
