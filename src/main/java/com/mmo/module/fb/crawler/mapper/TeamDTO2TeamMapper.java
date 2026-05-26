package com.mmo.module.fb.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.crawler.model.sofa.SofaStandingsData;
import com.mmo.module.fb.entity.Team;
import org.springframework.stereotype.Component;

@Component
public class TeamDTO2TeamMapper extends AbstractMapper<SofaStandingsData.TeamDTO, Team> {

    @Override
    public Team map(SofaStandingsData.TeamDTO source, Team target) {
        return Team.builder()
                .sofaScoreId(source.getId())
                .name(source.getName())
                .code(source.getNameCode())
                .shortName(source.getShortName())
                .slug(source.getSlug())
                .logoUrl(String.format("https://api.sofascore.app/api/v1/team/%d/image", source.getId()))
                .build();
    }
}
