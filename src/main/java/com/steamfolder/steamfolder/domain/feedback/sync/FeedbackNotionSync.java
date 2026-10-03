package com.steamfolder.steamfolder.domain.feedback.sync;

import com.steamfolder.steamfolder.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "feedback_notion_sync", indexes = {
        @Index(name = "idx_feedback_notion_sync_pending", columnList = "version, acknowledged_version")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackNotionSync extends BaseTimeEntity {

    @Id
    @Column(name = "feedback_id")
    private Long feedbackId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 10, nullable = false)
    private FeedbackNotionSyncAction action;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "acknowledged_version", nullable = false)
    private long acknowledgedVersion;

    @Column(name = "notion_page_id", length = 100)
    private String notionPageId;

    private FeedbackNotionSync(Long feedbackId, FeedbackNotionSyncAction action) {
        this.feedbackId = feedbackId;
        this.action = action;
        this.version = 1;
    }

    public static FeedbackNotionSync upsert(Long feedbackId) {
        return new FeedbackNotionSync(feedbackId, FeedbackNotionSyncAction.UPSERT);
    }

    public static FeedbackNotionSync archive(Long feedbackId) {
        return new FeedbackNotionSync(feedbackId, FeedbackNotionSyncAction.ARCHIVE);
    }

    public void request(FeedbackNotionSyncAction nextAction) {
        action = nextAction;
        version++;
    }

    public boolean acknowledge(long expectedVersion, FeedbackNotionSyncAction expectedAction, String pageId) {
        if (version != expectedVersion || action != expectedAction) {
            return false;
        }
        if (acknowledgedVersion == version) {
            return action == FeedbackNotionSyncAction.ARCHIVE || notionPageId.equals(pageId);
        }
        acknowledgedVersion = version;
        notionPageId = action == FeedbackNotionSyncAction.UPSERT ? pageId : null;
        return true;
    }
}
