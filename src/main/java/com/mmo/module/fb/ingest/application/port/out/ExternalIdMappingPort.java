package com.mmo.module.fb.ingest.application.port.out;

import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchExternalId;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.domain.team.entity.TeamExternalId;
import com.mmo.module.fb.ingest.enums.Provider;

/** Application-facing port for registering canonical team and match provider identities. */
public interface ExternalIdMappingPort {
    MatchExternalId registerMatch(Match match, Provider provider, String externalId);
    TeamExternalId registerTeam(Team team, Provider provider, String externalId);
}
