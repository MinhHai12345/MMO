package com.mmo.module.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.converter.DynamicConverter;
import com.mmo.entity.Match;
import com.mmo.entity.Team;
import com.mmo.entity.enums.MatchStatus;
import com.mmo.understat.model.LeagueData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchMapper extends AbstractMapper<LeagueData.MatchDate, Match> {
    private final DynamicConverter dynamicConverter;

    @Override
    public Match map(LeagueData.MatchDate source, Match target) {
        target.setHomeTeam(dynamicConverter.convert(source.getH(), Team.class));
        target.setAwayTeam(dynamicConverter.convert(source.getA(), Team.class));
        target.setAwayScore(Integer.valueOf(source.getGoals().get("a")));
        target.setHomeScore(Integer.valueOf(source.getGoals().get("h")));
        target.setUnderStatMatchId(source.getId());
        target.setMatchTime(source.getDatetime());
        target.setStatus(source.isResult() ? MatchStatus.FINISHED : MatchStatus.UPCOMING);
        return target;
    }
}
