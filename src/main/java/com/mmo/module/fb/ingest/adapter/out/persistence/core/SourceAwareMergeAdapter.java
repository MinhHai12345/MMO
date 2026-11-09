package com.mmo.module.fb.ingest.adapter.out.persistence.core;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchFieldProvenance;
import com.mmo.module.fb.domain.match.enums.MatchProvider;
import com.mmo.module.fb.domain.match.repository.MatchFieldProvenanceRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.ingest.application.model.FieldMergeCommand;
import com.mmo.module.fb.ingest.application.port.out.SourceAwareMergePort;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.policy.Dataset;
import com.mmo.module.fb.ingest.policy.ProviderRoute;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;

/**
 * Field-level merge gate. Provenance is prefetched once per batch and flushed with
 * saveAll, avoiding a provenance SELECT/UPSERT for every field of every fixture.
 */
@Repository
@RequiredArgsConstructor
public class SourceAwareMergeAdapter implements SourceAwareMergePort {

    private final DataSourcePolicy dataSourcePolicy;
    private final MatchRepository matchRepository;
    private final SeasonRepository seasonRepository;
    private final MatchFieldProvenanceRepository provenanceRepository;

    @Override
    @Transactional
    public boolean merge(Match match, Provider provider, Dataset dataset, String fieldKey,
                         boolean currentValuePresent, Instant sourceUpdatedAt, Runnable applyValue) {
        return mergeAll(List.of(new FieldMergeCommand(match, provider, dataset, fieldKey,
                currentValuePresent, sourceUpdatedAt, applyValue))) == 1;
    }

    @Override
    @Transactional
    public int mergeAll(List<FieldMergeCommand> commands) {
        if (commands == null || commands.isEmpty()) return 0;

        List<FieldMergeCommand> valid = commands.stream()
                .filter(Objects::nonNull)
                .filter(command -> command.match() != null && command.match().getId() != null
                        && command.provider() != null && command.dataset() != null
                        && command.fieldKey() != null && !command.fieldKey().isBlank()
                        && command.applyValue() != null)
                .toList();
        if (valid.isEmpty()) return 0;

        List<Long> matchIds = valid.stream().map(command -> command.match().getId()).distinct().toList();
        Set<Long> seasonIds = valid.stream()
                .map(command -> command.match().getSeason())
                .filter(Objects::nonNull)
                .map(Season::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Season> seasonsById = seasonIds.isEmpty() ? Map.of()
                : seasonRepository.findAllWithLeagueByIdIn(seasonIds).stream()
                        .collect(Collectors.toMap(Season::getId, Function.identity(), (left, right) -> left));
        // Serialize provider merges against the same canonical fixture before reading provenance.
        matchRepository.lockAllByIds(matchIds);
        List<String> fieldKeys = valid.stream().map(FieldMergeCommand::fieldKey).distinct().toList();
        Map<ProvenanceKey, MatchFieldProvenance> provenanceByKey = new HashMap<>();
        provenanceRepository.findAllByMatchIdInAndFieldKeyIn(matchIds, fieldKeys).forEach(provenance ->
                provenanceByKey.put(new ProvenanceKey(
                        provenance.getMatch().getId(), provenance.getFieldKey()), provenance));

        List<MatchFieldProvenance> changedProvenance = new ArrayList<>();
        int applied = 0;
        for (FieldMergeCommand command : valid) {
            Match match = command.match();
            Provider provider = command.provider();
            Dataset dataset = command.dataset();
            Season season = match.getSeason() == null ? null : seasonsById.get(match.getSeason().getId());
            if (season == null) continue;
            ProviderRoute route = dataSourcePolicy.route(dataset, season);
            if (!route.allows(provider)) continue;

            ProvenanceKey key = new ProvenanceKey(match.getId(), command.fieldKey());
            MatchFieldProvenance current = provenanceByKey.get(key);
            MatchProvider domainProvider = MatchProvider.fromName(provider.name());
            boolean sameSource = current != null && current.getProvider() == domainProvider;
            boolean primary = route.primary() == provider;

            if (!primary && !sameSource && command.currentValuePresent()) continue;
            if (sameSource && command.sourceUpdatedAt() != null && current.getSourceUpdatedAt() != null
                    && command.sourceUpdatedAt().isBefore(current.getSourceUpdatedAt())) {
                continue;
            }

            command.applyValue().run();
            MatchFieldProvenance provenance = current == null
                    ? MatchFieldProvenance.builder().match(match).fieldKey(command.fieldKey()).build()
                    : current;
            provenance.setDatasetKey(dataset.name());
            provenance.setProvider(domainProvider);
            provenance.setSourceUpdatedAt(command.sourceUpdatedAt());
            provenance.setFetchedAt(Instant.now());
            provenanceByKey.put(key, provenance);
            changedProvenance.add(provenance);
            applied++;
        }

        if (!changedProvenance.isEmpty()) {
            provenanceRepository.saveAll(changedProvenance.stream().distinct().toList());
        }
        return applied;
    }

    private record ProvenanceKey(Long matchId, String fieldKey) {
    }
}
