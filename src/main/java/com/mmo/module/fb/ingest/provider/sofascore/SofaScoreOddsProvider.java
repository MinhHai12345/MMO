package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.ingest.provider.model.OddsQuery;
import com.mmo.module.fb.ingest.provider.model.ProviderOdds;
import com.mmo.module.fb.ingest.provider.model.sofa.SofaOddsData;
import com.mmo.module.fb.ingest.provider.service.SofaCrawlerService;
import com.mmo.module.fb.ingest.provider.sofascore.mapper.SofaScoreOddsMapper;
import com.mmo.module.fb.ingest.provider.spi.OddsProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SofaScoreOddsProvider extends AbstractSofaScoreProvider implements OddsProvider {

    private final SofaScoreOddsMapper mapper;

    public SofaScoreOddsProvider(SofaCrawlerService sofaCrawlerService, SofaScoreOddsMapper mapper) {
        super(sofaCrawlerService);
        this.mapper = mapper;
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
                    sofaCrawlerService.fetchDailyMatchOdds(page);
            if (allOdds == null) return List.of();
            return mapper.toProviderOdds(allOdds.get(eventId));
        });
    }
}
