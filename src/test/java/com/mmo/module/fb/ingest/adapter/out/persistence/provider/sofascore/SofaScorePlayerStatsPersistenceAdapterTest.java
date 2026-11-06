package com.mmo.module.fb.ingest.adapter.out.persistence.provider.sofascore;

import com.mmo.module.fb.domain.league.entity.League;
import com.mmo.module.fb.domain.league.entity.Season;
import com.mmo.module.fb.domain.match.entity.Match;
import com.mmo.module.fb.domain.match.entity.MatchFieldProvenance;
import com.mmo.module.fb.domain.match.repository.MatchFieldProvenanceRepository;
import com.mmo.module.fb.domain.match.repository.MatchRepository;
import com.mmo.module.fb.domain.player.entity.Player;
import com.mmo.module.fb.domain.player.entity.PlayerExternalId;
import com.mmo.module.fb.domain.player.entity.PlayerMatchStats;
import com.mmo.module.fb.domain.player.repository.PlayerExternalIdRepository;
import com.mmo.module.fb.domain.player.repository.PlayerMatchStatsRepository;
import com.mmo.module.fb.domain.player.repository.PlayerRepository;
import com.mmo.module.fb.domain.team.entity.Team;
import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.adapter.out.persistence.core.SourceAwareMergeAdapter;
import com.mmo.module.fb.ingest.policy.DataSourcePolicy;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLineupPlayer;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLineups;
import com.mmo.module.fb.ingest.application.model.provider.ProviderPlayerMatchStats;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SofaScorePlayerStatsPersistenceAdapterTest {

    @Test
    void persistsPlayerMetricsWithMatchAndPlayerScopedProvenance() {
        MatchRepository matchRepository = mock(MatchRepository.class);
        PlayerRepository playerRepository = mock(PlayerRepository.class);
        PlayerExternalIdRepository externalIdRepository = mock(PlayerExternalIdRepository.class);
        PlayerMatchStatsRepository statsRepository = mock(PlayerMatchStatsRepository.class);
        MatchFieldProvenanceRepository provenanceRepository = mock(MatchFieldProvenanceRepository.class);

        Team home = new Team();
        home.setId(10L);
        Team away = new Team();
        away.setId(11L);
        League league = new League();
        league.setName("Premier League");
        Season season = new Season();
        season.setYear("2024/2025");
        season.setLeague(league);
        Match match = new Match();
        match.setId(55L);
        match.setSeason(season);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        when(matchRepository.findById(55L)).thenReturn(Optional.of(match));
        when(statsRepository.findAllByMatchIdOrderByIdAsc(55L)).thenReturn(List.of());

        Player player = new Player();
        player.setId(777L);
        player.setName("Test Player");
        player.setTeam(home);
        when(externalIdRepository.findByProviderAndExternalId(Provider.SOFA_SCORE, "123"))
                .thenReturn(Optional.of(PlayerExternalId.builder()
                        .player(player).provider(Provider.SOFA_SCORE).externalId("123").build()));

        DataSourcePolicy policy = new DataSourcePolicy();
        SourceAwareMergeAdapter mergeService = new SourceAwareMergeAdapter(policy, provenanceRepository);
        SofaScorePlayerStatsPersistenceAdapter service = new SofaScorePlayerStatsPersistenceAdapter(
                matchRepository, playerRepository, externalIdRepository, statsRepository, mergeService);

        var statistics = new ProviderPlayerMatchStats(
                null, 2, null, null, null, BigDecimal.ZERO, null, null, null,
                null, null, null, null, null, null, null, null, null);
        var lineupPlayer = new ProviderLineupPlayer(
                "123", "Test Player", "Midfielder", "XX", 180, 75, statistics);
        var response = new ProviderLineups(List.of(lineupPlayer), List.of());

        assertEquals(1, service.persist(55L, response));

        verify(statsRepository).saveAll(argThat(rows -> {
            PlayerMatchStats saved = rows.iterator().next();
            return saved.getGoals() == 2 && saved.getXg().signum() == 0;
        }));
        verify(provenanceRepository, times(2)).saveAll(anyList());
        verify(provenanceRepository, atLeastOnce()).saveAll(argThat(rows ->
                rows.stream().map(MatchFieldProvenance.class::cast)
                        .anyMatch(provenance -> provenance.getMatch().getId().equals(55L)
                                && provenance.getProvider().name().equals("SOFA_SCORE")
                                && provenance.getFieldKey().equals("player:777:goals"))));
        verify(provenanceRepository, atLeastOnce()).saveAll(argThat(rows ->
                rows.stream().map(MatchFieldProvenance.class::cast)
                        .anyMatch(provenance -> provenance.getFieldKey().equals("player:777:xg"))));
    }
}
