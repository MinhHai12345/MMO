package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaMatchesData;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

@Component
public class SofaScoreFixtureMapper {

    public ProviderFixture toProviderFixture(SofaMatchesData.SofaEventDTO event) {
        if (event == null || event.getId() == null || event.getHomeTeam() == null
                || event.getAwayTeam() == null || event.getHomeTeam().getId() == null
                || event.getAwayTeam().getId() == null) return null;
        Instant kickoff = event.getStartTimestamp() == null
                ? null : Instant.ofEpochSecond(event.getStartTimestamp());
        String status = event.getStatus() == null ? null : mapStatus(event.getStatus().getType());
        return new ProviderFixture(event.getId().toString(),
                event.getHomeTeam().getId().toString(), event.getAwayTeam().getId().toString(),
                kickoff,
                event.getRoundInfo() == null || event.getRoundInfo().getRound() == null
                        ? null : event.getRoundInfo().getRound().toString(),
                null, null, status,
                event.getHomeScore() == null ? null : event.getHomeScore().getCurrent(),
                event.getAwayScore() == null ? null : event.getAwayScore().getCurrent());
    }

    private String mapStatus(String status) {
        if (status == null) return "NS";
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "finished" -> "FT";
            case "inprogress", "live" -> "LIVE";
            case "postponed" -> "PST";
            case "canceled", "cancelled" -> "CANC";
            default -> "NS";
        };
    }
}
