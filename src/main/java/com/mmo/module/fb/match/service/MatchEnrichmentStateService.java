package com.mmo.module.fb.match.service;

import com.mmo.module.fb.match.entity.Match;
import com.mmo.module.fb.match.entity.MatchEnrichmentState;
import com.mmo.module.fb.match.enums.MatchEnrichmentResource;
import com.mmo.module.fb.match.enums.MatchProvider;
import com.mmo.module.fb.match.repository.MatchEnrichmentStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Transactional lifecycle for provider/resource-specific enrichment attempts. */
@Service
@RequiredArgsConstructor
public class MatchEnrichmentStateService {

    private static final Duration EMPTY_RETRY_DELAY = Duration.ofHours(24);
    private static final Duration FAILURE_RETRY_DELAY = Duration.ofMinutes(30);

    private final MatchEnrichmentStateRepository repository;

    @Transactional
    public void start(Match match, MatchProvider provider, MatchEnrichmentResource resource) {
        MatchEnrichmentState state = getOrCreate(match, provider, resource);
        state.markAttempt(Instant.now());
        repository.save(state);
    }

    @Transactional
    public void succeed(Match match, MatchProvider provider, MatchEnrichmentResource resource) {
        MatchEnrichmentState state = getOrCreate(match, provider, resource);
        state.markSucceeded(Instant.now());
        repository.save(state);
    }

    @Transactional
    public void empty(Match match, MatchProvider provider, MatchEnrichmentResource resource) {
        Instant now = Instant.now();
        MatchEnrichmentState state = getOrCreate(match, provider, resource);
        state.markEmpty(now, now.plus(EMPTY_RETRY_DELAY));
        repository.save(state);
    }

    @Transactional
    public void skipped(Match match, MatchProvider provider, MatchEnrichmentResource resource) {
        MatchEnrichmentState state = getOrCreate(match, provider, resource);
        state.markSkipped(Instant.now().plus(EMPTY_RETRY_DELAY));
        repository.save(state);
    }

    @Transactional
    public void failed(Match match, MatchProvider provider, MatchEnrichmentResource resource, Throwable error) {
        Instant now = Instant.now();
        MatchEnrichmentState state = getOrCreate(match, provider, resource);
        state.markFailed("PROVIDER_ERROR",
                error == null ? "Unknown provider error" : error.getMessage(),
                now.plus(FAILURE_RETRY_DELAY));
        repository.save(state);
    }

    private MatchEnrichmentState getOrCreate(
            Match match, MatchProvider provider, MatchEnrichmentResource resource) {
        if (match == null || match.getId() == null || provider == null || resource == null) {
            throw new IllegalArgumentException("Match, provider and enrichment resource are required");
        }
        return repository.findByMatchIdAndProviderAndResource(match.getId(), provider, resource)
                .orElseGet(() -> MatchEnrichmentState.builder()
                        .match(match).provider(provider).resource(resource).build());
    }
}
