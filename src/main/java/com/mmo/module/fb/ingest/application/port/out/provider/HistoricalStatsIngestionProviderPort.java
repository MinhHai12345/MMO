package com.mmo.module.fb.ingest.application.port.out.provider;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderLeagueMonthlyStats;
import com.mmo.module.fb.ingest.provider.model.ProviderMatchStatistics;
import com.mmo.module.fb.ingest.provider.model.ProviderTeamMatchStats;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

/** Application-owned outbound capability for historical team and league statistics. */
public interface HistoricalStatsIngestionProviderPort {
    Provider getProvider();
    List<ProviderMatchStatistics> fetchLeagueMatches(String leagueSlug, int season);
    List<ProviderTeamMatchStats> fetchTeamHistory(String leagueSlug, String teamSlug, int season);
    Optional<ProviderLeagueMonthlyStats> fetchLeagueMonthlyStats(String leagueSlug, int season, YearMonth month);
}
