package com.mmo.module.fb.ingest.adapter.out.persistence.identity;

import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.league.repository.SeasonExternalIdRepository;
import com.mmo.module.fb.domain.league.repository.SeasonRepository;
import com.mmo.module.fb.ingest.application.port.out.SeasonIngestionQueryPort;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** JPA adapter for the season-selection queries used by ingestion. */
@Repository
@RequiredArgsConstructor
public class SeasonIngestionQueryJpaAdapter implements SeasonIngestionQueryPort {
    private final SeasonExternalIdRepository seasonExternalIdRepository;
    private final SeasonRepository seasonRepository;

    @Override
    public List<Season> findMappedSeasons(Provider provider) {
        return seasonExternalIdRepository.findAllByProvider(provider).stream()
                .map(mapping -> mapping.getSeason())
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public List<Season> findAllForActiveLeagues() {
        return seasonRepository.findAllForActiveLeagues();
    }
}
