package com.mmo.module.fb.crawler.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.enums.MatchStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchMapper extends AbstractMapper<JsonNode, Match> {

    @Override
    public Match map(JsonNode source, Match target) {
        JsonNode homeScoreNode = source.path("homeScore");
        JsonNode awayScoreNode = source.path("awayScore");

        String status = source.path("status").path("type").asText();

        return Match.builder()
                .sofaScoreHomeTeamId(source.path("homeTeam").path("id").asLong())
                .sofaScoreAwayTeamId(source.path("awayTeam").path("id").asLong())
                .sofaScoreId(source.path("id").asLong())
                .slug(source.path("slug").asText())
                .homeScore(homeScoreNode.has("current") ? homeScoreNode.path("current").asInt() : null)
                .awayScore(awayScoreNode.has("current") ? awayScoreNode.path("current").asInt() : null)
                .status("finished".equals(status) ? MatchStatus.FINISHED : MatchStatus.UPCOMING)
                .matchTime(source.path("startTimestamp").asLong())
                .round(source.path("roundInfo").path("round").asInt(0))
                .build();
    }
}
