package com.mmo.module.fb.ingest.provider.spi;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLeagueMonthlyStats;
import com.mmo.module.fb.ingest.provider.model.ProviderMatchStatistics;
import com.mmo.module.fb.ingest.provider.model.ProviderTeamMatchStats;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/**
 * Provider contract for historical, team-level advanced statistics used by pre-match analytics.
 * The returned values are source-neutral and do not contain persistence entities.
 */
public interface HistoricalStatsProvider {

    Provider getProvider();

    List<ProviderMatchStatistics> fetchLeagueMatches(String leagueSlug, int season);

    List<ProviderTeamMatchStats> fetchTeamHistory(String teamSlug, int season);

    Optional<ProviderLeagueMonthlyStats> fetchLeagueMonthlyStats(
            String leagueSlug, int season, YearMonth month);
}
