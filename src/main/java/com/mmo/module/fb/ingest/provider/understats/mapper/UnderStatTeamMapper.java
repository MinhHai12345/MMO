package com.mmo.module.fb.ingest.provider.understats.mapper;

import com.mmo.module.fb.ingest.provider.model.MmoProviderDataStandardizer;
import com.mmo.module.fb.ingest.provider.model.ProviderTeam;
import com.mmo.module.fb.ingest.provider.understats.dto.UnderStatDtos;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/** Maps UnderStat team catalogue rows to the provider-neutral team model. */
@Component
public class UnderStatTeamMapper {
    public List<ProviderTeam> toProviderTeams(UnderStatDtos.LeagueData response) {
        if (response == null || response.teams() == null || response.teams().isEmpty()) {
            return List.of();
        }
        return response.teams().entrySet().stream()
                .map(entry -> toProviderTeam(entry.getKey(), entry.getValue()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private ProviderTeam toProviderTeam(String key, UnderStatDtos.LeagueTeam source) {
        if (source == null) return null;
        String externalId = StringUtils.hasText(source.id()) ? source.id().trim() : key;
        String name = MmoProviderDataStandardizer.standardTeamName(source.title());
        if (!StringUtils.hasText(externalId) || !StringUtils.hasText(name)) return null;
        return new ProviderTeam(externalId, name, name, null, null, null, null);
    }
}
