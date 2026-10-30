package com.mmo.module.fb.ingest.provider.understats;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderSeason;
import com.mmo.module.fb.ingest.provider.model.SeasonQuery;
import com.mmo.module.fb.ingest.provider.spi.SeasonProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

/**
 * UnderStat uses the season start year in /getLeagueData/{league}/{season}.
 *
 * The /getStatData endpoint is a monthly statistics catalogue, not a complete
 * season catalogue. Do not use its sparse monthly rows to decide which seasons
 * exist: expose the supported historical range directly.
 */
@Component
public class UnderStatSeasonProvider implements SeasonProvider {
    private static final int FIRST_SUPPORTED_SEASON = 2014;

    @Override
    public Provider getProvider() {
        return Provider.UNDER_STATS;
    }

    @Override
    public List<ProviderSeason> fetchSeasons(SeasonQuery query) {
        if (query == null || !StringUtils.hasText(query.leagueExternalId())) {
            return List.of();
        }

        String leagueExternalId = query.leagueExternalId().trim();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int currentSeasonStartYear = today.getMonthValue() >= 7
                ? today.getYear() : today.getYear() - 1;

        return IntStream.rangeClosed(FIRST_SUPPORTED_SEASON, currentSeasonStartYear)
                .mapToObj(year -> new ProviderSeason(
                        leagueExternalId + ":" + year,
                        String.valueOf(year),
                        year == currentSeasonStartYear,
                        null,
                        null))
                .toList();
    }
}
