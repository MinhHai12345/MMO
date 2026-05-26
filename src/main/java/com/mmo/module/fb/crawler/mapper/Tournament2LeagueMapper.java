package com.mmo.module.fb.crawler.mapper;

import com.mmo.converter.AbstractMapper;
import com.mmo.module.fb.crawler.model.sofa.SofaUniqueTournamentsData;
import com.mmo.module.fb.entity.League;
import org.springframework.stereotype.Component;

@Component
public class Tournament2LeagueMapper extends
        AbstractMapper<SofaUniqueTournamentsData.UniqueTournamentDTO, League> {
    @Override
    public League map(SofaUniqueTournamentsData.UniqueTournamentDTO source, League target) {
        target.setName(source.getName());
        target.setSlug(source.getSlug());
        target.setSofaScoreId(source.getId());
        target.setActive(true);
        return target;
    }
}
