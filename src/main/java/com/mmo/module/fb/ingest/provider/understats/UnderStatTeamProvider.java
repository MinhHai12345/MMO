package com.mmo.module.fb.ingest.provider.understats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.model.TeamQuery;
import com.mmo.module.fb.ingest.provider.spi.TeamProvider;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import com.mmo.module.fb.ingest.provider.understats.mapper.UnderStatTeamMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** Fetches UnderStat teams for one league-season. */
@Component
@RequiredArgsConstructor
public class UnderStatTeamProvider implements TeamProvider {
    private static final TypeReference<UnderStatDtos.LeagueData> RESPONSE_TYPE = new TypeReference<>() { };

    private final UnderStatClient client;
    private final UnderStatTeamMapper mapper;

    @Override
    public Provider getProvider() {
        return Provider.UNDER_STATS;
    }

    @Override
    public List<ProviderTeam> fetchTeams(TeamQuery query) {
        if (query == null || !StringUtils.hasText(query.leagueIdentify())
                || !StringUtils.hasText(query.season())) {
            return List.of();
        }
        int year = Integer.parseInt(query.season());
        UnderStatDtos.LeagueData response = client.fetchLeagueData(query.leagueIdentify(), year, RESPONSE_TYPE);
        return mapper.toProviderTeams(response);
    }
}
