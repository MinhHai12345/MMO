package com.mmo.module.fb.ingest.provider.sofascore.mapper;

import com.mmo.module.fb.ingest.provider.sofascore.dto.SofaScoreResponseDTO.EventDTO;

import com.mmo.module.fb.ingest.provider.model.ProviderFixture;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

@Component
public class SofaScoreFixtureMapper {

    public ProviderFixture toProviderFixture(EventDTO event) {
        if (event == null || event.id() == null || event.homeTeam() == null
                || event.awayTeam() == null || event.homeTeam().id() == null
                || event.awayTeam().id() == null) return null;
        Instant kickoff = event.startTimestamp() == null
                ? null : Instant.ofEpochSecond(event.startTimestamp());
        String status = event.status() == null ? null : mapStatus(event.status().type());
        return new ProviderFixture(event.id().toString(),
                event.homeTeam().id().toString(), event.awayTeam().id().toString(),
                kickoff,
                event.roundInfo() == null || event.roundInfo().round() == null
                        ? null : event.roundInfo().round().toString(),
                null, null, status,
                event.homeScore() == null ? null : event.homeScore().current(),
                event.awayScore() == null ? null : event.awayScore().current());
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
