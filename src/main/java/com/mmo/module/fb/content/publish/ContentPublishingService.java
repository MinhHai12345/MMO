package com.mmo.module.fb.content.publish;

import java.time.Instant;
import java.util.List;

public interface ContentPublishingService {

    PublicationDetails create(String idempotencyKey, CreatePublicationCommand command);

    List<PublicationSummary> list(String state, int limit);

    PublicationDetails get(Long publicationId);

    PublicationDetails approve(Long publicationId, ApprovalCommand command);

    PublicationDetails dryRun(Long publicationId, DryRunCommand command);

    record CreatePublicationCommand(
            Long draftId,
            PublicationPlatform platform,
            String title,
            String body,
            String mediaReference,
            Instant scheduledAt,
            String requestedBy) {}

    record ApprovalCommand(String approvedBy, String notes) {}

    record DryRunCommand(String requestedBy) {}

    record PublicationSummary(
            Long id,
            Long draftId,
            PublicationPlatform platform,
            PublicationStateMachine.State state,
            String title,
            Instant scheduledAt,
            String createdBy,
            String approvedBy,
            Instant approvedAt,
            String lastAttemptStatus,
            String lastAttemptMessage,
            Instant lastAttemptAt,
            Instant createdAt,
            Instant updatedAt) {}

    record PublicationDetails(
            Long id,
            Long draftId,
            PublicationPlatform platform,
            PublicationStateMachine.State state,
            String idempotencyKey,
            String title,
            String body,
            String mediaReference,
            Instant scheduledAt,
            String createdBy,
            String approvedBy,
            Instant approvedAt,
            String reviewNotes,
            String externalPostId,
            String lastAttemptStatus,
            String lastAttemptMessage,
            int attemptCount,
            Instant lastAttemptAt,
            Instant publishedAt,
            Instant createdAt,
            Instant updatedAt,
            List<PublicationAttemptView> attempts) {
        public PublicationDetails {
            attempts = attempts == null ? List.of() : List.copyOf(attempts);
        }
    }

    record PublicationAttemptView(
            Long id,
            int attemptNumber,
            String executionMode,
            String status,
            String requestSummary,
            String responseSummary,
            String requestedBy,
            Instant startedAt,
            Instant completedAt) {}
}
