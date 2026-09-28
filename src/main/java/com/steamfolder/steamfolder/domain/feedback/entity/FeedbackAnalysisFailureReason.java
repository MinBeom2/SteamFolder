package com.steamfolder.steamfolder.domain.feedback.entity;

public enum FeedbackAnalysisFailureReason {
    // Kept for feedback rows created before OpenAI became the analysis provider.
    N8N_CONFIGURATION_MISSING,
    N8N_CONNECTION_FAILED,
    N8N_TIMEOUT,
    AI_CONFIGURATION_MISSING,
    AI_AUTHENTICATION_FAILED,
    AI_CONNECTION_FAILED,
    AI_TIMEOUT,
    AI_RATE_LIMITED,
    AI_INVALID_RESPONSE,
    AI_ANALYSIS_FAILED,
    UNKNOWN_ERROR
}
