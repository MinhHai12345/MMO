package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.ingest.application.port.out.SourceAwareMergePort;
import com.mmo.module.fb.domain.match.entity.MatchFieldProvenance;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchFieldProvenanceRepository;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.policy.ProviderRoute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Field-level merge gate. Primary sources may refresh their own fields; secondary sources
 * may only fill null canonical fields (or refresh fields they previously supplied), and
 * stale source revisions never replace newer revisions from the same provider.
 */
@Service
@RequiredArgsConstructor
public class SourceAwareMergeService implements SourceAwareMergePort {

    private final DataSourcePolicy dataSourcePolicy;
    private final MatchFieldProvenanceRepository provenanceRepository;

    @Override
    @Transactional
    public boolean merge(Match match, Provider provider, Dataset dataset, String fieldKey,
                         boolean currentValuePresent, Instant sourceUpdatedAt, Runnable applyValue) {
        if (match == null || match.getId() == null || provider == null || dataset == null
                || fieldKey == null || fieldKey.isBlank() || applyValue == null) {
            return false;
        }

        ProviderRoute route = dataSourcePolicy.route(dataset, match.getSeason());
        if (!route.allows(provider)) return false;

        MatchProvider domainProvider = MatchProvider.fromName(provider.name());
        MatchFieldProvenance current = provenanceRepository
                .findByMatchIdAndFieldKey(match.getId(), fieldKey).orElse(null);
        boolean sameSource = current != null && current.getProvider() == domainProvider;
        boolean primary = route.primary() == provider;

        if (!primary) {
            // Secondary sources are strictly fill-only unless they own the existing value.
            if (!sameSource && currentValuePresent) return false;
        }

        if (sameSource && sourceUpdatedAt != null && current.getSourceUpdatedAt() != null
                && sourceUpdatedAt.isBefore(current.getSourceUpdatedAt())) {
            return false;
        }

        applyValue.run();
        MatchFieldProvenance provenance = current == null
                ? MatchFieldProvenance.builder().match(match).fieldKey(fieldKey).build()
                : current;
        provenance.setDatasetKey(dataset.name());
        provenance.setProvider(domainProvider);
        provenance.setSourceUpdatedAt(sourceUpdatedAt);
        provenance.setFetchedAt(Instant.now());
        provenanceRepository.save(provenance);
        return true;
    }
}
