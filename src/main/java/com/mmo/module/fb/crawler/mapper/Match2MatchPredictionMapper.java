package com.mmo.module.fb.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.MatchPrediction;
import com.mmo.module.fb.entity.enums.MatchPredictionStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

@Component
public class Match2MatchPredictionMapper extends AbstractMapper<Match, MatchPrediction> {


    @Override
    public MatchPrediction map(Match source, MatchPrediction target) {
        target.setMatch(source);
        target.setKickoffTime(Instant.ofEpochSecond(source.getMatchTime())
                .atZone(ZoneId.of("Asia/Ho_Chi_Minh"))
                .toLocalDateTime());
        target.setStatus(MatchPredictionStatus.PENDING);
        return target;
    }
}
