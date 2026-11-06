package com.mmo.module.fb.ingest.adapter.out.provider.sofascore;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.application.model.provider.ProviderMatchAdvancedStatistics;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client.AbstractSofaScoreProvider;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.client.SofaScoreClient;
import com.mmo.module.fb.ingest.adapter.out.provider.sofascore.mapper.SofaScoreMatchStatisticsMapper;
import com.mmo.module.fb.ingest.application.port.out.provider.MatchStatisticsIngestionProviderPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/** SofaScore adapter for fetching one event's advanced statistics. */
@Component
@RequiredArgsConstructor
public class SofaScoreMatchStatisticsProvider extends AbstractSofaScoreProvider
        implements MatchStatisticsIngestionProviderPort {

    private final SofaScoreClient sofaScoreClient;
    private final SofaScoreMatchStatisticsMapper mapper;

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    @Override
    public Optional<ProviderMatchAdvancedStatistics> fetchMatchStatistics(String externalMatchId) {
        if (!StringUtils.hasText(externalMatchId)) return Optional.empty();
        final Long matchId;
        try {
            matchId = Long.valueOf(externalMatchId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid SofaScore match ID: " + externalMatchId, exception);
        }
        return withPage(page -> mapper.toProviderStatistics(
                externalMatchId, sofaScoreClient.fetchMatchStatistics(matchId, page)));
    }
}
