package com.mmo.module.fb.ingest.adapter.out.persistence.identity;

import com.mmo.module.fb.domain.team.repository.TeamExternalIdRepository;
import com.mmo.module.fb.ingest.application.port.out.TeamExternalMappingPort;
import com.mmo.module.fb.ingest.enums.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

/** JPA adapter translating persistence entities into application-owned mapping data. */
@Repository
@RequiredArgsConstructor
public class TeamExternalMappingJpaAdapter implements TeamExternalMappingPort {
    private final TeamExternalIdRepository repository;

    @Override
    public List<TeamMapping> findAllByProvider(Provider provider) {
        return repository.findAllByProvider(provider).stream()
                .filter(mapping -> mapping.getTeam() != null)
                .map(mapping -> new TeamMapping(mapping.getTeam().getId(), mapping.getExternalId()))
                .toList();
    }
}
