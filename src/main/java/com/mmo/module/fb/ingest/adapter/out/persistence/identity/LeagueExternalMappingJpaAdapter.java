package com.mmo.module.fb.ingest.adapter.out.persistence.identity;

import com.mmo.module.fb.domain.league.repository.LeagueExternalIdRepository;
import com.mmo.module.fb.ingest.application.port.out.LeagueExternalMappingPort;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** JPA adapter translating persistence entities into application-owned mapping data. */
@Repository
@RequiredArgsConstructor
public class LeagueExternalMappingJpaAdapter implements LeagueExternalMappingPort {
    private final LeagueExternalIdRepository repository;

    @Override
    public List<LeagueMapping> findAllByProvider(Provider provider) {
        return repository.findAllByProvider(provider).stream()
                .filter(mapping -> mapping.getLeague() != null)
                .map(mapping -> new LeagueMapping(
                        mapping.getLeague().getId(),
                        mapping.getExternalId(),
                        mapping.getIdentify(),
                        mapping.getLeague().getName(),
                        mapping.getLeague().getCountry(),
                        mapping.getLeague().getLogoUrl()))
                .toList();
    }
}
