package com.steamfolder.steamfolder.domain.feedback.sync;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/feedback-notion-sync/jobs")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "feedback.notion-sync", name = "enabled", havingValue = "true")
public class FeedbackNotionSyncController {

    private final FeedbackNotionSyncService syncService;

    @GetMapping
    public ResponseEntity<ApiResponse<FeedbackNotionSyncBatch>> getPending(
            @RequestParam(defaultValue = "0") long afterId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(syncService.findPending(afterId, limit)));
    }

    @PostMapping("/{feedbackId}/ack")
    public ResponseEntity<Void> acknowledge(
            @PathVariable Long feedbackId,
            @Valid @RequestBody FeedbackNotionSyncAckRequest request
    ) {
        syncService.acknowledge(feedbackId, request);
        return ResponseEntity.noContent().build();
    }
}
