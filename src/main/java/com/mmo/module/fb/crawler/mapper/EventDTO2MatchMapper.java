package com.mmo.module.fb.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.crawler.model.sofa.SofaMatchData;
import com.mmo.module.fb.entity.Match;
import com.mmo.module.fb.entity.enums.MatchStatus;
import org.springframework.stereotype.Component;

@Component
public class EventDTO2MatchMapper extends AbstractMapper<SofaMatchData.SofaEventDTO, Match> {

    @Override
    public Match map(SofaMatchData.SofaEventDTO source, Match target) {
        SofaMatchData.ScoreDetailDTO homeScore = source.getHomeScore();
        SofaMatchData.ScoreDetailDTO awayScore = source.getAwayScore();

        return Match.builder()
                .sofaScoreHomeTeamId(source.getHomeTeam().getId())
                .sofaScoreAwayTeamId(source.getAwayTeam().getId())
                .sofaScoreId(source.getId())
                .slug(source.getSlug())
                .homeScore(homeScore.getCurrent())
                .awayScore(awayScore.getCurrent())
                .status("finished".equals(source.getStatus().getType()) ? MatchStatus.FINISHED : MatchStatus.UPCOMING)
                .matchTime(source.getStartTimestamp())
                .round(source.getRoundInfo().getRound())
                .build();
    }
}
