package com.mmo.module.fb.ingest.provider.sofascore;

import com.mmo.module.fb.ingest.enums.Provider;
import com.mmo.module.fb.ingest.provider.model.ProviderLineups;
import com.mmo.module.fb.ingest.provider.sofascore.client.SofaScoreLineupsClient;
import com.mmo.module.fb.ingest.provider.sofascore.mapper.SofaScoreLineupsMapper;
import com.mmo.module.fb.ingest.application.port.out.provider.PlayerLineupsIngestionPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** SofaScore implementation of the provider-neutral player-lineups SPI. */
@Component
@RequiredArgsConstructor
public class SofaScoreLineupsProvider implements PlayerLineupsIngestionPort {

    private final SofaScoreLineupsClient client;
    private final SofaScoreLineupsMapper mapper;

    @Override
    public Provider getProvider() {
        return Provider.SOFA_SCORE;
    }

    @Override
    public Optional<ProviderLineups> fetchLineups(String externalMatchId) {
        if (externalMatchId == null || externalMatchId.isBlank()) return Optional.empty();
        final Long matchId;
        try {
            matchId = Long.valueOf(externalMatchId.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid SofaScore match ID: " + externalMatchId, exception);
        }
        if (matchId <= 0) throw new IllegalArgumentException("SofaScore match ID must be positive");
        var response = client.fetchLineups(matchId);
        if (response == null) return Optional.empty();
        ProviderLineups result = mapper.toModel(response);
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }
}
