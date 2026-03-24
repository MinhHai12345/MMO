package com.mmo.module.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.entity.Match;
import com.mmo.entity.enums.MatchStatus;
import com.mmo.understat.model.LeagueData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class MatchMapper extends AbstractMapper<LeagueData.MatchDate, Match> {

    @Override
    public Match map(LeagueData.MatchDate source, Match target) {
        target.setUnderStatMatchId(source.getId());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        target.setMatchTime(LocalDateTime.parse(source.getDatetime(), formatter));
        target.setStatus(source.isResult() ? MatchStatus.FINISHED : MatchStatus.UPCOMING);
        target.setAwayScore(source.getGoals().get("a"));
        target.setHomeScore(source.getGoals().get("h"));
        target.setHomeXG(source.getXG().get("h"));
        target.setAwayXG(source.getXG().get("a"));
        if (source.getForecast() != null) {
            target.setWinProbability(source.getForecast().get("w"));
            target.setDrawProbability(source.getForecast().get("d"));
            target.setLossProbability(source.getForecast().get("l"));
        }
        return target;
    }
}
