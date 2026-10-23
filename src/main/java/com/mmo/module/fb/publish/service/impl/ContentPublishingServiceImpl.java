package com.mmo.module.fb.publish.service.impl;

import com.mmo.module.fb.content.entity.ContentDraftRecordEntity;
import com.mmo.module.fb.content.repository.ContentDraftRecordRepository;
import com.mmo.module.fb.publish.service.ContentPublisher;
import com.mmo.module.fb.publish.service.ContentPublishingService;
import com.mmo.module.fb.publish.domain.PublicationPlatform;
import com.mmo.module.fb.publish.domain.PublicationPayloadValidator;
import com.mmo.module.fb.publish.domain.PublicationStateMachine;
import com.mmo.module.fb.publish.entity.ContentPublicationAttemptEntity;
import com.mmo.module.fb.publish.repository.ContentPublicationAttemptRepository;
import com.mmo.module.fb.publish.entity.ContentPublicationEntity;
import com.mmo.module.fb.publish.repository.ContentPublicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ContentPublishingServiceImpl implements ContentPublishingService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int MAX_ACTOR_LENGTH = 120;

    private final ContentDraftRecordRepository draftRepository;
    private final ContentPublicationRepository publicationRepository;
    private final ContentPublicationAttemptRepository attemptRepository;
    private final ContentPublisher dryRunPublisher;

    @Override
    @Transactional
    public PublicationDetails create(String idempotencyKey, CreatePublicationCommand command) {
        validateCreate(command);
        String key = normalizeIdempotencyKey(idempotencyKey);
        String actor = normalizeActor(command.requestedBy(), "requestedBy");

        ContentPublicationEntity existing = publicationRepository
                .findByPlatformAndIdempotencyKey(command.platform(), key)
                .orElse(null);
        if (existing != null) {
            if (!sameRequest(existing, command)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key was already used for a different publication request");
            }
            return details(existing);
        }

        ContentDraftRecordEntity draft = draftRepository.findById(command.draftId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Content draft not found"));
        requireEditorialApproval(draft);

        ContentPublicationEntity publication = new ContentPublicationEntity();
        publication.setDraft(draft);
        publication.setPlatform(command.platform());
        publication.setIdempotencyKey(key);
        publication.setTitle(command.title().strip());
        publication.setBody(command.body().strip());
        publication.setMediaReference(normalizeOptional(command.mediaReference()));
        publication.setScheduledAt(command.scheduledAt());
        publication.setCreatedBy(actor);
        publication.setState(PublicationStateMachine.transition(
                PublicationStateMachine.State.DRAFT, PublicationStateMachine.State.NEEDS_REVIEW));
        publication.setAttemptCount(0);
        publication.setCreatedAtSnapshot(Instant.now());
        publication.setUpdatedAtSnapshot(Instant.now());

        try {
            return details(publicationRepository.saveAndFlush(publication));
        } catch (DataIntegrityViolationException duplicateKeyRace) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A publication request with this idempotency key was created concurrently; retry the same request",
                    duplicateKeyRace);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicationSummary> list(String state, int limit) {
        PageRequest page = PageRequest.of(0, normalizeLimit(limit));
        List<ContentPublicationEntity> publications;
        if (state == null || state.isBlank()) {
            publications = publicationRepository.findAllByOrderByCreatedAtSnapshotDesc(page);
        } else {
            PublicationStateMachine.State parsed;
            try {
                parsed = PublicationStateMachine.State.valueOf(state.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported publication state");
            }
            publications = publicationRepository.findByStateOrderByCreatedAtSnapshotDesc(parsed, page);
        }
        return publications.stream().map(this::summary).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PublicationDetails get(Long publicationId) {
        return details(findPublication(publicationId, false));
    }

    @Override
    @Transactional
    public PublicationDetails approve(Long publicationId, ApprovalCommand command) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Approval command is required");
        }
        String actor = normalizeActor(command.approvedBy(), "approvedBy");
        ContentPublicationEntity publication = findPublication(publicationId, true);
        if (publication.getState() != PublicationStateMachine.State.NEEDS_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only a publication in NEEDS_REVIEW can be approved");
        }

        requireEditorialApproval(publication.getDraft());
        publication.setState(PublicationStateMachine.transition(
                publication.getState(), PublicationStateMachine.State.APPROVED));
        if (publication.getScheduledAt() != null && publication.getScheduledAt().isAfter(Instant.now())) {
            publication.setState(PublicationStateMachine.transition(
                    publication.getState(), PublicationStateMachine.State.SCHEDULED));
        }
        publication.setApprovedBy(actor);
        publication.setApprovedAt(Instant.now());
        publication.setReviewNotes(normalizeOptional(command.notes()));
        publication.setUpdatedAtSnapshot(Instant.now());
        return details(publicationRepository.save(publication));
    }

    @Override
    @Transactional
    public PublicationDetails dryRun(Long publicationId, DryRunCommand command) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dry-run command is required");
        }
        String actor = normalizeActor(command.requestedBy(), "requestedBy");
        ContentPublicationEntity publication = findPublication(publicationId, true);
        PublicationStateMachine.State state = publication.getState();
        if (state != PublicationStateMachine.State.APPROVED
                && state != PublicationStateMachine.State.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Dry-run requires an approved publication");
        }
        requireEditorialApproval(publication.getDraft());

        int attemptNumber = publication.getAttemptCount() + 1;
        Instant startedAt = Instant.now();
        ContentPublicationAttemptEntity attempt = new ContentPublicationAttemptEntity();
        attempt.setPublication(publication);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setExecutionMode("DRY_RUN");
        attempt.setStatus("RUNNING");
        attempt.setRequestedBy(actor);
        attempt.setStartedAt(startedAt);
        attempt.setRequestSummary("platform=" + publication.getPlatform()
                + ", titleLength=" + publication.getTitle().length()
                + ", bodyLength=" + publication.getBody().length()
                + ", mediaAttached=" + (publication.getMediaReference() != null)
                + ", scheduled=" + (publication.getScheduledAt() != null));
        attemptRepository.saveAndFlush(attempt);

        try {
            ContentPublisher.PublishResult result = dryRunPublisher.publish(
                    new ContentPublisher.PublishCommand(
                            publication.getIdempotencyKey(),
                            publication.getTitle(),
                            publication.getBody(),
                            publication.getMediaReference(),
                            publication.getScheduledAt()));
            Instant completedAt = result.completedAt() == null ? Instant.now() : result.completedAt();
            attempt.setStatus("DRY_RUN");
            attempt.setResponseSummary(result.message());
            attempt.setCompletedAt(completedAt);
            publication.setAttemptCount(attemptNumber);
            publication.setLastAttemptStatus("DRY_RUN");
            publication.setLastAttemptMessage(result.message());
            publication.setLastAttemptAt(completedAt);
            publication.setUpdatedAtSnapshot(completedAt);
            attemptRepository.save(attempt);
            publicationRepository.save(publication);
            return details(publication);
        } catch (RuntimeException failure) {
            Instant completedAt = Instant.now();
            attempt.setStatus("FAILED");
            attempt.setResponseSummary(safeFailureMessage(failure));
            attempt.setCompletedAt(completedAt);
            attemptRepository.save(attempt);
            publication.setAttemptCount(attemptNumber);
            publication.setLastAttemptStatus("FAILED");
            publication.setLastAttemptMessage(safeFailureMessage(failure));
            publication.setLastAttemptAt(completedAt);
            publication.setUpdatedAtSnapshot(completedAt);
            publicationRepository.save(publication);
            return details(publication);
        }
    }

    private ContentPublicationEntity findPublication(Long publicationId, boolean lock) {
        if (publicationId == null || publicationId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive publicationId is required");
        }
        return (lock ? publicationRepository.findByIdForUpdate(publicationId)
                : publicationRepository.findById(publicationId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Publication not found"));
    }

    private PublicationSummary summary(ContentPublicationEntity publication) {
        return new PublicationSummary(
                publication.getId(), publication.getDraft().getId(), publication.getPlatform(),
                publication.getState(), publication.getTitle(), publication.getScheduledAt(),
                publication.getCreatedBy(), publication.getApprovedBy(), publication.getApprovedAt(),
                publication.getLastAttemptStatus(), publication.getLastAttemptMessage(),
                publication.getLastAttemptAt(), publication.getCreatedAtSnapshot(),
                publication.getUpdatedAtSnapshot());
    }

    private PublicationDetails details(ContentPublicationEntity publication) {
        List<PublicationAttemptView> attempts = attemptRepository
                .findByPublicationIdOrderByAttemptNumberDesc(publication.getId()).stream()
                .map(attempt -> new PublicationAttemptView(
                        attempt.getId(), attempt.getAttemptNumber(), attempt.getExecutionMode(),
                        attempt.getStatus(), attempt.getRequestSummary(), attempt.getResponseSummary(),
                        attempt.getRequestedBy(), attempt.getStartedAt(), attempt.getCompletedAt()))
                .toList();
        return new PublicationDetails(
                publication.getId(), publication.getDraft().getId(), publication.getPlatform(),
                publication.getState(), publication.getIdempotencyKey(), publication.getTitle(),
                publication.getBody(), publication.getMediaReference(), publication.getScheduledAt(),
                publication.getCreatedBy(), publication.getApprovedBy(), publication.getApprovedAt(),
                publication.getReviewNotes(), publication.getExternalPostId(), publication.getLastAttemptStatus(),
                publication.getLastAttemptMessage(), publication.getAttemptCount(),
                publication.getLastAttemptAt(), publication.getPublishedAt(),
                publication.getCreatedAtSnapshot(), publication.getUpdatedAtSnapshot(), attempts);
    }

    private static void validateCreate(CreatePublicationCommand command) {
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Publication command is required");
        }
        if (command.draftId() == null || command.draftId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A positive draftId is required");
        }
        if (command.platform() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Publication platform is required");
        }
        if (command.title() == null || command.title().isBlank() || command.title().strip().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Title is required and must be at most 500 characters");
        }
        if (command.body() == null || command.body().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Publication body is required");
        }
        PublicationPayloadValidator.validate(command.platform(), command.title().strip(), command.body().strip(), command.mediaReference());
        if (command.mediaReference() != null && command.mediaReference().length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mediaReference must be at most 1000 characters");
        }
        if (command.scheduledAt() != null && command.scheduledAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "scheduledAt must be in the future");
        }
        normalizeActor(command.requestedBy(), "requestedBy");
    }

    private static String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }
        String normalized = key.strip();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must not exceed 128 characters");
        }
        return normalized;
    }

    private static String normalizeActor(String actor, String field) {
        if (actor == null || actor.isBlank() || actor.strip().length() > MAX_ACTOR_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " is required and must be at most 120 characters");
        }
        return actor.strip();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static int normalizeLimit(int limit) {
        return limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
    }

    private static void requireEditorialApproval(ContentDraftRecordEntity draft) {
        if (!"APPROVED".equalsIgnoreCase(draft.getEditorialStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The source content draft must be editorially APPROVED first");
        }
        if ("BLOCKED".equalsIgnoreCase(draft.getGenerationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Blocked content drafts cannot be published");
        }
    }

    private static boolean sameRequest(ContentPublicationEntity existing, CreatePublicationCommand command) {
        return existing.getDraft() != null
                && java.util.Objects.equals(existing.getDraft().getId(), command.draftId())
                && existing.getPlatform() == command.platform()
                && java.util.Objects.equals(existing.getTitle(), command.title().strip())
                && java.util.Objects.equals(existing.getBody(), command.body().strip())
                && java.util.Objects.equals(existing.getMediaReference(), normalizeOptional(command.mediaReference()))
                && java.util.Objects.equals(existing.getScheduledAt(), command.scheduledAt());
    }

    private static String safeFailureMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
