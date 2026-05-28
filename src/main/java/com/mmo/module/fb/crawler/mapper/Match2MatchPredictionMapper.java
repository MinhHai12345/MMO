package com.mmo.module.fb.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchPrediction;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

@Component
public class Match2MatchPredictionMapper extends AbstractMapper<Match, MatchPrediction> {


    @Override
    public MatchPrediction map(Match source, MatchPrediction target) {
        target.setMatch(source);
        target.setKickoffTime(Instant.ofEpochMilli(source.getMatchTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime());
        return target;
    }
}
