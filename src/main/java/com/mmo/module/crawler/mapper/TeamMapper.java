package com.mmo.module.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.entity.Team;
import com.mmo.understat.model.LeagueData;
import org.springframework.stereotype.Component;

@Component
public class TeamMapper extends AbstractMapper<LeagueData.TeamData, Team> {

    @Override
    public Team map(LeagueData.TeamData source, Team target) {
        target.setName(source.getTitle());
        target.setUnderStatId(source.getId());
        return target;
    }
}
