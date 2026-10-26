package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.OddsQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaOddsData;
import com.mmo.module.fb.ingest.provider.sofascore.client.SofaScoreClient;
import com.mmo.module.fb.ingest.provider.sofascore.mapper.SofaScoreOddsMapper;
import com.mmo.module.fb.ingest.provider.spi.OddsProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SofaScoreOddsProvider extends AbstractSofaScoreProvider implements OddsProvider {

    private final SofaScoreOddsMapper mapper;
    private final SofaScoreClient sofaScoreClient;

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    @Override
    public List<ProviderOdds> fetchOdds(OddsQuery query) {
        if (query == null || query.fixtureExternalId() == null) return List.of();
        final long eventId;
        try {
            eventId = Long.parseLong(query.fixtureExternalId());
        } catch (NumberFormatException ex) {
            return List.of();
        }

        return withPage(page -> {
            Map<Long, SofaOddsData.MatchOddDetailDTO> allOdds =
                    sofaScoreClient.fetchDailyMatchOdds(page);
            if (allOdds == null) return List.of();
            return mapper.toProviderOdds(allOdds.get(eventId));
        });
    }
}
